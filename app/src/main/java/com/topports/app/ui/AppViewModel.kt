package com.topports.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.topports.app.TopPortsApp
import com.topports.app.ui.theme.AppTheme
import com.topports.app.ui.theme.Px
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.topports.app.ui.theme.UiStyle
import com.topports.app.ui.theme.UiAnim

enum class Tab(val label: String) {
    // Las actualizaciones van dentro de Instalados (antes tenían su pestaña).
    STORE("Tienda"), INSTALLED("Instalados"), FILES("Archivos"), TASKS("Tareas"), SETTINGS("Ajustes"),
}

/** Secciones de Ajustes. */
enum class SettingsSection(val label: String) {
    // Los permisos van dentro de Almacenamiento y la privacidad dentro de Legal, plegados.
    VERSION("Versión"), STORAGE("Almacenamiento"), SOUND("Sonido"), THEME("Temas"), GAMEPAD("Mando"),
    ACCOUNT("Cuenta"), LEGAL("Legal"),
}

/** Filtro especial de la tienda: solo los juegos marcados como favoritos. */
const val FAVORITES_FILTER = "★"

/** Orden de la tienda. */
enum class SortMode(val label: String) {
    NEWEST("Más nuevos"), UPDATED("Actualizados"), ALPHABETICAL("A-Z"), SYSTEM("Sistema"),
    POPULAR("Más corazones"), SMALLEST("Más ligeros"), LARGEST("Más pesados"),
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val graph = (app as TopPortsApp).graph
    val auth = graph.telegram.authState
    val catalog = graph.channel.state
    val files = graph.telegram.files
    val tasks = graph.tasks.tasks
    val updates = graph.updates.updates
    val installed = graph.updates.installed
    val hearts = graph.channel.hearts

    private var currentTab by mutableStateOf(Tab.STORE)
    private val tabHistory = ArrayDeque<Tab>()

    /**
     * El explorador de archivos está eligiendo una carpeta: la de juegos (Ajustes → Almacenamiento)
     * o dónde extraer un juego ("Otra carpeta…"). [onPick] devuelve el motivo si no vale.
     */
    class FolderPick(
        val title: String,
        val hint: String,
        /** Al terminar, volver a Ajustes → Almacenamiento (y no a su primera sección). */
        val fromStorageSettings: Boolean = false,
        val onPick: (java.io.File) -> String?,
    )

    var folderPick by mutableStateOf<FolderPick?>(null)

    /** Pestaña visible. Cada cambio se recuerda para que "atrás" vuelva a la anterior. */
    var tab: Tab
        get() = currentTab
        set(value) {
            if (value == currentTab) return
            tabHistory.addLast(currentTab)
            if (tabHistory.size > 30) tabHistory.removeFirst()
            switchTab(value)
        }

    /** Vuelve a la pestaña anterior; false si no hay ninguna. */
    fun goBackTab(): Boolean {
        val previous = tabHistory.removeLastOrNull() ?: return false
        switchTab(previous)
        return true
    }

    /** Juego desde el que se fue a TAREAS (o el que se está descargando), para volver a él. */
    private var tasksGameId: Long? = null

    private fun switchTab(value: Tab) {
        if (value == Tab.TASKS && currentTab == Tab.STORE) tasksGameId = selectedGameId
        // Al salir de TAREAS hacia la tienda se vuelve a la ficha del juego que se descargaba.
        if (currentTab == Tab.TASKS && value == Tab.STORE) {
            val id = tasksGameId ?: graph.tasks.pendingDownloads().lastOrNull()?.gameId
            if (id != null && catalog.value.games.any { it.id == id }) openGame(id)
            tasksGameId = null
        }
        // Ajustes siempre se abre por su primera sección (no recuerda dónde se quedó).
        if (value == Tab.SETTINGS && currentTab != Tab.SETTINGS) settingsSection = SettingsSection.VERSION
        // Al salir del explorador eligiendo la carpeta de juegos, de vuelta a Almacenamiento.
        folderPick?.let { pick ->
            if (currentTab == Tab.FILES) {
                folderPick = null
                if (value == Tab.SETTINGS && pick.fromStorageSettings) settingsSection = SettingsSection.STORAGE
            }
        }
        currentTab = value
    }

    /** Botón "Ver juego" de una descarga en TAREAS. */
    fun openGameFromTasks(id: Long) {
        tasksGameId = id
        tab = Tab.STORE
    }
    var selectedGameId by mutableStateOf<Long?>(null)
    var browserDir by mutableStateOf<String?>(null)
    var search by mutableStateOf("")

    private val prefs = app.getSharedPreferences("ui", Context.MODE_PRIVATE)

    /** Orden de la tienda; se recuerda entre sesiones. */
    var sortMode by mutableStateOf(
        SortMode.entries.firstOrNull { it.name == prefs.getString("sort", null) } ?: SortMode.NEWEST,
    )
        private set

    /** Botón ORDEN o X del mando: pasa al siguiente orden (ver [SortMode]). */
    fun toggleSort() {
        val modes = SortMode.entries
        sortMode = modes[(sortMode.ordinal + 1) % modes.size]
        prefs.edit().putString("sort", sortMode.name).apply()
    }

    /** L1 / R1 en Ajustes: sección anterior o siguiente (da la vuelta al llegar al final). */
    fun cycleSettingsSection(dir: Int) {
        val all = SettingsSection.entries
        settingsSection = all[Math.floorMod(settingsSection.ordinal + dir, all.size)]
    }

    /** Juegos marcados como favoritos (ids); se recuerdan entre sesiones. */
    var favorites by mutableStateOf(
        prefs.getStringSet("favorites", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
    )
        private set

    fun toggleFavorite(gameId: Long) {
        val title = catalog.value.games.firstOrNull { it.id == gameId }?.title
        if (gameId in favorites) {
            favoriteTitles = favoriteTitles - gameId
        } else if (title != null) {
            favoriteTitles = favoriteTitles + (gameId to title)
        }
        saveFavorites(if (gameId in favorites) favorites - gameId else favorites + gameId)
    }

    /**
     * Título de cada favorito: si el post se borra y se vuelve a subir, el juego cambia de id y,
     * por el título, el favorito pasa al juego nuevo en vez de perderse.
     */
    private var favoriteTitles: Map<Long, String> =
        prefs.getStringSet("favoriteTitles", emptySet()).orEmpty().mapNotNull { entry ->
            val id = entry.substringBefore('\t').toLongOrNull() ?: return@mapNotNull null
            id to entry.substringAfter('\t')
        }.toMap()

    private fun saveFavorites(ids: Set<Long>) {
        favorites = ids
        favoriteTitles = favoriteTitles.filterKeys { it in ids }
        prefs.edit()
            .putStringSet("favorites", ids.map { it.toString() }.toSet())
            .putStringSet("favoriteTitles", favoriteTitles.map { (id, title) -> "$id\t$title" }.toSet())
            .apply()
    }

    /** Pasa los favoritos de juegos que ya no están al juego del mismo título, y apunta títulos. */
    private fun reconcileFavorites(games: List<com.topports.core.Game>) {
        if (games.isEmpty() || favorites.isEmpty()) return
        val byId = games.associateBy { it.id }
        var ids = favorites
        var titles = favoriteTitles
        for (id in favorites) {
            val game = byId[id]
            if (game != null) {
                if (titles[id] != game.title) titles = titles + (id to game.title)
                continue
            }
            val title = titles[id] ?: continue
            val replacement = games.filter { it.title.equals(title, ignoreCase = true) && it.id !in ids }
                .maxByOrNull { it.id } ?: continue
            ids = ids - id + replacement.id
            titles = titles - id + (replacement.id to replacement.title)
        }
        if (ids != favorites || titles != favoriteTitles) {
            favoriteTitles = titles
            saveFavorites(ids)
        }
    }

    init {
        viewModelScope.launch {
            catalog.map { it.games }.distinctUntilChanged().collect { reconcileFavorites(it) }
        }
    }

    /** Últimas búsquedas (la más reciente primero), para repetirlas desde el teclado. */
    var searchHistory by mutableStateOf(
        prefs.getString("searchHistory", "").orEmpty().split('\n').filter { it.isNotBlank() },
    )
        private set

    /** Al cerrar el teclado: apunta la búsqueda si no está vacía. */
    fun rememberSearch() {
        val q = search.trim()
        if (q.length < 2) return
        searchHistory = (listOf(q) + searchHistory.filterNot { it.equals(q, ignoreCase = true) }).take(6)
        prefs.edit().putString("searchHistory", searchHistory.joinToString("\n")).apply()
    }

    /** Tienda sin los juegos ya instalados (Sistema → Ocultar instalados); se recuerda. */
    var hideInstalled by mutableStateOf(prefs.getBoolean("hideInstalled", false))
        private set

    fun toggleHideInstalled() {
        hideInstalled = !hideInstalled
        prefs.edit().putBoolean("hideInstalled", hideInstalled).apply()
    }

    /** Sistema elegido en la tienda ("N64", "X360"…); null = todos; [FAVORITES_FILTER] = favoritos. */
    var systemFilter by mutableStateOf<String?>(null)
    var systemPickerOpen by mutableStateOf(false)

    /** Tipo de sonido para menú y música (Ajustes → Sonido). */
    var soundType by mutableStateOf(RetroSound.SoundType.fromName(prefs.getString("soundType", null)))
        private set

    fun changeSoundType(type: RetroSound.SoundType) {
        soundType = type
        RetroSound.type = type
        prefs.edit().putString("soundType", type.name).apply()
        RetroSound.play(RetroSound.Kind.ACCEPT)
    }

    /** Cambia con cada "Restablecer ajustes", para que Ajustes vuelva a leer lo guardado. */
    var settingsResets by mutableIntStateOf(0)
        private set

    /**
     * Todos los ajustes como vienen de serie: tema, paleta, animación, sonido, música, mando,
     * descargas y orden de la tienda. No toca los favoritos, las descargas ni la sesión.
     */
    fun resetSettings() {
        prefs.edit().apply {
            listOf(
                "theme", "uiStyle", "anim", "sounds", "soundSoft", "soundType", "music", "swapAB", "sort",
                "autoExtract", "maxParallel", "wifiOnly", "hideInstalled", "resumeOnBoot", "keepScreenOn",
            ).forEach { remove(it) }
        }.apply()
        Px.theme = AppTheme.fromName(null)
        Px.style = UiStyle.fromName(null)
        Px.anim = UiAnim.fromName(null)
        sounds = true
        RetroSound.enabled = true
        soundType = RetroSound.SoundType.SOFT
        RetroSound.type = soundType
        music = 3
        ChipMusic.level = music
        swapAB = false
        Gamepad.swapAB = false
        sortMode = SortMode.NEWEST
        hideInstalled = false
        graph.tasks.autoExtract = true
        graph.tasks.maxParallel = 2
        graph.tasks.wifiOnly = false
        graph.tasks.resumeOnBoot = true
        graph.tasks.keepScreenOn = true
        settingsResets++
    }

    /**
     * Copia de los ajustes en un archivo (Ajustes → Versión), para pasarlos a otra consola: tema,
     * paleta, sonido, mando, descargas, favoritos y últimas búsquedas. Nada de la sesión de Telegram.
     */
    fun settingsFile(): java.io.File =
        java.io.File(com.topports.app.storage.Storage.defaultTopPortsDir(getApplication()), "topports-ajustes.json")

    fun exportSettings(): java.io.File {
        val json = org.json.JSONObject().put("version", 1)
        val all = prefs.all
        for ((key, type) in backupKeys) {
            val value = all[key] ?: continue
            json.put(key, if (type == "set") org.json.JSONArray((value as Set<*>).toList()) else value)
        }
        val file = settingsFile()
        file.parentFile?.mkdirs()
        file.writeText(json.toString(2))
        return file
    }

    /** Lee la copia de [settingsFile]; hay que reiniciar la app para que se aplique todo. */
    fun importSettings() {
        val file = settingsFile()
        if (!file.isFile) throw java.io.IOException("No hay copia en ${file.path}")
        val json = org.json.JSONObject(file.readText())
        val edit = prefs.edit()
        for ((key, type) in backupKeys) {
            if (!json.has(key)) continue
            when (type) {
                "bool" -> edit.putBoolean(key, json.getBoolean(key))
                "int" -> edit.putInt(key, json.getInt(key))
                "long" -> edit.putLong(key, json.getLong(key))
                "string" -> edit.putString(key, json.getString(key))
                "set" -> json.getJSONArray(key).let { arr -> edit.putStringSet(key, List(arr.length()) { arr.getString(it) }.toSet()) }
            }
        }
        // commit (no apply): la app se reinicia justo después y no se puede perder.
        if (!edit.commit()) throw java.io.IOException("No se pudieron guardar los ajustes")
    }

    private val backupKeys = mapOf(
        "theme" to "string", "uiStyle" to "string", "anim" to "string", "sounds" to "bool",
        "soundType" to "string", "music" to "int", "swapAB" to "bool", "sort" to "string",
        "autoExtract" to "bool", "maxParallel" to "int", "wifiOnly" to "bool", "hideInstalled" to "bool",
        "resumeOnBoot" to "bool", "keepScreenOn" to "bool", "favorites" to "set", "favoriteTitles" to "set", "searchHistory" to "string",
        "seenAnnouncement" to "long",
    )

    /** Música chiptune: 0 apagada, 1 baja, 2 media, 3 alta (por defecto) (Ajustes → Sonido). */
    var music by mutableStateOf(prefs.getInt("music", 3))
        private set

    fun changeMusic(level: Int) {
        music = level
        ChipMusic.level = level
        prefs.edit().putInt("music", level).apply()
    }

    /** Sonidos de menú (Ajustes → Sonido); se recuerda. */
    var sounds by mutableStateOf(prefs.getBoolean("sounds", true))
        private set

    fun changeSounds(on: Boolean) {
        sounds = on
        RetroSound.enabled = on
        prefs.edit().putBoolean("sounds", on).apply()
        if (on) RetroSound.play(RetroSound.Kind.ACCEPT)
    }

    /** Animación de cambio de pantalla; se aplica al momento y se recuerda. */
    fun setAnim(anim: UiAnim) {
        Px.anim = anim
        prefs.edit().putString("anim", anim.name).apply()
    }

    /** Tema (estilo de interfaz: letras y recuadros); se aplica al momento y se recuerda. */
    fun setStyle(style: UiStyle) {
        Px.style = style
        prefs.edit().putString("uiStyle", style.name).apply()
    }

    /** Paleta de colores; se aplica al momento y se recuerda. */
    fun setTheme(theme: AppTheme) {
        Px.theme = theme
        prefs.edit().putString("theme", theme.name).apply()
    }

    /** Último aviso de novedades ya visto: el popup solo sale con uno más nuevo. */
    var seenAnnouncementId by mutableLongStateOf(prefs.getLong("seenAnnouncement", 0L))
        private set

    /** Abierto a mano desde Ajustes → Versión. */
    var announcementOpen by mutableStateOf(false)

    fun dismissAnnouncement(id: Long) {
        announcementOpen = false
        if (id > seenAnnouncementId) {
            seenAnnouncementId = id
            prefs.edit().putLong("seenAnnouncement", id).apply()
        }
    }

    /** Sección abierta en Ajustes (se conserva al cambiar de pestaña). */
    var settingsSection by mutableStateOf(SettingsSection.VERSION)

    /** Intercambiar A y B (mandos con distribución Nintendo). */
    var swapAB by mutableStateOf(Gamepad.swapAB)
        private set

    fun toggleSwapAB() {
        swapAB = !swapAB
        Gamepad.swapAB = swapAB
        prefs.edit().putBoolean("swapAB", swapAB).apply()
    }

    /** Teclado de búsqueda abierto (botón BUSCAR o Y del mando). */
    var searchOpen by mutableStateOf(false)

    /** Último juego abierto: al volver a la tienda, el mando vuelve a esa carátula. */
    var lastGameId by mutableStateOf<Long?>(null)

    /** Carátula de la tienda seleccionada con el mando (índice en la lista visible). */
    var catalogFocusIndex by mutableStateOf(-1)

    /** Juego seleccionado en la tienda (para SELECT en Favoritos: quitarlo). */
    var catalogFocusGameId by mutableStateOf<Long?>(null)

    /** Juego que se va a quitar de favoritos (diálogo de confirmación abierto). */
    var confirmUnfavorite by mutableStateOf<Long?>(null)

    /** Quita de favoritos; si era el último, la tienda vuelve a mostrar todos. */
    fun removeFavorite(gameId: Long) {
        if (gameId in favorites) toggleFavorite(gameId)
        if (favorites.isEmpty() && systemFilter == FAVORITES_FILTER) systemFilter = null
    }

    /** Petición de L1/R1: filas a moverse (y un sello para repetir la misma petición). */
    var catalogJump by mutableStateOf<Pair<Int, Long>?>(null)
        private set

    fun jumpCatalogRows(rows: Int) {
        catalogJump = rows to System.nanoTime()
    }

    /** Petición de L1/R1 con un juego abierto: desplazar la descripción (-1 arriba, 1 abajo). */
    var descriptionJump by mutableStateOf<Pair<Int, Long>?>(null)
        private set

    fun scrollDescription(dir: Int) {
        descriptionJump = dir to System.nanoTime()
    }

    /** Joystick derecho con un juego abierto: -1 arriba … 1 abajo (0 = suelto). */
    var descriptionStick by mutableFloatStateOf(0f)

    /** Posición de la cuadrícula de la tienda (se conserva al entrar y salir de una ficha). */
    val catalogGrid = LazyGridState()

    fun openGame(id: Long) {
        lastGameId = id
        selectedGameId = id
    }

    /** B del mando: cerrar la ficha y volver a la tienda. */
    fun goHome() {
        // Desde TAREAS, B vuelve al juego que se estaba descargando (ver [switchTab]).
        if (currentTab != Tab.TASKS) selectedGameId = null
        tab = Tab.STORE
    }

    /** L2 / R2 del mando: pestaña anterior / siguiente. */
    fun cycleTab(step: Int) {
        val tabs = Tab.entries
        tab = tabs[(tabs.indexOf(tab) + step + tabs.size) % tabs.size]
    }

    var authBusy by mutableStateOf(false)
        private set
    var authError by mutableStateOf<String?>(null)
        private set

    fun submitPhone(phone: String) = authCall {
        val clean = phone.filter { it.isDigit() || it == '+' }
        // Sin prefijo internacional se asume España (+34).
        graph.telegram.sendPhoneNumber(if (clean.startsWith("+")) clean else "+34$clean")
    }
    fun submitCode(code: String) = authCall { graph.telegram.sendCode(code.trim()) }
    fun submitPassword(password: String) = authCall { graph.telegram.sendPassword(password) }
    fun resendCode() = authCall { graph.telegram.resendCode() }
    fun submitEmail(email: String) = authCall { graph.telegram.sendEmail(email.trim()) }
    fun submitEmailCode(code: String) = authCall { graph.telegram.sendEmailCode(code.trim()) }

    /**
     * Número equivocado, o se quiere usar otra cuenta: se cancela el inicio de sesión a medias y
     * Telegram vuelve a pedir el número (sin borrar los datos de la app).
     */
    fun changePhoneNumber() = logOut()

    fun logOut() {
        selectedGameId = null
        tabHistory.clear()
        currentTab = Tab.STORE
        authCall { graph.telegram.logOut() }
    }

    fun loadCatalogIfNeeded() {
        val state = catalog.value
        if (state.games.isEmpty() && !state.loading && state.error == null) graph.channel.refresh()
    }

    fun refreshCatalog() = graph.channel.refresh()

    /** "Comprobar" en Ajustes → Versión: busca la actualización en el servidor; avisa si falla. */
    fun checkAppUpdate(onError: (String) -> Unit) {
        viewModelScope.launch {
            graph.channel.checkAppUpdate()?.let { onError("No se pudo comprobar: $it") }
        }
    }

    /** Abre el explorador para elegir una carpeta, empezando en [start]. */
    fun pickFolder(pick: FolderPick, start: java.io.File?) {
        folderPick = pick
        browserDir = start?.takeIf { it.isDirectory }?.path
        tab = Tab.FILES
    }

    fun pickGamesDir() {
        pickFolder(
            FolderPick(
                "ELIGE LA CARPETA DE JUEGOS",
                "Entra en la carpeta (o créala con Nueva carpeta) y pulsa Usar esta carpeta. " +
                    "Ahí se descargarán y extraerán los juegos.",
                fromStorageSettings = true,
            ) { dir ->
                graph.tasks.setGamesDir(dir).also { error ->
                    if (error == null) {
                        val app = getApplication<android.app.Application>()
                        android.widget.Toast.makeText(
                            app, "Carpeta de juegos: ${com.topports.app.storage.Storage.displayPath(app, dir)}",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
            com.topports.app.storage.Storage.topPortsDir(getApplication()),
        )
    }

    fun openFolder(path: String) {
        browserDir = path
        tab = Tab.FILES
    }

    private fun authCall(block: suspend () -> Unit) {
        viewModelScope.launch {
            authBusy = true
            authError = null
            try {
                block()
            } catch (e: Exception) {
                authError = translateError(e.message ?: e.toString())
            } finally {
                authBusy = false
            }
        }
    }

    private fun translateError(raw: String): String = when {
        "PHONE_NUMBER_INVALID" in raw -> "Número no válido. Usa el prefijo del país, p. ej. +34 600 000 000"
        "PHONE_CODE_INVALID" in raw -> "Código incorrecto"
        "PHONE_CODE_EXPIRED" in raw -> "El código ha caducado, vuelve a pedirlo"
        "PASSWORD_HASH_INVALID" in raw -> "Contraseña incorrecta"
        "EMAIL_INVALID" in raw -> "Correo no válido"
        "EMAIL_CODE_INVALID" in raw || "CODE_INVALID" in raw -> "Código incorrecto"
        "FLOOD_WAIT" in raw || "Too Many Requests" in raw -> "Demasiados intentos. Espera un rato y vuelve a probar"
        else -> raw
    }
}
