package io.github.sudodiego.g9control

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Seguimiento de Vista + Menú para la barra de progreso. Lo usan igual la guía (servicio) y la franja
 * de la app, para que se comporten lo mismo.
 *
 * Tras un cambio de modo el control no cuenta los botones que siguen apretados: para el siguiente
 * cambio hay que soltarlos y volver a mantenerlos. Android sí los recibe como apretados después de la
 * reconexión, así que hasta que se suelten la barra no avanza y se pide soltar ([release]).
 */
class HoldTracker {
    /** Desde cuándo (uptime ms) se mantienen los dos a la vez; null si no. */
    var holdSince by mutableStateOf<Long?>(null)
        private set
    /** Se mantienen desde antes del último cambio: hay que soltarlos y volver a apretarlos. */
    var release by mutableStateOf(false)
        private set

    private companion object {
        /** Tras reconectarse, Android entrega enseguida los botones que seguían apretados. */
        const val HELD_OVER_MS = 800L
        /** El control suelta los botones y tarda en reconectarse: lo mantenido hace menos que esto cuenta. */
        const val HELD_RECENT_MS = 3000L
    }

    private val pressed = mutableSetOf<Int>()
    private var needRelease = false
    private var changedAt = 0L
    /** Última vez que se mantenían los dos (uptime ms). */
    private var bothAt = 0L

    /** Vista (Select) o Menú (Start) apretado o soltado. [counting] = false no arranca la barra. */
    fun onKey(keyCode: Int, down: Boolean, counting: Boolean = true) {
        val now = android.os.SystemClock.uptimeMillis()
        // Lo que llega apretado justo al reconectarse son los botones que se seguían manteniendo. Un toque
        // nuevo más tarde es una pulsación de verdad: ya se habían soltado (aunque Android no lo avisara).
        if (down && pressed.isEmpty() && now - changedAt > HELD_OVER_MS) needRelease = false
        if (down) pressed += keyCode else pressed -= keyCode
        if (!down && pressed.isEmpty()) needRelease = false
        val both = pressed.size >= 2
        if (both || (!down && pressed.size == 1)) bothAt = now
        release = both && needRelease
        if (both && !needRelease && holdSince == null && counting) holdSince = now
        if (!both) holdSince = null
    }

    /**
     * El control cambió de modo (se reconectó): no avisa qué botones quedaron apretados. Solo hay que
     * soltarlos si se estaban manteniendo justo antes (el cambio fue con Vista + Menú); si cambió la app
     * por USB, la siguiente pulsación ya cuenta.
     */
    fun modeChanged() {
        val now = android.os.SystemClock.uptimeMillis()
        needRelease = pressed.size >= 2 || now - bothAt < HELD_RECENT_MS
        pressed.clear()
        holdSince = null
        release = false
        changedAt = now
    }

    /** Empieza de cero (aviso nuevo en pantalla). */
    fun reset() {
        pressed.clear()
        holdSince = null
        release = false
    }
}
