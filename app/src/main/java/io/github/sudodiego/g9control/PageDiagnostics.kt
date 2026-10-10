package io.github.sudodiego.g9control

import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp

/** Página "Ajustes": opciones de la app, datos del control, valores de fábrica y detalles técnicos. */
@Composable
fun DiagnosticsPage(s: AppState, a: MainActivity) {
    // Detalles técnicos ocultos: como el modo desarrollador de Android, aparecen al tocar la versión 5 veces.
    var dev by remember { mutableStateOf(Prefs.devTools(a)) }
    var taps by remember { mutableIntStateOf(0) }
    TwoPane(
        first = {
            Panel(tr("Aplicación", "App")) {
                Text(tr("Idioma", "Language"), fontSize = 14.sp)
                ChoiceChips(AppLang.entries.map { it to it.label }, Lang.choice) { Lang.set(a, it) }
                Text(tr("Estilo del dibujo", "Drawing style"), fontSize = 14.sp)
                ChoiceChips(ControllerSkin.entries.map { it to it.label }, SkinState.skin) {
                    SkinState.skin = it; Prefs.setSkin(a, it)
                }
                Text(tr("Tema", "Theme"), fontSize = 14.sp)
                ChoiceChips(AppTheme.entries.map { it to it.label }, ThemeState.theme) {
                    ThemeState.theme = it; Prefs.setTheme(a, it)
                }
                Text(tr("Dibujo del control", "Controller drawing"), fontSize = 14.sp)
                ChoiceChips(listOf(false to tr("A la izquierda", "On the left"), true to tr("Al centro", "Centered")), s.centered) {
                    s.centered = it; Prefs.setCentered(a, it)
                }
                OutlinedButton(onClick = { s.showSetup = true }) { Text(tr("Ver la configuración inicial", "Show quick setup")) }
                SwitchRow(tr("Abrir la app al conectar el control", "Open the app when the controller is connected"), s.openOnConnect) {
                    s.openOnConnect = it; Prefs.setOpenOnConnect(a, it)
                }
                val version = runCatching { a.packageManager.getPackageInfo(a.packageName, 0).versionName }.getOrNull() ?: "?"
                Row(
                    Modifier.fillMaxWidth().clickable {
                        taps++
                        if (taps >= 5 && !dev) { dev = true; Prefs.setDevTools(a, true); s.status = tr("Herramientas de desarrollo activadas.", "Developer tools enabled.") }
                    }.padding(vertical = 6.dp)
                ) {
                    Text(tr("Versión", "Version"), Modifier.weight(1f), fontSize = 14.sp)
                    Text(version, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        second = {
            GamePanelSettings(s, a)
            NoticesPanel(s, a)
            if (s.devInfo.isNotEmpty()) Panel(tr("Control", "Controller")) {
                // El identificador solo con las herramientas de desarrollo.
                s.devInfo.filter { dev || it.first !in setOf("Identificador", "Identifier") }.forEach { (k, v) ->
                    Row {
                        Text(k, Modifier.width(140.dp), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, fontSize = 14.sp)
                    }
                }
            }
            ResetPanel(s, a)
            if (dev) {
                Panel(tr("Detalles técnicos", "Technical details"), trailing = {
                    TextButton(onClick = { dev = false; taps = 0; Prefs.setDevTools(a, false) }) { Text(tr("Ocultar", "Hide")) }
                }) {
                    Mono(s.deviceText)
                    s.mode?.let { Mono(tr("Modo leído: ${it.main} (submodo ${it.sub})", "Mode read: ${it.main} (submode ${it.sub})")) }
                    s.inputDevice?.let { Mono(tr("Entrada: $it", "Input: $it")) }
                    // Solo si hay algo más que el G9 (si no, repite lo de arriba).
                    if (s.allDevicesText.trim() != s.deviceText.trim()) Mono(s.allDevicesText)
                }
                ConsolePanel(s, a)
                Panel(tr("Registro", "Log"), trailing = {
                    IconButton(onClick = { s.refreshLog() }) { Icon(Icons.Outlined.Refresh, tr("Actualizar", "Refresh")) }
                }) {
                    Mono(s.logText.ifEmpty { "—" })
                }
            }
        }
    )
}

@Composable
private fun ResetPanel(s: AppState, a: MainActivity) {
    var ask by remember { mutableStateOf(false) }
    Panel(tr("Valores de fábrica", "Factory settings")) {
        OutlinedButton(onClick = { ask = true }, enabled = s.connected && !s.busy) {
            Text(tr("Restaurar valores de fábrica", "Restore factory settings"), color = MaterialTheme.colorScheme.error)
        }
    }
    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text(tr("¿Restaurar valores de fábrica?", "Restore factory settings?")) },
        text = {
            Text(
                tr(
                    "Se restauran las zonas muertas (sticks 10 %, gatillos 5–95 %), la vibración (75 %) y las luces, se desactivan el gatillo rápido y las " +
                        "opciones de la cruceta, A/B y X/Y vuelven a su lugar, y los botones traseros quedan sin asignar (se borran sus macros).\n\n" +
                        "Los perfiles guardados en la tablet no se tocan: puedes volver a aplicar uno después.",
                    "Dead zones (sticks 10%, triggers 5–95%), vibration (75%) and lights are restored, hair trigger and D-pad options are " +
                        "turned off, A/B and X/Y go back to normal, and the back buttons are unassigned (their macros are deleted).\n\n" +
                        "Profiles saved on the tablet aren't touched: you can apply one again afterwards."
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { ask = false; a.factoryReset() }) {
                Text(tr("Restaurar", "Restore"), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { ask = false }) { Text(tr("Cancelar", "Cancel")) } }
    )
}

@Composable
private fun Mono(text: String) =
    Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)

@Composable
private fun ConsolePanel(s: AppState, a: MainActivity) {
    var hex by remember { mutableStateOf("08 02 05 00") }
    var sum by remember { mutableStateOf(true) }
    Panel(tr("Consola", "Console"), tr("Envía bytes al control tal cual. No hace falta para el uso normal.", "Sends raw bytes to the controller. Not needed for normal use.")) {
        OutlinedTextField(
            hex, { hex = it }, Modifier.fillMaxWidth(), label = { Text(tr("Bytes en hex", "Hex bytes")) },
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            singleLine = true
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = sum, onCheckedChange = { sum = it })
            Text(tr("Agregar checksum", "Add checksum"), Modifier.weight(1f))
            Button(onClick = { a.console(hex, sum) }) { Text(tr("Enviar", "Send")) }
        }
        if (s.consoleOut.isNotEmpty()) Mono(s.consoleOut)
    }
}

/**
 * Cómo se ven los avisos de cambio de modo, ordenados por cuándo aparecen: al abrir un juego, al
 * salir de él y cuando la app cambia el modo sola. Cada uno con su bloqueo en una sola elección.
 */
@Composable
private fun NoticesPanel(s: AppState, a: MainActivity) {
    var small by remember { mutableStateOf(AutoRules.guideSmall(a)) }
    var big by remember { mutableStateOf(lockOf(AutoRules.guideBlock(a), AutoRules.blockSticks(a))) }
    var mini by remember { mutableStateOf(lockOf(AutoRules.miniBlock(a), AutoRules.miniBlockSticks(a))) }
    var pos by remember { mutableStateOf(AutoRules.noticePos(a)) }
    var label by remember { mutableStateOf(AutoRules.showLabel(a)) }
    var guidePos by remember { mutableStateOf(AutoRules.guidePos(a)) }
    var anim by remember { mutableStateOf(AutoRules.noticeAnim(a)) }
    val on = s.autoServiceOn
    // Estas opciones son de la app, no del control: siempre se pueden cambiar.
    CompositionLocalProvider(LocalEditable provides true) {
        Panel(tr("Avisos de cambio de modo", "Mode change notices")) {
            NoticeBox(tr("Al abrir un juego", "When opening a game"), tr("Pide mantener Vista + Menú si el juego usa otro modo.", "Asks you to hold View + Menu if the game uses another mode."),
                if (on) { { AutoRules.preview(a, if (small) "mini" else "big") } } else null) {
                ChoiceChips(listOf(false to tr("Tarjeta en el centro", "Card in the center"), true to tr("Aviso pequeño", "Small notice")), small) {
                    small = it; AutoRules.setGuideSmall(a, it)
                }
                if (small) Hint(tr("Usa el mismo bloqueo y posición que el aviso al salir de un juego.", "Uses the same blocking and position as the notice when leaving a game."))
                else LockChips(big) { big = it; AutoRules.setGuideBlock(a, it > 0); AutoRules.setBlockSticks(a, it > 1) }
            }
            NoticeBox(tr("Al salir de un juego", "When leaving a game"), tr("Aviso pequeño para volver a Android.", "Small notice to return to Android."),
                if (on) { { AutoRules.preview(a, "mini") } } else null,
                side = { PosGrid(guidePos) { guidePos = it; AutoRules.setGuidePos(a, it) } }) {
                LockChips(mini) { mini = it; AutoRules.setMiniBlock(a, it > 0); AutoRules.setMiniBlockSticks(a, it > 1) }
            }
            NoticeBox(tr("Cuando la app cambia el modo", "When the app switches mode"), tr("Etiqueta que muestra el modo nuevo. No bloquea nada.", "Label showing the new mode. Doesn't block anything."),
                if (on && label) { { AutoRules.preview(a, "banner") } } else null,
                side = if (label) { { PosGrid(pos) { pos = it; AutoRules.setNoticePos(a, it) } } } else null) {
                SwitchRow(tr("Mostrar la etiqueta", "Show the label"), label) { label = it; AutoRules.setShowLabel(a, it) }
            }
            if ((!small && big > 0) || mini > 0)
                Hint(tr("Para quitar un aviso que bloquea sin cambiar de modo: mantén presionada la pantalla 2 s.", "To dismiss a blocking notice without switching mode: press and hold the screen for 2 s."))
            Text(tr("Animación", "Animation"), fontSize = 14.sp)
            ChoiceChips(NoticeAnim.entries.map { it to it.label }, anim) { anim = it; AutoRules.setNoticeAnim(a, it) }
            if (!on) Hint(tr("Para ver los avisos hay que activar el servicio de accesibilidad (en Juegos).", "To see the notices, turn on the accessibility service (in Games)."))
        }
    }
}

/**
 * Panel de juego (L3 + R3) y lo que se ve de Game Assistant, el de la ROM de la tablet: si está en modo
 * juego ahora (se actualiza solo) y un acceso a sus ajustes.
 */
@Composable
private fun GamePanelSettings(s: AppState, a: MainActivity) {
    var on by remember { mutableStateOf(AutoRules.gamePanel(a)) }
    var shot by remember { mutableStateOf(AutoRules.touchpadShot(a)) }
    var shotPos by remember { mutableStateOf(AutoRules.shotPos(a)) }
    var shotSound by remember { mutableStateOf(AutoRules.shotSound(a)) }
    var shotAnim by remember { mutableStateOf(AutoRules.shotAnim(a)) }
    var legion by remember { mutableStateOf(AutoRules.legionKeys(a)) }
    var swapVol by remember { mutableStateOf(AutoRules.swapVolume(a)) }
    val zui = remember { ZuiGame.installed(a) }
    var game by remember { mutableStateOf(ZuiGame.inGame(a)) }
    if (zui) DisposableEffect(Unit) {
        val o = ZuiGame.observe(a, android.os.Handler(a.mainLooper)) { game = ZuiGame.inGame(a) }
        onDispose { ZuiGame.unobserve(a, o) }
    }
    CompositionLocalProvider(LocalEditable provides true) {
        Panel(tr("Panel de juego", "Game panel"), tr("Menú flotante para usar desde cualquier juego sin soltar el control.", "Floating menu to use from any game without letting go of the controller.")) {
            SwitchRow(
                tr("L3 + R3", "L3 + R3"), on,
                tr("Tocar: abre el panel. Mantener medio segundo: captura directa (en cualquier modo).",
                    "Tap: opens the panel. Hold half a second: direct screenshot (in any mode).")
            ) { on = it; AutoRules.setGamePanel(a, it) }
            SwitchRow(
                tr("M dos veces en DS5: captura", "M twice in DS5: screenshot"), shot,
                tr("Captura solo el juego, sin el puntero que aparece. El toque ya no llega al juego como clic del touchpad: apágalo si un juego lo usa o si conectas un mouse.",
                    "Captures only the game, without the pointer that appears. The tap no longer reaches the game as a touchpad click: turn it off if a game uses it or if you connect a mouse.")
            ) { shot = it; AutoRules.setTouchpadShot(a, it) }
            SwitchRow(
                tr("Botón Legion", "Legion button"), legion,
                tr("Tocar: Atrás. Mantener medio segundo: ajustes rápidos. En cualquier modo y app, menos dentro de G9 Control.",
                    "Tap: Back. Hold half a second: quick settings. In any mode and app, except inside G9 Control.")
            ) { legion = it; AutoRules.setLegionKeys(a, it) }
            SwitchRow(
                tr("Invertir el volumen de la tablet", "Swap the tablet's volume buttons"), swapVol,
                tr("El botón de abajo sube el volumen y el de arriba lo baja. Solo los botones de la tablet; los del control quedan igual.",
                    "The lower button turns the volume up and the upper one turns it down. Only the tablet's buttons; the controller's stay the same.")
            ) { swapVol = it; AutoRules.setSwapVolume(a, it) }
            ScreenRow(a)
            NoticeBox(tr("Aviso de captura", "Screenshot notice"), tr("Miniatura de la captura que entra desde el borde.", "Thumbnail of the screenshot that slides in from the edge."),
                if (s.autoServiceOn) { { AutoRules.preview(a, "shot") } } else null,
                side = { PosGrid(shotPos) { shotPos = it; AutoRules.setShotPos(a, it) } }) {
                Text(tr("Sonido", "Sound"), fontSize = 14.sp)
                ChoiceChips(ShotSound.entries.map { it to it.label }, shotSound) { shotSound = it; AutoRules.setShotSound(a, it) }
                Text(tr("Animación", "Animation"), fontSize = 14.sp)
                ChoiceChips(NoticeAnim.entries.map { it to it.label }, shotAnim) { shotAnim = it; AutoRules.setShotAnim(a, it) }
            }
            Hint(tr("Captura de pantalla, panel de Game Assistant (si está activo), cambiar el modo del control y abrir G9 Control. " +
                "Se maneja con LB / RB, A y B; mientras está abierto, los botones no llegan al juego.",
                "Screenshot, Game Assistant panel (when active), switch controller mode and open G9 Control. " +
                    "Use LB / RB, A and B; while it's open, buttons don't reach the game."))
            if (!s.autoServiceOn) Hint(tr("Necesita el servicio de accesibilidad (en Juegos).", "Needs the accessibility service (in Games)."))
            if (zui) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Game Assistant", Modifier.weight(1f), fontSize = 14.sp)
                    Text(
                        if (game) tr("En modo juego", "In game mode") else tr("Sin juego activo", "No game active"),
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { if (!ZuiGame.openSettings(a)) s.fail(tr("No se pudieron abrir los ajustes de Game Assistant.", "Couldn't open Game Assistant settings.")) }) {
                    Text(tr("Ajustes de Game Assistant", "Game Assistant settings"))
                }
                ShizukuRow(a)
            }
        }
    }
}

/**
 * Shizuku: con él, el panel de juego puede cambiar el modo de rendimiento y mostrar Game Assistant aunque esté
 * oculto. Sin él, se explica cómo ocultarlo o mostrarlo a mano.
 */
@Composable
private fun ShizukuRow(a: MainActivity) {
    LaunchedEffect(Unit) { Shell.init(a); Shell.refresh() }
    val st = Shell.state
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Shizuku", Modifier.weight(1f), fontSize = 14.sp)
        Text(
            when (st) {
                Shell.State.READY -> tr("Listo", "Ready")
                Shell.State.NO_PERMISSION -> tr("Falta el permiso", "Permission missing")
                Shell.State.NOT_RUNNING -> tr("No está activo", "Not running")
            },
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (st == Shell.State.READY) {
        val gaScope = rememberCoroutineScope()
        var gaHidden by remember { mutableStateOf(AutoRules.gaHidden(a)) }
        // Lo que el usuario eligió, no cómo está ahora: puede estar visible un rato porque G9 Control lo está usando
        // (copiar el estado actual a la preferencia hacía que no se volviera a ocultar). Solo la primera vez, sin
        // preferencia guardada, se toma de cómo está.
        LaunchedEffect(Unit) { if (gaHidden == null) ZuiGame.hidden()?.let { gaHidden = it; AutoRules.setGaHidden(a, it) } }
        SwitchRow(
            tr("Ocultar Game Assistant", "Hide Game Assistant"), gaHidden == true,
            tr("Quita su pestaña lateral, su panel y sus avisos (sigue funcionando por detrás). G9 Control lo muestra solo mientras lo usa.",
                "Removes its side tab, panel and notices (it keeps working in the background). G9 Control shows it only while using it.")
        ) { want ->
            gaScope.launch {
                if (ZuiGame.setHidden(want)) {
                    AutoRules.setGaHidden(a, want)
                    gaHidden = want
                }
            }
        }
    }
    when (st) {
        Shell.State.READY -> Hint(tr("En un juego, el panel de juego ofrece Rendimiento (Ahorro, Equilibrado o Bestia) y muestra Game Assistant aunque esté oculto; al salir del juego lo vuelve a ocultar.",
            "In a game, the game panel offers Performance (Power saving, Balanced or Beast) and shows Game Assistant even if it's hidden; it hides it again when you leave the game."))
        Shell.State.NO_PERMISSION -> OutlinedButton(onClick = { Shell.requestPermission() }) { Text(tr("Dar permiso a G9 Control", "Grant permission to G9 Control")) }
        Shell.State.NOT_RUNNING -> Hint(tr("Con Shizuku (hay que iniciarlo tras cada reinicio), el panel de juego cambia el modo de rendimiento. Sin él, para ocultar Game Assistant: adb shell appops set ${ZuiGame.PACKAGE} SYSTEM_ALERT_WINDOW ignore (allow para mostrarlo).",
            "With Shizuku (it must be started after every reboot), the game panel changes the performance mode. Without it, to hide Game Assistant: adb shell appops set ${ZuiGame.PACKAGE} SYSTEM_ALERT_WINDOW ignore (allow to show it)."))
    }
}

/**
 * Pantalla 16:9 para un monitor externo. Sin Shizuku si la app tiene WRITE_SECURE_SETTINGS (se da una vez con
 * adb); si no, con Shizuku; si no hay ninguno, el comando para darlo.
 */
@Composable
private fun ScreenRow(a: MainActivity) {
    val scope = rememberCoroutineScope()
    var wide by remember { mutableStateOf(Aspect.is169(a)) }
    if (!Aspect.available(a)) {
        Text(tr("Pantalla 16:9", "16:9 screen"), fontSize = 14.sp)
        Hint(tr("Para un monitor externo sin bordes. Necesita un permiso que se da una vez desde un computador: ${Aspect.GRANT_COMMAND}",
            "For an external monitor without borders. It needs a permission you grant once from a computer: ${Aspect.GRANT_COMMAND}"))
        return
    }
    SwitchRow(
        tr("Pantalla 16:9", "16:9 screen"), wide,
        tr("Para un monitor externo: la imagen llena el monitor sin bordes (en la tablet quedan franjas negras). Se recuerda al reiniciar; también se cambia desde el panel de juego. Algunas apps abiertas se cierran al cambiarla (Dolphin).",
            "For an external monitor: the picture fills it with no borders (the tablet shows black bars). It persists after a reboot; you can also switch it from the game panel. Some open apps close when it changes (Dolphin).")
    ) { want ->
        scope.launch {
            if (want != Aspect.is169(a)) Aspect.toggle(a)
            kotlinx.coroutines.delay(500)
            wide = Aspect.is169(a)
        }
    }
}

/** Bloqueo en un número: 0 nada, 1 botones y pantalla, 2 también sticks y gatillos. */
private fun lockOf(block: Boolean, sticks: Boolean) = if (!block) 0 else if (!sticks) 1 else 2

/** Qué bloquea un aviso mientras está en pantalla, en una sola elección. */
@Composable
private fun LockChips(level: Int, onPick: (Int) -> Unit) {
    Text(tr("Mientras está en pantalla, bloquear", "While on screen, block"), fontSize = 14.sp)
    ChoiceChips(listOf(0 to tr("Nada", "Nothing"), 1 to tr("Botones", "Buttons"), 2 to tr("Todo", "Everything")), level, onPick)
    when (level) {
        1 -> Hint(tr("Botones y pantalla táctil. Sticks y gatillos siguen llegando al juego.", "Buttons and touchscreen. Sticks and triggers still reach the game."))
        2 -> Hint(tr("Botones, pantalla, sticks y gatillos. El juego pierde el foco y puede pausarse.", "Buttons, screen, sticks and triggers. The game loses focus and may pause."))
    }
}

/** Recuadro de un aviso: cuándo aparece, sus opciones, botón "Ver" y, a la derecha, su posición. */
@Composable
private fun NoticeBox(
    title: String, desc: String, onPreview: (() -> Unit)?, side: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = MaterialTheme.colorScheme
    Surface(color = c.surfaceContainerHigh, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    if (onPreview != null) TextButton(onClick = onPreview) { Text(tr("Ver", "Preview")) }
                }
                Text(desc, fontSize = 13.sp, color = c.onSurfaceVariant)
                content()
            }
            if (side != null) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tr("Posición", "Position"), fontSize = 13.sp, color = c.onSurfaceVariant)
                side()
            }
        }
    }
}

/** Posición en la pantalla: una pantalla en miniatura con 9 lugares; el elegido se marca. */
@Composable
private fun PosGrid(sel: NoticePos, onPick: (NoticePos) -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        color = c.surface, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.width(170.dp).aspectRatio(16f / 10f)
    ) {
        Column(Modifier.padding(8.dp)) {
            for (v in -1..1) Row(Modifier.weight(1f)) {
                for (h in -1..1) {
                    val p = NoticePos.entries.first { it.h == h && it.v == v }
                    val on = p == sel
                    Box(
                        Modifier.weight(1f).fillMaxHeight().padding(3.dp).clip(MaterialTheme.shapes.small)
                            .background(if (on) c.primary else c.surfaceContainerHighest)
                            .clickable(onClickLabel = p.label) { onPick(p) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (on) Box(Modifier.size(width = 28.dp, height = 8.dp).clip(CircleShape).background(c.onPrimary))
                    }
                }
            }
        }
    }
}
