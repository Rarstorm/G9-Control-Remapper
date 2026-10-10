package io.github.sudodiego.g9control

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Puente entre el servicio de accesibilidad (que ve teclas del sistema, como encendido o volumen) y
 * la prueba de botones. Solo tiene un oyente mientras la app está en primer plano.
 */
object KeyBus {
    @Volatile var listener: ((KeyEvent) -> Unit)? = null
}

/** Modo que quiere cada app. */
enum class AutoTarget { XINPUT, DS5 }

/** Dónde aparecen los avisos pequeños (el de volver a Android y la etiqueta del modo). */
enum class NoticePos(val h: Int, val v: Int) {
    TOP_START(-1, -1), TOP(0, -1), TOP_END(1, -1),
    CENTER_START(-1, 0), CENTER(0, 0), CENTER_END(1, 0),
    BOTTOM_START(-1, 1), BOTTOM(0, 1), BOTTOM_END(1, 1);

    /** Nombre para lectores de pantalla: "Arriba a la izquierda"… */
    val label: String get() {
        val row = when (v) { -1 -> tr("Arriba", "Top"); 1 -> tr("Abajo", "Bottom"); else -> tr("Al medio", "Middle") }
        val col = when (h) { -1 -> tr("a la izquierda", "left"); 1 -> tr("a la derecha", "right"); else -> tr("al centro", "center") }
        return "$row $col"
    }

}

/** Sonido al sacar una captura. */
enum class ShotSound { NONE, SHUTTER, JINGLE;
    val label get() = when (this) { NONE -> tr("Sin sonido", "None"); SHUTTER -> tr("Obturador", "Shutter"); JINGLE -> tr("Jingle", "Jingle") }
}

/** Cómo entran y salen los avisos. */
enum class NoticeAnim { SMOOTH, SLIDE, NONE;
    val label get() = when (this) { SMOOTH -> tr("Suave", "Smooth"); SLIDE -> tr("Deslizar", "Slide"); NONE -> tr("Sin animación", "None") }
}

/**
 * Reglas del cambio automático. La app cambia el control desde Android a X-Input o DS5; desde esos
 * modos (y para volver a Android) se usa Start + Select, con una guía en pantalla.
 */
object AutoRules {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("g9", Context.MODE_PRIVATE)

    fun enabled(ctx: Context) = sp(ctx).getBoolean("auto_enabled", true)
    fun setEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("auto_enabled", v).apply()

    /** Recordar volver a Android al salir de una app con regla. */
    fun remindReturn(ctx: Context) = sp(ctx).getBoolean("auto_remind_return", true)
    fun setRemindReturn(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("auto_remind_return", v).apply()

    /** Mientras la guía grande pide Vista + Menú: bloquear botones del control y pantalla táctil. */
    fun guideBlock(ctx: Context) = sp(ctx).getBoolean("guide_block", false)
    fun setGuideBlock(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("guide_block", v).apply()

    /** Con bloqueo, también sticks y gatillos: la guía toma el foco (el juego puede pausarse). */
    fun blockSticks(ctx: Context) = sp(ctx).getBoolean("block_sticks", false)
    fun setBlockSticks(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("block_sticks", v).apply()
    /** Lo mismo, para el aviso pequeño. */
    fun miniBlockSticks(ctx: Context) = sp(ctx).getBoolean("mini_block_sticks", false)
    fun setMiniBlockSticks(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("mini_block_sticks", v).apply()

    /** Bloquear también con el aviso pequeño (por ejemplo, al salir de un juego). */
    fun miniBlock(ctx: Context) = sp(ctx).getBoolean("mini_block", false)
    fun setMiniBlock(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("mini_block", v).apply()

    /** Al abrir un juego: tarjeta en el centro (false) o directamente el aviso pequeño (true). */
    fun guideSmall(ctx: Context) = sp(ctx).getBoolean("guide_small", false)
    fun setGuideSmall(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("guide_small", v).apply()

    /** Mostrar la etiqueta del modo cuando la app lo cambia sola. */
    fun showLabel(ctx: Context) = sp(ctx).getBoolean("show_label", true)
    fun setShowLabel(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("show_label", v).apply()

    /** L3 + R3 abre el panel de juego (captura, Game Assistant, modo del control). */
    fun gamePanel(ctx: Context) = sp(ctx).getBoolean("game_panel", true)
    fun setGamePanel(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("game_panel", v).apply()

    /** En DS5, M dos veces (llega como un toque del touchpad) saca la captura limpia del panel de juego. */
    fun touchpadShot(ctx: Context) = sp(ctx).getBoolean("touchpad_shot", true)
    fun setTouchpadShot(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("touchpad_shot", v).apply()

    /** Botón Legion (Home del control): tocar = Atrás, mantener = ajustes rápidos. */
    fun legionKeys(ctx: Context) = sp(ctx).getBoolean("legion_keys", true)
    fun setLegionKeys(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("legion_keys", v).apply()

    /** Aviso de captura: dónde sale (por defecto arriba a la izquierda, como en la Switch) y con qué sonido. */
    fun shotPos(ctx: Context) = runCatching { NoticePos.valueOf(sp(ctx).getString("shot_pos", null)!!) }
        .getOrDefault(NoticePos.TOP_START)
    fun setShotPos(ctx: Context, v: NoticePos) = sp(ctx).edit().putString("shot_pos", v.name).apply()
    fun shotSound(ctx: Context) = runCatching { ShotSound.valueOf(sp(ctx).getString("shot_sound", null)!!) }
        .getOrDefault(ShotSound.JINGLE)
    fun setShotSound(ctx: Context, v: ShotSound) = sp(ctx).edit().putString("shot_sound", v.name).apply()
    fun shotAnim(ctx: Context) = runCatching { NoticeAnim.valueOf(sp(ctx).getString("shot_anim", null)!!) }
        .getOrDefault(NoticeAnim.SLIDE)
    fun setShotAnim(ctx: Context, v: NoticeAnim) = sp(ctx).edit().putString("shot_anim", v.name).apply()

    fun noticePos(ctx: Context) = runCatching { NoticePos.valueOf(sp(ctx).getString("notice_pos", null)!!) }
        .getOrDefault(NoticePos.TOP_END)
    fun setNoticePos(ctx: Context, v: NoticePos) = sp(ctx).edit().putString("notice_pos", v.name).apply()

    /** Posición de la guía pequeña (volver a Android); por defecto arriba al centro, como antes. */
    fun guidePos(ctx: Context) = runCatching { NoticePos.valueOf(sp(ctx).getString("guide_pos", null)!!) }
        .getOrDefault(NoticePos.TOP)
    fun setGuidePos(ctx: Context, v: NoticePos) = sp(ctx).edit().putString("guide_pos", v.name).apply()

    fun noticeAnim(ctx: Context) = runCatching { NoticeAnim.valueOf(sp(ctx).getString("notice_anim", null)!!) }
        .getOrDefault(NoticeAnim.SMOOTH)
    fun setNoticeAnim(ctx: Context, v: NoticeAnim) = sp(ctx).edit().putString("notice_anim", v.name).apply()

    /** Pide al servicio mostrar un ejemplo del aviso (para ver posición y animación). */
    fun preview(ctx: Context, kind: String) =
        ctx.sendBroadcast(Intent(ACTION_PREVIEW).setPackage(ctx.packageName).putExtra("kind", kind))
    const val ACTION_PREVIEW = "io.github.sudodiego.g9control.PREVIEW_NOTICE"

    /** Cambio pedido a mano (ajustes rápidos): el servicio cambia por USB o muestra el aviso de Vista + Menú. */
    fun requestSwitch(ctx: Context, to: PadMode) =
        ctx.sendBroadcast(Intent(ACTION_SWITCH).setPackage(ctx.packageName).putExtra("target", to.name))
    const val ACTION_SWITCH = "io.github.sudodiego.g9control.SWITCH_MODE"

    fun rules(ctx: Context): Map<String, AutoTarget> {
        val x = sp(ctx).getStringSet("auto_xinput", emptySet()).orEmpty().associateWith { AutoTarget.XINPUT }
        val d = sp(ctx).getStringSet("auto_ds5", emptySet()).orEmpty().associateWith { AutoTarget.DS5 }
        return x + d
    }

    fun setRules(ctx: Context, v: Map<String, AutoTarget>) = sp(ctx).edit()
        .putStringSet("auto_xinput", v.filterValues { it == AutoTarget.XINPUT }.keys)
        .putStringSet("auto_ds5", v.filterValues { it == AutoTarget.DS5 }.keys)
        .apply()

    /** true si el servicio de accesibilidad de la app está activado en los ajustes de Android. */
    fun serviceEnabled(ctx: Context): Boolean {
        val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(ctx, AutoModeService::class.java)
        return list.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    /** Permiso de las ROMs chinas para leer la lista de apps; en Android estándar no existe. */
    const val PERM_APP_LIST = "com.android.permission.GET_INSTALLED_APPS"

    fun openServiceSettings(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * Servicio de accesibilidad: escucha qué app pasa a primer plano (de las ventanas solo usa su número, para la
 * captura; no lee su contenido) y filtra teclas para el panel de juego y el botón Legion. Según las reglas, cambia el G9 a X-Input y muestra una guía flotante para llegar a
 * DS5 o volver a Android con Start + Select.
 */
class AutoModeService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val main = Handler(Looper.getMainLooper())
    private var lastPackage: String? = null
    /** App con regla que está (o estuvo) al frente; al salir de ella se recuerda volver a Android. */
    private var ruleApp: String? = null
    private lateinit var guide: ModeGuide
    private lateinit var panel: GamePanel
    /** Se salió de una app con regla y el control aún no vuelve a Android. */
    private var returnPending = false
    /** G9 Control está al frente: tiene su propia franja de modo, así que la guía se quita. */
    private var appInFront = false
    private var hiddenForApp = false
    /** Ventana de la app al frente (del último cambio de ventana), o -1. La usa la captura del panel de juego. */
    @Volatile var appWindowId = -1
        private set

    /**
     * Pantalla apagada: la guía se quita (no tiene sentido en la pantalla de bloqueo). Al desbloquear,
     * si sigue pendiente volver a Android, aparece el aviso pequeño de arriba.
     */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> { main.removeCallbacks(waitUnlock); guide.suspendForLock() }
                AutoRules.ACTION_PREVIEW -> when (val kind = i.getStringExtra("kind") ?: "banner") {
                    "shot" -> panel.previewShot()
                    else -> guide.preview(kind)
                }
                AutoRules.ACTION_SWITCH -> i.getStringExtra("target")
                    ?.let { runCatching { PadMode.valueOf(it) }.getOrNull() }?.let(::manualSwitch)
                // En esta tablet USER_PRESENT no siempre llega: al encender la pantalla se espera el desbloqueo.
                Intent.ACTION_SCREEN_ON -> { unlockTries = 0; main.removeCallbacks(waitUnlock); main.post(waitUnlock) }
                Intent.ACTION_USER_PRESENT -> { main.removeCallbacks(waitUnlock); onUnlocked() }
            }
        }
    }

    private var unlockTries = 0
    /** Revisa cada 300 ms (hasta 2 min) si ya se quitó la pantalla de bloqueo. */
    private val waitUnlock: Runnable = object : Runnable {
        override fun run() {
            val km = getSystemService(android.app.KeyguardManager::class.java)
            if (!km.isKeyguardLocked) { onUnlocked(); return }
            if (++unlockTries < 400) main.postDelayed(this, 300)
        }
    }

    private var lastUnlock = 0L

    /** Recién desbloqueada: vuelve la guía que había, o el aviso de volver a Android. */
    private fun onUnlocked() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastUnlock < 1500) return   // USER_PRESENT y la espera pueden llegar juntos
        lastUnlock = now
        Log.d(TAG, "desbloqueada")
        val mode = ModeSwitcher.detect()
        if (mode == PadMode.ANDROID) returnPending = false
        if (guide.resumeAfterLock()) return
        if (returnPending && ruleApp == null && mode != null) guide.show(PadMode.ANDROID, mini = true)
    }

    private val inputListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(id: Int) = changed()
        override fun onInputDeviceRemoved(id: Int) = changed()
        override fun onInputDeviceChanged(id: Int) = changed()
        private fun changed() {
            ModeTileService.refreshAll(this@AutoModeService)
            updateTouchpad()
            if (ModeSwitcher.detect() == PadMode.ANDROID) returnPending = false
            guide.update()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Lang.load(this)
        guide = ModeGuide(this)
        panel = GamePanel(this)
        updateTouchpad()
        getSharedPreferences("g9", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefsListener)
        getSystemService(InputManager::class.java).registerInputDeviceListener(inputListener, main)
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(AutoRules.ACTION_PREVIEW)
            addAction(AutoRules.ACTION_SWITCH)
        }, if (android.os.Build.VERSION.SDK_INT >= 33) Context.RECEIVER_NOT_EXPORTED else 0)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // La propia app (no las ventanas de la guía, que también son de este paquete): la guía se quita para
        // que Vista y Menú le lleguen a la app y su franja muestre el avance.
        if (pkg == packageName && event.className?.toString() == MainActivity::class.java.name) {
            appInFront = true
            lastPackage = pkg
            if (guide.isGuiding) { guide.hide(); hiddenForApp = true }
            return
        }
        if (isTransient(pkg)) return
        // Ventana de la app al frente, para capturarla sola (sin puntero ni superposiciones). -1 si Android no la entrega.
        appWindowId = event.windowId
        if (pkg == lastPackage) return
        appInFront = false
        val wasHidden = hiddenForApp
        hiddenForApp = false
        lastPackage = pkg
        panel.hide()
        Log.d(TAG, "app al frente: $pkg (${event.className}), ventana $appWindowId, modo ${ModeSwitcher.detect()}, ZUI ${ZuiGame.sideBar(this)}")
        if (!AutoRules.enabled(this)) return

        val target = AutoRules.rules(this)[pkg]
        if (target == null) {
            // Se salió de una app con regla: recordar volver a Android.
            if (ruleApp != null) {
                ruleApp = null
                val mode = ModeSwitcher.detect()
                if (AutoRules.remindReturn(this) && mode != null && mode != PadMode.ANDROID) {
                    returnPending = true
                    // Solo el aviso pequeño de arriba: recuerda volver sin tapar la pantalla.
                    guide.show(PadMode.ANDROID, mini = true)
                }
            } else if (wasHidden && returnPending && ModeSwitcher.detect().let { it != null && it != PadMode.ANDROID }) {
                // Se había quitado al abrir G9 Control: vuelve el aviso de volver a Android.
                guide.show(PadMode.ANDROID, mini = true)
            }
            return
        }
        ruleApp = pkg
        returnPending = false
        applyRule(target, retry = true)
    }

    private fun applyRule(target: AutoTarget, retry: Boolean) {
        val mode = ModeSwitcher.detect()
        Log.d(TAG, "regla $target, modo actual $mode")
        when {
            // Desde Android: cambio directo con aviso "Cambiando a …" → "Listo ✓".
            mode == PadMode.ANDROID -> switchTo(wanted(target))
            // Ya está en el modo pedido: si quedó la guía de volver a Android, ya no sirve.
            mode == wanted(target) -> if (guide.isGuiding) guide.hide()
            // Desde otro modo que no es el pedido: guía de Start + Select.
            mode != null && mode != wanted(target) -> guide.show(wanted(target))
            // El control se está reconectando: se reintenta una vez.
            mode == null && retry -> main.postDelayed({ applyRule(target, retry = false) }, 2500)
        }
    }

    /** Start/Select que se retuvieron mientras estaba la guía; su "soltar" también se retiene. */
    private val held = mutableSetOf<Int>()

    /**
     * Mientras la guía está en pantalla, Start y Select no llegan al juego (para cambiar de modo sin
     * pausar ni abrir menús). El resto de los botones pasa normal.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val k = event.keyCode
        // Teclas que no llegan a las apps (encendido, volumen…): se pasan a la prueba de botones.
        KeyBus.listener?.let { l ->
            if (!KeyEvent.isGamepadButton(k) && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val copy = KeyEvent(event)
                main.post { l(copy) }
            }
        }
        if (panelChord(event)) return true
        if (panel.isOpen && panel.onKey(event)) return true
        if (legionKey(event)) return true
        if (k != KeyEvent.KEYCODE_BUTTON_START && k != KeyEvent.KEYCODE_BUTTON_SELECT) {
            // Guía grande con bloqueo: el resto de los botones del control no llega a las apps.
            val fromPad = KeyEvent.isGamepadButton(k) || event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
                event.isFromSource(InputDevice.SOURCE_JOYSTICK)
            return fromPad && guide.blocksInput
        }
        if (event.repeatCount == 0 && (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP)) {
            guide.onKey(k, event.action == KeyEvent.ACTION_DOWN)
        }
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> if (guide.isRetaining) { held += k; true } else false
            KeyEvent.ACTION_UP -> held.remove(k) || guide.isRetaining
            else -> false
        }
    }

    /** Esta pulsación del botón Legion es nuestra (no llega a la app). */
    private var legionOwned = false
    /** Botón Legion apretado y todavía sin decidir si es toque (Atrás) o mantener (ajustes rápidos). */
    private var legionPending = false
    private val legionHold = Runnable {
        if (!legionPending) return@Runnable
        legionPending = false
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    }

    /**
     * Botón Legion (Home del control, llega como BUTTON_MODE en los tres modos): tocar = Atrás, mantener 0,5 s
     * = ajustes rápidos. Dentro de G9 Control llega normal (lo usa la prueba de botones). Llega con algo de
     * retraso: el firmware espera por si es un doble toque o Home + cruceta. Devuelve si el evento es nuestro.
     */
    private fun legionKey(e: KeyEvent): Boolean {
        if (e.keyCode != KeyEvent.KEYCODE_BUTTON_MODE) return false
        when (e.action) {
            KeyEvent.ACTION_DOWN -> {
                if (e.repeatCount > 0) return legionOwned
                legionOwned = AutoRules.legionKeys(this) && !appInFront && !guide.blocksInput
                if (legionOwned) { legionPending = true; main.postDelayed(legionHold, 500) }
                return legionOwned
            }
            KeyEvent.ACTION_UP -> {
                if (!legionOwned) return false
                legionOwned = false
                if (legionPending) { legionPending = false; main.removeCallbacks(legionHold); performGlobalAction(GLOBAL_ACTION_BACK) }
                return true
            }
        }
        return legionOwned
    }

    private var lastTouchpad = 0L
    private var touchpadOn = false
    /** Al cambiar la opción en Ajustes se aplica enseguida (no recién al reconectar el control). */
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "touchpad_shot") updateTouchpad()
    }

    /**
     * Recibir los eventos de mouse (así llega el touchpad del G9) solo en DS5 y con la opción activa. Android 14
     * y 15 no permiten solo mirarlos: el servicio se los queda, así que un mouse real tampoco funciona en DS5.
     */
    private fun updateTouchpad() {
        if (android.os.Build.VERSION.SDK_INT < 34) return
        val want = AutoRules.touchpadShot(this) && ModeSwitcher.detect() == PadMode.DS5
        if (want == touchpadOn) return
        runCatching { serviceInfo = serviceInfo.apply { motionEventSources = if (want) InputDevice.SOURCE_MOUSE else 0 } }
            .onSuccess { touchpadOn = want; Log.d(TAG, "touchpad del G9: ${if (want) "capturas" else "normal"}") }
            .onFailure { Log.w(TAG, "no se pudo pedir el touchpad", it) }
    }

    /**
     * Touchpad del G9 en DS5. El control no tiene touchpad: solo llega M dos veces, como un toque corto
     * (que además muestra el puntero). El primer evento tras 1,5 s sin ninguno saca la captura limpia. Solo
     * llegan mientras [updateTouchpad] los pidió.
     */
    override fun onMotionEvent(event: MotionEvent) {
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) return
        val now = android.os.SystemClock.uptimeMillis()
        val quiet = now - lastTouchpad > 1500
        lastTouchpad = now
        if (!quiet || !AutoRules.touchpadShot(this)) return
        if (event.device?.name?.contains("g9", true) != true || ModeSwitcher.detect() != PadMode.DS5) return
        Log.d(TAG, "M dos veces en DS5 (touchpad): captura")
        panel.capture()
    }

    /** L3 y R3 apretados ahora mismo. */
    private val sticksDown = mutableSetOf<Int>()
    /** El stick que completó L3 + R3: ni su presión ni su soltar llegan al juego. */
    private var chordKey = -1
    /** L3 + R3 apretados y todavía sin decidir si es toque (panel) o mantener (captura). */
    private var chordPending = false

    /** L3 + R3 mantenidos medio segundo: captura directa, sin abrir el panel. */
    private val chordHold = Runnable {
        if (!chordPending) return@Runnable
        chordPending = false
        Log.d(TAG, "L3 + R3 mantenidos: captura")
        panel.hide()
        panel.capture()
    }

    /**
     * L3 + R3: tocarlos abre o cierra el panel de juego; mantenerlos 0,5 s saca la captura directamente.
     * El primero de los dos llega al juego como siempre (no se sabe todavía si viene el otro); el segundo
     * se lo queda el panel. No funciona mientras está la guía.
     */
    private fun panelChord(e: KeyEvent): Boolean {
        val k = e.keyCode
        if (k != KeyEvent.KEYCODE_BUTTON_THUMBL && k != KeyEvent.KEYCODE_BUTTON_THUMBR) return false
        when (e.action) {
            KeyEvent.ACTION_DOWN -> {
                if (e.repeatCount > 0) return k == chordKey
                sticksDown += k
                if (sticksDown.size == 2 && AutoRules.gamePanel(this) && !guide.isGuiding) {
                    chordKey = k
                    chordPending = true
                    main.postDelayed(chordHold, 500)
                    return true
                }
            }
            KeyEvent.ACTION_UP -> {
                sticksDown -= k
                // Se soltó antes del medio segundo: era un toque.
                if (chordPending) { chordPending = false; main.removeCallbacks(chordHold); panel.toggle() }
                if (k == chordKey) { chordKey = -1; return true }
            }
        }
        return false
    }

    private val transientCache = mutableMapOf<String, Boolean>()

    /**
     * Ventanas que no cuentan como "cambiar de app": esta app, la barra del sistema, el teclado y
     * superposiciones sin ícono en el menú de apps (por ejemplo la barra de juegos de ZUI, que aparece
     * justo al abrir un juego). Las pantallas de inicio (Cocoon Shell) sí cuentan.
     */
    private fun isTransient(pkg: String): Boolean = transientCache.getOrPut(pkg) {
        if (pkg == packageName || pkg == "com.android.systemui") return@getOrPut true
        val imm = getSystemService(InputMethodManager::class.java)
        if (imm.enabledInputMethodList.any { it.packageName == pkg }) return@getOrPut true
        val pm = packageManager
        val launchable = pm.getLaunchIntentForPackage(pkg) != null
        val home = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
        ).any { it.activityInfo.packageName == pkg }
        !launchable && !home
    }

    private fun wanted(t: AutoTarget) = if (t == AutoTarget.DS5) PadMode.DS5 else PadMode.XINPUT

    /**
     * Cambio pedido a mano (ajustes rápidos o panel de juego). Desde Android va por USB; desde otro modo
     * muestra el aviso pequeño de Vista + Menú (reemplaza la guía que hubiera) y cierra el panel de
     * notificaciones para que se vea.
     */
    internal fun manualSwitch(to: PadMode) {
        val mode = ModeSwitcher.detect() ?: return
        if (mode == to) return
        if (mode == PadMode.ANDROID) { switchTo(to); return }
        if (android.os.Build.VERSION.SDK_INT >= 31) performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        // Con G9 Control al frente guía su propia franja (si no, la guía se quedaría con Vista y Menú).
        if (appInFront) startActivity(Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_SWITCH_TO, to.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        else guide.show(to, mini = true)
    }

    private fun switchTo(to: PadMode) {
        val target = to.target ?: return
        val d = G9Usb.findDevice(this) ?: return
        if (!G9Usb.hasPermission(this, d)) {
            Toast.makeText(
                this, tr("G9 Control: falta el permiso USB. Abre la app una vez con el control conectado.",
                    "G9 Control: USB permission missing. Open the app once with the controller connected."),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        scope.launch {
            ModeSwitcher.switchTo(this@AutoModeService, target)
            if (AutoRules.showLabel(this@AutoModeService)) guide.show(to, hold = false)
        }
    }

    override fun onInterrupt() {}

    companion object { internal const val TAG = "G9Auto" }

    override fun onDestroy() {
        getSystemService(InputManager::class.java).unregisterInputDeviceListener(inputListener)
        getSharedPreferences("g9", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefsListener)
        runCatching { unregisterReceiver(screenReceiver) }
        main.removeCallbacksAndMessages(null)
        if (::guide.isInitialized) guide.hide()
        if (::panel.isInitialized) panel.release()
        scope.cancel()
        super.onDestroy()
    }
}
