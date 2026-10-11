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
    var sel by remember { mutableStateOf(PadKey.B) }
    val key = PAD_KEYCODE[sel] ?: KeyEvent.KEYCODE_BUTTON_B

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

    val withRules = remember(rev) { Gestures.apps(a) }
    val globRule = remember(rev, key) { Gestures.rules(a)[key] }
    val appRule = remember(rev, key, scope) { scope?.let { Gestures.appRules(a, it)[key] } }
    val shown = appRule ?: globRule ?: GestureRule(key, GestureAction.NONE, GestureAction.NONE, GestureAction.NONE)
    val inherited = scope != null && appRule == null
    val scopeLabel = scope?.let { p -> apps.firstOrNull { it.first == p }?.second ?: p } ?: tr("Todas las apps", "All apps")

    fun write(r: GestureRule) {
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
                            DropdownMenuItem(text = { Text(tr("Todas las apps", "All apps")) }, onClick = { pick = false; scope = null })
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
                if (scope == null) tr("Gestos", "Gestures") else tr("Gestos en esta app", "Gestures in this app"),
                tr("Mientras un botón tenga gestos, ese botón no llega a la app: lo usa G9 Control.", "While a button has gestures, that button doesn't reach the app: G9 Control uses it.")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Botón", "Button"), Modifier.width(120.dp), fontWeight = FontWeight.SemiBold)
                    Text(sel.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (inherited) {
                        Spacer(Modifier.width(10.dp))
                        Text(tr("(hereda de Todas)", "(inherits from All)"), fontSize = 12.sp)
                    }
                }
                GestureRow(tr("Tocar", "Tap"), shown.tap) { write(shown.copy(tap = it)) }
                GestureRow(tr("Mantener", "Long-press"), shown.long) { write(shown.copy(long = it)) }
                GestureRow(tr("Doble toque", "Double-tap"), shown.dbl) { write(shown.copy(dbl = it)) }

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
