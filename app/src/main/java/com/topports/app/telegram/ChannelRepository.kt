package com.topports.app.telegram

import com.topports.app.BuildConfig
import com.topports.core.CatalogBuilder
import com.topports.core.ChannelLink
import com.topports.core.ChannelMessage
import com.topports.core.Game
import com.topports.core.PhotoRef
import com.topports.core.RemoteFile
import com.topports.core.VideoRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import com.topports.core.AppRelease
import com.topports.core.Announcement
import com.topports.core.Announcements
import com.topports.core.SelfUpdate

data class CatalogState(
    val games: List<Game> = emptyList(),
    val loading: Boolean = false,
    val scannedMessages: Int = 0,
    /** Fecha del mensaje más antiguo leído (para comprobar si se ha leído el canal entero). */
    val oldestDate: Int = 0,
    val channelTitle: String = "",
    val chatId: Long = 0,
    val error: String? = null,
    /** APK de Top Ports más nuevo que el instalado, si se ha publicado en el canal. */
    val appUpdate: AppRelease? = null,
    /** Último aviso de novedades ("ACTUALIZACIÓN DE PORTS") del canal. */
    val announcement: Announcement? = null,
)

/** Corazones (reacción ❤) de un post del canal: cuántos hay y si el usuario ha dado el suyo. */
data class Hearts(val count: Int = 0, val mine: Boolean = false)

class ChannelNotAccessibleException : Exception(
    "No se encuentra el canal. Únete al canal desde Telegram con esta misma cuenta y pulsa Reintentar.",
)

/** Lee el canal de ports y lo convierte en catálogo. */
class ChannelRepository(
    private val tg: TelegramClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(CatalogState())
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    private val messages = LinkedHashMap<Long, ChannelMessage>()

    /** Corazones por id de mensaje (solo los que tienen alguno). */
    private val _hearts = MutableStateFlow<Map<Long, Hearts>>(emptyMap())
    val hearts: StateFlow<Map<Long, Hearts>> = _hearts.asStateFlow()
    private var loadJob: Job? = null
    private var listening = false

    /**
     * Posts más recientes pedidos directamente al servidor (no a la copia local de TDLib), y el
     * canal se cierra y se vuelve a abrir para que Telegram lo sincronice. Así un juego recién
     * publicado aparece al recargar o al volver a la app, sin tener que reiniciarla.
     */
    fun syncRecent() {
        val chatId = _state.value.chatId
        if (chatId == 0L) return
        scope.launch {
            runCatching { tg.send(TdApi.CloseChat(chatId)) }
            runCatching { tg.send(TdApi.OpenChat(chatId)) }
            val filters = listOf(
                TdApi.SearchMessagesFilterPhoto(),
                TdApi.SearchMessagesFilterDocument(),
                TdApi.SearchMessagesFilterVideo(),
            )
            for (filter in filters) {
                runCatching { tg.send(TdApi.SearchChatMessages(chatId, null, "", null, 0, 0, 50, filter)) }
                    .getOrNull()?.messages?.forEach { add(it) }
            }
            // Y los últimos mensajes de texto (avisos de novedades), también del servidor.
            runCatching { tg.send(TdApi.GetChatHistory(chatId, 0, 0, 50, false)) }
                .getOrNull()?.messages?.forEach { add(it) }
            publish()
        }
    }

    fun refresh() {
        // Lo nuevo primero, aunque la lectura completa siga en marcha.
        syncRecent()
        if (loadJob?.isActive == true) return
        loadJob = scope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val chat = resolveChat()
                _state.update { it.copy(channelTitle = chat.title, chatId = chat.id) }
                runCatching { tg.send(TdApi.OpenChat(chat.id)) }
                listenForNewPosts(chat.id)
                loadHistory(chat.id)
                _state.update { it.copy(loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    init {
        // Si TDLib se reinicia (p. ej. al dar el permiso de archivos) mientras se lee el canal, la
        // lectura se corta: al volver a estar listo se lee otra vez para no quedarse a medias.
        scope.launch {
            var wasReady = false
            tg.authState.collect { state ->
                val ready = state is TdApi.AuthorizationStateReady
                if (ready && !wasReady) tg.setOnline(true)
                if (ready && !wasReady && _state.value.chatId != 0L) {
                    // La lectura anterior pudo quedarse colgada con el TDLib viejo: se empieza de nuevo.
                    loadJob?.cancel()
                    refresh()
                }
                wasReady = ready
            }
        }
    }

    fun game(id: Long): Game? = _state.value.games.firstOrNull { it.id == id }

    private suspend fun loadHistory(chatId: Long) {
        var fromId = 0L
        var emptyPages = 0
        while (messages.size < MAX_MESSAGES) {
            val page = tg.send(TdApi.GetChatHistory(chatId, fromId, 0, 100, false))
            if (page.messages.isEmpty()) {
                // TDLib devuelve páginas vacías mientras sincroniza (sobre todo con datos móviles):
                // se insiste un rato antes de dar el canal por leído entero, o faltarían juegos.
                if (++emptyPages >= EMPTY_PAGE_RETRIES) break
                delay(600L * emptyPages)
                continue
            }
            emptyPages = 0
            page.messages.forEach { add(it) }
            fromId = page.messages.last().id
            publish()
        }
        publish()
        fillFromServerSearch(chatId)
    }

    /**
     * Segunda pasada directamente contra el servidor: todas las fotos, archivos y vídeos del canal.
     * GetChatHistory se apoya en la copia local de TDLib y, si en un dispositivo está incompleta,
     * se para antes de tiempo (en un móvil faltaban unos 40 juegos). Esto rellena lo que falte.
     */
    private suspend fun fillFromServerSearch(chatId: Long) {
        val filters = listOf(
            TdApi.SearchMessagesFilterPhoto(),
            TdApi.SearchMessagesFilterDocument(),
            TdApi.SearchMessagesFilterVideo(),
        )
        for (filter in filters) {
            var from = 0L
            var pages = 0
            while (pages++ < MAX_MESSAGES / 100) {
                val found = runCatching {
                    tg.send(TdApi.SearchChatMessages(chatId, null, "", null, from, 0, 100, filter))
                }.getOrNull() ?: break
                found.messages.forEach { add(it) }
                publish()
                if (found.messages.isEmpty() || found.nextFromMessageId == 0L) break
                from = found.nextFromMessageId
            }
        }
    }

    private fun listenForNewPosts(chatId: Long) {
        if (listening) return
        listening = true
        scope.launch {
            tg.newMessages.collect { message ->
                if (message.chatId == chatId) {
                    add(message)
                    publish()
                }
            }
        }
        // Corazones que dan otros usuarios (o tú desde Telegram) mientras la app está abierta.
        scope.launch {
            tg.interactions.collect { update ->
                if (update.chatId == chatId) setHearts(update.messageId, heartsOf(update.interactionInfo))
            }
        }
    }

    /** Mensaje del juego donde están (o irán) sus corazones y cuántos tiene. */
    fun heartsOf(game: Game, all: Map<Long, Hearts> = _hearts.value): Pair<Long, Hearts> {
        val known = game.messageIds.mapNotNull { id -> all[id]?.let { id to it } }
        // El que ya tiene tu corazón; si no, el que más tiene; si ninguno, el primer mensaje del post.
        return known.firstOrNull { it.second.mine } ?: known.maxByOrNull { it.second.count } ?: (game.id to Hearts())
    }

    /** Da o quita tu corazón al juego, como en el canal. */
    suspend fun toggleHeart(game: Game) {
        val chatId = _state.value.chatId
        val (target, current) = heartsOf(game)
        val add = !current.mine
        // Se ve al momento; si Telegram lo rechaza se deshace.
        setHearts(target, Hearts((current.count + if (add) 1 else -1).coerceAtLeast(0), add))
        try {
            val heart = TdApi.ReactionTypeEmoji(HEART)
            if (add) tg.send(TdApi.AddMessageReaction(chatId, target, heart, false, true))
            else tg.send(TdApi.RemoveMessageReaction(chatId, target, heart))
        } catch (e: Exception) {
            setHearts(target, current)
            val msg = e.message.orEmpty()
            throw Exception(
                when {
                    "REACTION" in msg -> "El canal no permite corazones en este post"
                    else -> "No se pudo enviar el corazón: $msg"
                },
            )
        }
        runCatching { tg.send(TdApi.GetMessage(chatId, target)) }.getOrNull()
            ?.let { setHearts(it.id, heartsOf(it.interactionInfo)) }
    }

    private fun setHearts(messageId: Long, hearts: Hearts) {
        _hearts.update { if (hearts.count == 0 && !hearts.mine) it - messageId else it + (messageId to hearts) }
    }

    private fun heartsOf(info: TdApi.MessageInteractionInfo?): Hearts {
        val heart = info?.reactions?.reactions?.firstOrNull { r ->
            (r.type as? TdApi.ReactionTypeEmoji)?.emoji?.trimEnd('\uFE0F') == HEART
        } ?: return Hearts()
        return Hearts(heart.totalCount, heart.isChosen)
    }

    private fun add(message: TdApi.Message) {
        setHearts(message.id, heartsOf(message.interactionInfo))
        val mapped = message.toChannelMessage() ?: return
        synchronized(messages) { messages[mapped.id] = mapped }
    }

    private fun publish() {
        val snapshot = synchronized(messages) { messages.values.toList() }
        // Los APK de la propia tienda no son juegos (se ofrecen en Ajustes) y los avisos de
        // novedades tampoco (se muestran una vez al abrir la app).
        // (El post de la app con su APK lo aparta CatalogBuilder, con foto y texto incluidos.)
        val gameMessages = snapshot.filterNot(Announcements::isAnnouncement)
        val announcements = Announcements.all(snapshot)
        // Los ports que nombra un aviso cuentan como actualizados en su fecha.
        val games = Announcements.applyTo(CatalogBuilder.build(gameMessages), announcements)
        val appUpdate = SelfUpdate.newest(snapshot, BuildConfig.VERSION_NAME)
        _state.update {
            it.copy(
                games = games,
                scannedMessages = snapshot.size,
                oldestDate = snapshot.minOfOrNull { m -> m.date } ?: 0,
                appUpdate = appUpdate,
                announcement = announcements.firstOrNull(),
            )
        }
    }

    private suspend fun resolveChat(): TdApi.Chat {
        val supergroupId = BuildConfig.CHANNEL_ID
        val chatId = -1_000_000_000_000L - supergroupId

        attempt { tg.send(TdApi.GetChat(chatId)) }?.let { return it }
        attempt { tg.send(TdApi.CreateSupergroupChat(supergroupId, false)) }?.let { return it }

        // Si el usuario es miembro, el canal aparece al cargar su lista de chats.
        for (i in 0 until 30) {
            val more = attempt { tg.send(TdApi.LoadChats(TdApi.ChatListMain(), 100)) } != null
            attempt { tg.send(TdApi.GetChat(chatId)) }?.let { return it }
            if (!more) break
        }

        val username = channelUsername()
        if (username != null) {
            attempt { tg.send(TdApi.SearchPublicChat(username)) }?.let { return it }
        }

        val invite = (ChannelLink.parse(BuildConfig.CHANNEL_INVITE_LINK) as? ChannelLink.Invite)?.url
        if (invite != null) {
            val info = attempt { tg.send(TdApi.CheckChatInviteLink(invite)) }
            if (info != null && info.chatId != 0L) {
                attempt { tg.send(TdApi.GetChat(info.chatId)) }?.let { return it }
            }
            val joined = attempt { tg.send(TdApi.JoinChatByInviteLink(invite)) }
            if (joined is TdApi.ChatJoinResultSuccess) return tg.send(TdApi.GetChat(joined.chatId))
        }

        throw ChannelNotAccessibleException()
    }

    private inline fun <T> attempt(block: () -> T): T? = try {
        block()
    } catch (e: TdException) {
        null
    }

    companion object {
        const val MAX_MESSAGES = 20_000

        /** Páginas vacías seguidas antes de dar por terminado el historial (unos 10 s en total). */
        const val EMPTY_PAGE_RETRIES = 6

        /** Reacción de corazón de Telegram (sin el selector de variante U+FE0F). */
        const val HEART = "\u2764"

        /**
         * @usuario del canal público. Vale tanto TOPPORTS_CHANNEL_USERNAME como un enlace
         * `t.me/usuario` puesto en TOPPORTS_CHANNEL_INVITE_LINK.
         */
        fun channelUsername(): String? =
            listOf(BuildConfig.CHANNEL_USERNAME, BuildConfig.CHANNEL_INVITE_LINK)
                .firstNotNullOfOrNull { (ChannelLink.parse(it) as? ChannelLink.Username)?.name }

        /** Enlace para abrir el canal (o un mensaje) en la app de Telegram. */
        fun telegramLink(messageId: Long? = null): String {
            val username = channelUsername()
            // Los ids de mensaje de TDLib son el id de Telegram desplazado 20 bits.
            val post = messageId?.let { "/" + (it shr 20) } ?: ""
            return when {
                username != null -> "https://t.me/$username$post"
                messageId == null && BuildConfig.CHANNEL_INVITE_LINK.isNotBlank() -> BuildConfig.CHANNEL_INVITE_LINK
                else -> "https://t.me/c/${BuildConfig.CHANNEL_ID}${post.ifEmpty { "/1" }}"
            }
        }
    }
}

internal fun TdApi.Message.toChannelMessage(): ChannelMessage? {
    var text = ""
    var photo: PhotoRef? = null
    var document: RemoteFile? = null
    var video: VideoRef? = null
    when (val c = content) {
        is TdApi.MessageText -> text = c.text.text
        is TdApi.MessagePhoto -> {
            text = c.caption.text
            photo = c.photo.toRef()
        }
        is TdApi.MessageDocument -> {
            text = c.caption.text
            val file = c.document.document
            document = RemoteFile(
                id = file.id,
                name = c.document.fileName.ifBlank { "archivo_${file.id}" },
                size = if (file.size > 0) file.size else file.expectedSize,
            )
        }
        is TdApi.MessageVideo -> {
            text = c.caption.text
            video = VideoRef(
                fileId = c.video.video.id,
                thumbFileId = c.video.thumbnail?.file?.id,
                durationSeconds = c.video.duration,
                caption = c.caption.text.lines().firstOrNull { it.isNotBlank() }?.trim() ?: "Vídeo",
            )
        }
        is TdApi.MessageAnimation -> text = c.caption.text
        else -> return null
    }
    return ChannelMessage(
        id = id,
        date = date,
        editDate = editDate,
        albumId = mediaAlbumId,
        text = text,
        photo = photo,
        document = document,
        video = video,
    )
}

private fun TdApi.Photo.toRef(): PhotoRef? {
    val sorted = sizes.sortedBy { it.width }
    if (sorted.isEmpty()) return null
    // ~800 px: nítida en las tarjetas grandes de la tienda sin bajar la foto original.
    val thumb = sorted.firstOrNull { it.width >= 600 } ?: sorted.last()
    return PhotoRef(thumbFileId = thumb.photo.id, fullFileId = sorted.last().photo.id)
}
