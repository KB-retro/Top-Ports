package com.topports.app.ui

import android.app.Application
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.topports.app.TopPortsApp
import kotlinx.coroutines.launch

enum class Tab(val label: String) { STORE("Tienda"), FILES("Archivos"), TASKS("Tareas") }

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val graph = (app as TopPortsApp).graph
    val auth = graph.telegram.authState
    val catalog = graph.channel.state
    val files = graph.telegram.files
    val tasks = graph.tasks.tasks

    var tab by mutableStateOf(Tab.STORE)
    var selectedGameId by mutableStateOf<Long?>(null)
    var browserDir by mutableStateOf<String?>(null)
    var search by mutableStateOf("")

    /** Último juego abierto: al volver a la tienda, el mando vuelve a esa carátula. */
    var lastGameId by mutableStateOf<Long?>(null)

    /** Posición de la cuadrícula de la tienda (se conserva al entrar y salir de una ficha). */
    val catalogGrid = LazyGridState()

    fun openGame(id: Long) {
        lastGameId = id
        selectedGameId = id
    }

    /** L1 / R1 del mando: pestaña anterior / siguiente. */
    fun cycleTab(step: Int) {
        val tabs = Tab.entries
        tab = tabs[(tabs.indexOf(tab) + step + tabs.size) % tabs.size]
    }

    var authBusy by mutableStateOf(false)
        private set
    var authError by mutableStateOf<String?>(null)
        private set

    fun submitPhone(phone: String) = authCall { graph.telegram.sendPhoneNumber(phone.filter { it.isDigit() || it == '+' }) }
    fun submitCode(code: String) = authCall { graph.telegram.sendCode(code.trim()) }
    fun submitPassword(password: String) = authCall { graph.telegram.sendPassword(password) }

    fun logOut() {
        selectedGameId = null
        tab = Tab.STORE
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
