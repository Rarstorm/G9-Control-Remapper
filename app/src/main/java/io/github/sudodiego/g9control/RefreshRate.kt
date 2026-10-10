package io.github.sudodiego.g9control

import android.content.Context
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.util.Log
import android.view.Display
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Frecuencia de la pantalla por juego: un juego a 60 FPS se ve igual a 60 Hz (y gasta menos), y RetroArch con
 * inserción de cuadros negros quiere 120. Se fija con los ajustes del sistema `peak_refresh_rate` y
 * `min_refresh_rate` iguales (probado: 60 → 60 Hz, 165 → 165 Hz). Son privados del sistema: una app no los puede
 * escribir (ni con WRITE_SECURE_SETTINGS), así que va con Shizuku. La elección se guarda por app; al pasar a una app
 * sin elección se vuelve a lo que había antes (guardado al aplicar la primera vez).
 */
object RefreshRate {
    private const val PEAK = "peak_refresh_rate"
    private const val MIN = "min_refresh_rate"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun sp(ctx: Context) = ctx.getSharedPreferences("g9", Context.MODE_PRIVATE)

    fun canWrite() = Shell.state == Shell.State.READY

    /** Frecuencias que admite la pantalla (30 sirve para juegos a 30 FPS). */
    fun supported(ctx: Context): List<Int> =
        display(ctx)?.supportedModes?.map { it.refreshRate.roundToInt() }?.filter { it >= 30 }?.distinct()?.sorted().orEmpty()

    private fun display(ctx: Context) = ctx.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)

    /** Hz de la pantalla ahora. */
    fun current(ctx: Context): Int? = display(ctx)?.refreshRate?.roundToInt()

    /** La elección para una app (null = la del sistema). */
    fun forApp(ctx: Context, pkg: String?): Int? = pkg?.let { sp(ctx).getInt("hz_$it", 0).takeIf { v -> v > 0 } }
    fun setForApp(ctx: Context, pkg: String, hz: Int?) =
        sp(ctx).edit().apply { if (hz == null) remove("hz_$pkg") else putInt("hz_$pkg", hz) }.apply()

    /**
     * Aplica la frecuencia de esa app (al cambiar de app o al elegirla en el panel). Sin elección, vuelve a lo
     * que había antes de la primera vez que G9 Control la cambió. [done] se llama al terminar (en otro hilo).
     */
    fun applyFor(ctx: Context, pkg: String?, done: () -> Unit = {}) {
        val want = forApp(ctx, pkg)
        val p = sp(ctx)
        if (want == null && !p.contains("hz_saved_peak")) return done()
        val cr = ctx.contentResolver
        scope.launch {
            if (!canWrite()) return@launch done()
            if (want != null) {
                // Lo del usuario se guarda una sola vez (mientras G9 Control tenga la frecuencia cambiada).
                if (!p.contains("hz_saved_peak")) p.edit()
                    .putFloat("hz_saved_peak", Settings.System.getFloat(cr, PEAK, 120f))
                    .putFloat("hz_saved_min", Settings.System.getFloat(cr, MIN, 30f)).commit()
                Shell.run(Shell.refreshRate(PEAK, want.toFloat()))
                Shell.run(Shell.refreshRate(MIN, want.toFloat()))
                Log.d(AutoModeService.TAG, "pantalla a $want Hz ($pkg)")
            } else {
                Shell.run(Shell.refreshRate(PEAK, p.getFloat("hz_saved_peak", 120f)))
                Shell.run(Shell.refreshRate(MIN, p.getFloat("hz_saved_min", 30f)))
                p.edit().remove("hz_saved_peak").remove("hz_saved_min").commit()
                Log.d(AutoModeService.TAG, "pantalla a su frecuencia de siempre")
            }
            done()
        }
    }

    /** Lo que tiene el sistema sin G9 Control (para mostrar "Sistema · 120 Hz"). */
    fun systemPeak(ctx: Context): Int {
        val p = sp(ctx)
        return (if (p.contains("hz_saved_peak")) p.getFloat("hz_saved_peak", 120f)
        else runCatching { Settings.System.getFloat(ctx.contentResolver, PEAK) }.getOrDefault(120f)).roundToInt()
    }
}
