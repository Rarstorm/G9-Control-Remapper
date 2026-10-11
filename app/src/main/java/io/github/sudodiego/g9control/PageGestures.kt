package io.github.sudodiego.g9control

import android.content.Intent
import android.view.KeyEvent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Página "Gestos": un botón del control (por ejemplo, el que manda un botón trasero) hace algo
 * distinto según se toque, se mantenga o se toque dos veces.
 */
@Composable
fun GesturesPage(s: AppState, a: MainActivity) {
    val ctx = LocalContext.current
    var scope by remember { mutableStateOf<String?>(null) }                            // null = todas las apps
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }  // paquete a nombre
    var rev by remember { mutableStateOf(0) }                                          // se sube al guardar
    // Sin botón preseleccionado: antes abría con B ya elegido y confundía (comentario del debug 10-10).
    var sel by remember { mutableStateOf<PadKey?>(null) }
    val key = sel?.let { PAD_KEYCODE[it] } ?: 0   // 0 = todavía no se tocó ningún botón

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != ctx.packageName }
                .map { it.packageName to pm.getApplicationLabel(it).toString() }
                .sortedBy { it.second.lowercase() }
        }
    }

    // «Aprender del mando»: el botón que se apretó en el control queda elegido aquí y se borra el aviso.
    LaunchedEffect(s.learnedPad) {
        s.learnedPad?.let { pk ->
            if (pk in PAD_KEYCODE) sel = pk
            s.learnedPad = null
        }
    }

    val withRules = remember(rev) { Gestures.apps(a) }
    // Lo que tiene cada app, para el selector: «● Chrome — LT → Atrás · A → tecla Espacio».
    val summaries = remember(rev) { Gestures.apps(a).associateWith { p -> gestureSummary(Gestures.appRules(a, p)) } }
    val allSummary = remember(rev) { gestureSummary(Gestures.rules(a)) }
    val globRule = remember(rev, key) { if (key == 0) null else Gestures.rules(a)[key] }
    val appRule = remember(rev, key, scope) { if (key == 0 || scope == null) null else Gestures.appRules(a, scope!!)[key] }
    val shown = appRule ?: globRule ?: GestureRule(key, GestureAction.NONE, GestureAction.NONE, GestureAction.NONE)
    val inherited = scope != null && appRule == null && globRule != null
    val scopeLabel = scope?.let { p -> apps.firstOrNull { it.first == p }?.second ?: p } ?: tr("Todas las apps", "All apps")

    fun write(r: GestureRule) {
        if (key == 0) return   // sin botón elegido no se escribe nada
        val p = scope
        if (p == null) Gestures.setRule(a, r) else Gestures.setAppRule(a, p, r)
        rev++
    }

    val padOf = MACRO_KEY_BYTE.entries.associate { (k, v) -> v to k }
    val backTags = BackKey.entries
        .mapNotNull { k -> padOf[s.backKeys[k.id] ?: 0]?.let { it to k.label } }
        .groupBy({ it.first }, { it.second })
    val scopeRules = remember(rev, scope) { if (scope == null) Gestures.rules(a) else Gestures.appRules(a, scope!!) }

    TwoPane(
        first = {
            Panel {
                var pick by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Aplica a", "Applies to"), Modifier.width(90.dp), fontWeight = FontWeight.SemiBold)
                    Box {
                        OutlinedButton(onClick = { pick = true }) {
                            Text(scopeLabel)
                            Icon(Icons.Filled.ArrowDropDown, null)
                        }
                        DropdownMenu(pick, { pick = false }) {
                            DropdownMenuItem(
                                text = { Text(tr("Todas las apps", "All apps") + if (allSummary.isEmpty()) "" else " — $allSummary", maxLines = 4) },
                                onClick = { pick = false; scope = null }
                            )
                            apps.sortedByDescending { it.first in withRules }.forEach { (p, l) ->
                                DropdownMenuItem(
                                    text = { Text(((if (p in withRules) "● " else "") + l + (summaries[p]?.let { " — $it" } ?: "")), maxLines = 4) },
                                    onClick = { pick = false; scope = p }
                                )
                            }
                        }
                    }
                }
                ControllerKeyPicker(
                    lit = setOfNotNull(sel), tags = backTags, enabled = true,
                    modifier = Modifier.fillMaxWidth(), lights = s
                ) { k ->
                    if (k in PAD_KEYCODE) sel = k
                    // Home ya se puede usar (si tiene gesto, el servicio le deja pasar): lo que no se puede
                    // son los botones que no llegan a Android (M, sticks).
                    else s.fail(tr("Ese botón no sirve para gestos.", "That button can't be used for gestures."))
                }
                Text(tr("Botón con gestos", "Gesture button"), fontWeight = FontWeight.SemiBold)
                if (s.learnGesture) {
                    Text(tr("Aprieta ahora un botón en el mando…", "Now press a button on the controller…"), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { s.learnGesture = false }) { Text(tr("Cancelar", "Cancel")) }
                } else {
                    OutlinedButton(onClick = { s.learnGesture = true }) { Text(tr("Aprender del mando", "Learn from controller")) }
                }
                Hint(
                    tr("Toca un botón del dibujo, o apriétalo en el mando con «Aprender del mando». Para los traseros: primero asígnalos en «Traseros» al botón que elijas aquí.",
                        "Tap a button on the drawing, or press it on the controller with “Learn from controller”. For the back paddles: first assign them in “Paddles” to the button you pick here.")
                )
            }
        },
        second = {
            Panel(
                if (scope == null) tr("Gestos", "Gestures") else tr("Gestos en esta app", "Gestures in this app"),
                tr("Mientras un botón tenga gestos, ese botón no llega a la app: lo usa G9 Control.", "While a button has gestures, that button doesn't reach the app: G9 Control uses it.")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Botón", "Button"), Modifier.width(120.dp), fontWeight = FontWeight.SemiBold)
                    Text(sel?.name ?: tr("Ningún botón", "No button"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (inherited) {
                        Spacer(Modifier.width(10.dp))
                        Text(tr("(hereda de Todas)", "(inherits from All)"), fontSize = 12.sp)
                    }
                }
                // Sin botón elegido no hay nada que configurar (y no se escribe nada por accidente).
                if (key == 0) {
                    Hint(tr("Toca un botón del dibujo para ver y cambiar sus gestos.",
                        "Tap a button on the drawing to see and change its gestures."))
                } else {
                GestureRow(tr("Tocar", "Tap"), shown.tap) { write(shown.copy(tap = it)) }
                GestureRow(tr("Mantener", "Long-press"), shown.long) { write(shown.copy(long = it)) }
                GestureRow(tr("Doble toque", "Double-tap"), shown.dbl) { write(shown.copy(dbl = it)) }

                // La tecla solo aparece si algún gesto la usa: el mando NO envía teclas de teclado,
                // las inyecta la app con Shizuku (`input keyevent`).
                if (shown.anyKey) {
                    KeyPicker(shown.kc) { write(shown.copy(kc = it)) }
                    if (Shell.state != Shell.State.READY) Hint(
                        tr("Ojo: para enviar la tecla hace falta Shizuku iniciado (Ajustes → Permisos).",
                            "Note: sending the key needs Shizuku running (Settings → Permissions).")
                    )
                    else Hint(
                        tr("La tecla la envía G9 Control: el control no manda teclas de teclado. Tarda unas décimas de segundo.",
                            "G9 Control sends the key: the controller doesn't send keyboard keys. It takes a few tenths of a second.")
                    )
                }

                if (scope != null) {
                    Hint(
                        tr("Aquí, «Nada» en los tres hace que el botón funcione normal en esta app (llega al juego).",
                            "Here, “Nothing” on all three makes the button work normally in this app (it reaches the game).")
                    )
                    if (appRule != null) OutlinedButton(onClick = { Gestures.clearApp(a, scope!!); rev++ }) {
                        Text(tr("Quitar lo propio de esta app", "Remove this app's settings"))
                    }
                }
                val others = scopeRules.keys.filter { it != key }
                if (others.isNotEmpty()) Hint(
                    tr("Otros botones con gestos aquí: " + others.joinToString { KEYCODE_PAD[it]?.name ?: "?" },
                        "Other buttons with gestures here: " + others.joinToString { KEYCODE_PAD[it]?.name ?: "?" })
                )
                Hint(
                    tr("Para borrar: «Nada» en los tres. Tocar espera ~0,3 s (por si viene el doble); mantener entra a ~0,45 s.",
                        "To clear: “Nothing” on all three. A tap waits ~0.3 s (in case a double comes); long-press triggers at ~0.45 s.")
                )
                }

                // Qué tiene la app elegida (o «Todas las apps»), sin ir botón por botón: los botones y qué hace cada uno.
                val summary = gestureSummary(scopeRules)
                if (summary.isNotEmpty()) {
                    Text(
                        if (scope == null) tr("Lo que tiene Todas las apps", "What All apps has")
                        else tr("Lo que tiene esta app", "What this app has"),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(summary, fontSize = 13.sp)
                }
                // Tiempos de los gestos: antes solo se podían cambiar por dentro (g_tap_max, g_long, g_double).
                // Los tres sliders a la vez ocupaban mucho: se elige el tiempo con un botón y debajo sale solo
                // el slider del elegido (0 = tocar, 1 = mantener, 2 = doble).
                var tapMs by remember(rev) { mutableIntStateOf(Gestures.tapMax(a)) }
                var longMs by remember(rev) { mutableIntStateOf(Gestures.longMs(a)) }
                var doubleMs by remember(rev) { mutableIntStateOf(Gestures.doubleMs(a)) }
                var openTimer by remember { mutableStateOf<Int?>(null) }
                Text(tr("Tiempos", "Timings"), fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimerButton(tr("Tocar", "Tap"), "$tapMs ms", openTimer == 0, Modifier.weight(1f)) { openTimer = if (openTimer == 0) null else 0 }
                    TimerButton(tr("Mantener", "Long"), "$longMs ms", openTimer == 1, Modifier.weight(1f)) { openTimer = if (openTimer == 1) null else 1 }
                    TimerButton(tr("Doble", "Double"), "$doubleMs ms", openTimer == 2, Modifier.weight(1f)) { openTimer = if (openTimer == 2) null else 2 }
                }
                when (openTimer) {
                    0 -> SliderRow(tr("Tocar: hasta", "Tap: up to"), tapMs, 100..500, "$tapMs ms") { tapMs = it; Gestures.setDurations(a, it, longMs, doubleMs) }
                    1 -> SliderRow(tr("Mantener: desde", "Long-press: from"), longMs, 250..1000, "$longMs ms") { longMs = it; Gestures.setDurations(a, tapMs, it, doubleMs) }
                    2 -> SliderRow(tr("Doble toque: dentro de", "Double-tap: within"), doubleMs, 150..600, "$doubleMs ms") { doubleMs = it; Gestures.setDurations(a, tapMs, longMs, it) }
                }
            }
        }
    )
}

@Composable
private fun GestureRow(label: String, cur: GestureAction, onPick: (GestureAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 15.sp)
        Box {
            OutlinedButton(onClick = { open = true }) {
                Text(if (Lang.en) cur.en else cur.es)
                Icon(Icons.Filled.ArrowDropDown, null)
            }
            DropdownMenu(open, { open = false }) {
                GestureAction.entries.forEach { act ->
                    DropdownMenuItem(
                        text = { Text(if (Lang.en) act.en else act.es) },
                        onClick = { open = false; onPick(act) }
                    )
                }
            }
        }
    }
}

/** Un tiempo de gesto (Tocar / Mantener / Doble): al tocarlo aparece su slider debajo del todo el bloque. */
@Composable
private fun TimerButton(label: String, value: String, open: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 13.sp, maxLines = 1)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
    val padding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
    if (open) Button(onClick = onClick, modifier = modifier, contentPadding = padding) { content() }
    else OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = padding) { content() }
}

/** Elige la tecla de teclado que se inyecta cuando el gesto es «Tecla del teclado». */
@Composable
private fun KeyPicker(cur: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Tecla", "Key"), Modifier.width(120.dp), fontWeight = FontWeight.SemiBold)
        Box {
            OutlinedButton(onClick = { open = true }) {
                Text(gestureKeyName(cur).ifEmpty { tr("Elegir tecla…", "Choose key…") })
                Icon(Icons.Filled.ArrowDropDown, null)
            }
            DropdownMenu(open, { open = false }) {
                GESTURE_KEYS.groupBy { it.group }.forEach { (g, keys) ->
                    val head = GESTURE_KEY_GROUPS.getOrNull(g)
                    if (head != null) Text(
                        if (Lang.en) head.second else head.first,
                        Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                    )
                    keys.forEach { k ->
                        DropdownMenuItem(
                            text = { Text(if (Lang.en) k.en else k.es) },
                            onClick = { open = false; onPick(k.kc) }
                        )
                    }
                }
            }
        }
    }
}
