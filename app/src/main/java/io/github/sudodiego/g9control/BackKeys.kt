package io.github.sudodiego.g9control

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Botones traseros (comando 31, GcmKeyUtil en la app oficial). Paquete sin byte de largo:
 * `1F acción origen destino extra chk`. Acción 0 leer, 1 escribir, 2 restaurar.
 * Origen: L4 1, R4 2, L5 3, R5 4. Destino: código de botón de KeyCodeUtil menos 256 (A = B4…);
 * 0 = sin asignar (se vuelve a eso con la acción 2; escribir 0 deja A). Home también sirve aunque
 * la app oficial no lo ofrece. Las teclas de teclado se guardan pero no envían nada. Confirmado en el G9.
 */
enum class BackKey(val id: Int, val label: String) { L4(1, "L4"), R4(2, "R4"), L5(3, "L5"), R5(4, "R5") }

/** Botones que se pueden asignar a un trasero (los de la lista de la app oficial para el G9). */
val BACK_TARGETS: List<Pair<Int, String>> get() = listOf(
    0 to tr("Sin asignar", "Unassigned"),
    0xB4 to "A", 0xB5 to "B", 0xB7 to "X", 0xB8 to "Y",
    0xBA to "LB", 0xBB to "RB", 0xBE to "LT", 0xBF to "RT",
    0xC0 to "L3", 0xC1 to "R3", 0xBC to tr("Vista", "View"), 0xBD to tr("Menú", "Menu"), 0xC2 to "Home",
    0xB0 to tr("Cruceta ↑", "D-pad ↑"), 0xB1 to tr("Cruceta ↓", "D-pad ↓"), 0xB2 to tr("Cruceta ←", "D-pad ←"), 0xB3 to tr("Cruceta →", "D-pad →"),
) + stickTargets(tr("Stick izq.", "Left stick"), 0xEF) + stickTargets(tr("Stick der.", "Right stick"), 0xF7)

/** Las 8 direcciones de un stick, en el orden del firmware: ←, ↖, ↑, ↗, →, ↘, ↓, ↙. */
private fun stickTargets(name: String, first: Int) =
    listOf("←", "↖", "↑", "↗", "→", "↘", "↓", "↙").mapIndexed { i, d -> (first + i) to "$name $d" }

fun backTargetName(b: Int) = if (b == BACK_MACRO) "Macro"
    else BACK_TARGETS.firstOrNull { it.first == b }?.second ?: tr("Otro (0x%02X)", "Other (0x%02X)").format(b)

/** Botón del dibujo → byte que entiende el control (para grabar macros). */
val MACRO_KEY_BYTE = mapOf(
    PadKey.A to 0xB4, PadKey.B to 0xB5, PadKey.X to 0xB7, PadKey.Y to 0xB8,
    PadKey.LB to 0xBA, PadKey.RB to 0xBB, PadKey.LT to 0xBE, PadKey.RT to 0xBF,
    PadKey.L3 to 0xC0, PadKey.R3 to 0xC1, PadKey.VIEW to 0xBC, PadKey.MENU to 0xBD, PadKey.HOME to 0xC2,
    PadKey.UP to 0xB0, PadKey.DOWN to 0xB1, PadKey.LEFT to 0xB2, PadKey.RIGHT to 0xB3,
)

/**
 * Página "Traseros": a la izquierda el dibujo grande del control, donde se toca el botón que copia el
 * trasero elegido; a la derecha los cuatro traseros. Con una macro abierta, la página es el editor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackPage(s: AppState, a: MainActivity) {
    if (s.editingMacro != null) { MacroEditor(s, a); return }
    // Editable con el control conectado en modo Android, aunque no se haya podido leer qué tiene cada
    // trasero: cada cambio se escribe y se comprueba releyendo, y si falla lo dice. Antes la página
    // quedaba deshabilitada y en silencio cuando la lectura fallaba (appliedBackKeys == null).
    val editable = s.connected
    /** Ya se leyó lo que tiene cada trasero. Sin lectura, la página avisa y no marca ningún chip. */
    val known = s.appliedBackKeys != null
    var sel by rememberSaveable { mutableStateOf(BackKey.L4) }
    val cur = if (known) s.backKeys[sel.id] ?: 0 else 0
    val padOf = MACRO_KEY_BYTE.entries.associate { (k, v) -> v to k }
    // Lee el valor actual (no "cur"): el dibujo puede llamar a una versión guardada de esta función.
    // Sin lectura previa no hay valor con el que comparar, así que se escribe siempre.
    fun pick(byte: Int) { if (!known || byte != (s.backKeys[sel.id] ?: 0)) a.setBackKey(sel, byte) }
    val c = MaterialTheme.colorScheme

    val drawing: @Composable ColumnScope.() -> Unit = {
        Panel {
            val tags = BackKey.entries.mapNotNull { k -> padOf[s.backKeys[k.id] ?: 0]?.let { it to k.label } }
                .groupBy({ it.first }, { it.second })
            ControllerKeyPicker(
                lit = setOfNotNull(padOf[cur]), tags = tags, enabled = editable, modifier = Modifier.fillMaxWidth(), lights = s
            ) { k -> MACRO_KEY_BYTE[k]?.let(::pick) }
            Text(tr("${sel.label}: toca el botón que copia", "${sel.label}: tap the button it copies"), fontWeight = FontWeight.SemiBold)
            // Gatillos y bumpers arriba, sobre el dibujo.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TargetChip("LT", known && cur == 0xBE, editable, Modifier.weight(1f)) { pick(0xBE) }
                TargetChip("LB", known && cur == 0xBA, editable, Modifier.weight(1f)) { pick(0xBA) }
                TargetChip("RB", known && cur == 0xBB, editable, Modifier.weight(1f)) { pick(0xBB) }
                TargetChip("RT", known && cur == 0xBF, editable, Modifier.weight(1f)) { pick(0xBF) }
            }
            // Todos los botones, en filas que llenan el ancho: antes cada fila dejaba el hueco del final
            // (y entre LB y RB, que iban con un separador en medio).
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TargetChip("A", known && cur == 0xB4, editable, Modifier.weight(1f)) { pick(0xB4) }
                TargetChip("B", known && cur == 0xB5, editable, Modifier.weight(1f)) { pick(0xB5) }
                TargetChip("X", known && cur == 0xB7, editable, Modifier.weight(1f)) { pick(0xB7) }
                TargetChip("Y", known && cur == 0xB8, editable, Modifier.weight(1f)) { pick(0xB8) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TargetChip("L3", known && cur == 0xC0, editable, Modifier.weight(1f)) { pick(0xC0) }
                TargetChip("R3", known && cur == 0xC1, editable, Modifier.weight(1f)) { pick(0xC1) }
                TargetChip(tr("Vista", "View"), known && cur == 0xBC, editable, Modifier.weight(1f)) { pick(0xBC) }
                TargetChip(tr("Menú", "Menu"), known && cur == 0xBD, editable, Modifier.weight(1f)) { pick(0xBD) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TargetChip("Home", known && cur == 0xC2, editable, Modifier.weight(1f)) { pick(0xC2) }
                TargetChip(tr("Cruceta ↑", "D-pad ↑"), known && cur == 0xB0, editable, Modifier.weight(1f)) { pick(0xB0) }
                TargetChip(tr("Cruceta ↓", "D-pad ↓"), known && cur == 0xB1, editable, Modifier.weight(1f)) { pick(0xB1) }
                TargetChip(tr("Cruceta ←", "D-pad ←"), known && cur == 0xB2, editable, Modifier.weight(1f)) { pick(0xB2) }
                TargetChip(tr("Cruceta →", "D-pad →"), known && cur == 0xB3, editable, Modifier.weight(1f)) { pick(0xB3) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TargetChip(tr("Sin asignar", "Unassigned"), known && cur == 0, editable, Modifier.weight(1f)) { pick(0) }
                TargetChip(if (cur == BACK_MACRO) tr("Editar macro", "Edit macro") else "Macro", known && cur == BACK_MACRO, editable, Modifier.weight(1f)) { a.openMacroEditor(sel) }
                StickDirChip(tr("Stick izq.", "Left stick"), 0xEF, cur, editable, ::pick, Modifier.weight(1f))
                StickDirChip(tr("Stick der.", "Right stick"), 0xF7, cur, editable, ::pick, Modifier.weight(1f))
            }
        }
    }
    val keys: @Composable ColumnScope.() -> Unit = {
        Panel(tr("Traseros", "Back buttons")) {
            // Los cuatro traseros, con lo que tiene cada uno. L4/R4 arriba, L5/R5 abajo, como en el control.
            BackKey.entries.forEach { k ->
                val on = k == sel
                val v = if (known) s.backKeys[k.id] ?: 0 else 0
                Surface(
                    onClick = { sel = k }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    color = if (on) c.primaryContainer else c.surfaceContainerHigh,
                    border = if (on) androidx.compose.foundation.BorderStroke(1.5.dp, c.primary) else null
                ) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(k.label, Modifier.width(56.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (known) backTargetName(v) else tr("Sin leer", "Not read"), Modifier.weight(1f), fontSize = 16.sp,
                            color = if (on) c.onPrimaryContainer else c.onSurfaceVariant
                        )
                        if (v == BACK_MACRO) Icon(Icons.Outlined.Edit, null, tint = c.onSurfaceVariant)
                    }
                }
            }
            // Aprender del mando: sin tocar la pantalla, aprietas el botón en el control y queda como destino
            // del trasero elegido. Como el trasero manda el mismo evento que el botón que copia, lo que se
            // aprende es el botón físico que aprietas (apretar el trasero apuntaría al que ya copia).
            if (s.learnBack == sel) {
                Text(
                    tr("Aprieta ahora en el mando el botón que quieres que copie ${sel.label}…",
                        "Now press on the controller the button you want ${sel.label} to copy…"),
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = { s.learnBack = null }) { Text(tr("Cancelar", "Cancel")) }
            } else {
                OutlinedButton(onClick = { s.learnBack = sel }, enabled = editable) {
                    Text(tr("Aprender del mando", "Learn from controller"))
                }
            }
            // Borrar todos: deja los cuatro traseros sin asignar (ni botón, ni macro, ni lo que se les grabó).
            OutlinedButton(onClick = { a.clearAllBackKeys() }, enabled = editable && !s.busy) {
                Text(tr("Borrar todos", "Clear all"))
            }
            // Si un trasero deja de responder, se prepara desde aquí, en llano (nada de «firmas»).
            OutlinedButton(onClick = { a.preparePaddles(loud = true) }, enabled = s.connected && !s.busy) {
                Text(tr("Preparar los traseros", "Prepare the paddles"))
            }
            Hint(
                tr("Solo si al apretar un trasero no pasa nada: la app los prepara sola cuando les asignas algo en «Gestos». Reasignar un trasero aquí o «Borrar todos» los desprepara.",
                    "Only if pressing a paddle does nothing: the app prepares them on its own when you assign something in “Gestures”. Reassigning a paddle here or “Clear all” un-prepares them.")
            )
            // Recordatorio cuando de verdad importa: hay gestos de trasero y alguno se quedó sin firma.
            val sinFirma = BackKey.entries.filter { s.backKeys[it.id] != null && s.backKeys[it.id] != BACK_MACRO }
            val hayReglasTrasero = remember(s.backKeys) { Gestures.rules(a).keys.any { it in PADDLE_KEYCODE.values } }
            if (s.connected && sinFirma.isNotEmpty() && hayReglasTrasero) Hint(
                tr("Recordatorio: " + sinFirma.joinToString { it.label } + " no está(n) preparado(s): sus gestos de «Gestos» no se van a usar hasta prepararlos.",
                    "Reminder: " + sinFirma.joinToString { it.label } + " isn't/aren't prepared: their “Gestures” won't be used until you prepare them.")
            )
            when {
                !s.connected ->
                    Hint(tr("Conecta el control en modo Android para cambiarlos.", "Connect the controller in Android mode to change them."))
                // Lectura fallida (por ejemplo, el permiso USB que se pierde al reinstalar): antes la página
                // quedaba deshabilitada sin decir nada.
                !known -> {
                    Hint(
                        tr("No pude leer qué tiene cada trasero (suele ser el permiso USB o una lectura sin respuesta). Puedes asignarlos igual: cada cambio se escribe y se comprueba al momento.",
                            "Couldn't read what each paddle has right now (usually the USB permission or a read with no answer). You can still assign them: every change is written and checked right away."),
                        Icons.Outlined.WarningAmber
                    )
                    TextButton(onClick = { a.readAll() }, enabled = !s.busy) { Text(tr("Reintentar lectura", "Retry read")) }
                }
            }
        }
    }
    DrawingPane(drawing = drawing, side = keys)
}

@Composable
private fun TargetChip(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, enabled = enabled,
        label = { Text(label, fontSize = 13.sp, maxLines = 1) },
        modifier = modifier.padding(horizontal = 2.dp),
        // Fondo sólido: LT/LB/RB/RT van encima del dibujo.
        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

/** Dirección de un stick: chip con menú de las 8 direcciones. */
@Composable
internal fun StickDirChip(
    name: String, first: Int, cur: Int, enabled: Boolean, onPick: (Int) -> Unit, modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val dirs = listOf("←", "↖", "↑", "↗", "→", "↘", "↓", "↙")
    val i = cur - first
    Box(modifier) {
        FilterChip(
            modifier = Modifier.fillMaxWidth(),
            selected = i in dirs.indices, onClick = { open = true }, enabled = enabled,
            label = { Text(if (i in dirs.indices) "$name ${dirs[i]}" else name) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) }
        )
        DropdownMenu(open, { open = false }) {
            // Las 8 direcciones en una cuadrícula de 3×3, como se ven en el stick.
            val grid = listOf(listOf(1, 2, 3), listOf(0, -1, 4), listOf(7, 6, 5))
            grid.forEach { row ->
                Row {
                    row.forEach { d ->
                        if (d < 0) Spacer(Modifier.size(52.dp))
                        else TextButton(onClick = { open = false; onPick(first + d) }, modifier = Modifier.size(52.dp)) {
                            Text(dirs[d], fontSize = 20.sp, fontWeight = if (d == i) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}

/** Opciones del control que no son de un botón en particular. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlOptionsPanel(s: AppState, a: MainActivity) {
    CompositionLocalProvider(LocalEditable provides s.connected) {
        Panel(tr("Opciones", "Options")) {
            s.abSwapped?.let { sw -> SwitchRow(tr("Intercambiar A↔B y X↔Y", "Swap A↔B and X↔Y"), sw) { a.setAbSwap(it) } }
            SwitchRow(tr("Bloquear diagonales de la cruceta", "Lock D-pad diagonals"), s.dpadLock) { s.dpadLock = it }
            SwitchRow(tr("Intercambiar stick izquierdo y cruceta", "Swap left stick and D-pad"), s.swapDpad) { s.swapDpad = it }
        }
    }
}

