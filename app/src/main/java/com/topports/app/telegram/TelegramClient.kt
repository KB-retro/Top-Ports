package com.topports.app.telegram

import android.content.Context
import android.os.Build
import android.util.Log
import com.topports.app.BuildConfig
import com.topports.app.storage.Storage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TdException(val code: Int, message: String) : Exception(message)

/**
 * Envoltorio de TDLib con corrutinas. Cada usuario inicia sesión con su propia
 * cuenta de Telegram; las descargas van directas de los servidores de Telegram.
 */
class TelegramClient(private val context: Context) {

    private val _authState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authState: StateFlow<TdApi.AuthorizationState?> = _authState.asStateFlow()

    /** Último estado conocido de cada archivo (progreso de descarga incluido). */
    private val _files = MutableStateFlow<Map<Int, TdApi.File>>(emptyMap())
    val files: StateFlow<Map<Int, TdApi.File>> = _files.asStateFlow()

    private val _newMessages = MutableSharedFlow<TdApi.Message>(extraBufferCapacity = 64)
    val newMessages: SharedFlow<TdApi.Message> = _newMessages

    @Volatile
    private var client: Client? = null

    val isConfigured: Boolean
        get() = BuildConfig.TELEGRAM_API_ID != 0 && BuildConfig.TELEGRAM_API_HASH.isNotBlank()

    @Synchronized
    fun start() {
        if (client != null || !isConfigured) return
        runCatching { Client.execute(TdApi.SetLogVerbosityLevel(1)) }
        client = Client.create(Client.ResultHandler { onUpdate(it) }, null, null)
    }

    private fun onUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> onAuthorizationState(update.authorizationState)
            is TdApi.UpdateFile -> rememberFile(update.file)
            is TdApi.UpdateNewMessage -> _newMessages.tryEmit(update.message)
            // Post editado (texto o archivo cambiado): se vuelve a leer y se trata como nuevo,
            // así el catálogo se entera de las actualizaciones al momento.
            is TdApi.UpdateMessageEdited -> client?.send(
                TdApi.GetMessage(update.chatId, update.messageId),
                Client.ResultHandler { result -> if (result is TdApi.Message) _newMessages.tryEmit(result) },
            )
        }
    }

    private fun onAuthorizationState(state: TdApi.AuthorizationState) {
        _authState.value = state
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> sendParameters()
            is TdApi.AuthorizationStateClosed -> {
                // Tras cerrar sesión TDLib se apaga: creamos otro cliente para volver a entrar.
                synchronized(this) { client = null }
                _files.value = emptyMap()
                start()
            }
            else -> Unit
        }
    }

    /** Carpeta donde TDLib guarda las descargas (y la caché de imágenes). */
    @Volatile
    var filesDir: File = File(context.filesDir, "tdlib-files")
        private set

    /**
     * Con permiso de archivos, las descargas van a /TopPorts/.telegram, en la misma memoria que las
     * carpetas de los juegos: al terminar, el archivo se mueve (renombrar) sin copiarlo ni escribirlo
     * otra vez. Sin permiso, a la carpeta privada de la app.
     */
    private fun chooseFilesDir(): File {
        if (Storage.hasAllFilesAccess(context)) {
            val dir = File(Storage.externalRoot, "TopPorts/.telegram")
            if (dir.isDirectory || dir.mkdirs()) {
                // Que la galería no muestre las carátulas y vídeos de la caché.
                runCatching { File(dir, ".nomedia").createNewFile() }
                return dir
            }
        }
        return File(context.filesDir, "tdlib-files")
    }

    /**
     * Tras dar el permiso de archivos: reinicia TDLib (la sesión se conserva) para que las
     * descargas vayan ya a /TopPorts/.telegram. No se hace con descargas en marcha.
     */
    fun applyStorageLocation() {
        if (_authState.value !is TdApi.AuthorizationStateReady) return
        if (chooseFilesDir() == filesDir) return
        if (_files.value.values.any { it.local.isDownloadingActive }) return
        client?.send(TdApi.Close(), Client.ResultHandler { })
    }

    private fun sendParameters() {
        val params = TdApi.SetTdlibParameters().apply {
            useTestDc = false
            databaseDirectory = File(context.filesDir, "tdlib").absolutePath
            filesDirectory = chooseFilesDir().also { filesDir = it }.absolutePath
            databaseEncryptionKey = ByteArray(0)
            useFileDatabase = true
            useChatInfoDatabase = true
            useMessageDatabase = true
            useSecretChats = false
            apiId = BuildConfig.TELEGRAM_API_ID
            apiHash = BuildConfig.TELEGRAM_API_HASH
            systemLanguageCode = Locale.getDefault().language.ifBlank { "es" }
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            systemVersion = "Android ${Build.VERSION.RELEASE}"
            applicationVersion = BuildConfig.VERSION_NAME
        }
        client?.send(params, Client.ResultHandler { result ->
            if (result is TdApi.Error) Log.e(TAG, "setTdlibParameters: ${result.message}")
        })
    }

    fun rememberFile(file: TdApi.File) {
        _files.update { it + (file.id to file) }
    }

    suspend fun <T : TdApi.Object> send(function: TdApi.Function<T>): T {
        val c = client ?: throw TdException(-1, "Telegram no está iniciado")
        return suspendCancellableCoroutine { cont ->
            c.send(function, Client.ResultHandler { result ->
                if (result is TdApi.Error) {
                    cont.resumeWithException(TdException(result.code, result.message))
                } else {
                    @Suppress("UNCHECKED_CAST")
                    cont.resume(result as T)
                }
            })
        }
    }

    // --- Inicio de sesión ---

    suspend fun sendPhoneNumber(phone: String) {
        val settings = TdApi.PhoneNumberAuthenticationSettings().apply {
            authenticationTokens = arrayOf()
        }
        send(TdApi.SetAuthenticationPhoneNumber(phone, settings))
    }

    suspend fun sendCode(code: String) {
        send(TdApi.CheckAuthenticationCode(code))
    }

    suspend fun sendPassword(password: String) {
        send(TdApi.CheckAuthenticationPassword(password))
    }

    suspend fun logOut() {
        send(TdApi.LogOut())
    }

    // --- Archivos ---

    /** Descarga un archivo pequeño (fotos) y devuelve su ruta local. */
    suspend fun downloadNow(fileId: Int, priority: Int = 16): String? {
        val file = send(TdApi.DownloadFile(fileId, priority, 0, 0, true))
        rememberFile(file)
        return file.local.path.takeIf { file.local.isDownloadingCompleted && it.isNotEmpty() }
    }

    suspend fun startDownload(fileId: Int, priority: Int = 1) {
        rememberFile(send(TdApi.DownloadFile(fileId, priority, 0, 0, false)))
    }

    suspend fun pauseDownload(fileId: Int) {
        send(TdApi.CancelDownloadFile(fileId, false))
        refreshFile(fileId)
    }

    suspend fun deleteFile(fileId: Int) {
        runCatching { send(TdApi.DeleteFile(fileId)) }
        refreshFile(fileId)
    }

    suspend fun refreshFile(fileId: Int): TdApi.File =
        send(TdApi.GetFile(fileId)).also { rememberFile(it) }

    companion object {
        private const val TAG = "TelegramClient"
    }
}
