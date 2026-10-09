package io.github.sudodiego.g9control

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sticks y gatillos: cada lado del control en su columna. Un solo dibujo por stick o gatillo muestra a
 * la vez la posición en vivo y las zonas muertas (en gris), así no hace falta una curva aparte.
 */
@Composable
fun SticksPage(s: AppState, a: MainActivity) {
    TwoPane(
        first = {
            StickPanel(tr("Stick izquierdo", "Left stick"), s, a, true, Gcm.DZ_L_INNER, Gcm.DZ_L_OUTER, s.lx, s.ly)
            TriggerPanel("LT", s, Gcm.DZ_LT_FRONT, Gcm.DZ_LT_BACK, s.lt, s.quickL) { s.quickL = it }
        },
        second = {
            StickPanel(tr("Stick derecho", "Right stick"), s, a, false, Gcm.DZ_R_INNER, Gcm.DZ_R_OUTER, s.rx, s.ry)
            TriggerPanel("RT", s, Gcm.DZ_RT_FRONT, Gcm.DZ_RT_BACK, s.rt, s.quickR) { s.quickR = it }
        }
    )
}

@Composable
private fun StickPanel(title: String, s: AppState, a: MainActivity, left: Boolean, innerId: Int, outerId: Int, x: Float, y: Float) {
    val inner = s.deadZones[innerId] ?: 0
    val outer = s.deadZones[outerId] ?: 100
    val d = s.drift?.takeIf { it.left == left }
    val busy = s.drift?.measuring == true
    // El resultado de la medición va en la línea del título, para no cambiar el alto del panel.
    val sub = when {
        d == null -> null
        d.measuring -> tr("No toques el stick…", "Don't touch the stick…")
        d.maxPct == 0 -> tr("Sin desvío", "No drift")
        else -> tr("Desvío ${d.maxPct}% · sugerido ${d.suggest}%", "Drift ${d.maxPct}% · suggested ${d.suggest}%")
    }
    Panel(title, sub, trailing = {
        when {
            d?.measuring == true -> LinearProgressIndicator(progress = { d.progress }, Modifier.width(120.dp))
            d != null && d.maxPct > 0 && d.suggest != inner ->
                TextButton(onClick = { s.deadZones[innerId] = d.suggest; s.drift = null }) { Text(tr("Usar ${d.suggest}%", "Use ${d.suggest}%")) }
            else -> TextButton(onClick = { a.measureDrift(left) }, enabled = LocalEditable.current && !busy && !s.sticksDirty) {
                Text(tr("Medir desvío", "Measure drift"))
            }
        }
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            StickView(x, y, inner / 100f, outer / 100f, Modifier.size(150.dp))
            Column(Modifier.weight(1f)) {
                SliderRow(tr("Zona muerta interna", "Inner dead zone"), inner, 0..100, "$inner%") { s.deadZones[innerId] = it }
                SliderRow(tr("Zona muerta externa", "Outer dead zone"), outer, 0..100, "$outer%") { s.deadZones[outerId] = it }
            }
        }
    }
}

/**
 * Stick visto desde arriba: el centro gris es la zona muerta interna (no se envía nada) y el anillo
 * gris exterior es donde ya se llegó al máximo. El punto es lo que recibe el juego.
 */
@Composable
private fun StickView(x: Float, y: Float, inner: Float, outer: Float, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Canvas(modifier) {
        val r = size.minDimension / 2f - 4.dp.toPx()
        drawCircle(c.surfaceContainerHigh, r, center)
        if (outer < 1f) drawCircle(c.outline.copy(alpha = 0.18f), r, center)
        drawCircle(c.surfaceContainerHigh, r * outer.coerceIn(0f, 1f), center)
        if (inner > 0f) drawCircle(c.outline.copy(alpha = 0.30f), r * inner.coerceIn(0f, 1f), center)
        drawLine(c.outlineVariant, Offset(center.x - r, center.y), Offset(center.x + r, center.y), 1.5f)
        drawLine(c.outlineVariant, Offset(center.x, center.y - r), Offset(center.x, center.y + r), 1.5f)
        drawCircle(c.outline, r, center, style = Stroke(2f))
        val p = Offset(center.x + x.coerceIn(-1f, 1f) * r, center.y + y.coerceIn(-1f, 1f) * r)
        drawLine(c.primary.copy(alpha = 0.5f), center, p, 3f)
        drawCircle(Color.Black.copy(alpha = 0.3f), 9.dp.toPx(), p.copy(y = p.y + 2f))
        drawCircle(c.primary, 8.dp.toPx(), p)
        drawCircle(c.onPrimary, 8.dp.toPx(), p, style = Stroke(2.5f))
    }
}

@Composable
private fun TriggerPanel(
    name: String, s: AppState, startId: Int, endId: Int, value: Float, quick: Boolean,
    onQuick: (Boolean) -> Unit
) {
    val start = s.deadZones[startId] ?: 0
    val end = s.deadZones[endId] ?: 100
    Panel(tr("Gatillo $name", "$name trigger"), trailing = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Gatillo rápido", "Hair trigger"), fontSize = 14.sp)
            Spacer(Modifier.width(10.dp))
            androidx.compose.material3.Switch(quick, onQuick, enabled = LocalEditable.current)
        }
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TriggerBar(value, start / 100f, end / 100f, Modifier.weight(1f))
            Text("${(value * 100).toInt()}%", Modifier.width(52.dp), fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.weight(1f)) { SliderRow(tr("Inicio", "Start"), start, 0..100, "$start%") { s.deadZones[startId] = it } }
            Box(Modifier.weight(1f)) { SliderRow(tr("Fin", "End"), end, 0..100, "$end%") { s.deadZones[endId] = it } }
        }
    }
}

/** Señal del gatillo en vivo; en gris, el tramo inicial que no cuenta y el final que ya es el máximo. */
@Composable
private fun TriggerBar(value: Float, start: Float, end: Float, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Canvas(modifier.height(28.dp)) {
        val h = 18.dp.toPx()
        val top = center.y - h / 2
        val cr = CornerRadius(h / 2)
        drawRoundRect(c.surfaceContainerHigh, Offset(0f, top), Size(size.width, h), cr)
        val s0 = start.coerceIn(0f, 1f)
        val e0 = end.coerceIn(0f, 1f).coerceAtLeast(s0)
        if (s0 > 0f) drawRoundRect(c.outline.copy(alpha = 0.30f), Offset(0f, top), Size(size.width * s0, h), cr)
        if (e0 < 1f) drawRoundRect(c.outline.copy(alpha = 0.18f), Offset(size.width * e0, top), Size(size.width * (1f - e0), h), cr)
        val v = value.coerceIn(0f, 1f)
        if (v > 0f) drawRoundRect(c.primary, Offset(0f, top), Size(size.width * v, h), cr)
    }
}
