package io.github.sudodiego.g9control

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pestaña «Ratón»: el puntero que aparece con L3 + R3 (como en Mantis Gamepad) y cómo se mueve.
 *
 * Funciona en cualquier sitio —el launcher, el cajón de apps o dentro de una app— porque el puntero lo dibuja la
 * app encima de todo y el clic se inyecta justo donde está (Android no tiene API para mover el ratón del
 * sistema). Los botones del ratón (clic, clic derecho, doble clic, arrastrar y rueda) se asignan AQUÍ, en la
 * última sección de esta pestaña, y valen en todas las apps a la vez: no importa en qué app o en qué parte de
 * la tableta estés, el botón que elijas mueve el ratón. Se guardan APARTE (no son gestos), así que no salen
 * listados en la pestaña «Gestos» ni se comportan como un gesto del botón.
 */
/** Colores del puntero (ARGB): blanco, negro, amarillo, verde, cian, rosa y naranja. */
private val CURSOR_COLORS = listOf(
    0xFFFFFFFF.toInt(), 0xFF101010.toInt(), 0xFFFFD400.toInt(),
    0xFF35D07F.toInt(), 0xFF38D9F5.toInt(), 0xFFFF5FA2.toInt(), 0xFFFF8A34.toInt()
)

@Composable
fun MousePage(s: AppState, a: MainActivity) {
    val ctx = LocalContext.current
    var chord by remember { mutableStateOf(MouseRules.chord(a)) }
    var roleL by remember { mutableStateOf(MouseRules.stickRole(a, true)) }
    var roleR by remember { mutableStateOf(MouseRules.stickRole(a, false)) }
    var dpi by remember { mutableStateOf(MouseRules.dpi(a)) }
    var smooth by remember { mutableStateOf(MouseRules.smooth(a)) }
    var curve by remember { mutableStateOf(MouseRules.curve(a)) }
    var dead by remember { mutableStateOf(MouseRules.dead(a)) }
    var wheelStep by remember { mutableStateOf(MouseRules.wheelStep(a)) }
    var scrollInv by remember { mutableStateOf(MouseRules.scrollInvert(a)) }
    var cursorSize by remember { mutableStateOf(MouseRules.cursorSize(a)) }
    var cursorColor by remember { mutableStateOf(MouseRules.cursorColor(a)) }
    var cursorStyle by remember { mutableStateOf(MouseRules.cursorStyle(a)) }
    val live = MouseRules.live   // estado en vivo: el servicio y la app comparten el mismo proceso
    val api = android.os.Build.VERSION.SDK_INT

    fun save(enControl: Boolean = false) = s.saved(enControl = enControl)

    Panel(
        tr("Puntero del ratón", "Mouse pointer"),
        tr("Con L3 + R3 aparece un puntero que funciona en cualquier sitio: el launcher, el cajón de apps y dentro de cualquier app.",
            "Press L3 + R3 and a pointer appears that works anywhere: the launcher, the app drawer and inside any app.")
    ) {
        if (api < 34) {
            Hint(
                tr("Este Android es más viejo que 14: para leer los ejes del mando hace falta Android 14 o posterior, así que el puntero no puede moverse.",
                    "This Android is older than 14: reading the controller's axes needs Android 14 or later, so the pointer can't move.")
            )
        }
        SwitchRow(
            tr("L3 + R3 lo enciende", "L3 + R3 turns it on"), chord,
            tr("La misma combinación lo apaga. En la pantalla de inicio, L3 y R3 no llegan a los iconos (encender el ratón ahí abría la app del primer icono). Si lo apagas aquí abajo, la combinación queda libre.",
                "The same combination turns it off. On the home screen, L3 and R3 never reach the icons (turning the pointer on there used to open the first icon's app). If you turn it off here below, the combination stops being used.")
        ) { chord = it; MouseRules.setChord(a, it); save() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { MouseRules.setOn(a, !live); save() },
                enabled = api >= 34
            ) { Text(if (live) tr("Apagar el puntero ahora", "Turn the pointer off now") else tr("Encender el puntero ahora", "Turn the pointer on now")) }
            Spacer(Modifier.width(12.dp))
            Text(
                if (live) tr("Encendido", "On") else tr("Apagado", "Off"),
                fontSize = 14.sp, fontWeight = FontWeight.Medium,
                color = if (live) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Hint(
            tr("Los clics salen donde esté el puntero: un toque es un clic, el clic derecho es un toque largo (menú) y el doble clic son dos toques. Así funciona en cualquier app; un juego que use ratón de verdad (con su propio puntero) no lo verá como ratón.",
                "Clicks go wherever the pointer is: a tap is a click, right click is a long press (menu) and double click is two taps. That works in any app; a game that uses a real mouse (with its own pointer) won't see it as a mouse.")
        )
    }

    Panel(tr("Movimiento", "Movement"), tr("Cómo responde el puntero al stick.", "How the pointer responds to the stick.")) {
        // Cada stick por separado. Los dos con el mismo papel también vale: con los dos en «Puntero» mueve el que
        // muevas, y con los dos en «Scroll» los dos desplazan la pantalla.
        val roles = listOf(
            MouseRules.STICK_NONE to tr("Nada", "Nothing"),
            MouseRules.STICK_POINTER to tr("Puntero", "Pointer"),
            MouseRules.STICK_SCROLL to tr("Scroll", "Scroll")
        )
        Text(tr("Stick izquierdo", "Left stick"), fontWeight = FontWeight.SemiBold)
        ChoiceChips(roles, roleL) { roleL = it; MouseRules.setStickRole(a, true, it); save() }
        Text(tr("Stick derecho", "Right stick"), fontWeight = FontWeight.SemiBold)
        ChoiceChips(roles, roleR) { roleR = it; MouseRules.setStickRole(a, false, it); save() }
        Hint(
            tr("«Scroll» usa ese stick para bajar y subir la pantalla (el sentido y el tamaño, como la rueda de abajo): más apartado, más rápido.",
                "“Scroll” uses that stick to move the screen up and down (direction and size like the wheel below): the further you push, the faster it goes.")
        )
        if (roleL != MouseRules.STICK_POINTER && roleR != MouseRules.STICK_POINTER) Hint(
            tr("Ningún stick mueve el puntero: los clics saldrán donde lo dejaras la última vez. Pon «Puntero» en uno.",
                "No stick moves the pointer: clicks will land wherever you left it. Set “Pointer” on one.")
        )
        // El DPI se guarda en pasos de 50; el slider va en esos pasos y el texto enseña el valor real.
        SliderRow(tr("Velocidad (DPI)", "Speed (DPI)"), dpi / 50, 4..80, "$dpi") {
            dpi = it * 50; MouseRules.setDpi(a, dpi); save()
        }
        SliderRow(tr("Suavidad", "Smoothness"), smooth, 0..90, "$smooth %") {
            smooth = it; MouseRules.setSmooth(a, it); save()
        }
        SliderRow(tr("Curva (aceleración)", "Curve (acceleration)"), curve, 0..100, "$curve %") {
            curve = it; MouseRules.setCurve(a, it); save()
        }
        SliderRow(tr("Zona muerta", "Dead zone"), dead, 0..40, "$dead %") {
            dead = it; MouseRules.setDead(a, it); save()
        }
        Hint(
            tr("Velocidad es lo que avanza el puntero con el stick a fondo (2.000 ≈ cómodo para tocar la pantalla; 4.000 ≈ ratón rápido). Suavidad filtra el movimiento: alto = más lento de reacción pero sin temblores. Curva 0 = proporcional; alto = fino cerca del centro y rápido al apartarlo. La zona muerta es el margen que no lo mueve (evita que se vaya solo).",
                "Speed is how far the pointer travels with the stick all the way (2,000 ≈ comfortable for tapping the screen; 4,000 ≈ fast mouse). Smoothness filters the movement: high = slower to react but no jitter. Curve 0 = proportional; high = fine near the centre and fast when pushed. Dead zone is the margin that doesn't move it (keeps it from drifting).")
        )
        Hint(
            tr("Mientras el puntero está encendido, ese stick lo usa la app y el juego no lo ve (Android no deja leer los ejes sin quedárselos). Al apagarlo vuelve al juego tal cual.",
                "While the pointer is on, the app uses that stick and the game doesn't see it (Android doesn't let an app read the axes without taking them). Turning it off gives it straight back to the game.")
        )
    }

    Panel(tr("Aspecto del puntero", "Pointer looks"), tr("Tamaño, color y forma.", "Size, colour and shape.")) {
        SliderRow(tr("Tamaño", "Size"), cursorSize, 10..48, "$cursorSize dp") {
            cursorSize = it; MouseRules.setCursorSize(a, it); save()
        }
        Text(tr("Forma", "Shape"), fontWeight = FontWeight.SemiBold)
        ChoiceChips(
            listOf(
                MouseRules.CURSOR_ARROW to tr("Flecha", "Arrow"),
                MouseRules.CURSOR_DOT to tr("Punto", "Dot"),
                MouseRules.CURSOR_CROSS to tr("Cruz", "Cross")
            ),
            cursorStyle
        ) { cursorStyle = it; MouseRules.setCursorStyle(a, it); save() }
        Text(tr("Color", "Colour"), fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CURSOR_COLORS.forEach { argb ->
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(
                            if (argb == cursorColor) 3.dp else 1.dp,
                            if (argb == cursorColor) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape
                        )
                        .clickable { cursorColor = argb; MouseRules.setCursorColor(a, argb); save() }
                )
            }
        }
        Hint(
            tr("El contorno del puntero se pone solo del color que contraste con el relleno, para que se vea sobre cualquier fondo.",
                "The pointer's outline picks a contrasting colour by itself, so it shows on any background.")
        )
    }

    Panel(tr("Rueda", "Wheel"), tr("Para listas, menús y páginas que se desplazan.", "For lists, menus and pages that scroll.")) {
        // La «rueda de verdad» por Shizuku se retiró: en la tableta no desplazaba (comando medido en 0 px) y
        // el tirón corto sí funciona en todas las apps.
        SliderRow(tr("Cuánto avanza cada paso", "How far each step goes"), wheelStep, 1..10, "$wheelStep") {
            wheelStep = it; MouseRules.setWheelStep(a, it); save()
        }
        // Cada quien siente la rueda al revés: quien la quiera al contrario, lo activa aquí.
        SwitchRow(
            tr("Reversar la dirección", "Reverse the direction"),
            scrollInv,
            tr("Si al desplazar te parece que va al revés.", "If scrolling feels backwards to you.")
        ) { scrollInv = it; MouseRules.setScrollInvert(a, it); save() }
        Hint(
            tr("El desplazamiento es un tirón corto en la posición del puntero (lo entienden todas las apps). Cuanto más apartes el stick, más largo el paso. Si te parece que va al revés, activa «Reversar la dirección».",
                "Scrolling is a short flick at the pointer's position (every app understands it). The further you push the stick, the longer the step. If it feels backwards, turn on \"Reverse the direction\".")
        )
    }

    // ── Botones del ratón ─────────────────────────────────────────────────────────────────────────────────
    // Antes vivían en la pestaña «Gestos» y se guardaban como reglas de gestos generales: por eso aparecían
    // listados en «Gestos» como si fueran gestos del botón. Ahora se guardan en su propio almacén
    // (MouseRules.buttons) y el servicio los lee aparte: da igual en qué app o en qué parte de la tableta estés,
    // y con el puntero encendido son lo ÚNICO que hace algo del mando.
    var rev by remember { mutableStateOf(0) }
    val botones = remember(rev) { MouseRules.buttons(a) }
    var eligiendo by remember { mutableStateOf<GestureAction?>(null) }

    fun asignar(act: GestureAction, kc: Int) {
        MouseRules.setButton(a, kc, act)
        s.saved(enControl = false)
        rev++
    }

    fun quitar(act: GestureAction) {
        MouseRules.clearButton(a, act)
        s.saved(enControl = false)
        rev++
    }

    Panel(
        tr("Botones del ratón", "Mouse buttons"),
        tr("Elige qué botón hace cada cosa del ratón. Vale en todas las apps y en cualquier parte de la tableta.",
            "Pick which button does what. Applies to every app and anywhere on the tablet.")
    ) {
        GestureAction.entries.filter { it.cat == 7 }.forEach { act ->
            val dueno = botones.entries.firstOrNull { it.value == act }?.key
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(if (Lang.en) act.en else act.es, Modifier.weight(1f))
                OutlinedButton(onClick = { eligiendo = act }) {
                    Text(dueno?.let { nombreBoton(it) } ?: tr("Sin asignar", "Not assigned"))
                }
                if (dueno != null) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { quitar(act) }) {
                        Text(tr("Quitar", "Clear"))
                    }
                }
            }
        }
        Hint(
            tr("Con el puntero encendido el mando es un ratón: lo único que hace algo son los botones de esta sección (y L3 + R3 para apagarlo). Con el puntero apagado estos botones siguen siendo los de siempre: hacen lo que tengan asignado en «Gestos» o lo que haga Android con ellos. Al elegir un botón se suelta de donde estuviera: una acción del ratón, un botón. «Arrastrar»: lo aprietas, mueves el puntero y al soltar arrastra desde donde empezó.",
                "While the pointer is on, the controller is a mouse: only the buttons in this section do anything (plus L3 + R3 to turn it off). With the pointer off these buttons stay their normal selves: whatever is assigned in “Gestures”, or whatever Android does with them. Picking a button releases it from wherever it was: one mouse action, one button. “Drag”: press it, move the pointer and release to drag from where it started.")
        )
        if (eligiendo != null) {
            val act = eligiendo!!
            DropdownMenu(
                expanded = true,
                onDismissRequest = { eligiendo = null },
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                Text(
                    tr("¿Qué botón dispara «${if (Lang.en) act.en else act.es}»?",
                        "Which button fires “${if (Lang.en) act.en else act.es}”?"),
                    Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
                PadKey.entries.forEach { pk ->
                    DropdownMenuItem(
                        text = { Text(pk.name) },
                        onClick = { asignar(act, PAD_KEYCODE.getValue(pk)); eligiendo = null }
                    )
                }
                PADDLE_KEYCODE.entries.forEach { (bk, kc) ->
                    DropdownMenuItem(
                        text = { Text(bk.label) },
                        onClick = { asignar(act, kc); eligiendo = null }
                    )
                }
            }
        }
    }
}

/** Nombre del botón (A, B, LB, L4…) desde su keyCode: es lo que se enseña en la pestaña «Ratón». */
private fun nombreBoton(kc: Int): String = KEYCODE_PAD[kc]?.name
    ?: PADDLE_KEYCODE.entries.firstOrNull { it.value == kc }?.key?.label
    ?: tr("Otro botón", "Another button")
