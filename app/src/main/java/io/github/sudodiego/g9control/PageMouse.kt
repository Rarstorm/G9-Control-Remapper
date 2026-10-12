package io.github.sudodiego.g9control

import androidx.compose.foundation.layout.*
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
 * sistema). Los botones del ratón (clic, clic derecho, doble clic, arrastrar y rueda) se asignan en «Gestos» →
 * categoría «Ratón», botón por botón y por app.
 */
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
    var wheelMode by remember { mutableStateOf(MouseRules.wheelMode(a)) }
    var wheelStep by remember { mutableStateOf(MouseRules.wheelStep(a)) }
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
            tr("La misma combinación lo apaga. Si lo apagas aquí abajo, la combinación queda libre.",
                "The same combination turns it off. If you turn it off here below, the combination stops being used.")
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

    Panel(tr("Rueda", "Wheel"), tr("Para listas, menús y páginas que se desplazan.", "For lists, menus and pages that scroll.")) {
        Text(tr("Cómo se manda", "How it's sent"), fontWeight = FontWeight.SemiBold)
        ChoiceChips(
            listOf(
                0 to tr("Deslizamiento (siempre funciona)", "Swipe (always works)"),
                1 to tr("Rueda de verdad (Shizuku)", "Real wheel (Shizuku)")
            ),
            wheelMode
        ) { wheelMode = it; MouseRules.setWheelMode(a, it); save() }
        SliderRow(tr("Cuánto avanza cada paso", "How far each step goes"), wheelStep, 1..10, "$wheelStep") {
            wheelStep = it; MouseRules.setWheelStep(a, it); save()
        }
        Hint(
            tr("El deslizamiento es un tirón corto en la posición del puntero (lo entienden todas las apps). La rueda de verdad manda el aviso de rueda del sistema por Shizuku: se siente como un ratón, pero necesita Shizuku iniciado.",
                "The swipe is a short flick at the pointer's position (every app understands it). The real wheel sends the system's wheel event through Shizuku: it feels like a mouse, but needs Shizuku running.")
        )
    }

    Panel(tr("Botones del ratón", "Mouse buttons"), tr("Se asignan como cualquier otro gesto.", "They're assigned like any other gesture.")) {
        Hint(
            tr("En «Gestos» hay una categoría «Ratón»: clic (toque), clic derecho (menú), doble clic, arrastrar, rueda arriba, rueda abajo y «Puntero: encender/apagar». Se ponen botón por botón y, si quieres, solo en una app («Aplica a»).",
                "In “Gestures” there's a “Mouse” category: click (tap), right click (menu), double click, drag, scroll up, scroll down and “Pointer: on/off”. You set them button by button and, if you want, only in one app (“Applies to”).")
        )
        Hint(
            tr("«Arrastrar» conviene ponerlo en «Mantener»: aprietas, mueves el puntero y al soltar se arrastra de donde estaba al principio hasta donde está ahora (como mover un icono).",
                "“Drag” is best set on “Long-press”: you press, move the pointer and on release it drags from where it started to where it is now (like moving an icon).")
        )
    }
}
