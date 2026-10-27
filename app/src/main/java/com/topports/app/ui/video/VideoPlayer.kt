package com.topports.app.ui.video

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.topports.app.ui.components.DialogGamepadSupport
import com.topports.app.ui.components.PixelButton
import com.topports.app.ui.components.pixelFrame
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.PxText
import kotlinx.coroutines.delay
import java.io.File

/**
 * Reproductor integrado. Se abre en una ventana centrada (la mitad del ancho en
 * horizontal) y se puede pasar a pantalla completa. Atrás / B sale de pantalla
 * completa y, en ventana, cierra el vídeo.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerDialog(file: File, title: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }
    var fullscreen by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlayerError(e: PlaybackException) {
                error = e.errorCodeName
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    val playFocus = remember { FocusRequester() }
    LaunchedEffect(fullscreen) {
        delay(150)
        runCatching { playFocus.requestFocus() }
    }

    Dialog(
        onDismissRequest = { if (fullscreen) fullscreen = false else onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DialogGamepadSupport()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            val frame = if (fullscreen) {
                Modifier.fillMaxSize().background(Color.Black)
            } else {
                Modifier
                    .align(Alignment.Center)
                    .width(maxWidth * if (landscape) 0.5f else 0.94f)
                    .pixelFrame(Px.navy, Px.pink)
                    .padding(8.dp)
            }
            Column(frame, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!fullscreen) {
                    Text(title.uppercase(), style = PxText.label.copy(color = Px.pink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val videoModifier = if (fullscreen) Modifier.fillMaxWidth().weight(1f)
                else Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                Box(videoModifier.background(Color.Black)) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = true
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                // El mando se maneja con los botones pixel de abajo.
                                isFocusable = false
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    error?.let { code ->
                        Column(
                            Modifier.align(Alignment.Center).padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("No se puede reproducir ($code)", style = PxText.body.copy(color = Px.red))
                            PixelButton("Abrir con otra app", onClick = {
                                openVideoExternally(context, file)
                                onClose()
                            }, color = Px.blue, small = true)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(bottom = if (fullscreen) 8.dp else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                ) {
                    PixelButton("-10s", onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }, color = Px.grey, small = true)
                    PixelButton(
                        if (playing) "Pausa" else "Play",
                        onClick = { if (player.isPlaying) player.pause() else player.play() },
                        color = Px.pink,
                        small = true,
                        modifier = Modifier.focusRequester(playFocus),
                    )
                    PixelButton("+10s", onClick = { player.seekTo(player.currentPosition + 10_000) }, color = Px.grey, small = true)
                    PixelButton(
                        if (fullscreen) "Ventana" else "Pantalla completa",
                        onClick = { fullscreen = !fullscreen },
                        color = Px.blue,
                        small = true,
                    )
                    PixelButton("Cerrar", onClick = onClose, color = Px.red, textColor = Px.white, small = true)
                }
            }
        }
    }
}

/** Plan B si el formato no es compatible: abrirlo con el reproductor del sistema. */
fun openVideoExternally(context: Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "video/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        Toast.makeText(context, "No hay reproductor de vídeo instalado", Toast.LENGTH_SHORT).show()
    }
}
