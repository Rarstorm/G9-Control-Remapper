package io.github.sudodiego.g9control

import android.content.Context
import android.os.Handler
import android.os.SystemClock
import android.view.KeyEvent

/** Qué hace un gesto de un botón. */
enum class GestureAction(val es: String, val en: String) {
    NONE("Nada", "Nothing"),
    BACK("Atrás", "Back"),
    HOME("Inicio", "Home"),
    RECENTS("Recientes", "Recents"),
    NOTIFICATIONS("Notificaciones", "Notifications"),
    PLAY_PAUSE("Reproducir / Pausa", "Play / Pause"),
    VOL_UP("Subir volumen", "Volume up"),
    VOL_DOWN("Bajar volumen", "Volume down"),
    SCREENSHOT("Captura", "Screenshot");

    companion object { fun of(s: String?) = entries.firstOrNull { it.name == s } ?: NONE }
}

/** Gestos de un botón del control: toque, mantener y doble toque. */
data class GestureRule(val key: Int, val tap: GestureAction, val long: GestureAction, val dbl: GestureAction) {
    val any get() = tap != GestureAction.NONE || long != GestureAction.NONE || dbl != GestureAction.NONE
}

/** Botón del dibujo → keyCode de Android que llega al servicio. Home no está: es el botón de la guía (Atrás / panel). */
val PAD_KEYCODE: Map<PadKey, Int> = mapOf(
    PadKey.A to KeyEvent.KEYCODE_BUTTON_A,
    PadKey.B to KeyEvent.KEYCODE_BUTTON_B,
    PadKey.X to KeyEvent.KEYCODE_BUTTON_X,
    PadKey.Y to KeyEvent.KEYCODE_BUTTON_Y,
    PadKey.LB to KeyEvent.KEYCODE_BUTTON_L1,
    PadKey.RB to KeyEvent.KEYCODE_BUTTON_R1,
    PadKey.LT to KeyEvent.KEYCODE_BUTTON_L2,
    PadKey.RT to KeyEvent.KEYCODE_BUTTON_R2,
    PadKey.L3 to KeyEvent.KEYCODE_BUTTON_THUMBL,
    PadKey.R3 to KeyEvent.KEYCODE_BUTTON_THUMBR,
    PadKey.VIEW to KeyEvent.KEYCODE_BUTTON_SELECT,
    PadKey.MENU to KeyEvent.KEYCODE_BUTTON_START,
    PadKey.UP to KeyEvent.KEYCODE_DPAD_UP,
    PadKey.DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
    PadKey.LEFT to KeyEvent.KEYCODE_DPAD_LEFT,
    PadKey.RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
)
val KEYCODE_PAD: Map<Int, PadKey> = PAD_KEYCODE.entries.associate { (k, v) -> v to k }

/** Guarda y lee la config de gestos, en las mismas preferencias ("g9") que el resto. */
object Gestures {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("g9", Context.MODE_PRIVATE)

    /** Toque = hasta tapMax ms; mantener = a partir de longMs; doble = dos toques dentro de doubleMs. */
    fun tapMax(ctx: Context) = sp(ctx).getInt("g_tap_max", 220)
    fun longMs(ctx: Context) = sp(ctx).getInt("g_long", 450)
    fun doubleMs(ctx: Context) = sp(ctx).getInt("g_double", 300)

    fun rules(ctx: Context): Map<Int, GestureRule> =
        sp(ctx).getStringSet("g_rules", emptySet()).orEmpty().mapNotNull { line ->
            val p = line.split('|')
            if (p.size != 4) null else p[0].toIntOrNull()?.let {
                it to GestureRule(it, GestureAction.of(p[1]), GestureAction.of(p[2]), GestureAction.of(p[3]))
            }
        }.toMap()

    fun rule(ctx: Context, key: Int) = rules(ctx)[key]
        ?: GestureRule(key, GestureAction.NONE, GestureAction.NONE, GestureAction.NONE)

    fun setRule(ctx: Context, r: GestureRule) {
        val m = rules(ctx).toMutableMap()
        if (r.any) m[r.key] = r else m.remove(r.key)
        sp(ctx).edit().putStringSet(
            "g_rules",
            m.values.map { "${it.key}|${it.tap.name}|${it.long.name}|${it.dbl.name}" }.toSet()
        ).apply()
    }

    fun setDurations(ctx: Context, tap: Int, long: Int, dbl: Int) =
        sp(ctx).edit().putInt("g_tap_max", tap).putInt("g_long", long).putInt("g_double", dbl).apply()
}

/**
 * Motor de gestos: para cada botón con gestos configurados convierte apretar y soltar en toque,
 * mantener o doble toque, y consume el evento (no llega a las apps). Un solo motor por servicio.
 */
class GestureEngine(private val handler: Handler) {
    private var longFired = false
    private var curRule: GestureRule? = null
    private var curFire: ((GestureAction) -> Unit)? = null
    private var pendingKey = 0
    private var pendingAt = 0L
    private var pendingRule: GestureRule? = null
    private var pendingFire: ((GestureAction) -> Unit)? = null
    private var swallowUp = false

    private val longRunnable = Runnable {
        longFired = true
        val r = curRule
        if (r != null && r.long != GestureAction.NONE) curFire?.invoke(r.long)
    }

    private val tapRunnable = Runnable {
        val r = pendingRule
        val f = pendingFire
        pendingKey = 0; pendingRule = null; pendingFire = null
        if (r != null && r.tap != GestureAction.NONE) f?.invoke(r.tap)
    }

    /** @return true si el evento es nuestro (se consumió y no llega a las apps). */
    fun onKey(ctx: Context, keyCode: Int, down: Boolean, repeatCount: Int, fire: (GestureAction) -> Unit): Boolean {
        // Al apretar otro botón, el toque que esperaba su doble se resuelve ya.
        if (down && pendingKey != 0 && pendingKey != keyCode) {
            handler.removeCallbacks(tapRunnable)
            val r = pendingRule
            val f = pendingFire
            pendingKey = 0; pendingRule = null; pendingFire = null
            if (r != null && r.tap != GestureAction.NONE) f?.invoke(r.tap)
        }
        val rule = Gestures.rules(ctx)[keyCode]
        if (rule == null || !rule.any) return false

        if (down) {
            if (repeatCount > 0) return true
            val now = SystemClock.uptimeMillis()
            // Segundo toque dentro de la ventana: doble toque.
            if (pendingKey == keyCode && now - pendingAt <= Gestures.doubleMs(ctx)) {
                handler.removeCallbacks(tapRunnable)
                pendingKey = 0; pendingRule = null; pendingFire = null
                if (rule.dbl != GestureAction.NONE) fire(rule.dbl)
                swallowUp = true
                return true
            }
            longFired = false; curRule = rule; curFire = fire; swallowUp = false
            if (rule.long != GestureAction.NONE) handler.postDelayed(longRunnable, Gestures.longMs(ctx).toLong())
            return true
        }

        // Soltar.
        if (swallowUp) { swallowUp = false; return true }
        handler.removeCallbacks(longRunnable)
        if (longFired) { longFired = false; return true }
        // Toque. Con doble configurado, se espera la ventana para ver si viene el segundo.
        if (rule.dbl != GestureAction.NONE) {
            pendingKey = keyCode; pendingAt = SystemClock.uptimeMillis()
            pendingRule = rule; pendingFire = fire
            handler.postDelayed(tapRunnable, Gestures.doubleMs(ctx).toLong())
        } else if (rule.tap != GestureAction.NONE) {
            fire(rule.tap)
        }
        return true
    }
}
