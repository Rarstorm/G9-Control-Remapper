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
    SCREENSHOT("Captura", "Screenshot"),
    /** Tecla de teclado inyectada con Shizuku: el mando no envía teclas, las manda la app. La tecla va en [GestureRule.kc]. */
    KEY("Tecla del teclado", "Keyboard key"),
    /** Abre «Ajustes rápidos» (el mismo panel de mantener el botón guía o L3 + R3). */
    PANEL("Ajustes rápidos", "Quick settings"),
    /** Abre una app elegida: el paquete va en [GestureRule.pkg]. */
    OPEN_APP("Abrir una app", "Open an app"),
    /** Abre el cajón de apps: un deslizamiento hacia arriba, como el del launcher. */
    APP_DRAWER("Abrir el cajón de apps", "Open the app drawer"),
    /** Página siguiente del launcher: un deslizamiento hacia la izquierda. */
    PAGE_NEXT("Página siguiente", "Next page"),
    /** Página anterior del launcher: un deslizamiento hacia la derecha. */
    PAGE_PREV("Página anterior", "Previous page");

    companion object { fun of(s: String?) = entries.firstOrNull { it.name == s } ?: NONE }
}

/** Una tecla de teclado asignable a un gesto. `group` es el encabezado con el que sale en el menú. */
data class GestureKey(val kc: Int, val es: String, val en: String, val group: Int)

/** Encabezados de los grupos de teclas (mismo índice que `GestureKey.group`). */
val GESTURE_KEY_GROUPS: List<Pair<String, String>> = listOf(
    "Atajos" to "Shortcuts",
    "Flechas" to "Arrows",
    "Letras" to "Letters",
    "Números" to "Numbers",
    "Función" to "Function",
)

/** Teclas de teclado que se pueden asignar a un gesto (códigos de Android). */
val GESTURE_KEYS: List<GestureKey> =
    listOf(
        GestureKey(KeyEvent.KEYCODE_SPACE, "Espacio", "Space", 0),
        GestureKey(KeyEvent.KEYCODE_ENTER, "Intro", "Enter", 0),
        GestureKey(KeyEvent.KEYCODE_ESCAPE, "Escape", "Escape", 0),
        GestureKey(KeyEvent.KEYCODE_TAB, "Tabulador", "Tab", 0),
        GestureKey(KeyEvent.KEYCODE_DEL, "Retroceso", "Backspace", 0),
        GestureKey(KeyEvent.KEYCODE_FORWARD_DEL, "Suprimir", "Delete", 0),
        GestureKey(KeyEvent.KEYCODE_SHIFT_LEFT, "Mayúsculas", "Shift", 0),
        GestureKey(KeyEvent.KEYCODE_CTRL_LEFT, "Control", "Ctrl", 0),
        GestureKey(KeyEvent.KEYCODE_ALT_LEFT, "Alt", "Alt", 0),
    ) +
    listOf(
        GestureKey(KeyEvent.KEYCODE_DPAD_UP, "Arriba", "Up", 1),
        GestureKey(KeyEvent.KEYCODE_DPAD_DOWN, "Abajo", "Down", 1),
        GestureKey(KeyEvent.KEYCODE_DPAD_LEFT, "Izquierda", "Left", 1),
        GestureKey(KeyEvent.KEYCODE_DPAD_RIGHT, "Derecha", "Right", 1),
    ) +
    ('A'..'Z').map { GestureKey(KeyEvent.KEYCODE_A + (it - 'A'), it.toString(), it.toString(), 2) } +
    (0..9).map { GestureKey(KeyEvent.KEYCODE_0 + it, it.toString(), it.toString(), 3) } +
    (1..12).map { GestureKey(KeyEvent.KEYCODE_F1 + it - 1, "F$it", "F$it", 4) }

/** Nombre de la tecla elegida (vacío si todavía no se eligió ninguna). */
fun gestureKeyName(kc: Int): String =
    GESTURE_KEYS.firstOrNull { it.kc == kc }?.let { if (Lang.en) it.en else it.es } ?: ""

/**
 * Nombre corto de una acción de gesto, para los resúmenes. [appLabel] traduce el paquete de
 * [GestureAction.OPEN_APP] al nombre de la app (null = todavía no se eligió ninguna).
 */
fun gestureActionName(
    a: GestureAction, kc: Int, pkg: String = "", appLabel: (String) -> String? = { null }
): String = when {
    a == GestureAction.KEY -> (if (Lang.en) "key " else "tecla ") + gestureKeyName(kc).ifEmpty { "?" }
    a == GestureAction.OPEN_APP -> appLabel(pkg) ?: tr("una app sin elegir", "an app not chosen")
    else -> if (Lang.en) a.en else a.es
}

/** Nombre del botón para los resúmenes: los del dibujo y los traseros (que usan un código interno). */
fun gestureKeyLabel(k: Int): String =
    KEYCODE_PAD[k]?.name ?: PADDLE_KEYCODE.entries.firstOrNull { it.value == k }?.key?.label ?: "?"

/**
 * Resumen de lo que tiene un conjunto de reglas (una app, o «Todas las apps»), para enseñarlo sin tener que
 * ir botón por botón: «LT → Atrás, tecla A · L4 → Panel de juego».
 */
fun gestureSummary(rules: Map<Int, GestureRule>, appLabel: (String) -> String? = { null }): String = rules.entries
    .filter { it.value.any }
    .joinToString(" · ") { (k, r) ->
        val parts = listOfNotNull(
            r.tap.takeIf { it != GestureAction.NONE }?.let { gestureActionName(it, r.kc, r.pkg, appLabel) },
            r.long.takeIf { it != GestureAction.NONE }?.let { gestureActionName(it, r.kc, r.pkg, appLabel) },
            r.dbl.takeIf { it != GestureAction.NONE }?.let { gestureActionName(it, r.kc, r.pkg, appLabel) },
        )
        gestureKeyLabel(k) + " → " + parts.joinToString(", ")
    }

/** Gestos de un botón del control: un toque, doble toque y mantener. */
data class GestureRule(
    val key: Int, val tap: GestureAction, val long: GestureAction, val dbl: GestureAction,
    /** Tecla de Android que se inyecta cuando el gesto es [GestureAction.KEY] (0 = sin elegir). */
    val kc: Int = 0,
    /** App que se abre cuando el gesto es [GestureAction.OPEN_APP] (paquete; vacío = sin elegir). */
    val pkg: String = "",
) {
    val any get() = tap != GestureAction.NONE || long != GestureAction.NONE || dbl != GestureAction.NONE
    val anyKey get() = tap == GestureAction.KEY || long == GestureAction.KEY || dbl == GestureAction.KEY
    val anyOpenApp get() = tap == GestureAction.OPEN_APP || long == GestureAction.OPEN_APP || dbl == GestureAction.OPEN_APP
}

/**
 * Botón del dibujo → keyCode de Android que llega al servicio. Home está también: sin gesto asignado el
 * botón de la guía sigue siendo Atrás / panel (lo lleva el servicio), pero con gesto manda el gesto.
 */
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
    PadKey.HOME to KeyEvent.KEYCODE_BUTTON_MODE,
    PadKey.UP to KeyEvent.KEYCODE_DPAD_UP,
    PadKey.DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
    PadKey.LEFT to KeyEvent.KEYCODE_DPAD_LEFT,
    PadKey.RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
)
val KEYCODE_PAD: Map<Int, PadKey> = PAD_KEYCODE.entries.associate { (k, v) -> v to k }

/**
 * Config de gestos, por app. Hay dos niveles: las reglas de «Todas las apps» (generales) y las
 * propias de cada app, que pisan a las generales. En las propias, un botón con los tres gestos en
 * «Nada» significa «déjalo pasar»: en esa app el botón funciona normal (llega al juego).
 */
object Gestures {
    private const val GLOBAL = "g_rules"
    private fun key(pkg: String) = "g_rules__$pkg"
    private fun sp(ctx: Context) = ctx.getSharedPreferences("g9", Context.MODE_PRIVATE)

    private fun parse(set: Set<String>?): Map<Int, GestureRule> = set.orEmpty().mapNotNull { line ->
        val p = line.split('|')
        // 4 campos = respaldo viejo (sin tecla); 5 = con la tecla; 6 = con la app de «Abrir una app».
        if (p.size < 4) null else p[0].toIntOrNull()?.let {
            it to GestureRule(
                it, GestureAction.of(p[1]), GestureAction.of(p[2]), GestureAction.of(p[3]),
                p.getOrNull(4)?.toIntOrNull() ?: 0,
                p.getOrNull(5).orEmpty()
            )
        }
    }.toMap()

    private fun enc(m: Collection<GestureRule>) =
        m.map { "${it.key}|${it.tap.name}|${it.long.name}|${it.dbl.name}|${it.kc}|${it.pkg}" }.toSet()

    /** Toque = hasta tapMax ms; mantener = a partir de longMs; doble = dos toques dentro de doubleMs. */
    fun tapMax(ctx: Context) = sp(ctx).getInt("g_tap_max", 220)
    fun longMs(ctx: Context) = sp(ctx).getInt("g_long", 450)
    fun doubleMs(ctx: Context) = sp(ctx).getInt("g_double", 300)

    /** Reglas de «Todas las apps». */
    fun rules(ctx: Context): Map<Int, GestureRule> = parse(sp(ctx).getStringSet(GLOBAL, emptySet()))

    /** Reglas propias de una app (pueden incluir «Nada» para dejar pasar un botón). */
    fun appRules(ctx: Context, pkg: String): Map<Int, GestureRule> = parse(sp(ctx).getStringSet(key(pkg), emptySet()))

    /** Apps con reglas propias. */
    fun apps(ctx: Context): Set<String> =
        sp(ctx).all.keys.filter { it.startsWith("g_rules__") }.map { it.removePrefix("g_rules__") }.toSet()

    /** Lo que vale para una app: lo general, pisado por lo propio de la app. */
    fun rulesFor(ctx: Context, pkg: String?): Map<Int, GestureRule> {
        val m = rules(ctx).toMutableMap()
        if (!pkg.isNullOrEmpty()) appRules(ctx, pkg).forEach { (k, v) -> m[k] = v }
        return m
    }

    fun rule(ctx: Context, k: Int) = rules(ctx)[k] ?: GestureRule(k, GestureAction.NONE, GestureAction.NONE, GestureAction.NONE)

    /** Guarda una regla general; «Nada» en los tres la borra. */
    fun setRule(ctx: Context, r: GestureRule) {
        val m = rules(ctx).toMutableMap()
        if (r.any) m[r.key] = r else m.remove(r.key)
        sp(ctx).edit().putStringSet(GLOBAL, enc(m.values)).apply()
    }

    /** Guarda la regla propia de una app; «Nada» pisa lo general (deja pasar el botón). */
    fun setAppRule(ctx: Context, pkg: String, r: GestureRule) {
        val m = appRules(ctx, pkg).toMutableMap()
        if (r.any) m[r.key] = r
        else if (rules(ctx).containsKey(r.key)) m[r.key] = r
        else m.remove(r.key)
        if (m.isEmpty()) sp(ctx).edit().remove(key(pkg)).apply()
        else sp(ctx).edit().putStringSet(key(pkg), enc(m.values)).apply()
    }

    /** Borra todo lo propio de una app (vuelve a las reglas generales). */
    fun clearApp(ctx: Context, pkg: String) = sp(ctx).edit().remove(key(pkg)).apply()

    fun setDurations(ctx: Context, tap: Int, long: Int, dbl: Int) =
        sp(ctx).edit().putInt("g_tap_max", tap).putInt("g_long", long).putInt("g_double", dbl).apply()
}

/**
 * Motor de gestos: para cada botón con gestos configurados convierte apretar y soltar en toque,
 * mantener o doble toque, y consume el evento (no llega a las apps). Un solo motor por servicio.
 * Las reglas se resuelven por app: las generales, pisadas por las propias de la app al frente.
 */
class GestureEngine(private val handler: Handler) {
    private var longFired = false
    private var curRule: GestureRule? = null
    private var curFire: ((GestureAction, Int, String) -> Unit)? = null
    private var pendingKey = 0
    private var pendingAt = 0L
    private var pendingRule: GestureRule? = null
    private var pendingFire: ((GestureAction, Int, String) -> Unit)? = null
    private var swallowUp = false

    private val longRunnable = Runnable {
        longFired = true
        val r = curRule
        if (r != null && r.long != GestureAction.NONE) curFire?.invoke(r.long, r.kc, r.pkg)
    }

    private val tapRunnable = Runnable {
        val r = pendingRule
        val f = pendingFire
        pendingKey = 0; pendingRule = null; pendingFire = null
        if (r != null && r.tap != GestureAction.NONE) f?.invoke(r.tap, r.kc, r.pkg)
    }

    /** @return true si el evento es nuestro (se consumió y no llega a las apps). */
    fun onKey(
        ctx: Context, pkg: String?, keyCode: Int, down: Boolean, repeatCount: Int,
        fire: (GestureAction, Int, String) -> Unit
    ): Boolean {
        val rules = Gestures.rulesFor(ctx, pkg)
        // Al apretar otro botón, el toque que esperaba su doble se resuelve ya.
        if (down && pendingKey != 0 && pendingKey != keyCode) {
            handler.removeCallbacks(tapRunnable)
            val r = pendingRule
            val f = pendingFire
            pendingKey = 0; pendingRule = null; pendingFire = null
            if (r != null && r.tap != GestureAction.NONE) f?.invoke(r.tap, r.kc, r.pkg)
        }
        val rule = rules[keyCode]
        if (rule == null || !rule.any) return false

        if (down) {
            if (repeatCount > 0) return true
            val now = SystemClock.uptimeMillis()
            // Segundo toque dentro de la ventana: doble toque.
            if (pendingKey == keyCode && now - pendingAt <= Gestures.doubleMs(ctx)) {
                handler.removeCallbacks(tapRunnable)
                pendingKey = 0; pendingRule = null; pendingFire = null
                if (rule.dbl != GestureAction.NONE) fire(rule.dbl, rule.kc, rule.pkg)
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
            fire(rule.tap, rule.kc, rule.pkg)
        }
        return true
    }

    /**
     * Una pulsación ya resuelta (un trasero reconocido por su firma). El mando manda una firma corta de
     * ~25 ms, así que no hay forma de saber cuánto la mantuviste: dispara el mismo camino que un botón
     * (un toque o, si la aprietas dos veces seguidas, el doble toque).
     */
    fun onPress(ctx: Context, pkg: String?, keyCode: Int, fire: (GestureAction, Int, String) -> Unit): Boolean {
        val down = onKey(ctx, pkg, keyCode, true, 0, fire)
        val up = onKey(ctx, pkg, keyCode, false, 0, fire)
        return down || up
    }
}
