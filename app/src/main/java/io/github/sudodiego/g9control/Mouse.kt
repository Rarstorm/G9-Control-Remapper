package io.github.sudodiego.g9control

import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.hypot
import kotlin.math.pow

/**
 * Ajustes de la emulación de ratón.
 *
 * Android no tiene ninguna API para mover el ratón del sistema: la única inyección que existe sin root es
 * `AccessibilityService.dispatchGesture` (toques y deslizamientos, no un puntero). Así que el puntero lo dibuja
 * la app encima de todo (superposición de accesibilidad) y el clic es un toque inyectado justo donde está el
 * puntero: por eso funciona en cualquier sitio — el launcher, el cajón de apps o dentro de una app.
 */
object MouseRules {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("g9", Context.MODE_PRIVATE)

    /** El puntero está encendido ahora. */
    fun on(ctx: Context) = sp(ctx).getBoolean("mouse_on", false)
    fun setOn(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("mouse_on", v).apply()

    /** L3 + R3 enciende y apaga el puntero. */
    fun chord(ctx: Context) = sp(ctx).getBoolean("mouse_chord", true)
    fun setChord(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("mouse_chord", v).apply()

    /**
     * Papel de cada stick (izquierdo y derecho por separado): nada, mover el puntero, o hacer scroll arriba/abajo
     * como la rueda. Con los dos en «puntero» mueve el que muevas; con los dos en «scroll», los dos desplazan.
     */
    fun stickRole(ctx: Context, left: Boolean) =
        sp(ctx).getInt(if (left) "mouse_stick_l" else "mouse_stick_r", if (left) STICK_NONE else STICK_POINTER)
    fun setStickRole(ctx: Context, left: Boolean, v: Int) =
        sp(ctx).edit().putInt(if (left) "mouse_stick_l" else "mouse_stick_r", v).apply()

    /** Papeles de un stick, para [stickRole]. */
    const val STICK_NONE = 0
    const val STICK_POINTER = 1
    const val STICK_SCROLL = 2

    /** Velocidad: píxeles por segundo con el stick a fondo (los «DPI» de un ratón). 200–4000. */
    fun dpi(ctx: Context) = sp(ctx).getInt("mouse_dpi", 1400)
    fun setDpi(ctx: Context, v: Int) = sp(ctx).edit().putInt("mouse_dpi", v).apply()

    /** Suavidad 0–90 %: cuánto se filtra el movimiento (0 = seco, 90 = muy suave). */
    fun smooth(ctx: Context) = sp(ctx).getInt("mouse_smooth", 45)
    fun setSmooth(ctx: Context, v: Int) = sp(ctx).edit().putInt("mouse_smooth", v).apply()

    /** Curva 0–100 %: cuánto acelera al apartar el stick del centro (0 = lineal). */
    fun curve(ctx: Context) = sp(ctx).getInt("mouse_curve", 40)
    fun setCurve(ctx: Context, v: Int) = sp(ctx).edit().putInt("mouse_curve", v).apply()

    /** Zona muerta 0–40 %: cuánto hay que apartar el stick para que empiece a moverse. */
    fun dead(ctx: Context) = sp(ctx).getInt("mouse_dead", 12)
    fun setDead(ctx: Context, v: Int) = sp(ctx).edit().putInt("mouse_dead", v).apply()

    /** Rueda: 0 = deslizamiento (funciona siempre), 1 = rueda de verdad (necesita Shizuku). */
    fun wheelMode(ctx: Context) = sp(ctx).getInt("mouse_wheel", 0)
    fun setWheelMode(ctx: Context, v: Int) = sp(ctx).edit().putInt("mouse_wheel", v).apply()

    /** Cuánto avanza cada paso de rueda (líneas de la rueda, o cuadros del deslizamiento). */
    fun wheelStep(ctx: Context) = sp(ctx).getInt("mouse_wheel_step", 3)
    fun setWheelStep(ctx: Context, v: Int) = sp(ctx).edit().putInt("mouse_wheel_step", v).apply()

    /**
     * Estado en vivo: la app y el servicio viven en el mismo proceso, así que la pestaña «Ratón» y el mosaico
     * del panel lo leen tal cual (sin releer las preferencias).
     */
    var live by mutableStateOf(false)
}

/**
 * Emulación de ratón con el mando.
 *
 * El puntero es una ventana propia (superposición de accesibilidad, la misma que usa el panel) que se dibuja
 * encima de todo y solo se repinta al mover: no recibe toques. El stick elegido lo mueve con velocidad,
 * suavidad, curva y zona muerta configurables; los clics salen como gestos inyectados en la posición del
 * puntero (un toque = clic, un toque largo = menú contextual, dos toques = doble clic, un deslizamiento =
 * arrastrar) y la rueda como deslizamiento corto o con `input roll` por Shizuku.
 *
 * Mientras el puntero está encendido el stick elegido lo lee la app y por eso no llega al juego: es lo que pide
 * Android (`setMotionEventSources`, Android 14+) para poder leer los ejes del control.
 */
class MouseMode(
    private val svc: AutoModeService,
    private val handler: Handler,
    /** Correr un comando de Shizuku sin bloquear (lo pone el servicio). */
    private val shell: (String) -> Unit,
    /** Deslizamiento en fracciones de pantalla, el mismo que usa el resto de la app. */
    private val swipe: (Float, Float, Float, Float) -> Unit,
) {
    private val wm by lazy { svc.getSystemService(WindowManager::class.java) }
    private var view: View? = null

    /** Tamaño de la pantalla y posición del puntero en píxeles. */
    private var width = 0
    private var height = 0
    private var x = 0f
    private var y = 0f

    /** Velocidad suavizada, en píxeles por milisegundo. */
    private var vx = 0f
    private var vy = 0f
    private var lastAt = 0L

    /** Dónde estaba el puntero cuando se apretó el botón de «arrastrar». */
    private var dragFrom: Pair<Float, Float>? = null

    /** Hasta cuándo se dibuja el aro del clic. */
    private var flashUntil = 0L

    val isOn get() = view != null

    // ——— Encendido y apagado ———

    /** Deja el puntero como digan las preferencias (lo llama el servicio al arrancar y al cambiar la opción). */
    fun sync() {
        val want = MouseRules.on(svc)
        if (want && !isOn) on() else if (!want && isOn) off()
        MouseRules.live = isOn
    }

    fun on() {
        if (isOn) return
        if (android.os.Build.VERSION.SDK_INT < 34) {
            Log.w(AutoModeService.TAG, "el puntero necesita Android 14+ (para leer el stick del mando)")
            return
        }
        val m = svc.resources.displayMetrics
        width = m.widthPixels
        height = m.heightPixels
        x = width / 2f
        y = height / 2f
        vx = 0f
        vy = 0f
        lastAt = SystemClock.uptimeMillis()
        val v = CursorView()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        if (runCatching { wm.addView(v, lp) }.onFailure { Log.w(AutoModeService.TAG, "puntero del ratón", it) }.isFailure) return
        view = v
        Log.d(AutoModeService.TAG, "puntero encendido (${width}x$height)")
    }

    fun off() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        dragFrom = null
        MouseRules.live = false
        Log.d(AutoModeService.TAG, "puntero apagado")
    }

    fun release() = off()

    // ——— Movimiento con el stick ———

    /** Posición donde apretaron el botón de «arrastrar» (para saber de dónde sale el arrastre). */
    fun rememberOrigin() {
        if (!isOn) return
        dragFrom = x to y
    }

    /**
     * Un evento de ejes del control (los que llegan por [AutoModeService.onMotionEvent] con origen joystick).
     * Cada stick hace lo que tenga asignado: mover el puntero o hacer scroll arriba/abajo. La zona muerta se mide
     * en radial; la curva estira la respuesta (más aceleración al apartar el stick) y la suavidad la filtra.
     */
    fun onStick(event: MotionEvent) {
        if (!isOn) return
        val dead = MouseRules.dead(svc).coerceIn(0, 40) / 100f
        val lRole = MouseRules.stickRole(svc, true)
        val rRole = MouseRules.stickRole(svc, false)
        val lx = event.getAxisValue(MotionEvent.AXIS_X)
        val ly = event.getAxisValue(MotionEvent.AXIS_Y)
        val rx = event.getAxisValue(MotionEvent.AXIS_Z)
        val ry = event.getAxisValue(MotionEvent.AXIS_RZ)

        // Puntero: si los dos sticks lo mueven, manda el que más se aparte (no se suman: se doblaría la velocidad).
        when {
            lRole == MouseRules.STICK_POINTER && rRole == MouseRules.STICK_POINTER ->
                if (hypot(lx, ly) >= hypot(rx, ry)) movePointer(lx, ly, dead) else movePointer(rx, ry, dead)
            lRole == MouseRules.STICK_POINTER -> movePointer(lx, ly, dead)
            rRole == MouseRules.STICK_POINTER -> movePointer(rx, ry, dead)
        }

        // Scroll: pasos de rueda seguidos mientras el stick esté apartado.
        val now = SystemClock.uptimeMillis()
        if (lRole == MouseRules.STICK_SCROLL) scrollWithStick(ly, dead, now)
        if (rRole == MouseRules.STICK_SCROLL) scrollWithStick(ry, dead, now)
    }

    /** Mueve el puntero con el stick: zona muerta radial, curva, suavidad y velocidad (DPI). */
    private fun movePointer(ax: Float, ay: Float, dead: Float) {
        val draw = hypot(ax, ay)
        if (draw == 0f && vx == 0f && vy == 0f) return
        val curve = MouseRules.curve(svc).coerceIn(0, 100) / 100f
        val smooth = MouseRules.smooth(svc).coerceIn(0, 90) / 100f
        // Píxeles por segundo con el stick a fondo (200–4000, como los DPI de un ratón).
        val maxSpeed = MouseRules.dpi(svc).coerceIn(200, 4000) / 1000f

        var tx = 0f
        var ty = 0f
        if (draw > dead) {
            // 0 en la zona muerta → 1 con el stick a fondo.
            val mag = ((draw - dead) / (1f - dead).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
            // Curva: 0 = lineal; 100 = el doble de progresiva (arranque fino, punta rápida).
            val shaped = mag.pow(1f + 1.5f * curve)
            val speed = shaped * maxSpeed
            tx = ax / draw * speed
            ty = ay / draw * speed
        }
        // Suavidad: mezcla entre la velocidad nueva y la anterior (0 = va directo, 90 = muy filtrado).
        val mix = (1f - smooth).coerceIn(0.1f, 1f)
        vx += (tx - vx) * mix
        vy += (ty - vy) * mix

        val now = SystemClock.uptimeMillis()
        // Entre 4 y 60 ms: ni un salto por un evento tardío, ni una velocidad absurda por uno seguido.
        val dt = (now - lastAt).coerceIn(4L, 60L)
        lastAt = now
        if (vx == 0f && vy == 0f) return
        x = (x + vx * dt).coerceIn(0f, (width - 1).toFloat())
        y = (y + vy * dt).coerceIn(0f, (height - 1).toFloat())
        view?.invalidate()
    }

    /** Hasta cuándo no toca otro paso de scroll por stick (lo comparten los dos sticks). */
    private var nextScrollAt = 0L

    /**
     * Scroll con el stick: el eje vertical manda pasos de rueda seguidos mientras el stick esté apartado, más
     * seguidos cuanto más se aparte (de ~200 ms en el borde de la zona muerta a ~70 ms a fondo). Sale por donde
     * esté el puntero, con el ajuste de rueda de la pestaña «Ratón» (deslizamiento o rueda por Shizuku).
     */
    private fun scrollWithStick(ay: Float, dead: Float, now: Long) {
        val dir = when {
            ay > dead -> 1
            ay < -dead -> -1
            else -> return
        }
        if (now < nextScrollAt) return
        val mag = ((kotlin.math.abs(ay) - dead) / (1f - dead).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
        nextScrollAt = now + (200f - 130f * mag).toLong()
        wheel(dir)
    }

    // ——— Clics, arrastre y rueda ———

    /** Un toque en la posición del puntero: el «clic izquierdo». */
    fun click() = tap(55)

    /** Dos toques seguidos, como el doble clic de un ratón. */
    fun doubleClick() {
        if (!isOn) return
        tap(50)
        handler.postDelayed({ if (isOn) tap(50) }, 90)
    }

    /** Toque largo: en las apps es el menú contextual (el equivalente al clic derecho sin root). */
    fun rightClick() = tap(650)

    /**
     * Arrastrar: desde donde estaba el puntero cuando se apretó el botón hasta donde está ahora. No es un
     * arrastre «en vivo» (un gesto inyectado se manda entero, no se puede ir moviendo a la par del dedo): se
     * resuelve al soltar, como cuando arrastras un icono de un lado a otro.
     */
    fun drag() {
        if (!isOn) return
        val from = dragFrom
        dragFrom = x to y
        if (from == null) return
        val (sx, sy) = from
        val dist = hypot(x - sx, y - sy)
        if (dist < 12f) { tap(55); return }   // sin movimiento: es un clic (no un arrastre de 2 px)
        val ms = (dist / 1.2f).toLong().coerceIn(180L, 900L)
        val path = Path().apply { moveTo(sx, sy); lineTo(x, y) }
        flash()
        Log.d(AutoModeService.TAG, "arrastre de (${sx.toInt()},${sy.toInt()}) a (${x.toInt()},${y.toInt()}) en $ms ms")
        svc.dispatchGesture(
            GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, ms)).build(), null, null
        )
    }

    /** Aro alrededor del puntero al hacer clic: se ve que el toque salió de ahí. */
    private fun flash() {
        flashUntil = SystemClock.uptimeMillis() + 220
        view?.invalidate()
        handler.postDelayed({ view?.invalidate() }, 120)
        handler.postDelayed({ view?.invalidate() }, 240)
    }

    /** Un toque de [ms] milisegundos justo en la posición del puntero. */
    private fun tap(ms: Long) {
        if (!isOn) return
        flash()
        val path = Path().apply { moveTo(x, y); lineTo(x + 1f, y) }
        if (!svc.dispatchGesture(
                GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, ms)).build(), null, null
            )
        ) Log.w(AutoModeService.TAG, "no se pudo inyectar el clic del ratón")
    }

    /**
     * Rueda: [dir] = 1 hacia abajo (bajar en la lista), -1 hacia arriba.
     *  - Deslizamiento (por defecto): un tirón corto en la posición del puntero. Lo entienden todas las apps.
     *  - Rueda de verdad (Shizuku): `input roll` manda el aviso de rueda a la ventana que tiene el foco.
     */
    fun wheel(dir: Int) {
        if (!isOn) return
        val step = MouseRules.wheelStep(svc).coerceIn(1, 10)
        val fx = x / width.coerceAtLeast(1)
        val fy = y / height.coerceAtLeast(1)
        if (MouseRules.wheelMode(svc) == 1 && Shell.state == Shell.State.READY) {
            shell(Shell.wheel(dir * step))
            return
        }
        // Cada paso ≈ 1,2 % del alto; el dedo va al revés que el contenido.
        val half = (0.006f * step).coerceIn(0.02f, 0.12f)
        if (dir > 0) swipe(fx, fy + half, fx, fy - half) else swipe(fx, fy - half, fx, fy + half)
    }

    /**
     * El puntero dibujado: una flecha clásica con contorno oscuro (se ve sobre cualquier fondo) y un aro que
     * se enciende un instante al hacer clic. La ventana es de toda la pantalla y el puntero se dibuja dentro,
     * así mover el ratón es solo repintar (no se mueve ninguna ventana).
     */
    private inner class CursorView : View(svc) {
        private val dp = resources.displayMetrics.density
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.FILL }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCC0B0B0B.toInt(); style = Paint.Style.STROKE; strokeWidth = 1.6f * dp
        }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF7DD8F5.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f * dp
        }
        private val arrow = Path()
        private val h = 20f * dp

        init {
            // Punta en (0,0) y cuerpo hacia abajo a la derecha, como cualquier puntero.
            arrow.moveTo(0f, 0f)
            arrow.lineTo(0f, h)
            arrow.lineTo(0.26f * h, 0.76f * h)
            arrow.lineTo(0.44f * h, 1.06f * h)
            arrow.lineTo(0.60f * h, 0.98f * h)
            arrow.lineTo(0.42f * h, 0.68f * h)
            arrow.lineTo(0.72f * h, 0.62f * h)
            arrow.close()
        }

        override fun onDraw(canvas: Canvas) {
            canvas.save()
            canvas.translate(x, y)
            canvas.drawPath(arrow, fill)
            canvas.drawPath(arrow, line)
            val left = flashUntil - SystemClock.uptimeMillis()
            if (left > 0) {
                val r = (14f + (220 - left) / 6f) * dp
                ring.alpha = (left * 255 / 220).toInt().coerceIn(0, 255)
                canvas.drawCircle(0.3f * h, 0.5f * h, r, ring)
            }
            canvas.restore()
        }
    }
}
