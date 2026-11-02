package com.topports.app.ui.store

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.topports.app.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Espacio libre donde se descargan los juegos (-1 mientras no se sabe). Se vuelve a mirar cada
 * 10 segundos: cambia al descargar, extraer o borrar.
 */
@Composable
fun rememberFreeSpace(vm: AppViewModel): Long {
    val free by produceState(-1L) {
        while (true) {
            value = withContext(Dispatchers.IO) { vm.graph.tasks.freeSpace() }
            delay(10_000)
        }
    }
    return free
}
