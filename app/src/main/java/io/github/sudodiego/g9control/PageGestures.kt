package io.github.sudodiego.g9control

import android.view.KeyEvent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Página "Gestos": un botón del control (por ejemplo, el que manda un botón trasero) hace algo
 * distinto según se toque, se mantenga o se toque dos veces.
 */
@Composable
fun GesturesPage(s: AppState, a: MainActivity) {
    var sel by remember { mutableStateOf(PadKey.B) }
    val key = PAD_KEYCODE[sel] ?: KeyEvent.KEYCODE_BUTTON_B
    var rule by remember(sel) { mutableStateOf(Gestures.rule(a, key)) }

    val padOf = MACRO_KEY_BYTE.entries.associate { (k, v) -> v to k }
    val backTags = BackKey.entries
        .mapNotNull { k -> padOf[s.backKeys[k.id] ?: 0]?.let { it to k.label } }
        .groupBy({ it.first }, { it.second })

    TwoPane(
        first = {
            Panel {
                ControllerKeyPicker(
                    lit = setOf(sel), tags = backTags, enabled = true,
                    modifier = Modifier.fillMaxWidth(), lights = s
                ) { k ->
                    if (k in PAD_KEYCODE) sel = k
                    else s.status = tr("El botón de la guía no se puede usar para gestos.", "The guide button can't be used for gestures.")
                }
                Text(tr("Botón con gestos", "Gesture button"), fontWeight = FontWeight.SemiBold)
                Hint(
                    tr("Toca un botón del dibujo. Para los traseros: primero asígnalos en «Traseros» al botón que elijas aquí.",
                        "Tap a button on the drawing. For the back paddles: first assign them in “Paddles” to the button you pick here.")
                )
            }
        },
        second = {
            Panel(
                tr("Gestos", "Gestures"),
                tr("Mientras un botón tenga gestos, ese botón no llega al juego: lo usa la app.", "While a button has gestures, that button doesn't reach the game: the app uses it.")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Botón", "Button"), Modifier.width(120.dp), fontWeight = FontWeight.SemiBold)
                    Text(sel.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                GestureRow(tr("Tocar", "Tap"), rule.tap) { rule = rule.copy(tap = it); Gestures.setRule(a, rule) }
                GestureRow(tr("Mantener", "Long-press"), rule.long) { rule = rule.copy(long = it); Gestures.setRule(a, rule) }
                GestureRow(tr("Doble toque", "Double-tap"), rule.dbl) { rule = rule.copy(dbl = it); Gestures.setRule(a, rule) }

                val others = Gestures.rules(a).keys.filter { it != key }
                if (others.isNotEmpty()) Hint(
                    tr("Otros botones con gestos: " + others.joinToString { KEYCODE_PAD[it]?.name ?: "?" },
                        "Other buttons with gestures: " + others.joinToString { KEYCODE_PAD[it]?.name ?: "?" })
                )
                Hint(
                    tr("Para borrar los gestos de un botón: pon «Nada» en los tres. Tocar espera ~0,3 s (por si viene el doble); mantener entra a ~0,45 s.",
                        "To clear a button's gestures: set “Nothing” on all three. A tap waits ~0.3 s (in case a double comes); long-press triggers at ~0.45 s.")
                )
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
