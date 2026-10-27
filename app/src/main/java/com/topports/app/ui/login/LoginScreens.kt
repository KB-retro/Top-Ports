package com.topports.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.topports.app.ui.AppViewModel
import com.topports.app.ui.components.BlinkingText
import com.topports.app.ui.components.PixelButton
import com.topports.app.ui.components.PixelPanel
import com.topports.app.ui.components.PixelProgress
import com.topports.app.ui.components.PixelTextField
import com.topports.app.ui.theme.Px
import com.topports.app.ui.theme.PxText
import org.drinkless.tdlib.TdApi

/** Logotipo en bloques: un mando de 8 bits dibujado con cuadrados. */
@Composable
fun PixelLogo(modifier: Modifier = Modifier, pixel: Int = 8) {
    val art = listOf(
        ".BBBBBBBBBB.",
        "BBBBBBBBBBBB",
        "BBDBBBBBBRBB",
        "BDDDBBBBRBYB",
        "BBDBBBBBBYBB",
        "BBBBBBBBBBBB",
        "BBB......BBB",
    )
    val colors = mapOf('B' to Px.white, 'D' to Px.navy, 'R' to Px.red, 'Y' to Px.orange)
    Column(modifier) {
        art.forEach { row ->
            Row {
                row.forEach { c ->
                    Box(Modifier.size(pixel.dp).background(colors[c] ?: Color.Transparent))
                }
            }
        }
    }
}

@Composable
private fun LoginScaffold(subtitle: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        PixelLogo()
        Spacer(Modifier.height(20.dp))
        Text("TOP PORTS", style = PxText.title.copy(color = Px.orange, fontSize = PxText.title.fontSize * 1.6f))
        Spacer(Modifier.height(8.dp))
        Text(subtitle.uppercase(), style = PxText.tiny.copy(color = Px.blue), textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        PixelPanel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}

@Composable
fun SplashScreen() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PixelLogo()
        Spacer(Modifier.height(24.dp))
        BlinkingText("Cargando…", style = PxText.label.copy(color = Px.yellow))
        Spacer(Modifier.height(16.dp))
        PixelProgress(null, Modifier.fillMaxWidth(0.7f))
    }
}

@Composable
fun MessageScreen(title: String, message: String) {
    LoginScaffold("Tienda de ports") {
        Text(title.uppercase(), style = PxText.heading.copy(color = Px.red))
        Text(message, style = PxText.body)
    }
}

@Composable
fun LoginFlow(vm: AppViewModel, state: TdApi.AuthorizationState) {
    when (state) {
        is TdApi.AuthorizationStateWaitPhoneNumber -> PhoneStep(vm)
        is TdApi.AuthorizationStateWaitCode -> CodeStep(vm, state.codeInfo)
        is TdApi.AuthorizationStateWaitPassword -> PasswordStep(vm, state.passwordHint)
        is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> MessageScreen(
            "Confirma en otro dispositivo",
            "Abre Telegram en otro dispositivo y confirma el inicio de sesión: ${state.link}",
        )
        else -> MessageScreen(
            "Paso no soportado",
            "Tu cuenta necesita un paso extra (registro o correo). Termina el inicio de sesión en la app " +
                "oficial de Telegram y vuelve a abrir Top Ports.",
        )
    }
}

@Composable
private fun ErrorAndBusy(vm: AppViewModel) {
    vm.authError?.let { Text(it, style = PxText.body.copy(color = Px.red)) }
    if (vm.authBusy) PixelProgress(null, Modifier.fillMaxWidth())
}

@Composable
private fun PhoneStep(vm: AppViewModel) {
    var phone by rememberSaveable { mutableStateOf("+") }
    LoginScaffold("Entra con tu Telegram") {
        Text("1. TU NÚMERO", style = PxText.label.copy(color = Px.yellow))
        Text(
            "Inicia sesión con tu cuenta de Telegram para ver y descargar los ports del canal. " +
                "Telegram te enviará un código.",
            style = PxText.body,
        )
        PixelTextField(
            value = phone,
            onValueChange = { phone = it },
            placeholder = "+34 600 000 000",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.submitPhone(phone) }),
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorAndBusy(vm)
        PixelButton(
            "Enviar código",
            onClick = { vm.submitPhone(phone) },
            enabled = !vm.authBusy && phone.count { it.isDigit() } >= 6,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CodeStep(vm: AppViewModel, info: TdApi.AuthenticationCodeInfo) {
    var code by rememberSaveable { mutableStateOf("") }
    val where = when (info.type) {
        is TdApi.AuthenticationCodeTypeTelegramMessage -> "en un mensaje de Telegram"
        is TdApi.AuthenticationCodeTypeSms -> "por SMS"
        is TdApi.AuthenticationCodeTypeCall -> "con una llamada"
        else -> "en Telegram o por SMS"
    }
    LoginScaffold("Entra con tu Telegram") {
        Text("2. CÓDIGO", style = PxText.label.copy(color = Px.yellow))
        Text("Te hemos enviado el código $where al ${info.phoneNumber}.", style = PxText.body)
        PixelTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit).take(8) },
            placeholder = "12345",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.submitCode(code) }),
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorAndBusy(vm)
        PixelButton(
            "Entrar",
            onClick = { vm.submitCode(code) },
            enabled = !vm.authBusy && code.length >= 4,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PasswordStep(vm: AppViewModel, hint: String) {
    var password by rememberSaveable { mutableStateOf("") }
    LoginScaffold("Entra con tu Telegram") {
        Text("3. CONTRASEÑA", style = PxText.label.copy(color = Px.yellow))
        Text(
            "Tu cuenta tiene verificación en dos pasos." + if (hint.isNotBlank()) " Pista: $hint" else "",
            style = PxText.body,
        )
        PixelTextField(
            value = password,
            onValueChange = { password = it },
            password = true,
            placeholder = "contraseña",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.submitPassword(password) }),
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorAndBusy(vm)
        PixelButton(
            "Entrar",
            onClick = { vm.submitPassword(password) },
            enabled = !vm.authBusy && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
