package io.github.sudodiego.g9control

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bumpers y gatillos (LB/RB/LT/RT). En el dibujo del control no se pueden tocar a propósito: están en el borde
 * y quedan muy chicos, así que van como botones aparte, aquí junto a los traseros — antes la única forma de
 * darles gestos era «Aprender del mando».
 */
private val BUMPER_KEYS = listOf(PadKey.LB, PadKey.RB, PadKey.LT, PadKey.RT)

/**
 * Página «Gestos»: qué hace cada botón del control —al tocarlo, con doble toque o manteniéndolo— y qué
 * hace cada trasero. Todo se resuelve por app con el selector «Aplica a», que incluye la pantalla de inicio
 * (para lo que solo tiene sentido en el launcher: cajón de apps y páginas).
 */
@Composable
fun GesturesPage(s: AppState, a: MainActivity) {
    val ctx = LocalContext.current
    var scope by remember { mutableStateOf<String?>(null) }                            // null = todas las apps
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }  // paquete a nombre
    var rev by remember { mutableStateOf(0) }                                          // se sube al guardar
    // Sin botón preseleccionado: antes abría con B ya elegido y confundía (comentario del debug 10-10).
    var sel by remember { mutableStateOf<PadKey?>(null) }
    // Los traseros se configuran aquí mismo: su «botón» es un código interno y sus reglas se guardan por app.
    var selPaddle by remember { mutableStateOf<BackKey?>(null) }
    val key = selPaddle?.let { PADDLE_KEYCODE[it] } ?: sel?.let { PAD_KEYCODE[it] } ?: 0   // 0 = todavía no se tocó nada

    // La pantalla de inicio (el launcher de la tablet): se saca del sistema, no de una lista escrita a mano.
    val launcherPkg = remember {
        runCatching {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.getOrNull()
    }

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
            if (pk in PAD_KEYCODE) { selPaddle = null; sel = pk }
            s.learnedPad = null
        }
    }

    val withRules = remember(rev) { Gestures.apps(a) }
    // Para el resumen: el paquete de «Abrir una app» se enseña con el nombre de la app.
    val names = remember(apps) { apps.toMap() }
    val labels: (String) -> String? = remember(names) { { p -> names[p] } }
    val globRule = remember(rev, key) { if (key == 0) null else Gestures.rules(a)[key] }
    val appRule = remember(rev, key, scope) { if (key == 0 || scope == null) null else Gestures.appRules(a, scope!!)[key] }
    val base = appRule ?: globRule ?: GestureRule(key, GestureAction.NONE, GestureAction.NONE, GestureAction.NONE)
    // Un trasero manda un pulso de ~20 ms: no hay forma de saber si lo mantienes, así que no se ofrece.
    val shown = if (selPaddle != null) base.copy(long = GestureAction.NONE) else base
    val inherited = scope != null && appRule == null && globRule != null
    val scopeLabel = when {
        scope == null -> tr("Todas las apps", "All apps")
        scope == launcherPkg -> tr("Pantalla de inicio", "Home screen")
        else -> scope?.let { p -> apps.firstOrNull { it.first == p }?.second ?: p } ?: ""
    }

    fun write(r: GestureRule) {
        if (key == 0) return   // sin botón elegido no se escribe nada
        val p = scope
        if (p == null) Gestures.setRule(a, r) else Gestures.setAppRule(a, p, r)
        s.saved(enControl = false)   // los gestos se guardan en la app: se avisa igual
        rev++
        // Si es un trasero, la app lo deja preparado sola (no hay que saber nada de marcas ni de botones).
        if (selPaddle != null) a.preparePaddles()
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
                        DropdownMenu(pick, { pick = false }, modifier = Modifier.heightIn(max = 420.dp)) {
                            // Solo el punto: qué tiene cada app se ve en «Botones mapeados», aquí estorbaba.
                            DropdownMenuItem(
                                text = { Text(tr("Todas las apps", "All apps")) },
                                onClick = { pick = false; scope = null }
                            )
                            // La pantalla de inicio, arriba y con nombre fijo: sirve para lo que solo pasa ahí
                            // (cajón de apps, páginas del launcher) sin buscarla entre las apps.
                            launcherPkg?.let { lp ->
                                DropdownMenuItem(
                                    text = { Text((if (lp in withRules) "● " else "") + tr("Pantalla de inicio", "Home screen")) },
                                    onClick = { pick = false; scope = lp }
                                )
                            }
                            apps.sortedByDescending { it.first in withRules }.forEach { (p, l) ->
                                DropdownMenuItem(
                                    text = { Text((if (p in withRules) "● " else "") + l) },
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
                    if (k in PAD_KEYCODE) { selPaddle = null; sel = k }
                    // Home ya se puede usar (si tiene gesto, el servicio le deja pasar): lo que no se puede
                    // son los botones que no llegan a Android (M, sticks).
                    else s.fail(tr("Ese botón no sirve para gestos.", "That button can't be used for gestures."))
                }
                // Bumpers/gatillos y traseros, uno al lado del otro: son los dos grupos que no están en el
                // dibujo del mando, y así los traseros quedan a la vista sin bajar por la pantalla.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Bumpers y gatillos", "Bumpers and triggers"), fontWeight = FontWeight.SemiBold)
                        // Los cuatro chips se reparten el ancho a partes iguales y la etiqueta va en UNA línea:
                        // con el chip «natural», en media pantalla «RT» y «R5» se partían en dos renglones.
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            BUMPER_KEYS.forEach { k ->
                                FilterChip(
                                    selected = sel == k,
                                    onClick = { sel = k; selPaddle = null },
                                    modifier = Modifier.weight(1f),
                                    label = { Text(k.label, fontSize = 12.sp, maxLines = 1, softWrap = false) }
                                )
                            }
                        }
                    }
                    // Traseros: se configuran aquí mismo, con el mismo «Aplica a». El control los reconoce por su
                    // firma (una macro cortísima que se graba una vez), así que pueden hacer algo distinto por app.
                    Column(Modifier.weight(1f)) {
                        Text(tr("Traseros", "Back paddles"), fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            BackKey.entries.forEach { k ->
                                FilterChip(
                                    selected = selPaddle == k,
                                    onClick = { selPaddle = k; sel = null },
                                    modifier = Modifier.weight(1f),
                                    label = { Text(k.label, fontSize = 12.sp, maxLines = 1, softWrap = false) }
                                )
                            }
                        }
                    }
                }
                Text(tr("Botón con gestos", "Gesture button"), fontWeight = FontWeight.SemiBold)
                if (s.learnGesture) {
                    Text(tr("Aprieta ahora un botón en el mando…", "Now press a button on the controller…"), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { s.learnGesture = false }) { Text(tr("Cancelar", "Cancel")) }
                } else {
                    OutlinedButton(onClick = { s.learnGesture = true }) { Text(tr("Aprender del mando", "Learn from controller")) }
                }
                // Nada de explicaciones cuando todo va bien. La app prepara los traseros sola; esto solo sale
                // si de verdad al apretar un trasero no pasa nada (una línea, y el botón de arreglarlo).
                val sinFirma = BackKey.entries.filter { s.backKeys[it.id] != null && s.backKeys[it.id] != BACK_MACRO }
                val hayReglasTrasero = remember(rev) {
                    Gestures.rules(a).keys.any { it in PADDLE_KEYCODE.values } ||
                        Gestures.apps(a).any { p -> Gestures.appRules(a, p).keys.any { it in PADDLE_KEYCODE.values } }
                }
                if (s.connected && sinFirma.isNotEmpty() && (hayReglasTrasero || selPaddle != null)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tr("Al apretar un trasero no pasa nada.", "Pressing a paddle does nothing."),
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(10.dp))
                        OutlinedButton(onClick = { a.writeSignatures() }, enabled = !s.busy) { Text(tr("Arreglar", "Fix")) }
                    }
                }
                Hint(
                    tr("Toca un botón del dibujo (o un bumper/gatillo), o apriétalo en el mando con «Aprender del mando».",
                        "Tap a button on the drawing (or a bumper/trigger), or press it on the controller with “Learn from controller”.")
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
                    Text(selPaddle?.label ?: sel?.name ?: tr("Ningún botón", "No button"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (inherited) {
                        Spacer(Modifier.width(10.dp))
                        Text(tr("(hereda de Todas)", "(inherits from All)"), fontSize = 12.sp)
                    }
                }
                // Sin botón elegido no hay nada que configurar (y no se escribe nada por accidente).
                if (key == 0) {
                    Hint(tr("Toca un botón del dibujo (o elige un trasero) para ver y cambiar sus gestos.",
                        "Tap a button on the drawing (or pick a back paddle) to see and change its gestures."))
                } else {
                // El orden es el de uso: un toque, dos toques y, por último, mantener. CADA gesto lleva su
                // propia tecla de teclado: la fila de la tecla sale debajo del gesto que sea «Tecla del teclado».
                GestureRow(tr("Un toque", "One tap"), shown.tap, shown.kcTap,
                    { write(shown.copy(tap = it)) }, { write(shown.copy(kcTap = it)) })
                GestureRow(tr("Doble toque", "Double tap"), shown.dbl, shown.kcDbl,
                    { write(shown.copy(dbl = it)) }, { write(shown.copy(kcDbl = it)) })
                if (selPaddle == null) {
                    GestureRow(tr("Mantener", "Long-press"), shown.long, shown.kcLong,
                        { write(shown.copy(long = it)) }, { write(shown.copy(kcLong = it)) })
                } else Hint(
                    tr("En un trasero no hay «Mantener»: el mando no distingue si lo dejas apretado.",
                        "There's no “Long-press” on a paddle: the controller can't tell if you hold it.")
                )

                if (shown.anyKey && Shell.state != Shell.State.READY) Hint(
                    tr("Ojo: para enviar la tecla hace falta Shizuku iniciado (Ajustes → Permisos).",
                        "Note: sending the key needs Shizuku running (Settings → Permissions).")
                )

                // Secuencia de teclas: solo sale si algún gesto la usa. Esa es «la macro» de un juego: se
                // asigna por app («Aplica a») y al salir de esa app vuelve lo de siempre.
                if (shown.tap == GestureAction.COMBO || shown.dbl == GestureAction.COMBO || shown.long == GestureAction.COMBO) {
                    Text(tr("Secuencia", "Sequence"), fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        shown.combo.forEachIndexed { i, step ->
                            OutlinedButton(onClick = {
                                write(shown.copy(combo = shown.combo.toMutableList().also { it.removeAt(i) }))
                            }) { Text(gestureKeyName(step) + "  ✕") }
                        }
                        var addStep by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { addStep = true }) {
                                Text(tr("Añadir paso", "Add step"))
                                Icon(Icons.Filled.ArrowDropDown, null)
                            }
                            DropdownMenu(addStep, { addStep = false }, modifier = Modifier.heightIn(max = 420.dp)) {
                                GESTURE_KEYS.forEach { kk ->
                                    DropdownMenuItem(
                                        text = { Text(if (Lang.en) kk.en else kk.es) },
                                        onClick = { addStep = false; write(shown.copy(combo = shown.combo + kk.kc)) }
                                    )
                                }
                            }
                        }
                    }
                    Hint(
                        tr("Se mandan en orden, con ~70 ms entre cada una (necesita Shizuku).",
                            "They're sent in order, ~70 ms apart (needs Shizuku).")
                    )
                }

                // La app solo aparece si algún gesto es «Abrir una app».
                if (shown.anyOpenApp) {
                    var pickApp by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("App", "App"), Modifier.width(120.dp), fontWeight = FontWeight.SemiBold)
                        Box {
                            OutlinedButton(onClick = { pickApp = true }) {
                                Text(shown.pkg.takeIf { it.isNotEmpty() }?.let { labels(it) } ?: tr("Elegir app…", "Choose app…"))
                                Icon(Icons.Filled.ArrowDropDown, null)
                            }
                            DropdownMenu(pickApp, { pickApp = false }, modifier = Modifier.heightIn(max = 420.dp)) {
                                apps.forEach { (p, l) ->
                                    DropdownMenuItem(text = { Text(l) }, onClick = { pickApp = false; write(shown.copy(pkg = p)) })
                                }
                            }
                        }
                    }
                }

                if (scope != null && appRule != null) OutlinedButton(onClick = { Gestures.clearApp(a, scope!!); rev++ }) {
                    Text(tr("Quitar lo propio de esta app", "Remove this app's settings"))
                }
                val others = scopeRules.keys.filter { it != key }
                if (others.isNotEmpty()) Hint(
                    tr("Otros botones con gestos aquí: " + others.joinToString { gestureKeyLabel(it) },
                        "Other buttons with gestures here: " + others.joinToString { gestureKeyLabel(it) })
                )
                // Un solo (i) con toda la explicación: antes eran tres textos seguidos y ocupaban media página.
                var info by remember { mutableStateOf(false) }
                Row(Modifier.clickable { info = !info }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Cómo funciona", "How it works"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(if (info) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (info) {
                    Hint(
                        tr("Para borrar un gesto: «Nada» en los tres. Un toque espera ~0,3 s (por si viene el doble); mantener entra a ~0,45 s.",
                            "To clear a gesture: “Nothing” on all three. A tap waits ~0.3 s (in case a double comes); long-press triggers at ~0.45 s.")
                    )
                    Hint(
                        tr("Cada gesto puede llevar su propia tecla del teclado (las envía G9 Control: el mando no manda teclas; tardan unas décimas y necesitan Shizuku).",
                            "Each gesture can have its own keyboard key (G9 Control sends them: the controller doesn't send keys; they take a few tenths of a second and need Shizuku).")
                    )
                    if (scope != null) Hint(
                        tr("Aquí, «Nada» en los tres hace que el botón funcione normal en esta app (llega al juego).",
                            "Here, “Nothing” on all three makes the button work normally in this app (it reaches the game).")
                    )
                    Hint(
                        tr("El cajón de apps y las páginas del launcher primero prueban el intent del launcher y, si no lo atiende, van al inicio y deslizan. «Adelantar / Retroceder video» usan el tamaño de «Tiempos» → «Deslizamiento».",
                            "The app drawer and launcher pages first try the launcher's intent and, if it doesn't answer, go home and swipe. “Seek forward / back” use the size from “Timings” → “Swipe”.")
                    )
                    if (launcherPkg != null) Hint(
                        tr("«Pantalla de inicio» en «Aplica a» sirve para lo que solo pasa en el launcher (cajón de apps, páginas).",
                            "“Home screen” in “Applies to” is for what only happens in the launcher (app drawer, pages).")
                    )
                }
                }

                // Qué tiene la app elegida (o «Todas las apps»), sin ir botón por botón: los botones y qué hace cada uno.
                val summary = gestureSummary(scopeRules, labels)
                if (summary.isNotEmpty()) {
                    Text(
                        tr("Botones mapeados: ", "Mapped buttons: ") + "\"$scopeLabel\"",
                        fontWeight = FontWeight.SemiBold
                    )
                    // Agrupado por gesto: «Un toque: Y → tecla A», así se lee de un vistazo (idea de Roberto).
                    val slots: List<Triple<String, (GestureRule) -> GestureAction, Int>> = listOf(
                        Triple(tr("Un toque", "One tap"), { r: GestureRule -> r.tap }, 0),
                        Triple(tr("Doble toque", "Double tap"), { r: GestureRule -> r.dbl }, 2),
                        Triple(tr("Mantener", "Long-press"), { r: GestureRule -> r.long }, 1),
                    )
                    slots.forEach { (title, slot, at) ->
                        val rows = scopeRules.entries.filter { it.value.any && slot(it.value) != GestureAction.NONE }
                        if (rows.isEmpty()) return@forEach
                        Text(
                            "$title:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp)
                        )
                        rows.forEach { (k, r) ->
                            Row(Modifier.padding(start = 14.dp)) {
                                Text(gestureKeyLabel(k) + " → " + gestureActionName(slot(r), r.kcOf(at), r.pkg, labels), fontSize = 13.sp)
                            }
                        }
                    }
                }
                // Tiempos de los gestos: antes solo se podían cambiar por dentro (g_tap_max, g_long, g_double).
                // Los sliders a la vez ocupaban mucho: se elige el tiempo con un botón y debajo sale solo el
                // slider del elegido (0 = un toque, 1 = doble toque, 2 = mantener, 3 = deslizamiento).
                var tapMs by remember(rev) { mutableIntStateOf(Gestures.tapMax(a)) }
                var longMs by remember(rev) { mutableIntStateOf(Gestures.longMs(a)) }
                var doubleMs by remember(rev) { mutableIntStateOf(Gestures.doubleMs(a)) }
                var swipePct by remember(rev) { mutableIntStateOf(Gestures.swipePct(a)) }
                var openTimer by remember { mutableStateOf<Int?>(null) }
                Text(tr("Tiempos", "Timings"), fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimerButton(tr("Un toque", "One tap"), "$tapMs ms", openTimer == 0, Modifier.weight(1f)) { openTimer = if (openTimer == 0) null else 0 }
                    TimerButton(tr("Doble toque", "Double tap"), "$doubleMs ms", openTimer == 1, Modifier.weight(1f)) { openTimer = if (openTimer == 1) null else 1 }
                    TimerButton(tr("Mantener", "Long"), "$longMs ms", openTimer == 2, Modifier.weight(1f)) { openTimer = if (openTimer == 2) null else 2 }
                    TimerButton(tr("Deslizamiento", "Swipe"), "$swipePct %", openTimer == 3, Modifier.weight(1f)) { openTimer = if (openTimer == 3) null else 3 }
                }
                when (openTimer) {
                    0 -> SliderRow(tr("Un toque: hasta", "One tap: up to"), tapMs, 100..500, "$tapMs ms") { tapMs = it; Gestures.setDurations(a, it, longMs, doubleMs) }
                    1 -> SliderRow(tr("Doble toque: dentro de", "Double tap: within"), doubleMs, 150..600, "$doubleMs ms") { doubleMs = it; Gestures.setDurations(a, tapMs, longMs, it) }
                    2 -> SliderRow(tr("Mantener: desde", "Long-press: from"), longMs, 250..1000, "$longMs ms") { longMs = it; Gestures.setDurations(a, tapMs, it, doubleMs) }
                    3 -> SliderRow(tr("Deslizamiento: avance de video", "Swipe: video seek"), swipePct, 2..30, "$swipePct %") { swipePct = it; Gestures.setSwipePct(a, it) }
                }
            }
        }
    )
}

/**
 * Fila de un gesto. El menú de acciones va en dos pasos (categoría → acción) porque con tantas acciones una
 * lista plana se volvía un chorizo. Si el gesto es «Tecla del teclado», sale debajo su propio selector de
 * tecla: cada gesto lleva la suya.
 */
@Composable
private fun GestureRow(
    label: String, cur: GestureAction, kc: Int,
    onPick: (GestureAction) -> Unit, onPickKey: (Int) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    var cat by remember { mutableStateOf(-1) }   // -1 = primera pantalla (lista de categorías)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontSize = 15.sp)
            Box {
                OutlinedButton(onClick = { cat = -1; open = true }) {
                    Text(if (Lang.en) cur.en else cur.es)
                    Icon(Icons.Filled.ArrowDropDown, null)
                }
                DropdownMenu(open, { open = false; cat = -1 }, modifier = Modifier.heightIn(max = 420.dp)) {
                    if (cat < 0) {
                        // «Nada» directo (para quitar el gesto de un toque) y, debajo, las categorías.
                        DropdownMenuItem(
                            text = { Text(if (Lang.en) GestureAction.NONE.en else GestureAction.NONE.es) },
                            onClick = { open = false; onPick(GestureAction.NONE) }
                        )
                        GESTURE_CATS.forEachIndexed { i, _ ->
                            if (i == 0) return@forEachIndexed   // «Nada» ya está arriba
                            // «Ratón» (7) NO se ofrece aquí: sus botones se asignan en la pestaña «Ratón» y
                            // valen en todas las apps a la vez (no tiene sentido «solo en esta app»).
                            if (i == 7) {
                                DropdownMenuItem(
                                    text = { Text(tr("Ratón: se asignan en la pestaña «Ratón»",
                                        "Mouse: assign them in the “Mouse” tab"), fontSize = 13.sp) },
                                    enabled = false,
                                    onClick = { }
                                )
                                return@forEachIndexed
                            }
                            DropdownMenuItem(
                                text = { Text(gestureCatName(i)) },
                                trailingIcon = { Icon(Icons.Filled.KeyboardArrowRight, null) },
                                onClick = { cat = i }
                            )
                        }
                    } else {
                        DropdownMenuItem(
                            text = { Text("‹ " + gestureCatName(cat)) },
                            onClick = { cat = -1 }
                        )
                        GestureAction.inCat(cat).forEach { act ->
                            DropdownMenuItem(
                                text = { Text(if (Lang.en) act.en else act.es) },
                                onClick = { open = false; cat = -1; onPick(act) }
                            )
                        }
                    }
                }
            }
        }
        if (cur == GestureAction.KEY) KeyPicker(kc, onPickKey)
    }
}

/** Un tiempo de gesto (Tocar / Mantener / Doble / Deslizamiento): al tocarlo sale su slider debajo. */
@Composable
private fun TimerButton(label: String, value: String, open: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 12.sp, maxLines = 1)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
    val padding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
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
            DropdownMenu(open, { open = false }, modifier = Modifier.heightIn(max = 420.dp)) {
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
