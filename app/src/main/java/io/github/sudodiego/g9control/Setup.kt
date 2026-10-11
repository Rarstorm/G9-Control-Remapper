package io.github.sudodiego.g9control

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Configuración inicial: se muestra la primera vez que se abre la app y después desde Ajustes. Cada
 * paso se marca solo cuando ya está hecho (control detectado, conectado, servicio activo).
 */
@Composable
fun SetupDialog(s: AppState, a: MainActivity, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.92f)) {
            Column(Modifier.padding(28.dp)) {
                Text(tr("Configuración inicial", "Quick setup"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                // El aviso y los pasos van en el MISMO scroll: antes el aviso legal estaba fijo y al
                // desplazarse los pasos se comía media pantalla.
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                Text(
                    tr("Cuatro pasos para dejar G9 Control Remapper listo. Los que se marcan en verde ya están hechos.",
                        "Four steps to get G9 Control Remapper ready. Steps marked green are already done."),
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Aviso legal y de privacidad: visible antes de pedir cualquier permiso.
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(tr("Antes de empezar", "Before you start"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(
                            tr("G9 Control Remapper es una app gratuita y de código abierto (licencia GPL-3), hecha por la comunidad. No tiene relación con Lenovo, Legion ni GameSir. No se conecta a internet ni recopila datos.\n\n" +
                                "Para funcionar necesita permisos que activas TÚ mismo desde los ajustes de Android (accesibilidad y, opcional, Shizuku). Los activas bajo tu responsabilidad y puedes quitarlos cuando quieras.\n\n" +
                                "La accesibilidad le permite ver qué app está en pantalla y los botones del control, y tomar capturas: no lee tus textos ni lo que escribes.",
                                "G9 Control Remapper is a free, open-source app (GPL-3 licence), made by the community. It is not affiliated with Lenovo, Legion or GameSir. It doesn't connect to the internet or collect data.\n\n" +
                                    "To work it needs permissions you turn on YOURSELF in Android's settings (accessibility and, optionally, Shizuku). You enable them at your own responsibility and can remove them anytime.\n\n" +
                                    "Accessibility lets it see which app is on screen and the controller's buttons, and take screenshots: it doesn't read your text or what you type."),
                            fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                    Step(
                        1, s.padMode != null, tr("Conecta el control", "Connect the controller"),
                        tr("Coloca la tablet en el G9. Debe estar en modo Android (indicador azul oscuro). Si está en X-Input (verde) " +
                            "o DS5 (blanco), mantén Vista + Menú unos 2 segundos hasta llegar a Android.",
                            "Dock the tablet in the G9. It must be in Android mode (dark blue indicator). If it's in X-Input (green) " +
                                "or DS5 (white), hold View + Menu for about 2 seconds until it reaches Android.")
                    )
                    Step(
                        2, s.connected, tr("Permite el acceso USB", "Allow USB access"),
                        tr("Cuando Android pregunte si G9 Control Remapper puede acceder al control, marca «Usar siempre» (o «Abrir siempre») " +
                            "y acepta. Así la app y el cambio automático funcionan sin volver a preguntar.",
                            "When Android asks whether G9 Control Remapper can access the controller, check “Always” (or “Always open”) " +
                                "and accept. That way the app and automatic switching work without asking again.")
                    ) {
                        if (!s.connected && s.padMode == PadMode.ANDROID) Button(onClick = { a.readAll() }) { Text(tr("Conectar", "Connect")) }
                    }
                    Step(
                        3, s.autoServiceOn, tr("Servicio de accesibilidad", "Accessibility service"),
                        tr("Para que el control cambie solo a X-Input o DS5 al abrir un juego, para la guía de Vista + Menú y para " +
                            "el panel de juego (L3 + R3), activa el servicio de accesibilidad de G9 Control Remapper. Ve qué app está en " +
                            "pantalla y los botones del control, y saca capturas del juego cuando se lo pides. Android avisará que " +
                            "puede leer la pantalla: lo pide para saber cuál es la ventana del juego y capturarla sola; la app no " +
                            "lee sus textos, no ve lo que escribes y no se conecta a internet.",
                            "To switch the controller to X-Input or DS5 automatically when you open a game, for the View + Menu " +
                                "guide and for the game panel (L3 + R3), turn on G9 Control Remapper's accessibility service. It sees which app is " +
                                "on screen and the controller's buttons, and takes game screenshots when you ask. Android will warn that " +
                                "it can read the screen: it needs that to know which window is the game and capture only that; the app " +
                                "doesn't read its text, doesn't see what you type and doesn't connect to the internet.")
                    ) {
                        // El candado de «ajustes restringidos» se avisa siempre: quien instala el APK a mano
                        // (fuera de una tienda) lo va a necesitar para poder activar la accesibilidad.
                        Hint(tr("Si al activarlo Android dice «Ajuste restringido», primero abre «Ajustes de la app» → ⋮ → «Permitir ajustes restringidos», y después actívalo aquí.",
                            "If Android says “Restricted setting” when you turn it on, first open “App settings” → ⋮ → “Allow restricted settings”, then turn it on here."))
                        if (!s.autoServiceOn) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { AutoRules.openServiceSettings(a) }) { Text(tr("Abrir accesibilidad", "Open accessibility")) }
                                OutlinedButton(onClick = { AutoRules.openAppInfo(a) }) { Text(tr("Ajustes de la app", "App settings")) }
                            }
                        }
                    }
                    Step(
                        4, null, tr("Íconos de ajustes rápidos (opcional)", "Quick settings tiles (optional)"),
                        tr("Desliza dos veces hacia abajo desde arriba de la pantalla, toca el lápiz (editar) y arrastra " +
                            "«Android», «X-Input» y «DS5» (los del logo G9) a tus íconos. Cambian el modo con un toque.",
                            "Swipe down twice from the top of the screen, tap the pencil (edit) and drag " +
                                "“Android”, “X-Input” and “DS5” (the ones with the G9 logo) into your tiles. They switch mode with one tap.")
                    )
                    Hint(
                        tr("El firmware del control no se actualiza desde esta app. Si alguna vez lo necesitas, usa la app oficial de Legion una vez.",
                            "This app doesn't update the controller firmware. If you ever need to, use the official Legion app once.")
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onClose) { Text(tr("Listo", "Done")) }
                }
            }
        }
    }
}

@Composable
private fun Step(n: Int, done: Boolean?, title: String, text: String, action: @Composable () -> Unit = {}) {
    val c = MaterialTheme.colorScheme
    val ok = Color(0xFF2EB872)
    Surface(color = c.surfaceContainerHigh, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(if (done == true) ok else c.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (done == true) Icon(Icons.Filled.Check, null, Modifier.size(20.dp), tint = Color.White)
                else Text("$n", fontWeight = FontWeight.Bold, color = c.onPrimaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(text, fontSize = 14.sp, lineHeight = 20.sp, color = c.onSurfaceVariant)
                action()
            }
        }
    }
}
