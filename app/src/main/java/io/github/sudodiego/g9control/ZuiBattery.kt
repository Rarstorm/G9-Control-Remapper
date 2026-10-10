package io.github.sudodiego.g9control

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log

/**
 * Bypass charging: el cargador alimenta la tablet y la batería no se carga (no se calienta jugando enchufado). Es lo
 * mismo que hace Game Assistant en la Y700 Gen 3: el servicio de batería de ZUI (`zuibattery`,
 * `setBypassChargingStatus`). Ese servicio solo acepta apps del sistema (o una app de pruebas de Lenovo y SystemUI):
 * G9 Control no puede, pero shell sí (com.android.shell es del sistema), así que va con Shizuku.
 */
object ZuiBattery {
    /** Lo último que se supo (al leerlo o cambiarlo): el panel arma sus ítems al instante. */
    @Volatile var bypassNow: Boolean? = null
        private set

    /** Lee el bypass (null sin Shizuku). La respuesta de `service call` es `Parcel(00000000 0000000X ...)`. */
    suspend fun readBypass(): Boolean? =
        Shell.run(Shell.BYPASS_GET)?.let { parse(it) }?.also { bypassNow = it }

    /**
     * El usuario lo cambió a mano: el bypass automático no lo toca hasta desconectar el cargador o cambiar de app
     * (si no, lo volvería a cambiar en el siguiente aviso de la batería).
     */
    @Volatile var manualOverride = false

    /** Activa o desactiva el bypass. True si el servicio lo aceptó. [manual]: lo pidió el usuario (no el automático). */
    suspend fun setBypass(on: Boolean, manual: Boolean = true): Boolean {
        val ok = Shell.run(if (on) Shell.BYPASS_ON else Shell.BYPASS_OFF)?.let { parse(it) } == true
        if (ok) { bypassNow = on; if (manual) manualOverride = true }
        Log.d(AutoModeService.TAG, "bypass ${if (on) "activado" else "desactivado"}: $ok")
        return ok
    }

    /**
     * Límite de carga de ZUI ("Maximum charge limit" en Ajustes → Batería → Charge optimization): el nivel (80 %) o
     * null si está apagado o no se sabe. Con el límite, al llegar ZUI deja de cargar y no vuelve hasta bajar de ~95 %
     * de ese nivel; Android dice "no carga" y la barra muestra un enchufe (no es el bypass).
     */
    suspend fun readChargeLimit(): Int? {
        if (Shell.run(Shell.LIMIT_GET)?.let { parse(it) } != true) return null
        return Shell.run(Shell.LIMIT_LEVEL)?.let { parseInt(it) }?.takeIf { it in 1..99 }
    }

    /** Niveles que se pueden elegir para el límite (80 es el de ZUI). */
    val LIMITS = listOf(70, 75, 80, 85, 90)

    /**
     * El nivel del límite elegido en G9 Control (null = no tocarlo). ZUI no parece guardarlo al reiniciar ni tras su
     * calibración: el servicio lo vuelve a poner (al conectar el cargador y cuando Shizuku está listo).
     */
    fun wantedLimit(ctx: Context): Int? =
        ctx.getSharedPreferences("g9", Context.MODE_PRIVATE).getInt("charge_limit", 0).takeIf { it in LIMITS }
    fun setWantedLimit(ctx: Context, level: Int) =
        ctx.getSharedPreferences("g9", Context.MODE_PRIVATE).edit().putInt("charge_limit", level).apply()

    /** Cambia el nivel del límite de ZUI (solo si está activado). True si quedó en [level]. */
    suspend fun setChargeLimit(level: Int): Boolean {
        if (level !in LIMITS) return false
        Shell.run(Shell.limitSet(level)) ?: return false
        val now = readChargeLimit()
        Log.d(AutoModeService.TAG, "límite de carga: $now %")
        return now == level
    }

    private fun parseInt(out: String): Int? =
        Regex("Parcel\\(\\s*[0-9a-f]{8} ([0-9a-f]{8})").find(out)?.groupValues?.get(1)?.toLong(16)?.toInt()

    private fun parse(out: String): Boolean? = parseInt(out)?.let { it != 0 }

    /** La tablet está enchufada (el bypass solo tiene sentido así). */
    fun plugged(ctx: Context): Boolean {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        return i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    }
}
