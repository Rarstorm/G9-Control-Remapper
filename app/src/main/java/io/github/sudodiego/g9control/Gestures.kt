package io.github.sudodiego.g9control

import android.content.Context
import android.os.Handler
import android.os.SystemClock
import android.view.KeyEvent

/**
 * Categorías con las que sale la lista de acciones en «Gestos». El menú se abre en dos pasos (categoría →
 * acción): con tantas acciones, una lista plana se vuelve un chorizo. `GestureAction.cat` es el índice aquí.
 */
val GESTURE_CATS: List<Pair<String, String>> = listOf(
    "Nada" to "Nothing",
    "Sistema" to "System",
    "Media" to "Media",
    "Pantalla" to "Screen",
    "Apps" to "Apps",
    "Launcher" to "Launcher",
    "Entradas" to "Input",
    "Ratón" to "Mouse",
)

/** Qué hace un gesto de un botón. `cat` = categoría del menú (índice de [GESTURE_CATS]). */
enum class GestureAction(val es: String, val en: String, val cat: Int) {
    NONE("Nada", "Nothing", 0),
    // Sistema
    BACK("Atrás", "Back", 1),
    HOME("Inicio", "Home", 1),
    RECENTS("Recientes", "Recents", 1),
    NOTIFICATIONS("Notificaciones", "Notifications", 1),
    SCREENSHOT("Captura", "Screenshot", 1),
    /** Abre «Ajustes rápidos» (el mismo panel de mantener el botón guía). */
    PANEL("Ajustes rápidos", "Quick settings", 1),
    // Media
    PLAY_PAUSE("Reproducir / Pausa", "Play / Pause", 2),
    MEDIA_NEXT("Siguiente pista", "Next track", 2),
    MEDIA_PREV("Pista anterior", "Previous track", 2),
    /** Adelanta el video: un deslizamiento del tamaño elegido en «Tiempos» → «Deslizamiento». */
    MEDIA_FF("Adelantar video", "Seek forward", 2),
    /** Retrocede el video: un deslizamiento del tamaño elegido en «Tiempos» → «Deslizamiento». */
    MEDIA_REW("Retroceder video", "Seek back", 2),
    MEDIA_STOP("Detener", "Stop", 2),
    MUTE("Silenciar", "Mute", 2),
    VOL_UP("Subir volumen", "Volume up", 2),
    VOL_DOWN("Bajar volumen", "Volume down", 2),
    // Pantalla
    BRIGHT_UP("Subir brillo", "Brightness up", 3),
    BRIGHT_DOWN("Bajar brillo", "Brightness down", 3),
    // Apps
    /** Abre una app elegida: el paquete va en [GestureRule.pkg]. */
    OPEN_APP("Abrir una app", "Open an app", 4),
    // Launcher
    /** Abre el cajón de apps: intent del launcher y, si no lo atiende, Inicio + deslizamiento. */
    APP_DRAWER("Abrir el cajón de apps", "Open the app drawer", 5),
    /** Página siguiente del launcher: un deslizamiento hacia la izquierda. */
    PAGE_NEXT("Página siguiente", "Next page", 5),
    /** Página anterior del launcher: un deslizamiento hacia la derecha. */
    PAGE_PREV("Página anterior", "Previous page", 5),
    // Entradas
    /**
     * Tecla de teclado inyectada con Shizuku: el mando no envía teclas, las manda la app. Cada gesto tiene la
     * suya: [GestureRule.kcTap], [GestureRule.kcDbl] y [GestureRule.kcLong].
     */
    KEY("Tecla del teclado", "Keyboard key", 6),
    /** Varias teclas seguidas (una macro corta): los códigos van en [GestureRule.combo]. */
    COMBO("Secuencia de teclas", "Key sequence", 6),
    // Ratón: lo que hace el puntero de «L3 + R3» (ver Mouse.kt).
    /** Un toque donde está el puntero: el clic normal. */
    MOUSE_CLICK("Clic (toque)", "Click (tap)", 7),
    /** Toque largo: en las apps abre el menú contextual (el clic derecho sin root). */
    MOUSE_RIGHT("Clic derecho (menú)", "Right click (menu)", 7),
    MOUSE_DOUBLE("Doble clic", "Double click", 7),
    /** Mantener apretado: al soltar arrastra de donde estaba el puntero a donde esté ahora. */
    MOUSE_DRAG("Arrastrar", "Drag", 7),
    WHEEL_UP("Rueda arriba", "Scroll up", 7),
    WHEEL_DOWN("Rueda abajo", "Scroll down", 7),
    /** Enciende o apaga el puntero (lo mismo que L3 + R3). */
    MOUSE_TOGGLE("Puntero: encender/apagar", "Pointer: on/off", 7);

    companion object {
        fun of(s: String?) = entries.firstOrNull { it.name == s } ?: NONE

        /** Acciones de una categoría, en el orden en el que están escritas arriba. */
        fun inCat(cat: Int) = entries.filter { it.cat == cat }
    }
}

/** Nombre de una categoría de acciones (para el menú de dos pasos). */
fun gestureCatName(cat: Int): String =
    GESTURE_CATS.getOrNull(cat)?.let { if (Lang.en) it.second else it.first } ?: ""

/** Una tecla de teclado asignable a un gesto. `group` es el encabezado con el que sale en el menú. */
data class GestureKey(val kc: Int, val es: String, val en: String, val group: Int)

/** Encabezados de los grupos de teclas (mismo índice que `GestureKey.group`). */
val GESTURE_KEY_GROUPS: List<Pair<String, String>> = listOf(
    "Atajos" to "Shortcuts",
    "Flechas" to "Arrows",
    "Letras" to "Letters",
    "Números" to "Numbers",
    "Función" to "Function",
    "Mando" to "Controller",
    "Sistema" to "System",
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
    (1..12).map { GestureKey(KeyEvent.KEYCODE_F1 + it - 1, "F$it", "F$it", 4) } +
    // Botones del mando: una secuencia de combate (p. ej. Atrás, Atrás, Arriba, Abajo, A) los necesita.
    listOf(
        GestureKey(KeyEvent.KEYCODE_BUTTON_A, "A", "A", 5),
        GestureKey(KeyEvent.KEYCODE_BUTTON_B, "B", "B", 5),
        GestureKey(KeyEvent.KEYCODE_BUTTON_X, "X", "X", 5),
        GestureKey(KeyEvent.KEYCODE_BUTTON_Y, "Y", "Y", 5),
        GestureKey(KeyEvent.KEYCODE_BUTTON_L1, "LB", "LB", 5),
        GestureKey(KeyEvent.KEYCODE_BUTTON_R1, "RB", "RB", 5),
        GestureKey(KeyEvent.KEYCODE_BUTTON_L2, "LT", "LT", 5),
        GestureKey(KeyEvent.KEYCODE_BUTTON_R2, "RT", "RT", 5),
        GestureKey(KeyEvent.KEYCODE_BUTTON_THUMBL, "L3", "L3", 5),
        GestureKey(KeyEvent.KEYCODE_BUTTON_THUMBR, "R3", "R3", 5),
        GestureKey(KeyEvent.KEYCODE_BUTTON_SELECT, "Vista", "View", 5),
        GestureKey(KeyEvent.KEYCODE_BUTTON_START, "Menú", "Menu", 5),
    ) +
    // Teclas del sistema (las que se usan para «atrás» en una secuencia).
    listOf(
        GestureKey(KeyEvent.KEYCODE_BACK, "Atrás", "Back", 6),
        GestureKey(KeyEvent.KEYCODE_HOME, "Inicio del sistema", "System Home", 6),
        GestureKey(KeyEvent.KEYCODE_APP_SWITCH, "Recientes", "Recents", 6),
    )

/** Nombre de la tecla elegida (vacío si todavía no se eligió ninguna). */
fun gestureKeyName(kc: Int): String =
    GESTURE_KEYS.firstOrNull { it.kc == kc }?.let { if (Lang.en) it.en else it.es } ?: ""

/**
 * Nombre corto de una acción de gesto, para los resúmenes. [appLabel] traduce el paquete de
 * [GestureAction.OPEN_APP] al nombre de la app (null = todavía no se eligió ninguna).
 */
fun gestureActionName(
    a: GestureAction, kc: Int, pkg: String = "", appLabel: (String) -> String? = { null },
    combo: List<Int> = emptyList()
): String = when {
    a == GestureAction.KEY -> (if (Lang.en) "key " else "tecla ") + gestureKeyName(kc).ifEmpty { "?" }
    a == GestureAction.OPEN_APP -> appLabel(pkg) ?: tr("una app sin elegir", "an app not chosen")
    a == GestureAction.COMBO -> (if (Lang.en) "sequence: " else "secuencia: ") +
        combo.take(6).joinToString(" ", transform = { gestureKeyName(it) }) + if (combo.size > 6) "…" else ""
    else -> if (Lang.en) a.en else a.es
}

/** Nombre del botón para los resúmenes: los del dibujo y los traseros (que usan un código interno). */
fun gestureKeyLabel(k: Int): String =
    KEYCODE_PAD[k]?.name ?: PADDLE_KEYCODE.entries.firstOrNull { it.value == k }?.key?.label ?: "?"

/**
 * Resumen de lo que tiene un conjunto de reglas (una app, o «Todas las apps»), para enseñarlo sin tener que
 * ir botón por botón: «LT → Atrás, tecla A · L4 → Ajustes rápidos».
 */
fun gestureSummary(rules: Map<Int, GestureRule>, appLabel: (String) -> String? = { null }): String = rules.entries
    .filter { it.value.any }
    .joinToString(" · ") { (k, r) ->
        val parts = listOfNotNull(
            r.tap.takeIf { it != GestureAction.NONE }?.let { gestureActionName(it, r.kcTap, r.pkg, appLabel, r.combo) },
            r.long.takeIf { it != GestureAction.NONE }?.let { gestureActionName(it, r.kcLong, r.pkg, appLabel, r.combo) },
            r.dbl.takeIf { it != GestureAction.NONE }?.let { gestureActionName(it, r.kcDbl, r.pkg, appLabel, r.combo) },
        )
        gestureKeyLabel(k) + " → " + parts.joinToString(", ")
    }

/**
 * Gestos de un botón del control: un toque, doble toque y mantener. Cada gesto lleva SU propia tecla de
 * teclado (antes había una sola para todo el botón: con «Tecla del teclado» al tocar no se podía poner otra
 * al mantener).
 */
data class GestureRule(
    val key: Int, val tap: GestureAction, val long: GestureAction, val dbl: GestureAction,
    /** Tecla que se inyecta cuando el gesto es [GestureAction.KEY]: un toque (0 = sin elegir). */
    val kcTap: Int = 0,
    /** Tecla del gesto «mantener» (0 = sin elegir). */
    val kcLong: Int = 0,
    /** Tecla del gesto «doble toque» (0 = sin elegir). */
    val kcDbl: Int = 0,
    /** App que se abre cuando el gesto es [GestureAction.OPEN_APP] (paquete; vacío = sin elegir). */
    val pkg: String = "",
    /** Teclas de [GestureAction.COMBO], en orden (códigos de Android). */
    val combo: List<Int> = emptyList(),
) {
    val any get() = tap != GestureAction.NONE || long != GestureAction.NONE || dbl != GestureAction.NONE
    val anyKey get() = tap == GestureAction.KEY || long == GestureAction.KEY || dbl == GestureAction.KEY
    val anyOpenApp get() = tap == GestureAction.OPEN_APP || long == GestureAction.OPEN_APP || dbl == GestureAction.OPEN_APP

    /** Tecla de un gesto por su lugar en el motor: 0 = un toque, 1 = mantener, 2 = doble toque. */
    fun kcOf(slot: Int) = when (slot) { 0 -> kcTap; 1 -> kcLong; else -> kcDbl }
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

    /**
     * Formato de la regla (9 campos): `key|tap|long|dbl|kcTap|kcLong|kcDbl|pkg|combo`.
     * Se siguen leyendo los respaldos viejos (4 a 7 campos): traían UNA tecla para todo el botón y se le da
     * al gesto que era «Tecla del teclado».
     */
    private fun parse(set: Set<String>?): Map<Int, GestureRule> = set.orEmpty().mapNotNull { line ->
        val p = line.split('|')
        if (p.size < 4) null else p[0].toIntOrNull()?.let { k ->
            val tap = GestureAction.of(p[1])
            val long = GestureAction.of(p[2])
            val dbl = GestureAction.of(p[3])
            if (p.size >= 9) GestureRule(
                k, tap, long, dbl,
                p[4].toIntOrNull() ?: 0, p[5].toIntOrNull() ?: 0, p[6].toIntOrNull() ?: 0,
                p[7], p[8].split(',').mapNotNull { it.trim().toIntOrNull() }
            ) else {
                // Respaldo viejo: una sola tecla, que va al gesto que era «Tecla del teclado».
                val old = p.getOrNull(4)?.toIntOrNull() ?: 0
                GestureRule(
                    k, tap, long, dbl,
                    if (tap == GestureAction.KEY) old else 0,
                    if (long == GestureAction.KEY) old else 0,
                    if (dbl == GestureAction.KEY) old else 0,
                    p.getOrNull(5).orEmpty(),
                    p.getOrNull(6).orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }
                )
            }
        }
    }.associateBy { it.key }

    private fun enc(m: Collection<GestureRule>) =
        m.map { "${it.key}|${it.tap.name}|${it.long.name}|${it.dbl.name}|${it.kcTap}|${it.kcLong}|${it.kcDbl}|${it.pkg}|${it.combo.joinToString(",")}" }.toSet()

    /** Toque = hasta tapMax ms; mantener = a partir de longMs; doble = dos toques dentro de doubleMs. */
    fun tapMax(ctx: Context) = sp(ctx).getInt("g_tap_max", 220)
    fun longMs(ctx: Context) = sp(ctx).getInt("g_long", 450)
    fun doubleMs(ctx: Context) = sp(ctx).getInt("g_double", 300)

    /** Tamaño del deslizamiento de «Adelantar / Retroceder video», en % del ancho de la pantalla. */
    fun swipePct(ctx: Context) = sp(ctx).getInt("g_swipe_pct", 8)
    fun setSwipePct(ctx: Context, v: Int) = sp(ctx).edit().putInt("g_swipe_pct", v).apply()

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
class GestureEngine(
    private val handler: Handler,
    /**
     * Un botón se apretó y el gesto que le tocaba es «Nada»: hay que devolverlo a la app tal cual, o el botón
     * desaparece (con «Doble toque» asignado y «Un toque» en Nada, el toque normal no llegaba al juego).
     */
    private val onPass: (Int) -> Unit = {},
) {
    private var longFired = false
    private var curRule: GestureRule? = null
    private var curFire: ((GestureAction, Int, String, List<Int>) -> Unit)? = null
    private var pendingKey = 0
    private var pendingAt = 0L
    private var pendingRule: GestureRule? = null
    private var pendingFire: ((GestureAction, Int, String, List<Int>) -> Unit)? = null
    private var swallowUp = false

    private val longRunnable = Runnable {
        longFired = true
        val r = curRule
        if (r == null) return@Runnable
        if (r.long != GestureAction.NONE) curFire?.invoke(r.long, r.kcLong, r.pkg, r.combo) else onPass(r.key)
    }

    private val tapRunnable = Runnable {
        val r = pendingRule
        val f = pendingFire
        val k = pendingKey
        pendingKey = 0; pendingRule = null; pendingFire = null
        if (r == null) return@Runnable
        if (r.tap != GestureAction.NONE) f?.invoke(r.tap, r.kcTap, r.pkg, r.combo) else onPass(k)
    }

    private var lKey = 0
    private var lAt = 0L
    private var lPressAt = 0L
    private var lSwallowUp = false

    /**
     * Escucha sin interceptar (modo Key Mapper). El botón llega entero a la app; aquí solo se mira si hay dos
     * toques seguidos (se captura el segundo) o si se mantuvo lo suficiente para disparar «Mantener».
     * @return true solo en la pulsación que sí es nuestra (el segundo toque de un doble).
     */
    private fun listen(
        ctx: Context, rule: GestureRule, keyCode: Int, down: Boolean, repeatCount: Int,
        fire: (GestureAction, Int, String, List<Int>) -> Unit
    ): Boolean {
        val now = SystemClock.uptimeMillis()
        if (down) {
            if (repeatCount > 0) return false
            if (rule.dbl != GestureAction.NONE && lKey == keyCode && now - lAt <= Gestures.doubleMs(ctx)) {
                lKey = 0; lSwallowUp = true
                fire(rule.dbl, rule.kcDbl, rule.pkg, rule.combo)
                return true
            }
            lKey = keyCode; lAt = now; lPressAt = now; lSwallowUp = false
            return false
        }
        if (lSwallowUp) { lSwallowUp = false; return true }   // el soltar del segundo toque sí es nuestro
        if (rule.long != GestureAction.NONE && lKey == keyCode &&
            now - lPressAt >= Gestures.longMs(ctx)
        ) {
            lKey = 0
            fire(rule.long, rule.kcLong, rule.pkg, rule.combo)
        }
        return false
    }

    /** @return true si el evento es nuestro (se consumió y no llega a las apps). */
    fun onKey(
        ctx: Context, pkg: String?, keyCode: Int, down: Boolean, repeatCount: Int,
        fire: (GestureAction, Int, String, List<Int>) -> Unit
    ): Boolean {
        val rules = Gestures.rulesFor(ctx, pkg)
        // Al apretar otro botón, el toque que esperaba su doble se resuelve ya.
        if (down && pendingKey != 0 && pendingKey != keyCode) {
            handler.removeCallbacks(tapRunnable)
            val r = pendingRule
            val f = pendingFire
            pendingKey = 0; pendingRule = null; pendingFire = null
            if (r != null) {
                if (r.tap != GestureAction.NONE) f?.invoke(r.tap, r.kcTap, r.pkg, r.combo) else onPass(r.key)
            }
        }
        val rule = rules[keyCode]
        if (rule == null || !rule.any) return false
        // Modelo Key Mapper: con «Un toque» sin asignar, el botón NO se intercepta: pasa tal cual (al juego, al
        // sistema…) y la app solo escucha el doble toque y el mantener, que sí están asignados.
        if (rule.tap == GestureAction.NONE) return listen(ctx, rule, keyCode, down, repeatCount, fire)

        if (down) {
            if (repeatCount > 0) return true
            val now = SystemClock.uptimeMillis()
            // Segundo toque dentro de la ventana: doble toque.
            if (pendingKey == keyCode && now - pendingAt <= Gestures.doubleMs(ctx)) {
                handler.removeCallbacks(tapRunnable)
                pendingKey = 0; pendingRule = null; pendingFire = null
                if (rule.dbl != GestureAction.NONE) fire(rule.dbl, rule.kcDbl, rule.pkg, rule.combo) else onPass(keyCode)
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
            fire(rule.tap, rule.kcTap, rule.pkg, rule.combo)
        } else {
            onPass(keyCode)   // «Nada»: el botón sigue su camino normal (llega al juego)
        }
        return true
    }

    /**
     * Una pulsación ya resuelta (un trasero reconocido por su firma). El mando manda una firma corta de
     * ~25 ms, así que no hay forma de saber cuánto la mantuviste: dispara el mismo camino que un botón
     * (un toque o, si la aprietas dos veces seguidas, el doble toque).
     */
    fun onPress(ctx: Context, pkg: String?, keyCode: Int, fire: (GestureAction, Int, String, List<Int>) -> Unit): Boolean {
        val down = onKey(ctx, pkg, keyCode, true, 0, fire)
        val up = onKey(ctx, pkg, keyCode, false, 0, fire)
        return down || up
    }

    /**
     * Se cambió la app al frente: lo que estaba esperando un doble toque o midiendo un mantener ya no aplica
     * aquí (si no, la primera pulsación en la app nueva se podía comer como el «segundo toque» de la anterior).
     */
    fun reset() {
        handler.removeCallbacks(longRunnable)
        handler.removeCallbacks(tapRunnable)
        pendingKey = 0; pendingRule = null; pendingFire = null
        curRule = null; curFire = null
        longFired = false; swallowUp = false
        lKey = 0; lSwallowUp = false
    }
}
