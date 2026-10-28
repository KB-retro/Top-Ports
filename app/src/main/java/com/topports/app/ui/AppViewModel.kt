package com.topports.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.topports.app.TopPortsApp
import com.topports.app.ui.theme.AppTheme
import com.topports.app.ui.theme.Px
import kotlinx.coroutines.launch

enum class Tab(val label: String) {
    STORE("Tienda"), INSTALLED("Instalados"), UPDATES("Actualizar"), FILES("Archivos"), TASKS("Tareas"), SETTINGS("Ajustes"),
}

/** Orden de la tienda. */
enum class SortMode(val label: String) { NEWEST("Más nuevos"), ALPHABETICAL("A-Z") }

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val graph = (app as TopPortsApp).graph
    val auth = graph.telegram.authState
    val catalog = graph.channel.state
    val files = graph.telegram.files
    val tasks = graph.tasks.tasks
    val updates = graph.updates.updates
    val installed = graph.updates.installed

    private var currentTab by mutableStateOf(Tab.STORE)
    private val tabHistory = ArrayDeque<Tab>()

    /** Pestaña visible. Cada cambio se recuerda para que "atrás" vuelva a la anterior. */
    var tab: Tab
        get() = currentTab
        set(value) {
            if (value == currentTab) return
            tabHistory.addLast(currentTab)
            if (tabHistory.size > 30) tabHistory.removeFirst()
            currentTab = value
        }

    /** Vuelve a la pestaña anterior; false si no hay ninguna. */
    fun goBackTab(): Boolean {
        val previous = tabHistory.removeLastOrNull() ?: return false
        currentTab = previous
        return true
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

    /** Botón ORDEN o X del mando: alterna entre más nuevos y A-Z. */
    fun toggleSort() {
        sortMode = if (sortMode == SortMode.NEWEST) SortMode.ALPHABETICAL else SortMode.NEWEST
        prefs.edit().putString("sort", sortMode.name).apply()
    }

    /** Tema de colores; se aplica al momento y se recuerda. */
    fun setTheme(theme: AppTheme) {
        Px.theme = theme
        prefs.edit().putString("theme", theme.name).apply()
    }

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

    /** Posición de la cuadrícula de la tienda (se conserva al entrar y salir de una ficha). */
    val catalogGrid = LazyGridState()

    fun openGame(id: Long) {
        lastGameId = id
        selectedGameId = id
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
