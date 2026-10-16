package io.github.sudodiego.g9control

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.accessibilityservice.GestureDescription
import android.graphics.Path

/**
 * Puente entre el servicio de accesibilidad (que ve teclas del sistema, como encendido o volumen) y
 * la prueba de botones. Solo tiene un oyente mientras la app está en primer plano.
 */
object KeyBus {
    @Volatile var listener: ((KeyEvent) -> Unit)? = null

    /**
     * Prueba de botones en curso (la app está al frente). Mientras dura, el servicio no remapea nada:
     * los botones tienen que llegar tal cual a la prueba.
     */
    @Volatile var testing = false
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

    /** Ráfaga de capturas: mantener RB (con el botón guía) sigue sacando una por segundo. */
    fun burstShots(ctx: Context) = sp(ctx).getBoolean("burst_shots", true)
    fun setBurstShots(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("burst_shots", v).apply()

    /** Al salir de un juego: aviso pequeño (false, lo de siempre) o la tarjeta en el centro (true). */
    fun exitBig(ctx: Context) = sp(ctx).getBoolean("exit_big", false)
    fun setExitBig(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("exit_big", v).apply()

    /** Mostrar la etiqueta del modo cuando la app lo cambia sola. */
    fun showLabel(ctx: Context) = sp(ctx).getBoolean("show_label", true)
    fun setShowLabel(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("show_label", v).apply()

    /**
     * Opción vieja de «L3 + R3 abre el panel de juego»: ya no se usa — esa combinación es ahora el puntero del
     * ratón (ver [MouseRules.chord]) y el panel se abre manteniendo el botón guía. Se deja por compatibilidad con
     * los respaldos, pero no la lee nadie.
     */
    fun gamePanel(ctx: Context) = sp(ctx).getBoolean("game_panel", true)
    fun setGamePanel(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("game_panel", v).apply()

    /** En DS5, M dos veces (llega como un toque del touchpad) saca la captura limpia del panel de juego. */
    fun touchpadShot(ctx: Context) = sp(ctx).getBoolean("touchpad_shot", true)
    fun setTouchpadShot(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("touchpad_shot", v).apply()

    /**
     * Game Assistant oculto (appops, con Shizuku): G9 Control lo muestra solo mientras lo usa y después lo vuelve
     * a dejar así. Null mientras no se sabe (se toma de cómo esté la primera vez).
     */
    fun gaHidden(ctx: Context): Boolean? = sp(ctx).takeIf { it.contains("ga_hidden") }?.getBoolean("ga_hidden", false)
    fun setGaHidden(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("ga_hidden", v).apply()

    /**
     * Bypass automático propio (como el inteligente de Game Assistant, pero con los rangos que se elijan): enchufada,
     * con batería ≥ [smartBypassLevel] y temperatura ≥ [smartBypassTemp] (0 = sin mínimo), y si [smartBypassGames],
     * solo en juegos. Necesita Shizuku.
     */
    fun smartBypass(ctx: Context) = sp(ctx).getBoolean("smart_bypass", false)
    fun setSmartBypass(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("smart_bypass", v).apply()
    fun smartBypassLevel(ctx: Context) = sp(ctx).getInt("smart_bypass_level", 40)
    fun setSmartBypassLevel(ctx: Context, v: Int) = sp(ctx).edit().putInt("smart_bypass_level", v).apply()
    fun smartBypassTemp(ctx: Context) = sp(ctx).getInt("smart_bypass_temp", 0)
    fun setSmartBypassTemp(ctx: Context, v: Int) = sp(ctx).edit().putInt("smart_bypass_temp", v).apply()
    fun smartBypassGames(ctx: Context) = sp(ctx).getBoolean("smart_bypass_games", true)
    fun setSmartBypassGames(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("smart_bypass_games", v).apply()

    /** Avisos en Inicio: el cambio automático está apagado (hay reglas pero no el servicio) / Shizuku no está listo. */
    fun warnService(ctx: Context) = sp(ctx).getBoolean("warn_service", true)
    fun setWarnService(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("warn_service", v).apply()
    fun warnShizuku(ctx: Context) = sp(ctx).getBoolean("warn_shizuku", true)
    fun setWarnShizuku(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("warn_shizuku", v).apply()

    /** Botones de volumen de la tablet invertidos (el de abajo sube, el de arriba baja). Los del control no cambian. */
    fun swapVolume(ctx: Context) = sp(ctx).getBoolean("swap_volume", false)
    fun setSwapVolume(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("swap_volume", v).apply()

    /** Botón Legion (Home del control): tocar = Atrás, mantener = panel de juego. */
    fun legionKeys(ctx: Context) = sp(ctx).getBoolean("legion_keys", true)

    /**
     * Apps de streaming de PC o consola: ahí el botón Legion pasa normal (es el botón guía del PC: modo Xbox, Game
     * Bar). En Moonlight y Artemis solo durante la transmisión ([STREAM_ACTIVITY]); en su lista de PCs sigue siendo
     * Atrás, para poder salir. Si la app toma el control por USB (Artemis en X-Input), Android ni lo ve y da igual.
     */
    val STREAMING = setOf(
        "com.limelight", "com.limelight.noir", "com.limelight.unofficial",   // Moonlight, Artemis
        "com.valvesoftware.steamlink", "com.microsoft.xboxone.smartglass", "com.gamepass",
        "com.metallic.chiaki", "com.playstation.remoteplay", "tv.parsec.client", "com.nvidia.geforcenow",
    )
    /** La pantalla de transmisión de Moonlight y sus derivados (Artemis). */
    const val STREAM_ACTIVITY = "com.limelight.Game"
    fun setLegionKeys(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("legion_keys", v).apply()

    /** Aviso de captura: dónde sale (por defecto arriba a la izquierda, como en la Switch) y con qué sonido. */
    fun shotPos(ctx: Context) = runCatching { NoticePos.valueOf(sp(ctx).getString("shot_pos", null)!!) }
        .getOrDefault(NoticePos.TOP_START)
    fun setShotPos(ctx: Context, v: NoticePos) = sp(ctx).edit().putString("shot_pos", v.name).apply()
    /** Monitor de rendimiento sobre el juego (se enciende desde el panel) y su posición. */
    fun perfHud(ctx: Context) = sp(ctx).getBoolean("perf_hud", false)
    fun setPerfHud(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("perf_hud", v).apply()
    /** Barra completa (dos líneas: además watts, tiempo restante, Hz y calor de la tablet) o compacta. */
    fun perfFull(ctx: Context) = sp(ctx).getBoolean("perf_full", true)
    fun setPerfFull(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("perf_full", v).apply()
    fun perfPos(ctx: Context) = runCatching { NoticePos.valueOf(sp(ctx).getString("perf_pos", null)!!) }
        .getOrDefault(NoticePos.TOP_START)
    fun setPerfPos(ctx: Context, v: NoticePos) = sp(ctx).edit().putString("perf_pos", v.name).apply()
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

    /** Desde la app: volver a oscurecer la tablet con el monitor (después de encenderla con el botón). */
    fun darkenTablet(ctx: Context) = ctx.sendBroadcast(Intent(ACTION_DARKEN).setPackage(ctx.packageName))
    const val ACTION_DARKEN = "io.github.sudodiego.g9control.DARKEN_TABLET"

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

    /** Abre los Ajustes de Android de esta app (para «Permitir ajustes restringidos»). */
    fun openAppInfo(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", ctx.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
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
    /** Transmitiendo desde un PC o consola (el botón Legion pasa normal). */
    private var streaming = false
    /** App al frente (la última que no es pasajera). */
    val foregroundApp: String? get() = lastPackage
    /** App con regla que está (o estuvo) al frente; al salir de ella se recuerda volver a Android. */
    private var ruleApp: String? = null
    private lateinit var guide: ModeGuide
    private lateinit var panel: GamePanel
    private lateinit var perfHud: PerfHud
    /** Aviso de una sola vez: ¿abrir la app sola al conectar el control? (con su casilla). */
    private lateinit var openAsk: OpenAsk
    /** Se salió de una app con regla y el control aún no vuelve a Android. */
    private var returnPending = false
    /** G9 Control está al frente: tiene su propia franja de modo, así que la guía se quita. */
    private var appInFront = false
    private var hiddenForApp = false
    /** Un gesto de deslizamiento está en marcha: si se pide otuno, se cancela (evita que se apilen). */
    private var gestureRunning = false
    /** Ventana de la app al frente (del último cambio de ventana), o -1. La usa la captura del panel de juego. */
    @Volatile var appWindowId = -1
        private set

    /** Motor de gestos: botones con gestos configurados (tocar / mantener / doble toque). */
    private val gestures = GestureEngine(main, onPass = { code -> injectKey(code) })

    /**
     * Emulación de ratón (el puntero de «L3 + R3»): dibuja el puntero encima de todo y saca los clics como gestos
     * inyectados en su posición. El stick lo mueve con velocidad, suavidad, curva y zona muerta configurables.
     */
    private val mouse by lazy {
        MouseMode(
            this, main, { cmd -> scope.launch { Shell.run(cmd) } },
            { x1, y1, x2, y2, ms -> swipe(x1, y1, x2, y2, ms) },
            { gestureRunning }
        )
    }

    /**
     * Detector de las firmas de los traseros (Vista/Menú en 5 ms): reconoce la secuencia que lleva grabada cada
     * trasero y la convierte en «se apretó L4/R4/L5/R5». Si la secuencia no se completa, devuelve los botones
     * con Shizuku para que un Vista/Menú normal no se pierda.
     */
    private val signatures = SignatureDetector(
        main,
        onLost = { code -> lostSignatureKey(code) },
        onPaddle = { back -> runPaddle(back) },
    )

    /**
     * Devuelve un botón a la app tal cual (no se pudo dar el evento original). Primero con origen de mando:
     * los juegos solo atienden ese. Si el sistema no acepta el comando, se reenvía como tecla normal.
     */
    private fun injectKey(code: Int) {
        if (code <= 0) return   // los traseros usan códigos internos negativos: no son teclas
        scope.launch {
            val out = Shell.run(Shell.keyEventPad(code))
            if (out == null || out.contains("rror", true) || out.contains("sage", true)) {
                Shell.run(Shell.keyEvent(code))
            }
        }
    }

    /**
     * Vista/Menú que resultaron NO ser la firma de un trasero. Si esa app los usa como botones de gestos, se le
     * pasan al motor de gestos (como si se acabaran de apretar); si no, se reenvían a la app tal cual. Antes se
     * reenviaban siempre: en esta ROM el reenvío de Menú abría la app Files y además se comía los gestos.
     */
    private fun lostSignatureKey(code: Int) {
        val rule = Gestures.rulesFor(this, lastPackage)[code]
        if (rule != null && rule.any) {
            // Al motor se le pasa SOLO el apretar: el soltar de verdad llega después y con él se resuelve el
            // toque. Sintetizar también el soltar rompía dos cosas a la vez (medido por Roberto): el
            // «Mantener» se cancelaba y acababa saliendo al soltar el dedo, y el motor devolvía «no lo tomé»,
            // con lo que se reinyectaba la tecla — y en esta ROM eso abre la app Files con Menú.
            gestures.onKey(this, lastPackage, code, true, 0) { act, kc, pkg, combo -> runGestureAction(act, kc, pkg, combo) }
            return
        }
        // Sin regla en esta app: se devuelve con origen de MANDO (nunca `input keyevent` pelón: con Menú abre
        // la app Files en esta ROM).
        scope.launch {
            if (Shell.run(Shell.keyEventPad(code)) == null) Log.w(TAG, "no se pudo devolver la tecla $code")
        }
    }

    /**
     * Pantalla apagada: la guía se quita (no tiene sentido en la pantalla de bloqueo). Al desbloquear,
     * si sigue pendiente volver a Android, aparece el aviso pequeño de arriba.
     */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> { main.removeCallbacks(waitUnlock); guide.suspendForLock() }
                // Sin cargador el bypass no sirve, y si quedara activo la próxima vez que se enchufe no cargaría.
                Intent.ACTION_POWER_DISCONNECTED -> {
                    ZuiBattery.manualOverride = false
                    smartOn = false
                    scope.launch { if (ZuiBattery.readBypass() == true) ZuiBattery.setBypass(false, manual = false) }
                }
                Intent.ACTION_BATTERY_CHANGED -> { lastBattery = i; evaluateBypass() }
                AutoRules.ACTION_PREVIEW -> when (val kind = i.getStringExtra("kind") ?: "banner") {
                    "shot" -> panel.previewShot()
                    else -> guide.preview(kind)
                }
                AutoRules.ACTION_DARKEN -> if (Monitor.woken) toggleTabletScreen()
                AutoRules.ACTION_SWITCH -> i.getStringExtra("target")
                    ?.let { runCatching { PadMode.valueOf(it) }.getOrNull() }?.let(::manualSwitch)
                // En esta tablet USER_PRESENT no siempre llega: al encender la pantalla se espera el desbloqueo.
                Intent.ACTION_SCREEN_ON -> {
                    unlockTries = 0; main.removeCallbacks(waitUnlock); main.post(waitUnlock)
                    // Encendida con el botón: el sistema vuelve a encender el panel y queda así (es la forma de ver la
                    // tablet si el control no sirve con el monitor). Se oscurece otra vez desde el panel de juego o al
                    // volver a conectar el monitor.
                    if (panelOff || lightOff || darkView != null) {
                        panelOff = false; lightOff = false; Monitor.woken = true
                        main.removeCallbacks(monitorCheck); main.postDelayed(monitorCheck, 500)
                    }
                }
                Intent.ACTION_USER_PRESENT -> { main.removeCallbacks(waitUnlock); onUnlocked() }
            }
        }
    }

    /** Último aviso de la batería (nivel, temperatura, cargador). */
    private var lastBattery: Intent? = null
    /** El bypass automático lo activó (solo apaga lo que él activó). */
    private var smartOn = false
    private var lastBypassRead = 0L

    /**
     * Bypass automático, por dos razones (basta una): el automático (enchufada, con batería ≥ mínimo y temperatura ≥
     * mínimo, y en un juego si se eligió) o el límite de carga propio (enchufada y con la batería en el límite). Una
     * vez activo se mantiene hasta que la batería baje 2 % (3 % el límite) o la temperatura 1 °C (si no, parpadearía).
     * Si el usuario lo cambió a mano, no lo toca hasta desconectar o cambiar de app.
     */
    private fun evaluateBypass() {
        val i = lastBattery ?: return
        val limit = ZuiBattery.ownLimit(this)
        if (!AutoRules.smartBypass(this) && limit == null) {
            if (smartOn) { smartOn = false; scope.launch { ZuiBattery.setBypass(false, manual = false) } }
            return
        }
        if (ZuiBattery.manualOverride || Shell.state != Shell.State.READY) return
        // Lo encendió el automático pero alguien lo apagó (a mano, en otra app, ZUI o Game Assistant): ya no está
        // encendido por él. Si no, creería que sigue activo y no lo volvería a encender. Cada minuto se lee el estado
        // real (los avisos de la batería llegan seguido; leerlo es un comando de Shizuku).
        if (smartOn && ZuiBattery.bypassNow == false) smartOn = false
        val now = android.os.SystemClock.uptimeMillis()
        if (smartOn && now - lastBypassRead > 60_000) {
            lastBypassRead = now
            scope.launch { if (ZuiBattery.readBypass() == false) { Log.d(TAG, "bypass apagado desde fuera: se revisa"); evaluateBypass() } }
        }
        val plugged = i.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0
        val level = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) * 100 /
            i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val temp = i.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        val game = !AutoRules.smartBypassGames(this) || ZuiGame.inGame(this)
        val minLevel = AutoRules.smartBypassLevel(this)
        val minTemp = AutoRules.smartBypassTemp(this)
        val margin = if (smartOn) 2 else 0
        val smartWant = AutoRules.smartBypass(this) && game && level >= minLevel - margin && (minTemp == 0 || temp >= minTemp - margin / 2f)
        val limitWant = limit != null && level >= limit - (if (smartOn) 3 else 0)
        val want = plugged && (smartWant || limitWant)
        if (want == smartOn) return
        Log.d(TAG, "bypass automático: ${if (want) "activar" else "desactivar"} (batería $level %, $temp °C, juego $game, límite $limit)")
        smartOn = want
        scope.launch { if (!ZuiBattery.setBypass(want, manual = false) && want) smartOn = false }
    }

    /** Aviso de volver a Android al salir de un juego: el pequeño o la tarjeta, según lo elegido. */
    private fun showReturn() {
        val big = AutoRules.exitBig(this)
        guide.show(PadMode.ANDROID, mini = !big, big = big)
    }

    /** El bypass vuelve al automático (desde el panel): decide ya con lo que hay ahora. */
    fun bypassToAuto() {
        ZuiBattery.returnToAuto()
        smartOn = ZuiBattery.bypassNow == true
        evaluateBypass()
    }

    /** Hay algo automático para el bypass: el bypass automático o el límite de carga propio. */
    fun bypassHasAuto() = AutoRules.smartBypass(this) || ZuiBattery.ownLimit(this) != null

    // También la frecuencia de los juegos: ZUI tarda en decir que es un juego.
    private val bypassCheck = Runnable { evaluateBypass(); if (RefreshRate.forGames(this) != null) RefreshRate.applyFor(this, lastPackage) }

    /**
     * Monitor externo: en modo espejo, si para ese monitor (o para todos) se eligió 16:9, se pone solo; al
     * desconectarlo (o pasar al modo extendido) se vuelve a 16:10, solo si lo había puesto G9 Control. La pantalla
     * de la tablet queda como se eligió (oscura o apagada) mientras tanto. Se revisa un momento después de cada
     * cambio de pantalla o de modo (el monitor tarda en asentarse).
     */
    private val monitorCheck = Runnable {
        val m = Monitor.current(this)
        val mirror = Monitor.mirror(this)
        // Al arrancar el servicio (por ejemplo, tras actualizar la app con el panel apagado y el monitor ya quitado):
        // si no toca tenerlo apagado, se enciende por si acaso. Encenderlo estando encendido no hace nada.
        if (!panelChecked && Shell.state == Shell.State.READY && Monitor.screen(this) == Monitor.Screen.OFF) {
            panelChecked = true
            if (m == null || !mirror) scope.launch { Shell.run(Shell.screenPower(true)) }
        }
        if (m != null && m.key != lastMonitor) { Monitor.remember(this, m); Log.d(TAG, "monitor conectado: ${m.name} (${m.key})") }
        lastMonitor = m?.key
        val want = m != null && mirror && Monitor.wants169(this, m.key)
        val wide = Aspect.is169(this)
        when {
            want && !wide -> scope.launch { if (Aspect.toggle(this@AutoModeService)) { Monitor.setApplied(this@AutoModeService, true); Log.d(TAG, "monitor ${m?.name}: 16:9") } }
            !want && wide && Monitor.applied(this) -> scope.launch {
                Monitor.setApplied(this@AutoModeService, false)
                if (Aspect.toggle(this@AutoModeService)) Log.d(TAG, "sin monitor en espejo: 16:10")
            }
        }
        applyTabletScreen(if (m != null && mirror && !Monitor.woken) Monitor.screen(this) else Monitor.Screen.NORMAL)
        if (m == null) Monitor.woken = false
    }
    private var lastMonitor: String? = null
    private var panelChecked = false

    /** Lo que G9 Control hizo con la pantalla de la tablet, para deshacerlo. */
    private var darkView: View? = null
    private var panelOff = false

    /**
     * Pantalla de la tablet con un monitor en espejo. Oscura: una ventana invisible del servicio pide el brillo
     * "apagado", que Android deja en el mínimo (sin Shizuku; una app no puede apagar la luz del todo). Apagada: el
     * panel entero, con Shizuku, como Extinguish (si no está, queda oscura). Sin luz: la ventana de Oscura (así el
     * brillo automático no lo cambia) y, encima, el brillo en -1 con Shizuku, lo que hace Extinguish; al quitar la
     * ventana Android vuelve a poner el brillo de siempre.
     */
    private fun applyTabletScreen(target: Monitor.Screen) {
        val ready = Shell.state == Shell.State.READY
        val off = target == Monitor.Screen.OFF && ready
        val black = target == Monitor.Screen.NO_LIGHT && ready
        val dark = !off && target != Monitor.Screen.NORMAL
        // Saliendo de Sin luz: se quita la ventana para que Android vuelva a aplicar el brillo (después se pone si toca).
        if (lightOff && !black) {
            lightOff = false
            darkView?.let { v -> runCatching { getSystemService(WindowManager::class.java).removeView(v) }; darkView = null }
        }
        if (dark && darkView == null) {
            val v = View(this)
            val p = WindowManager.LayoutParams(
                1, 1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
            }
            runCatching { getSystemService(WindowManager::class.java).addView(v, p); darkView = v; Log.d(TAG, "tablet oscura") }
                .onFailure { Log.w(TAG, "no se pudo quitar la luz", it) }
        } else if (!dark && darkView != null) {
            runCatching { getSystemService(WindowManager::class.java).removeView(darkView) }
            darkView = null
            Log.d(TAG, "tablet con luz")
        }
        if (off != panelOff) {
            panelOff = off
            scope.launch { Shell.run(Shell.screenPower(!off))?.let { Log.d(TAG, if (off) "panel de la tablet apagado" else "panel de la tablet encendido") } }
        }
        if (black && !lightOff) {
            lightOff = true
            // Un momento después de la ventana: si no, el brillo mínimo que pide ella llega después y pisa el -1.
            scope.launch { kotlinx.coroutines.delay(800); if (lightOff) Shell.run(Shell.SCREEN_LIGHT_OFF)?.let { Log.d(TAG, "tablet sin luz") } }
        }
    }
    private var lightOff = false

    /** Desde el panel de juego: ver la tablet un rato con el monitor conectado (o volver a oscurecerla). */
    fun toggleTabletScreen() {
        Monitor.woken = !Monitor.woken
        main.removeCallbacks(monitorCheck)
        monitorCheck.run()
    }
    /** La pantalla de la tablet está oscurecida por el monitor (para el texto del panel de juego). */
    val tabletDark get() = darkView != null || panelOff
    private val displayListener = object : android.hardware.display.DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) = later()
        override fun onDisplayRemoved(id: Int) = later()
        override fun onDisplayChanged(id: Int) { if (id != android.view.Display.DEFAULT_DISPLAY) later() }
        fun later() { main.removeCallbacks(monitorCheck); main.postDelayed(monitorCheck, 1500) }
    }
    private val monitorModeObserver = object : android.database.ContentObserver(main) {
        override fun onChange(selfChange: Boolean) { main.removeCallbacks(monitorCheck); main.postDelayed(monitorCheck, 1500) }
    }

    /**
     * Moonlight/Artemis está transmitiendo. La vista del video se reporta como un `View` genérico, así que se mira
     * la forma: transmitiendo, su ventana activa son apenas un marco y unas pocas vistas (probado: FrameLayout con
     * View, View y el TextView de las estadísticas); en sus listas de PCs y apps hay grillas, imágenes y botones.
     * Solo nombres de clase, no el contenido.
     */
    private fun limelightStreaming(): Boolean {
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return streaming
        // Transmitiendo hay un TextView (las estadísticas de Artemis): ese no cuenta.
        val ui = listOf("Button", "ListView", "GridView", "RecyclerView", "ImageView", "EditText", "ScrollView")
        val queue = ArrayDeque(listOf(root))
        var count = 0
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (++count > 8) return false
            val cls = n.className?.toString().orEmpty()
            if (ui.any { cls.endsWith(it) }) return false
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.addLast(it) }
        }
        return true
    }

    /**
     * Al arrancar el servicio (al actualizar la app, por ejemplo) no llega ningún "cambio de app" hasta cambiar de
     * verdad: la app al frente se lee de la lista de ventanas (para el panel, las capturas en un launcher, etc.).
     */
    private fun initForeground() {
        if (lastPackage != null) return
        val apps = runCatching { windows }.getOrDefault(emptyList()).filter { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION }
        val w = apps.firstOrNull { it.isFocused } ?: apps.firstOrNull { it.isActive } ?: return
        val pkg = runCatching { w.root?.packageName?.toString() }.getOrNull() ?: return
        if (isTransient(pkg)) return
        lastPackage = pkg
        appWindowId = w.id
        if (pkg in AutoRules.STREAMING) { streaming = !pkg.startsWith("com.limelight") || limelightStreaming(); updatePadWatch() }
        Log.d(TAG, "app al frente al arrancar: $pkg${if (streaming) " (transmitiendo)" else ""}")
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
        if (returnPending && ruleApp == null && mode != null) showReturn()
    }

    private val inputListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(id: Int) = changed()
        override fun onInputDeviceRemoved(id: Int) = changed()
        override fun onInputDeviceChanged(id: Int) = changed()
        private fun changed() {
            ModeTileService.refreshAll(this@AutoModeService)
            updateTouchpad()
            updatePadWatch()
            // La primera vez que el control aparece: se pregunta (con su casilla) si quiere que la app se abra sola.
            if (::openAsk.isInitialized && G9Usb.findDevice(this@AutoModeService) != null) openAsk.askOnce()
            if (ModeSwitcher.detect() == PadMode.ANDROID) returnPending = false
            reconnectController()
            guide.update()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Lang.load(this)
        MouseRules.migrateFromGestures(this)   // botones del ratón que estaban guardados como gestos
        guide = ModeGuide(this)
        panel = GamePanel(this)
        perfHud = PerfHud(this).also { it.update() }
        openAsk = OpenAsk(this)
        Shell.init(this)
        ZuiBattery.init(this)
        ZuiGame.initPerf(this)
        updateTouchpad()
        updateMouse()
        getSharedPreferences("g9", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefsListener)
        getSystemService(InputManager::class.java).registerInputDeviceListener(inputListener, main)
        getSystemService(android.hardware.display.DisplayManager::class.java).registerDisplayListener(displayListener, main)
        contentResolver.registerContentObserver(Settings.System.getUriFor(Monitor.MODE_KEY), false, monitorModeObserver)
        main.postDelayed(monitorCheck, 2000)   // por si el monitor ya estaba conectado
        main.postDelayed(::initForeground, 1000)
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(AutoRules.ACTION_PREVIEW)
            addAction(AutoRules.ACTION_SWITCH)
            addAction(AutoRules.ACTION_DARKEN)
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
        // Transmitiendo desde el PC: el botón Legion es el guía de allá. En Moonlight/Artemis solo en su pantalla de
        // transmisión (las clases de diálogos o vistas sueltas no cuentan: solo las de la propia app).
        val cls = event.className?.toString().orEmpty()
        val wasStreaming = streaming
        if (pkg !in AutoRules.STREAMING) streaming = false
        else if (!pkg.startsWith("com.limelight")) streaming = true
        else if (cls.startsWith("com.limelight.")) streaming = cls == AutoRules.STREAM_ACTIVITY
        if (streaming != wasStreaming) { Log.d(TAG, "transmitiendo: $streaming ($pkg, $cls)"); updatePadWatch() }
        if (pkg == lastPackage) return
        appInFront = false
        val wasHidden = hiddenForApp
        hiddenForApp = false
        lastPackage = pkg
        // Otra app: el motor de gestos suelta lo que quedaba a medias (un doble toque que esperaba su segundo
        // toque, un mantener que se estaba midiendo). Si no, la primera pulsación en la app nueva se podía
        // comer como continuación de la anterior.
        gestures.reset()
        panel.hide()
        panel.onAppChanged()
        // Bypass automático: otra app, otra decisión ("solo en juegos"). Lo manual sigue valiendo hasta desconectar el
        // cargador. ZUI tarda un momento en decidir si es juego.
        main.removeCallbacks(bypassCheck)
        main.postDelayed(bypassCheck, 3000)
        Log.d(TAG, "app al frente: $pkg (${event.className}), ventana $appWindowId, modo ${ModeSwitcher.detect()}, ZUI ${ZuiGame.sideBar(this)}")
        // Frecuencia de la pantalla elegida para esta app (o la de siempre).
        RefreshRate.applyFor(this, pkg)
        if (!AutoRules.enabled(this)) return
        // Shizuku (para iniciarlo desde el panel): ir y volver no es salir del juego; el aviso de volver a Android
        // queda para cuando se vaya de verdad a otra app.
        if (pkg == Shell.PACKAGE) return

        val target = AutoRules.rules(this)[pkg]
        if (target == null) {
            // Se salió de una app con regla: recordar volver a Android.
            if (ruleApp != null) {
                ruleApp = null
                val mode = ModeSwitcher.detect()
                // Solo si el G9 se usó en esa app (si se jugó con otro control, no tiene sentido).
                if (AutoRules.remindReturn(this) && mode != null && mode != PadMode.ANDROID && lastG9Key >= ruleSince) {
                    returnPending = true
                    // Solo el aviso pequeño de arriba: recuerda volver sin tapar la pantalla.
                    showReturn()
                }
            } else if (wasHidden && returnPending && ModeSwitcher.detect().let { it != null && it != PadMode.ANDROID }) {
                // Se había quitado al abrir G9 Control: vuelve el aviso de volver a Android.
                showReturn()
            }
            return
        }
        ruleApp = pkg
        ruleSince = android.os.SystemClock.uptimeMillis()
        returnPending = false
        if (otherPadInUse) { Log.d(TAG, "regla $target: se está usando otro control, el G9 no se toca"); return }
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

    /** Última vez que se apretó un botón del G9 / de otro control (uptime en ms; 0 = nunca). */
    private var lastG9Key = 0L
    private var lastOtherPad = 0L
    /** Cuándo se abrió la app con regla actual. */
    private var ruleSince = 0L
    /** Lo último que se usó fue otro control: el G9 no se toca (ni cambio, ni guía, ni aviso de volver). */
    private val otherPadInUse get() = lastOtherPad > lastG9Key

    /** Start/Select que se retuvieron mientras estaba la guía; su "soltar" también se retiene. */
    private val held = mutableSetOf<Int>()

    /** Botones del control cuya presión llegó a la app: su soltar tiene que llegar también. */
    private val delivered = mutableSetOf<Int>()

    /**
     * Si la presión de un botón llegó a la app, su soltar llega siempre, aunque mientras tanto se haya abierto el
     * panel o la guía (si no, la app lo cree apretado para siempre: pasaba con el primer stick de L3 + R3).
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val k = event.keyCode
        // Qué control se usó por última vez: con otro control (8BitDo, por ejemplo) el G9 se queda como está.
        if (event.action == KeyEvent.ACTION_DOWN && KeyEvent.isGamepadButton(k)) {
            val now = android.os.SystemClock.uptimeMillis()
            // En X-Input el G9 se llama "Microsoft X-Box 360 pad" (el nombre del driver de Xbox por USB).
            if (esDelG9(event.device)) lastG9Key = now else lastOtherPad = now
        }
        if (event.action == KeyEvent.ACTION_UP && delivered.remove(k)) {
            handleKey(event)   // para que el resto (L3 + R3, la guía) se entere de que se soltó
            return false
        }
        val consumed = handleKey(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && !consumed && KeyEvent.isGamepadButton(k)) delivered += k
        return consumed
    }

    /**
     * ¿Esta entrada viene del G9? En modo Android el mando se presenta como «GameSir Legion Gaming
     * Controller G9…» y en X-Input como «Microsoft X-Box 360 pad» (el nombre del driver de Xbox por USB);
     * es el mismo criterio que ya usa [onKeyEvent] para saber qué control se apretó por última vez. Cualquier
     * otro nombre —o un VID que no es el del G9 (0x3537)— es OTRO control.
     */
    private fun esDelG9(dev: InputDevice?): Boolean {
        val n = dev?.name.orEmpty()
        if (n.contains("G9", true) || n.contains("Legion", true) || n.contains("x-box 360", true) ||
            n.contains("dualsense", true) || n.contains("dualshock", true)) return true
        // El FABRICANTE no basta: GameSir usa el mismo (0x3537) en otros mandos — el Cyclone 2 por Bluetooth
        // llega como 0x3537/0x1052 y así se nos colaba como G9. Se distingue por el PRODUCTO de este mando
        // (los dos PID del filtro USB: 4404 = 0x1134 y 4406 = 0x1136).
        return dev?.vendorId == 0x3537 && (dev.productId == 0x1134 || dev.productId == 0x1136)
    }

    /** Guía apretado en un control ajeno: mantener = Ajustes rápidos; tocar = Inicio del sistema. */
    private var guiaAjenaPendiente = false
    private val guiaAjenaPanel = Runnable { guiaAjenaPendiente = false; panel.show() }

    /**
     * El botón guía de un control que NO es el G9. Según el pad llega como `BUTTON_MODE` (botón de mando) o
     * como la tecla de Inicio; se atiende igual en los dos casos: **mantener 0,6 s abre los Ajustes rápidos**
     * y **tocar hace el Inicio del sistema** (lo que el pad haría solo). Se consume para que Android no lo
     * haga por su cuenta a la vez; devuelve si el evento es nuestro.
     */
    private fun guiaAjena(e: KeyEvent): Boolean {
        val k = e.keyCode
        if (k != KeyEvent.KEYCODE_BUTTON_MODE && k != KeyEvent.KEYCODE_HOME) return false
        when (e.action) {
            KeyEvent.ACTION_DOWN -> if (e.repeatCount == 0) {
                guiaAjenaPendiente = true
                main.removeCallbacks(guiaAjenaPanel)
                main.postDelayed(guiaAjenaPanel, 600)
            }
            KeyEvent.ACTION_UP -> {
                main.removeCallbacks(guiaAjenaPanel)
                if (guiaAjenaPendiente) {
                    guiaAjenaPendiente = false
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        }
        return true
    }

    /**
     * Mientras la guía está en pantalla, Start y Select no llegan al juego (para cambiar de modo sin
     * pausar ni abrir menús). El resto de los botones pasa normal. Devuelve si el evento es nuestro.
     */
    private fun handleKey(event: KeyEvent): Boolean {
        val k = event.keyCode
        // Teclas que no llegan a las apps (encendido, volumen…): se pasan a la prueba de botones.
        KeyBus.listener?.let { l ->
            if (!KeyEvent.isGamepadButton(k) && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val copy = KeyEvent(event)
                main.post { l(copy) }
            }
        }
        if (volumeSwap(event)) return true
        // Imagen congelada (cambiando el rendimiento): moverse sin ver a dónde, no. Los sticks los toma su ventana.
        if (panel.busy && (KeyEvent.isGamepadButton(k) || event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
                event.isFromSource(InputDevice.SOURCE_DPAD))) return true
        if (mouseChord(event)) return true
        if (panel.isOpen && panel.onKey(event)) return true
        // Los botones que se asignan en la pestaña «Ratón» son DEL RATÓN, y con el puntero encendido el mando
        // entero es un ratón: solo esos botones hacen algo y los demás NO llegan a la app ni al juego (el botón
        // guía tampoco, que lo pidió así). Sigue funcionando L3 + R3 (la forma de apagarlo) y el panel si ya
        // estaba abierto. La cruceta también cuenta: si no, movía la selección de la pantalla de inicio por
        // debajo del puntero. Dentro de G9 Control y en la prueba de botones no se toca nada.
        val esDelMando = KeyEvent.isGamepadButton(k) || event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.isFromSource(InputDevice.SOURCE_DPAD) ||
            k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN ||
            k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT
        // OTRO control (no el G9): de la app SOLO funciona el botón guía —mantenerlo abre los Ajustes
        // rápidos— y TODO lo demás de ese control pasa tal cual a la app: no se le comen botones, gestos ni
        // teclas. Lo propio del G9 (traseros por firma, ratón, reglas por app) no se le aplica a un pad ajeno.
        // Se decide por el DISPOSITIVO (no por la tecla): el guía de un pad ajeno puede llegar como botón de
        // mando o como la tecla de Inicio (medido en la tablet: GameSir-Cyclone 2 = KEY_HOMEPAGE -> Inicio).
        val srcAjeno = event.device?.sources ?: 0
        if (!esDelG9(event.device) &&
            srcAjeno and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK) != 0 &&
            !appInFront && !KeyBus.testing
        ) {
            if (guiaAjena(event)) return true
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0)
                Log.d(TAG, "otro control (${event.device?.name}): pasa tal cual (solo el botón guía es nuestro)")
            return false
        }
        if (mouse.isOn && esDelMando && !appInFront && !KeyBus.testing) {
            val act = MouseRules.buttons(this)[k]
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                // Deja rastro en el log para poder diagnosticar: si una acción del ratón «no hace nada», este
                // renglón dice si el botón llegó como tecla y qué tenía asignado (o «nada»).
                Log.d(TAG, "ratón: botón $k -> ${act ?: "nada"}")
            }
            when {
                event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 ->
                    if (act == GestureAction.MOUSE_DRAG) mouse.rememberOrigin() else act?.let { runMouseAction(it) }
                event.action == KeyEvent.ACTION_UP && act == GestureAction.MOUSE_DRAG -> runMouseAction(act)
            }
            return true
        }
        // Con el puntero APAGADO un botón del ratón se comporta como si no tuviera nada asignado: sigue siendo
        // el botón de siempre (A abre lo que esté enfocado, B es Atrás, LB/RB/LT/RT sus gestos, la cruceta
        // navega). Comérselos aquí fue el «me rompiste el resto de los botones».
        if (legionChord(event)) return true
        if (legionKey(event)) return true
        // Firmas de los traseros: la secuencia corta con la que el control dice qué trasero se apretó. Se vigila
        // si algún trasero trae su marca EN EL MANDO (o tiene reglas en «Todas las apps» / en la app al frente),
        // y si no, Vista/Menú pasan tal cual sin esperar nada.
        // Solo el G9 firma sus traseros con Vista/Menú: con OTRO control esos dos botones NO se vigilan. La
        // marca guardada de sesiones anteriores no basta — manda el control conectado, o un pad ajeno se queda
        // con Select/Start retenidos ~90 ms y devueltos por inyección (su «Select y Start no funcionan»).
        if (!appInFront && !KeyBus.testing && k in SIG_KEYCODES && esDelG9(event.device)) {
            // La marca (la macro corta) vive en el MANDO: si algún trasero la trae, sus Vista/Menú se vigilan
            // SIEMPRE, tenga o no reglas esa app. Con el filtro viejo («hay alguna regla de trasero») bastaba
            // con tener reglas solo en una app para que en las demás el detector se apartara y la firma llegara
            // cruda a la app: los Vista/Menú hacían lo que tuvieran asignado en cualquier app.
            val firmaEnMando = getSharedPreferences("g9", android.content.Context.MODE_PRIVATE)
                .getStringSet("paddle_sig", emptySet()).orEmpty().isNotEmpty()
            val hayTrasero = firmaEnMando ||
                Gestures.rulesFor(this, lastPackage).keys.any { it in PADDLE_KEYCODE.values }
            // Salida por app: si en ESTA app has configurado Vista o Menú (aunque sea «Nada»), esos botones son
            // suyos y llegan tal cual (en un juego las teclas reinyectadas no las ve: Eden no veía Menú). A
            // cambio, en esa app no se reconocen los traseros, porque el mando los firma justo con esos botones.
            val losUsaLaApp = SIG_KEYCODES.any { Gestures.appRules(this, lastPackage.orEmpty()).containsKey(it) }
            if (hayTrasero && !losUsaLaApp && signatures.onKey(k, event.action == KeyEvent.ACTION_DOWN)) return true
        }
        // Gestos (tocar / mantener / doble): no mientras G9 Control está al frente (ahí navega la app) ni
        // durante la prueba de botones: el botón probado tiene que llegar tal cual.
        // El paquete que creemos al frente puede ir un paso atrás justo al entrar a una app: el aviso del
        // sistema llega un poco después que la primera pulsación. Antes solo se le preguntaba a la ventana
        // activa cuando el paquete viejo NO tenía regla para ese botón — y si sí la tenía (la del launcher,
        // por ejemplo) se ejecutaba ESA regla y la app recién abierta nunca recibía el botón: exactamente el
        // «no reconoce la presión sencilla recién entrando a la app». Ahora se pregunta siempre, espaciado
        // 200 ms para no pagarlo en cada evento.
        var gpkg = lastPackage
        if (!appInFront && !KeyBus.testing) {
            val ahora = android.os.SystemClock.uptimeMillis()
            if (ahora - pkgCheckedAt > 200) {
                pkgCheckedAt = ahora
                val fresh = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
                if (!fresh.isNullOrEmpty() && !isTransient(fresh) && fresh != gpkg) {
                    Log.d(TAG, "paquete al frente al día: ${gpkg ?: "?"} -> $fresh")
                    lastPackage = fresh
                    gpkg = fresh
                }
            }
        }
        // El botón de «arrastrar»: se apunta dónde estaba el puntero al apretarlo, para saber de dónde sale.
        if (!appInFront && !KeyBus.testing && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && mouse.isOn) {
            val mr = Gestures.rulesFor(this, gpkg)[k]
            if (mr != null && (mr.tap == GestureAction.MOUSE_DRAG || mr.long == GestureAction.MOUSE_DRAG || mr.dbl == GestureAction.MOUSE_DRAG))
                mouse.rememberOrigin()
        }
        if (!appInFront && !KeyBus.testing && gestures.onKey(this, gpkg, k, event.action == KeyEvent.ACTION_DOWN, event.repeatCount) { act, kc, pkg, combo -> runGestureAction(act, kc, pkg, combo) }) return true
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

    /**
     * Volumen invertido en los botones de la tablet: el servicio se queda con la tecla y sube o baja al revés,
     * como lo haría Android (el volumen que corresponda y su barra). Solo los botones físicos de la tablet (no
     * externos ni virtuales): los del control y las teclas inyectadas quedan igual. La captura con encendido +
     * bajar volumen la procesa Android antes y sigue con el botón físico de siempre.
     */
    private fun volumeSwap(e: KeyEvent): Boolean {
        val k = e.keyCode
        if (k != KeyEvent.KEYCODE_VOLUME_UP && k != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (!AutoRules.swapVolume(this)) return false
        val dev = e.device ?: return false
        if (dev.isExternal || dev.isVirtual) return false
        when (e.action) {
            KeyEvent.ACTION_DOWN -> if (e.repeatCount == 0) {
                volKey = k
                volDir = if (k == KeyEvent.KEYCODE_VOLUME_UP) AudioManager.ADJUST_LOWER else AudioManager.ADJUST_RAISE
                adjustVolume(volDir)
                volSteps = 0
                main.removeCallbacks(volRepeat)
                main.postDelayed(volRepeat, 400)
            }
            // Solo el soltar del botón que se está repitiendo: al pasar rápido de uno al otro, el soltar del primero
            // puede llegar después de apretar el segundo, y cortaba su repetición.
            KeyEvent.ACTION_UP -> if (k == volKey) { main.removeCallbacks(volRepeat); volKey = 0 }
        }
        return true
    }

    /** El botón de volumen que se está manteniendo (0 = ninguno). */
    private var volKey = 0
    private var volDir = 0
    private var volSteps = 0
    /**
     * Mantener el botón: el servicio recibe la tecla antes de que Android genere las repeticiones (solo ve apretar y
     * soltar), así que las hace él, con los tiempos de Android: 400 ms y después un paso cada 80 ms. Como mucho
     * 40 pasos (más de lo que hay de 0 a 100 %): si el soltar no llega (pantalla apagada, la captura se lo quedó),
     * no sigue para siempre.
     */
    private val volRepeat: Runnable = object : Runnable {
        override fun run() {
            if (++volSteps > 40) return
            adjustVolume(volDir)
            main.postDelayed(this, 80)
        }
    }

    private fun adjustVolume(dir: Int) = getSystemService(AudioManager::class.java)
        .adjustSuggestedStreamVolume(dir, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)

    /**
     * Ejecuta la acción de un gesto. `kc` es la tecla elegida cuando la acción es [GestureAction.KEY] y
     * `pkg` la app cuando es [GestureAction.OPEN_APP].
     */
    private fun runGestureAction(a: GestureAction, kc: Int = 0, pkg: String = "", combo: List<Int> = emptyList()) {
        when (a) {
            GestureAction.NONE -> {}
            GestureAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            GestureAction.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            GestureAction.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            GestureAction.NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            GestureAction.VOL_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
            GestureAction.VOL_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
            GestureAction.PLAY_PAUSE -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            GestureAction.MEDIA_NEXT -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            GestureAction.MEDIA_PREV -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            // Adelantar / retroceder: un deslizamiento corto, del tamaño elegido en «Gestos» → «Tiempos».
            GestureAction.MEDIA_FF -> seekVideo(1)
            GestureAction.MEDIA_REW -> seekVideo(-1)
            // Doble toque: para los reproductores que no entienden un deslizamiento (mitad derecha / izquierda).
            GestureAction.MEDIA_FF_DBL -> seekVideoDouble(1)
            GestureAction.MEDIA_REW_DBL -> seekVideoDouble(-1)
            GestureAction.MEDIA_STOP -> mediaKey(KeyEvent.KEYCODE_MEDIA_STOP)
            GestureAction.MUTE -> SystemLevels.toggleMute(this)
            GestureAction.BRIGHT_UP -> SystemLevels.stepBrightness(this, 1)
            GestureAction.BRIGHT_DOWN -> SystemLevels.stepBrightness(this, -1)
            GestureAction.SCREENSHOT -> if (this::panel.isInitialized) panel.capture()
            // Ajustes rápidos: el mismo panel que se abre manteniendo el botón guía o L3 + R3.
            GestureAction.PANEL -> if (this::panel.isInitialized) panel.show()
            // Abrir una app: con Shizuku se abre por el shell (siempre puede); sin él se prueba el camino
            // normal, que Android solo deja si el sistema lo permite estando en segundo plano.
            GestureAction.OPEN_APP -> openApp(pkg)
            // Cajón de apps: primero el intent del launcher y, si no lo atiende, inicio + deslizamiento.
            GestureAction.APP_DRAWER -> openAppDrawer()
            // Páginas del launcher: tampoco hay acción; el launcher cambia de página con un deslizamiento
            // horizontal, así que se hace ese deslizamiento.
            GestureAction.PAGE_NEXT -> swipe(0.86f, 0.5f, 0.12f, 0.5f)
            GestureAction.PAGE_PREV -> swipe(0.14f, 0.5f, 0.88f, 0.5f)
            // Ratón: sus botones se asignan en la pestaña «Ratón» y solo valen con el puntero encendido (los
            // atiende [runMouseAction] antes de llegar aquí). Antes se disparaban como gestos y ENCENDÍAN el
            // puntero solos: apretar el botón del clic lo sacaba en pantalla sin pedirlo.
            GestureAction.MOUSE_CLICK, GestureAction.MOUSE_RIGHT, GestureAction.MOUSE_DOUBLE, GestureAction.MOUSE_DRAG,
            GestureAction.WHEEL_UP, GestureAction.WHEEL_DOWN -> {}
            GestureAction.MOUSE_TOGGLE -> turnMouseOff()
            // Secuencia de teclas (macro corta): se manda tecla a tecla, con una pausa para que el juego la
            // vea como pulsaciones separadas. Va con origen de mando, que es el que atienden los juegos.
            GestureAction.COMBO -> if (combo.isNotEmpty()) scope.launch {
                for ((i, c) in combo.withIndex()) {
                    if (i > 0) kotlinx.coroutines.delay(70)
                    val out = Shell.run(Shell.keyEventPad(c))
                    if (out == null || out.contains("rror", true) || out.contains("sage", true)) Shell.run(Shell.keyEvent(c))
                }
            }
            // Tecla de teclado: el mando no las envía, así que la inyecta Shizuku (`input keyevent`).
            GestureAction.KEY -> if (kc != 0) scope.launch {
                if (Shell.run(Shell.keyEvent(kc)) == null) {
                    Log.w(TAG, "no se pudo enviar la tecla $kc")
                    panel.message(tr("Enviar teclas necesita Shizuku iniciado.", "Sending keys needs Shizuku running."))
                }
            }
        }
    }

    /**
     * Un trasero reconocido por su firma. Se resuelve igual que un botón: sus reglas se guardan por app con el
     * código interno del trasero ([PADDLE_KEYCODE]). Sin regla para la app al frente no hace nada (los botones
     * de la firma ya se consumieron).
     */
    private fun runPaddle(back: BackKey) {
        val code = PADDLE_KEYCODE[back] ?: return
        Log.d(TAG, "firma de ${back.label} en ${lastPackage ?: "?"} (regla=${Gestures.rulesFor(this, lastPackage)[code] ?: "SIN REGLA"})")
        // Entra al mismo motor de gestos que los demás botones: así «Un toque» y «Doble toque» funcionan
        // igual que en un botón normal (antes solo corría el toque). El mando manda una firma corta
        // (~25 ms), así que no hay forma de saber cuánto la mantuviste: el «Mantener» de un trasero no se
        // distingue (se avisa en la página de gestos).
        gestures.onPress(this, lastPackage, code) { act, kc, pkg, combo -> runGestureAction(act, kc, pkg, combo) }
    }

    /** Abre una app asignada a un gesto. Sin Shizuku se prueba el camino normal y, si falla, se avisa. */
    private fun openApp(pkg: String) {
        if (pkg.isEmpty()) {
            if (this::panel.isInitialized) panel.message(tr("Elige la app en «Gestos».", "Choose the app in “Gestures”."))
            return
        }
        val intent = packageManager.getLaunchIntentForPackage(pkg) ?: run {
            if (this::panel.isInitialized) panel.message(tr("No encuentro esa app.", "I can't find that app."))
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val component = intent.component
        if (Shell.state == Shell.State.READY && component != null) {
            scope.launch { if (Shell.run(Shell.amStart(component.flattenToShortString())) == null) openAppDirect(intent) }
            return
        }
        openAppDirect(intent)
    }

    private fun openAppDirect(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure {
            Log.w(TAG, "no se pudo abrir la app", it)
            if (this::panel.isInitialized) panel.message(
                tr("El sistema no dejó abrir la app (con Shizuku iniciado sí se abre).",
                    "The system didn't let the app open (with Shizuku running it does).")
            )
        }
    }

    /**
     * Deslizamiento de un dedo por la pantalla, en tanto por uno (0..1): el cajón de apps y las páginas.
     * `ms` es la duración del gesto: para el cajón se acepta lo mínimo el sistema necesita para reconocer
     * el toque (el launcher de ZUI tarda en dibujarse) y por eso el cajón no se siente lento.
     */
    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long = 260): Boolean {
        val (sw, sh) = pantallaDeGesto()
        val path = Path().apply {
            moveTo(x1 * sw, y1 * sh)
            lineTo(x2 * sw, y2 * sh)
        }
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, ms)).build()
        // Si el gesto anterior todavía no terminó, el nuevo se despacha igual (Android no permite cancelarlo).
        // Android NO tiene ninguna forma de cancelar un gesto ya despachado: la referencia oficial de
        // AccessibilityService solo lista dispatchGesture (no existe cancelGesture), así que «cancelar el
        // anterior» era imposible. Se despacha igual y el sistema resuelve si dos se pisan; el indicador
        // se queda para no apilar el del cajón.
        if (gestureRunning) Log.d(TAG, "swipe: ya había uno en marcha")
        val ok = dispatchGesture(g, null, null)
        if (!ok) {
            // Android rechaza el gesto si ya hay otro en marcha (o el servicio no está listo). Antes se
            // ignoraba el resultado y un scroll podía quedarse en nada sin que nadie se enterara.
            Log.w(TAG, "el gesto NO se pudo despachar (había otro en marcha o el servicio no está listo)")
            return false
        }
        gestureRunning = true
        main.postDelayed({ gestureRunning = false }, ms + 80)
        return true
    }

    /**
     * Adelantar / retroceder video. Los reproductores que se manejan con el dedo (NextPlayer, VLC…) entienden
     * un deslizamiento horizontal como «buscar», y el salto es proporcional a lo que arrastras: por eso
     * «Página siguiente/anterior» (que recorre media pantalla) avanzaba muchísimo. Aquí se usa el tamaño
     * elegido en «Gestos» → «Tiempos» → «Deslizamiento» (8 % por defecto: un salto corto).
     */
    private fun seekVideo(dir: Int) {
        val pct = Gestures.swipePct(this).coerceIn(2, 30) / 100f
        if (dir > 0) swipe(0.5f - pct / 2, 0.5f, 0.5f + pct / 2, 0.5f, ms = 120)
        else swipe(0.5f + pct / 2, 0.5f, 0.5f - pct / 2, 0.5f, ms = 120)
    }

    /**
     * Adelantar / retroceder video con DOBLE TOQUE: para los reproductores que no entienden un deslizamiento,
     * dos toques en la mitad derecha (adelantar) o izquierda (retroceder) es lo que sí reconocen.
     * Un «toque» es una ruta de longitud cero (la referencia de Android de `GestureDescription` dice que una
     * ruta sin recorrido es un toque que no se mueve) y se mandan en DOS gestos separados 150 ms, que es lo
     * que hace un dedo en un doble toque de verdad.
     */
    private fun seekVideoDouble(dir: Int) {
        val x = if (dir > 0) 0.75f else 0.25f
        if (!tapAt(x, 0.5f)) return
        main.postDelayed({ tapAt(x, 0.5f) }, 150)
    }

    /**
     * Tamaño de la pantalla para los gestos, en el MISMO espacio en el que el sistema recibe los toques.
     * Antes se usaban los recursos de la app y en esta tableta eso daba 1904x3040 con la pantalla en horizontal
     * (medido el 16-10-2026): el deslizamiento del cajón caía FUERA de la pantalla y no hacía nada.
     * Primero manda la ventana al frente (sus límites SON ese espacio); si no, lo que diga el sistema.
     */
    private fun pantallaDeGesto(): Pair<Int, Int> {
        runCatching {
            val r = android.graphics.Rect()
            rootInActiveWindow?.getBoundsInScreen(r)
            if (r.width() > 0 && r.height() > 0) return r.width() to r.height()
        }
        val p = android.graphics.Point()
        runCatching {
            val wm = getSystemService(WindowManager::class.java)
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val b = wm.currentWindowMetrics.bounds
                p.x = b.width(); p.y = b.height()
            } else {
                @Suppress("DEPRECATION") wm.defaultDisplay.getRealSize(p)
            }
        }
        if (p.x > 0 && p.y > 0) return p.x to p.y
        val m = resources.displayMetrics
        return m.widthPixels to m.heightPixels
    }

    /** Un toque en un punto de la pantalla (tanto por uno del ancho y del alto). */
    private fun tapAt(x: Float, y: Float, ms: Long = 40): Boolean {
        val (sw, sh) = pantallaDeGesto()
        return tapPx(x * sw, y * sh, ms)
    }

    /** Un toque en un punto exacto de la pantalla (en píxeles, como los ve el sistema). */
    private fun tapPx(px: Float, py: Float, ms: Long = 40): Boolean {
        val path = Path().apply { moveTo(px, py) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, ms)).build()
        val ok = dispatchGesture(g, null, null)
        if (!ok) Log.w(TAG, "el toque NO se pudo despachar (había otro gesto en marcha o el servicio no está listo)")
        return ok
    }

    /** Tecla multimedia (reproducir, siguiente, anterior, detener…): el sistema la lleva al que esté sonando. */
    private fun mediaKey(code: Int) {
        val am = getSystemService(AudioManager::class.java)
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    /** Cuándo se le preguntó por última vez a la ventana activa de qué app se trata (se espacia 200 ms). */
    private var pkgCheckedAt = 0L

    /** Se pidió el cajón y todavía no se ha deslizado (0 = nada pendiente). */
    private var cajonPedido = 0L

    private fun openAppDrawer() {
        // Comprobado en la tableta: `com.zui.launcher/.drawer.DrawerLauncher` es la MISMA ventana del launcher.
        // Arrancarla lo trae al frente, pero deja la pantalla de INICIO: el cajón es una página dentro de esa
        // actividad, y Android no tiene ninguna acción para abrirlo (por eso el intent público
        // `android.intent.action.ALL_APPS` tampoco sirve en este launcher). El único camino que abre el cajón
        // de verdad es el del dedo: ir al inicio y deslizar hacia arriba.
        main.removeCallbacks(cajonDeslizar)
        cajonPedido = android.os.SystemClock.uptimeMillis()
        cajonIntento = 0
        if (esLauncherAlFrente()) {
            // Ya en la pantalla de inicio (o en el propio cajón): el deslizamiento sale en el acto — así el
            // cajón no se siente lento. Si no abre, [revisarCajon] lo mide y reintenta con Inicio.
            // NO se manda «Inicio» por adelantado: la premisa «Inicio vuelve a la página principal» no la pude
            // confirmar con medición (medido en la tableta, con el cajón abierto no hace nada más que abrir la
            // página donde estabas), así que mandarlo solo agregaba retraso.
            Log.d(TAG, "cajón de apps: ya en el launcher -> deslizamiento inmediato")
            main.post(cajonDeslizar)
        } else {
            Log.d(TAG, "cajón de apps: inicio + deslizamiento")
            performGlobalAction(GLOBAL_ACTION_HOME)
            // Antes se esperaban 180 ms «a ojo» y a veces el launcher no había terminado: el deslizamiento caía
            // en el vacío (te quedabas en el inicio) o tardaba. Ahora se pregunta hasta que el launcher esté de
            // verdad al frente (cada 60 ms, hasta 1 s) y se desliza en ese momento.
            esperarLauncherYDeslizar(0)
        }
    }

    /** Insiste hasta que el launcher esté al frente (o se acabe el tiempo) y entonces desliza. */
    private fun esperarLauncherYDeslizar(intento: Int) {
        if (cajonPedido == 0L) return
        if (esLauncherAlFrente()) {
            Log.d(TAG, "cajón de apps: launcher al frente tras ${intento * 60} ms")
            main.post(cajonDeslizar)
        } else if (intento < 16) {
            main.postDelayed({ esperarLauncherYDeslizar(intento + 1) }, 60)
        } else {
            // Se acabó el tiempo y no hay launcher: mejor no deslizar a ciegas encima de otra app.
            Log.w(TAG, "cajón de apps: el launcher no llegó al frente; no se desliza")
            cajonPedido = 0L
        }
    }

    /** ¿Está el launcher al frente ahora mismo? Se le pregunta a la ventana activa, no al último aviso. */
    private fun esLauncherAlFrente(): Boolean {
        val p = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull() ?: return false
        return esLauncher(p)
    }

    /** Si ese paquete es una de las pantallas de inicio que declara el sistema. */
    private fun esLauncher(pkg: String) = runCatching {
        packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
        ).any { it.activityInfo.packageName == pkg }
    }.getOrDefault(pkg == "com.zui.launcher")

    /** Cuántos deslizamientos llevamos en esta petición del cajón (máximo [CAJON_INTENTOS]). */
    private var cajonIntento = 0

    /** Desliza para abrir el cajón y después comprueba si de verdad abrió (ver [revisarCajon]). */
    private val cajonDeslizar = Runnable {
        if (cajonPedido == 0L) return@Runnable
        cajonIntento++
        // 1) El camino bueno: el botón que el PROPIO launcher publica («Todas las apps»). Medido en la tableta
        //    el 16-10-2026: pulsarlo abre el cajón al instante, sin deslizar y desde cualquier página de inicio.
        if (!pulsarTodasLasApps()) {
            // 2) Respaldo: deslizar hacia arriba con el tamaño de pantalla REAL (ver [pantallaDeGesto]).
            val (sw, sh) = pantallaDeGesto()
            Log.d(TAG, "cajón de apps: deslizo (intento $cajonIntento) con pantalla ${sw}x$sh")
            // 120 ms: mínimo que ZUI reconoce como swipe y no como toque.
            swipe(0.5f, 0.72f, 0.5f, 0.30f, ms = 120)
        } else {
            Log.d(TAG, "cajón de apps: intento $cajonIntento por el botón del launcher")
        }
        main.postDelayed({ revisarCajon() }, 700)
    }

    /** Nombres con los que el launcher publica su propio botón del cajón (en esta tableta: «Todas las apps»). */
    private val ETIQUETAS_CAJON = setOf("todas las apps", "todas las aplicaciones", "all apps", "all applications")

    /**
     * Pulsa el botón del cajón que trae el launcher en su dock (nodo de accesibilidad «Todas las apps»).
     * Es la vía que el propio launcher ofrece, no un gesto inventado: medido en la tableta, abre el cajón al
     * instante. Si el sistema no deja pulsar el nodo, se toca justo donde está. Devuelve si lo encontró.
     */
    private fun pulsarTodasLasApps(): Boolean {
        val raiz = runCatching { rootInActiveWindow }.getOrNull() ?: return false
        val pila = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
        pila.add(raiz)
        var vistos = 0
        while (pila.isNotEmpty() && vistos < 500) {
            val n = pila.removeFirst()
            vistos++
            val etiquetas = listOf(n.text, n.contentDescription).map { it?.toString()?.lowercase().orEmpty() }
            if (n.isClickable && etiquetas.any { it in ETIQUETAS_CAJON }) {
                if (n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) {
                    Log.d(TAG, "cajón de apps: pulsado el botón del launcher (" + (n.contentDescription ?: n.text) + ")")
                    return true
                }
                val r = android.graphics.Rect()
                n.getBoundsInScreen(r)
                Log.d(TAG, "cajón de apps: el nodo no acepta la pulsación; toco en ${r.exactCenterX()},${r.exactCenterY()}")
                return tapPx(r.exactCenterX(), r.exactCenterY())
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { pila.add(it) }
        }
        return false
    }

    /**
     * ¿Está el cajón de apps abierto? Se mira el árbol de la ventana al frente: el cajón es el único sitio
     * del launcher donde aparece la caja de búsqueda («Buscar apps») y, si no se encuentra (otro idioma), se
     * usa el otro dato medido: el cajón lista TODAS las apps, así que tiene muchas más etiquetas que una
     * página de inicio (medido en la tableta: 31 frente a 6-11).
     */
    private fun cajonAbierto(): Boolean {
        val raiz = runCatching { rootInActiveWindow }.getOrNull() ?: return false
        val pila = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
        pila.add(raiz)
        var etiquetas = 0
        var vistos = 0
        while (pila.isNotEmpty() && vistos < 500) {
            val n = pila.removeFirst()
            vistos++
            val t = n.text?.toString()?.lowercase().orEmpty()
            val d = n.contentDescription?.toString()?.lowercase().orEmpty()
            if (t.contains("buscar apps") || t.contains("search apps") || d.contains("buscar apps") ||
                d.contains("search apps") || t == "buscar" || d == "buscar"
            ) return true
            if (t.isNotEmpty()) etiquetas++
            for (i in 0 until n.childCount) n.getChild(i)?.let { pila.add(it) }
        }
        return etiquetas > 15
    }

    /**
     * Comprueba el cajón y, si no abrió, lo reintenta UNA vez volviendo a Inicio. Es el seguro contra los
     * casos que no se pueden prever: si el deslizamiento se pierde en la animación del launcher, si la
     * página no era la de casa o si el cajón ya estaba abierto, se mide y se corrige en vez de quedarse en
     * nada (antes, un deslizamiento perdido era «el botón no hizo nada»).
     */
    private fun revisarCajon() {
        if (cajonPedido == 0L) return
        if (cajonAbierto()) {
            Log.d(TAG, "cajón de apps: abierto con el intento $cajonIntento")
            cajonPedido = 0L
            return
        }
        if (cajonIntento >= CAJON_INTENTOS) {
            Log.w(TAG, "cajón de apps: no abrió tras $cajonIntento intento(s)")
            cajonPedido = 0L
            return
        }
        Log.d(TAG, "cajón de apps: no abrió; reintento desde Inicio")
        performGlobalAction(GLOBAL_ACTION_HOME)
        main.postDelayed(cajonDeslizar, 400)
    }

    /** Intentos de abrir el cajón por petición (el segundo es el seguro). */
    private val CAJON_INTENTOS = 2

    /**
     * El mando se duerme solo si no lo usas y, al despertar, la conexión USB ya se había caído: antes había que
     * abrir la app para que se reconectara. Aquí se vuelve a abrir sola en cuanto el control reaparece como
     * dispositivo de entrada (el aviso lo da el escucha de dispositivos, que ya vigilaba si el mando estaba).
     * Necesita el permiso USB guardado («Usar siempre»), que es el que se pide la primera vez.
     */
    private fun reconnectController() {
        if (G9Usb.isOpen()) return
        val d = G9Usb.findDevice(this) ?: return
        if (!G9Usb.hasPermission(this, d)) {
            // Android quita el permiso USB al desconectarse ("only until the device is disconnected") y el
            // permiso automático solo llega si el aviso de conexión encuentra la app: antes, sin permiso, la
            // reconexión se RENDÍA EN SILENCIO y el mando se quedaba fuera hasta abrir la app. Ahora se pide
            // (con su diálogo) y se reintenta; como mucho una petición cada 10 s.
            val ahora = android.os.SystemClock.uptimeMillis()
            if (ahora - ultimaPeticion > 10_000) {
                ultimaPeticion = ahora
                Log.d(TAG, "reconexión sola: falta el permiso USB, se pide")
                G9Usb.askPermission(this, d)
                main.postDelayed({
                    if (!G9Usb.isOpen() && G9Usb.hasPermission(this, d)) reconnectController()
                }, 1500)
            }
            return
        }
        scope.launch {
            kotlinx.coroutines.delay(800)   // el control tarda un momento en quedar listo tras despertar
            if (G9Usb.isOpen()) return@launch
            val err = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { G9Usb.open(this@AutoModeService) }.getOrNull()
            }
            Log.d(TAG, if (err == null) "mando reconectado solo" else "no se pudo reconectar el mando: $err")
        }
    }

    /** Cuándo se pidió por última vez el permiso USB desde aquí (para no repetir el diálogo). */
    private var ultimaPeticion = 0L

    /** Esta pulsación del botón Legion es nuestra (no llega a la app). */
    private var legionOwned = false
    /** Botón Legion apretado y todavía sin decidir si es toque (Atrás) o mantener (panel de juego). */
    private var legionPending = false
    private val legionHold = Runnable {
        if (!legionPending) return@Runnable
        legionPending = false
        panel.show()
    }

    /**
     * Botón Legion (Home del control, llega como BUTTON_MODE en los tres modos): tocar = Atrás, mantener 0,5 s
     * = panel de juego. Dentro de G9 Control llega normal (lo usa la prueba de botones), y en las apps de streaming
     * también ([AutoRules.STREAMING]: ahí es el botón guía del PC o la consola). Llega con algo de retraso: el
     * firmware espera por si es un doble toque o Home + cruceta. Devuelve si el evento es nuestro.
     */
    /** RB o LB apretados junto con Legion: no llegan a la app (ni al bajar ni al soltar). */
    private val chordSwallow = mutableSetOf<Int>()
    /**
     * Ya hubo una combinación con este Legion: mientras siga apretado, RB y LB son combinaciones (no llegan al juego).
     * El firmware del G9 avisa que Legion se soltó en cuanto se aprieta otro botón (aunque siga apretado) y, al
     * soltarlo de verdad, manda un toque corto de Legion (bajar y subir en ~70 ms), que no es Atrás. No hace falta
     * mantener Legion (5 s apaga el control): la sesión sigue 2 s después de la última combinación, y en ese rato
     * RB o LB solos también lo son (varias capturas seguidas).
     */
    private var legionChording = false
    private val chordExpire = Runnable { legionChording = false }

    /**
     * Legion + RB = captura (como Steam + R1 en el Steam Deck); mantener RB 1 s sigue sacando (ráfaga, una por
     * segundo, lo que deja Android) hasta soltarlo, aunque ya se haya soltado Legion (mantenerlo 5 s apaga el G9).
     * Legion + LB = Atrás mantenido (salir del juego en Dolphin; necesita Shizuku). Con Legion apretado y antes de
     * que se abra el panel; así Legion no hace Atrás al soltarlo.
     */
    private fun legionChord(e: KeyEvent): Boolean {
        val k = e.keyCode
        if (k != KeyEvent.KEYCODE_BUTTON_R1 && k != KeyEvent.KEYCODE_BUTTON_L1) return false
        if (e.action == KeyEvent.ACTION_UP) {
            if (k == KeyEvent.KEYCODE_BUTTON_R1) main.removeCallbacks(burst)
            return chordSwallow.remove(k)
        }
        if (e.repeatCount > 0) return k in chordSwallow
        if (!legionChording && !(legionOwned && legionPending)) return false
        legionPending = false
        legionChording = true
        main.removeCallbacks(legionHold)
        main.removeCallbacks(chordExpire)
        main.postDelayed(chordExpire, 2000)
        chordSwallow += k
        if (k == KeyEvent.KEYCODE_BUTTON_R1) {
            Log.d(TAG, "Legion + RB: captura")
            panel.capture()
            main.removeCallbacks(burst)
            if (AutoRules.burstShots(this)) main.postDelayed(burst, 1000)
        } else {
            Log.d(TAG, "Legion + LB: Atrás mantenido")
            scope.launch {
                if (Shell.run(Shell.BACK_LONG) == null) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    panel.message(tr("Mantener Atrás necesita Shizuku: se hizo un Atrás normal.", "Holding Back needs Shizuku: did a normal Back."))
                }
            }
        }
        return true
    }

    /** Ráfaga: RB sigue apretado (desde la combinación con Legion). */
    private val burst: Runnable = object : Runnable {
        override fun run() {
            if (KeyEvent.KEYCODE_BUTTON_R1 !in chordSwallow) return
            panel.capture()
            main.postDelayed(this, 1100)
        }
    }

    private fun legionKey(e: KeyEvent): Boolean {
        if (e.keyCode != KeyEvent.KEYCODE_BUTTON_MODE) return false
        // Prueba de botones: Home tiene que llegar a la app tal cual. Si el servicio se lo queda, se
        // convierte en Atrás/panel antes de que la prueba lo registre (appInFront puede estar desactualizado
        // justo cuando se abre la app). Sale por aquí el bajar y el soltar.
        if (KeyBus.testing) {
            legionOwned = false
            legionPending = false
            main.removeCallbacks(legionHold)
            return false
        }
        // Home con gesto: si le asignaste uno (en «Todas las apps» o en la app que está al frente), el gesto
        // manda y Home deja de ser Atrás/panel. Sin gesto, sigue igual que siempre.
        if (!appInFront && !guide.blocksInput && Gestures.rulesFor(this, lastPackage)[KeyEvent.KEYCODE_BUTTON_MODE]?.any == true) {
            Log.d(TAG, "Home tiene gesto: lo dejo pasar al motor de gestos")
            return false
        }
        // Home configurado en ESTA app (aunque sea «Nada»): es suyo y llega tal cual (en Eden, por ejemplo).
        if (!appInFront && Gestures.appRules(this, lastPackage.orEmpty()).containsKey(KeyEvent.KEYCODE_BUTTON_MODE)) {
            Log.d(TAG, "Home configurado en esta app: llega tal cual")
            return false
        }
        when (e.action) {
            KeyEvent.ACTION_DOWN -> {
                if (e.repeatCount > 0) return legionOwned || legionChording
                // El toque final del firmware al soltar Legion después de una combinación: no es Atrás.
                if (legionChording) return true
                // En Moonlight/Artemis se confirma mirando la pantalla ahora (los avisos de ventana a veces no llegan
                // o un menú de Artemis los cambia): así Legion no hace Atrás transmitiendo.
                if (lastPackage?.startsWith("com.limelight") == true) {
                    val now = limelightStreaming()
                    if (now != streaming) { streaming = now; Log.d(TAG, "transmitiendo (al apretar Legion): $now"); updatePadWatch() }
                }
                legionOwned = AutoRules.legionKeys(this) && !appInFront && !guide.blocksInput && !streaming
                if (legionOwned) { legionPending = true; main.postDelayed(legionHold, 500) }
                return legionOwned
            }
            KeyEvent.ACTION_UP -> {
                if (legionChording) { legionOwned = false; return true }
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
        if (key?.startsWith("mouse_") == true) updateMouse()
        if (key?.startsWith("smart_bypass") == true) evaluateBypass()
        if (key == "charge_limit") evaluateBypass()
        if ((key == "perf_hud" || key == "perf_pos") && ::perfHud.isInitialized) perfHud.update()
        if (key == "monitor_screen") Monitor.woken = false   // la opción recién elegida se ve al tiro
        if (key == "monitor_screen" || key == "monitor_all_169" || key?.startsWith("monitor_fit_") == true) {
            main.removeCallbacks(monitorCheck); main.postDelayed(monitorCheck, 300)
        }
    }

    /**
     * Recibir los eventos de mouse (así llega el touchpad del G9) solo en DS5 y con la opción activa. Android 14
     * y 15 no permiten solo mirarlos: el servicio se los queda, así que un mouse real tampoco funciona en DS5.
     */
    private fun updateTouchpad() {
        if (android.os.Build.VERSION.SDK_INT < 34) return
        val want = AutoRules.touchpadShot(this) && ModeSwitcher.detect() == PadMode.DS5
        if (want == touchpadOn) return
        touchpadOn = want
        applyMotionSources()
        Log.d(TAG, "touchpad del G9: ${if (want) "capturas" else "normal"}")
    }

    /**
     * M dos veces transmitiendo (Artemis/Moonlight en DS5): la app captura el puntero y el clic del touchpad no llega
     * al servicio. Con Shizuku se leen los eventos crudos del touchpad del G9 (`getevent`, solo lectura: la app
     * igual recibe el clic) y cada clic saca la captura. Solo mientras se transmite.
     */
    private var padWatch: Process? = null
    private fun updatePadWatch() {
        val want = streaming && AutoRules.touchpadShot(this) && ModeSwitcher.detect() == PadMode.DS5 && Shell.state == Shell.State.READY
        if (want == (padWatch != null)) return
        if (!want) { padWatch?.destroy(); padWatch = null; Log.d(TAG, "touchpad crudo: no"); return }
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // "add device 13: /dev/input/event12" y después '  name:     "… G9 Touchpad"'.
            val list = Shell.run(Shell.INPUT_LIST) ?: return@launch
            var last: String? = null
            var dev: String? = null
            for (line in list.lines()) {
                if (line.startsWith("add device")) last = line.substringAfter(": ").trim()
                else if ("name:" in line && "Touchpad" in line && ("G9" in line || "Legion" in line)) { dev = last; break }
            }
            val d = dev ?: return@launch
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (padWatch != null || !streaming) return@withContext
                padWatch = Shell.stream(Shell.inputEvents(d)) { l ->
                    if ("BTN_MOUSE" in l && "DOWN" in l) main.post { onPadClick() }
                }
                Log.d(TAG, "touchpad crudo: $d")
            }
        }
    }

    /** Clic del touchpad (M dos veces): captura, como en [onMotionEvent] (uno por toque, no cada evento). */
    private fun onPadClick() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastTouchpad < 700) return
        lastTouchpad = now
        Log.d(TAG, "M dos veces transmitiendo (touchpad crudo): captura")
        panel.capture()
    }

    /** Sticks y cruceta (como ejes) mientras el panel de juego está abierto: lo navegan y no llegan al juego. */
    private var panelSticks = false
    fun setPanelSticks(on: Boolean) {
        if (android.os.Build.VERSION.SDK_INT < 34 || on == panelSticks) return
        panelSticks = on
        applyMotionSources()
    }

    private fun applyMotionSources() {
        if (android.os.Build.VERSION.SDK_INT < 34) return
        val sources = (if (touchpadOn) InputDevice.SOURCE_MOUSE else 0) or
            (if (panelSticks || mouseSticks) InputDevice.SOURCE_JOYSTICK else 0)
        runCatching { serviceInfo = serviceInfo.apply { motionEventSources = sources } }
            .onFailure { Log.w(TAG, "no se pudieron pedir los eventos de movimiento", it) }
    }

    /**
     * Touchpad del G9 en DS5. El control no tiene touchpad: solo llega M dos veces, como un toque corto
     * (que además muestra el puntero). El primer evento tras 1,5 s sin ninguno saca la captura limpia. Solo
     * llegan mientras [updateTouchpad] los pidió.
     */
    override fun onMotionEvent(event: MotionEvent) {
        // Ejes del control: los usa el panel (para navegarlo) o el puntero del ratón (para moverse). Mientras
        // uno de los dos está activo, los ejes son suyos y no llegan al juego.
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            if (panel.isOpen) panel.onStick(event) else if (mouse.isOn) {
                mouse.onStick(event)
                hatMouse(event)
            }
            return
        }
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE)) return
        val now = android.os.SystemClock.uptimeMillis()
        val quiet = now - lastTouchpad > 1500
        lastTouchpad = now
        if (!quiet || !AutoRules.touchpadShot(this)) return
        if (event.device?.name?.contains("g9", true) != true || ModeSwitcher.detect() != PadMode.DS5) return
        Log.d(TAG, "M dos veces en DS5 (touchpad): captura")
        panel.capture()
    }

    /**
     * Dirección de la cruceta que se disparó la última vez. La cruceta de este mando llega como **eje** (HAT),
     * no como teclas (comprobado en la app: la prueba de botones la dibuja como eje, `PageTest`), así que el
     * camino de los botones nunca se enteraba de ella: una acción del ratón asignada a «Arriba/Abajo» (la
     * rueda, por ejemplo) **no hacía nada**. Aquí se traduce el cambio de dirección del HAT a la acción del
     * ratón que tenga asignada esa dirección, una sola vez por pulsación (mientras se mantiene, el valor no
     * cambia; al soltar, vuelve a 0).
     */
    private var hatX = 0f
    private var hatY = 0f

    private fun hatMouse(e: MotionEvent) {
        if (appInFront || KeyBus.testing) return
        val hx = e.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y)
        if (hx == hatX && hy == hatY) return
        hatX = hx
        hatY = hy
        val k = when {
            hy <= -0.5f -> KeyEvent.KEYCODE_DPAD_UP
            hy >= 0.5f -> KeyEvent.KEYCODE_DPAD_DOWN
            hx <= -0.5f -> KeyEvent.KEYCODE_DPAD_LEFT
            hx >= 0.5f -> KeyEvent.KEYCODE_DPAD_RIGHT
            else -> 0
        }
        if (k == 0) return
        val act = MouseRules.buttons(this)[k] ?: return
        Log.d(TAG, "cruceta (HAT $hx/$hy) sobre el código $k -> $act")
        runMouseAction(act)
    }

    /** L3 y R3 apretados ahora mismo, con el momento en que se apretó cada uno. */
    private val sticksDown = mutableMapOf<Int, Long>()
    /** El stick que completó L3 + R3: ni su presión ni su soltar llegan al juego. */
    private var chordKey = -1
    /** Cuánto pueden separarse las dos pulsaciones para que cuenten como combinación (ms). */
    private val chordWindow = 150L
    /** Si el «soltar» de un stick nunca llegó, su marca se olvida pasado este tiempo (ms). */
    private val chordStale = 2500L

    /**
     * L3 + R3: enciende y apaga el puntero del ratón (como en Mantis Gamepad), en cualquier app, en el launcher o
     * en el cajón de apps. El primero de los dos sticks llega al juego como siempre (todavía no se sabe si viene
     * el otro); el segundo se lo queda el ratón. Sin gestos asignados, los sticks no cambian nada más.
     *
     * Las dos teclas tienen que apretarse CASI AL MISMO TIEMPO (ventana de [chordWindow] ms) y una marca vieja
     * se olvida sola ([chordStale]): antes bastaba con que el «soltar» de un stick no llegara nunca para que el
     * estado quedara pegado creyendo que las dos estaban apretadas, y entonces una sola tecla encendía y apagaba.
     */
    /** Teclas de la combinación cuyo «apretar» se dejó pasar a la app: su «soltar» va igual. */
    private val chordPassed = mutableSetOf<Int>()

    private fun mouseChord(e: KeyEvent): Boolean {
        val k = e.keyCode
        if (k != KeyEvent.KEYCODE_BUTTON_THUMBL && k != KeyEvent.KEYCODE_BUTTON_THUMBR) return false
        if (!MouseRules.chord(this)) return false
        val now = android.os.SystemClock.uptimeMillis()
        when (e.action) {
            KeyEvent.ACTION_DOWN -> {
                if (e.repeatCount > 0) return k !in chordPassed
                sticksDown[k] = now
                // Se olvida cualquier marca vieja: así una sola pulsación no puede simular la combinación.
                for (otra in sticksDown.keys.toList()) {
                    if (otra != k && now - (sticksDown[otra] ?: now) > chordStale) sticksDown.remove(otra)
                }
                val otra = sticksDown.keys.firstOrNull { it != k }
                if (otra != null && now - (sticksDown[otra] ?: now) <= chordWindow && !guide.isGuiding) {
                    chordKey = k
                    toggleMouse()
                    return true
                }
                // La PRIMERA tecla de la combinación: no se devuelve a la app si es un botón del ratón (tiene
                // que llegar a [runMouseAction]: si no, el botón de apagarlo quedaba inservible y el puntero no
                // se apagaba nunca en la pantalla de inicio) ni si estamos en la pantalla de inicio (ahí un
                // botón del mando activa el icono enfocado y al encender el ratón se abría el primer icono).
                // En cualquier app y con un botón que no sea del ratón, se devuelve como siempre, para no
                // quitarle a los juegos el clic del stick.
                // Pasa si NO estamos en la pantalla de inicio, o si es un botón del ratón con el puntero
                // encendido (ese botón tiene que llegar a su acción: es el que apaga el ratón, y puede ser
                // cualquiera de los dos sticks o el que el usuario elija).
                val esDelRaton = MouseRules.buttons(this)[k] != null
                val pasar = !esLauncherAlFrente() || (esDelRaton && mouse.isOn)
                if (pasar) chordPassed += k
                return !pasar
            }
            KeyEvent.ACTION_UP -> {
                sticksDown.remove(k)
                if (k == chordKey) { chordKey = -1; return true }
                return chordPassed.remove(k).not()
            }
        }
        return false
    }

    /** Enciende o apaga el puntero (L3 + R3, la pestaña «Ratón» o el mosaico del panel de juego). */
    fun toggleMouse() {
        val want = !MouseRules.on(this)
        MouseRules.setOn(this, want)
        updateMouse()
        Log.d(TAG, if (want) "puntero del ratón encendido" else "puntero del ratón apagado")
        if (this::panel.isInitialized)
            panel.message(if (want) tr("Puntero del ratón encendido", "Mouse pointer on") else tr("Puntero del ratón apagado", "Mouse pointer off"))
    }

    /**
     * Una acción del ratón (los botones que se asignan en la pestaña «Ratón»). Solo se llega aquí con el puntero
     * encendido: apagarlo no enciende nada por su cuenta.
     */
    private fun runMouseAction(a: GestureAction) {
        when (a) {
            GestureAction.MOUSE_RIGHT -> mouse.rightClick()
            GestureAction.MOUSE_DOUBLE -> mouse.doubleClick()
            GestureAction.MOUSE_DRAG -> mouse.drag()
            GestureAction.WHEEL_UP -> mouse.wheel(-1)
            GestureAction.WHEEL_DOWN -> mouse.wheel(1)
            GestureAction.MOUSE_TOGGLE -> turnMouseOff()
            else -> mouse.click()
        }
    }

    /** Apaga el puntero con su botón («Puntero: solo apagar»): encender es L3 + R3 o la pestaña «Ratón». */
    fun turnMouseOff() {
        if (!MouseRules.on(this)) return
        MouseRules.setOn(this, false)
        updateMouse()
        Log.d(TAG, "puntero del ratón apagado con su botón")
        if (this::panel.isInitialized) panel.message(tr("Puntero del ratón apagado", "Mouse pointer off"))
    }

    /** El puntero está encendido: sus sticks los lee la app (Android no deja mirarlos sin quedárselos). */
    private var mouseSticks = false
    private fun updateMouse() {
        val want = MouseRules.on(this)
        if (want != mouseSticks) { mouseSticks = want; applyMotionSources() }
        mouse.sync()
    }

    /**
     * ¿La ventana que avisa es de una app de verdad, o de algo que no cuenta (teclado, sistema, superposición)?
     *
     * [winId] es la ventana que lo pidió, y con ella la decisión es directa: ventana de app = app, cualquier
     * otra cosa (teclado, sistema, superposición) no cuenta.
     *
     * Sin ese dato —normal, porque el aviso suele llegar ANTES de que la ventana esté en la lista— se decide
     * por el paquete, y aquí está el detalle que importaba: un paquete puede ser teclado Y app. Cocoon
     * (`rip.moth.cocoonshell`) declara su propio teclado (`CocoonInputMethodService`) y además es pantalla de
     * inicio; el chequeo de teclado iba primero, así que se descartaban TODOS sus avisos de ventana, la app
     * seguía creyendo que estabas en el launcher y dentro de Cocoon te aplicaba las reglas de «Pantalla de
     * inicio». Por eso ahora se pregunta primero si es una app (lanzable o pantalla de inicio) y solo después
     * se mira si además es teclado.
     */
    private fun isTransient(pkg: String, winId: Int = -1): Boolean {
        if (pkg == packageName || pkg == "com.android.systemui") return true
        if (winId >= 0) {
            val tipo = runCatching { windows.firstOrNull { it.id == winId }?.type }.getOrNull()
            if (tipo != null) {
                return tipo != android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
            }
        }
        val pm = packageManager
        val launchable = pm.getLaunchIntentForPackage(pkg) != null
        val esHome = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
        ).any { it.activityInfo.packageName == pkg }
        if (launchable || esHome) return false
        // Lo que queda (teclados y servicios sin actividad propia) no es una app al frente.
        return true
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
                this, tr("G9 Control Remapper: falta el permiso USB. Abre la app una vez con el control conectado.",
                    "G9 Control Remapper: USB permission missing. Open the app once with the controller connected."),
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
        getSystemService(android.hardware.display.DisplayManager::class.java).unregisterDisplayListener(displayListener)
        runCatching { contentResolver.unregisterContentObserver(monitorModeObserver) }
        getSharedPreferences("g9", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefsListener)
        runCatching { unregisterReceiver(screenReceiver) }
        main.removeCallbacksAndMessages(null)
        // Que la tablet no quede a oscuras si el servicio se apaga con el monitor conectado.
        darkView?.let { v -> runCatching { getSystemService(WindowManager::class.java).removeView(v) } }
        if (panelOff) Thread { kotlinx.coroutines.runBlocking { Shell.run(Shell.screenPower(true)) } }.start()
        if (::guide.isInitialized) guide.hide()
        if (::panel.isInitialized) panel.release()
        if (::perfHud.isInitialized) perfHud.release()
        if (::openAsk.isInitialized) openAsk.hide()
        padWatch?.destroy()
        scope.cancel()
        super.onDestroy()
    }
}
