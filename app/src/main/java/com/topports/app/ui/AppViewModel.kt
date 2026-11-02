package com.topports.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.topports.app.TopPortsApp
import com.topports.app.ui.theme.AppTheme
import com.topports.app.ui.theme.Px
import kotlinx.coroutines.launch
import com.topports.app.ui.theme.UiStyle
import com.topports.app.ui.theme.UiAnim

enum class Tab(val label: String) {
    STORE("Tienda"), INSTALLED("Instalados"), UPDATES("Actualizar"), FILES("Archivos"), TASKS("Tareas"), SETTINGS("Ajustes"),
}

/** Secciones de Ajustes. */
enum class SettingsSection(val label: String) {
    // "Descargas" (antes Almacenamiento) es corto a propósito: así las 9 secciones caben en una
    // línea en pantallas 4:3 como la RG477M.
    VERSION("Versión"), PERMISSIONS("Permisos"), STORAGE("Descargas"), SOUND("Sonido"), THEME("Temas"), GAMEPAD("Mando"),
    ACCOUNT("Cuenta"), PRIVACY("Privacidad"), LEGAL("Legal"),
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
        favorites = if (gameId in favorites) favorites - gameId else favorites + gameId
        prefs.edit().putStringSet("favorites", favorites.map { it.toString() }.toSet()).apply()
    }

    /** Sistema elegido en la tienda ("N64", "X360"…); null = todos; [FAVORITES_FILTER] = favoritos. */
    var systemFilter by mutableStateOf<String?>(null)
    var systemPickerOpen by mutableStateOf(false)

    /** Tipo de sonido para menú y música: false = 8 bits, true = suave (Ajustes → Sonido). */
    var softSound by mutableStateOf(prefs.getBoolean("soundSoft", false))
        private set

    fun changeSoftSound(soft: Boolean) {
        softSound = soft
        RetroSound.soft = soft
        prefs.edit().putBoolean("soundSoft", soft).apply()
        RetroSound.play(RetroSound.Kind.ACCEPT)
    }

    /** Música chiptune: 0 apagada, 1 baja, 2 media, 3 alta (Ajustes → Sonido). */
    var music by mutableStateOf(prefs.getInt("music", 0))
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
        "FLOOD_WAIT" in raw || "Too Many Requests" in raw -> "Demasiados intentos. Espera un rato y vuelve a probar"
        else -> raw
    }
}
