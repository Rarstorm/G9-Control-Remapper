package io.github.sudodiego.g9control

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Color que muestra la app para una luz (HSL del protocolo). */
fun lightColor(l: Light): Color =
    Color.hsl(l.hue.toFloat().coerceIn(0f, 359.9f), l.sat / 100f, l.lum / 100f)

val RAINBOW = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { Color.hsl(it.coerceAtMost(359.9f), 1f, 0.5f) }

private fun ringEffects() = listOf(1 to tr("Fijo", "Static"), 2 to tr("Respiración", "Breathing"), 4 to tr("Arcoíris", "Rainbow"), 255 to tr("Apagado", "Off"))

private data class Preset(val name: String, val hue: Int, val sat: Int, val lum: Int)

private val PRESETS = listOf(
    Preset("Rojo", 0, 100, 50), Preset("Naranja", 25, 100, 50), Preset("Amarillo", 52, 100, 50),
    Preset("Verde", 125, 100, 45), Preset("Cian", 188, 100, 50), Preset("Azul", 220, 100, 52),
    Preset("Violeta", 275, 100, 58), Preset("Rosa", 325, 100, 62), Preset("Blanco", 0, 0, 100),
)

@Composable
fun LightsPage(s: AppState, a: MainActivity) {
    DrawingPane(
        drawing = {
            // "Tiras iguales": tocar cualquiera de las dos tiras las edita juntas.
            var linked by remember { mutableStateOf(s.lightTarget == LightTarget.BOTH) }
            // Al pasar a "ambas" desde otro lado (por ejemplo, al elegir arcoíris), el interruptor lo refleja.
            LaunchedEffect(s.lightTarget) { if (s.lightTarget == LightTarget.BOTH) linked = true }
            // Sin título arriba: el dibujo queda a la misma altura que en Inicio.
            Panel {
                ControllerPreview(s, Modifier.fillMaxWidth(), selected = s.lightTarget, labels = true) { t ->
                    val target = if (linked && (t == LightTarget.LEFT || t == LightTarget.RIGHT)) LightTarget.BOTH else t
                    // Al pasar a "ambas", la tira derecha copia a la izquierda.
                    if (target == LightTarget.BOTH && s.lightTarget != LightTarget.BOTH) { s.right = s.left.copy(id = 2); s.lightsTouched = true }
                    s.lightTarget = target
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Toca una luz para cambiarla", "Tap a light to change it"), Modifier.weight(1f), fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(tr("Tiras iguales", "Same on both strips"), fontSize = 14.sp)
                    Spacer(Modifier.width(10.dp))
                    Switch(linked, { on ->
                        linked = on
                        if (on && s.lightTarget != LightTarget.HOME) {
                            s.right = s.left.copy(id = 2); s.lightTarget = LightTarget.BOTH; s.lightsTouched = true
                        }
                        if (!on && s.lightTarget == LightTarget.BOTH) s.lightTarget = LightTarget.LEFT
                    })
                }
            }
        },
        side = {
            if (s.lightTarget == LightTarget.HOME) HomeEditor(s) else RingEditor(s)
        }
    )
}

@Composable
private fun RingEditor(s: AppState) {
    val l = if (s.lightTarget == LightTarget.RIGHT) s.right else s.left
    fun edit(f: (Light) -> Light) {
        s.lightsTouched = true
        when (s.lightTarget) {
            LightTarget.LEFT -> s.left = f(s.left).copy(id = 1)
            LightTarget.RIGHT -> s.right = f(s.right).copy(id = 2)
            else -> { val n = f(s.left); s.left = n.copy(id = 1); s.right = n.copy(id = 2) }
        }
    }
    Panel(tr("Efecto", "Effect")) {
        ChoiceChips(ringEffects(), l.effect) { e ->
            // El arcoíris solo corre si las dos tiras lo tienen (confirmado en el control): poner o quitar el
            // arcoíris cambia las dos tiras juntas.
            val rainbow = e == 4 || s.left.effect == 4 || s.right.effect == 4
            if (rainbow && s.lightTarget != LightTarget.BOTH) {
                val n = l.copy(effect = e)
                s.left = n.copy(id = 1); s.right = n.copy(id = 2); s.lightTarget = LightTarget.BOTH; s.lightsTouched = true
            } else edit { it.copy(effect = e) }
        }
        if (l.effect == 4) Hint(tr("El arcoíris va en las dos tiras: el control no lo permite en una sola.", "Rainbow applies to both strips: the controller doesn't allow it on just one."))
        if (l.effect in listOf(2, 4)) {
            // El control nunca detiene el movimiento: en 0 solo va lo más lento posible.
            SliderRow(tr("Velocidad", "Speed"), l.speed, 0..255, if (l.speed == 0) tr("Mínima · 0", "Slowest · 0") else "${(l.speed * 100 / 255)}% · ${l.speed}") { v -> edit { it.copy(speed = v) } }
        }
        if (l.effect == 4) {
            SliderRow(tr("Brillo", "Brightness"), l.brightness, 0..255, "${(l.brightness * 100 / 255)}% · ${l.brightness}") { v -> edit { it.copy(brightness = v) } }
        }
    }
    when (l.effect) {
        1, 2 -> Panel(tr("Color", "Color")) { ColorEditor(l) { n -> edit { n } } }
        4 -> Panel { Hint(tr("El arcoíris usa todos los colores.", "Rainbow cycles through all colors.")) }
        else -> {}
    }
}

@Composable
private fun HomeEditor(s: AppState) {
    Panel(tr("Botón Home", "Home button")) {
        SwitchRow(tr("Luz encendida", "Light on"), !s.homeOff, tr("Solo admite luz fija.", "Static light only.")) { s.homeOff = !it; s.lightsTouched = true }
    }
    if (!s.homeOff) Panel(tr("Color", "Color")) { ColorEditor(s.home) { s.home = it.copy(id = 0, effect = 1); s.lightsTouched = true } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorEditor(l: Light, onChange: (Light) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PRESETS.forEach { p ->
            val selected = l.hue == p.hue && l.sat == p.sat && l.lum == p.lum
            val c = Color.hsl(p.hue.toFloat(), p.sat / 100f, p.lum / 100f)
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(c)
                    .border(
                        if (selected) 3.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        CircleShape
                    )
                    .clickable(enabled = LocalEditable.current) { onChange(l.copy(hue = p.hue, sat = p.sat, lum = p.lum)) }
            )
        }
    }
    val hue = l.hue.toFloat().coerceIn(0f, 359.9f)
    GradientSlider(
        tr("Tono", "Hue"), "${l.hue}°", l.hue / 360f, Brush.horizontalGradient(RAINBOW), Color.hsl(hue, 1f, 0.5f)
    ) { onChange(l.copy(hue = (it * 360).roundToInt())) }
    GradientSlider(
        tr("Saturación", "Saturation"), "${l.sat}%", l.sat / 100f,
        Brush.horizontalGradient(listOf(Color.hsl(hue, 0f, l.lum / 100f), Color.hsl(hue, 1f, l.lum / 100f))),
        lightColor(l)
    ) { onChange(l.copy(sat = (it * 100).roundToInt())) }
    GradientSlider(
        tr("Luminosidad", "Lightness"), "${l.lum}%", l.lum / 100f,
        Brush.horizontalGradient(listOf(Color.Black, Color.hsl(hue, l.sat / 100f, 0.5f), Color.White)),
        lightColor(l)
    ) { onChange(l.copy(lum = (it * 100).roundToInt())) }
}

