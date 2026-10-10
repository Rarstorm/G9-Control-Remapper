package io.github.sudodiego.g9control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Una acción del panel de juego. */
private class PanelItem(val label: String, val icon: ImageVector, val run: () -> Unit)

/** Estado que dibuja el panel. */
private class PanelUi {
    var items by mutableStateOf(listOf<PanelItem>())
    var sel by mutableIntStateOf(0)
}

/**
 * Panel de juego: menú flotante que se abre con L3 + R3 desde cualquier app. Se maneja con el control
 * sin quitarle el foco al juego, porque el servicio de accesibilidad se queda con los botones mientras
 * está abierto: LB/RB (o la cruceta, si llega como tecla) mueven, A elige y B cierra. En X-Input y DS5
 * la cruceta llega como eje y el servicio no la ve; por eso LB/RB. También se puede tocar.
 */
class GamePanel(private val svc: AutoModeService) {
    private val wm = svc.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val ui = PanelUi()
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    /** Se cierra solo si no se usa. */
    private val timeout = Runnable { hide() }

    val isOpen get() = view != null

    fun toggle() = if (isOpen) hide() else show()

    fun show() {
        ui.items = items()
        ui.sel = 0
        if (view == null) create()
        poke()
    }

    fun hide() {
        main.removeCallbacks(timeout)
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        owner?.destroy()
        owner = null
    }

    /** Al apagarse el servicio. */
    fun release() {
        hide()
        flash.hide()
        sounds.release()
        io.shutdown()
    }

    /** Ejemplo del aviso de captura (desde Ajustes), con una miniatura de muestra y el sonido elegido. */
    fun previewShot() {
        flash.show(flash.sample())
        sounds.play(AutoRules.shotSound(svc))
    }

    /**
     * Tecla mientras el panel está abierto. Se queda con los botones del control (no llegan al juego);
     * el resto (volumen, encendido) pasa normal.
     */
    fun onKey(e: KeyEvent): Boolean {
        val pad = KeyEvent.isGamepadButton(e.keyCode) || e.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            e.isFromSource(InputDevice.SOURCE_DPAD)
        if (!pad) return false
        if (e.action != KeyEvent.ACTION_DOWN) return true
        poke()
        when (e.keyCode) {
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> move(-1)
            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN -> move(1)
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER ->
                if (e.repeatCount == 0) ui.items.getOrNull(ui.sel)?.run?.invoke()
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> if (e.repeatCount == 0) hide()
        }
        return true
    }

    private fun move(d: Int) {
        val n = ui.items.size
        if (n > 0) ui.sel = (ui.sel + d + n) % n
    }

    private fun poke() {
        main.removeCallbacks(timeout)
        main.postDelayed(timeout, 10_000)
    }

    /** Acciones según lo que hay ahora: el panel de ZUI solo en modo juego, los modos según el actual. */
    private fun items(): List<PanelItem> = buildList {
        // La captura toma solo la ventana del juego: no hace falta esperar a que el panel desaparezca.
        add(PanelItem(tr("Captura", "Screenshot"), Icons.Outlined.Screenshot) { hide(); screenshot() })
        if (ZuiGame.sideBar(svc) != null)
            add(PanelItem("Game Assistant", Icons.Outlined.Tune) { afterHide { openZuiPanel() } })
        val mode = ModeSwitcher.detect()
        if (mode != null) PadMode.entries.filter { it != mode }.forEach { m ->
            add(PanelItem(m.label, Icons.Outlined.SportsEsports) { hide(); svc.manualSwitch(m) })
        }
        add(PanelItem("G9 Control", Icons.Outlined.OpenInNew) {
            hide()
            svc.startActivity(Intent(svc, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
    }

    /** Quita el panel y espera a que desaparezca de la pantalla antes de seguir (si no, sale en la captura). */
    private fun afterHide(action: () -> Unit) {
        hide()
        main.postDelayed(action, 300)
    }

    /** Captura limpia desde fuera del panel (M dos veces en DS5). */
    fun capture() = screenshot()

    /** Guarda las capturas fuera del hilo principal: codificar un PNG de 2560 × 1600 tarda unos cientos de ms. */
    private val io = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val flash = ShotFlash(svc)
    private val sounds = ShotSounds()

    /**
     * Captura solo la ventana del juego (Android 14+): sin el puntero del mouse ni superposiciones (Game
     * Assistant, este panel). Si Android no la entrega, la captura normal de toda la pantalla.
     */
    private fun screenshot() {
        // La ventana con el foco ahora (la del evento puede ser una pasajera que ya no existe).
        val id = focusedAppWindow().takeIf { it >= 0 } ?: svc.appWindowId
        if (Build.VERSION.SDK_INT >= 34 && id >= 0) {
            Log.d(AutoModeService.TAG, "captura de la ventana $id (del evento: ${svc.appWindowId})")
            svc.takeScreenshotOfWindow(id, io, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                    val saved = runCatching {
                        val bmp = try {
                            val hw = Bitmap.wrapHardwareBuffer(r.hardwareBuffer, r.colorSpace) ?: error("sin imagen")
                            hw.copy(Bitmap.Config.ARGB_8888, false).also { hw.recycle() } ?: error("sin copia")
                        } finally {
                            r.hardwareBuffer.close()
                        }
                        // El aviso (con su miniatura) apenas hay imagen; guardar el PNG sigue en segundo plano.
                        val thumb = flash.thumbnail(bmp)
                        main.post { flash.show(thumb); sounds.play(AutoRules.shotSound(svc)) }
                        try { saveScreenshot(bmp) } finally { bmp.recycle() }
                    }
                    saved.onFailure {
                        Log.w(AutoModeService.TAG, "no se guardó la captura", it)
                        main.post { flash.hide(); toast(tr("No se pudo guardar la captura.", "Couldn't save the screenshot.")) }
                    }
                }
                override fun onFailure(code: Int) {
                    Log.w(AutoModeService.TAG, "captura de la ventana $id falló (código $code)")
                    main.post { fullScreenshot() }
                }
            })
            return
        }
        Log.w(AutoModeService.TAG, "sin ventana para capturar (id $id)")
        fullScreenshot()
    }

    /** Ventana de app con el foco (o activa), de la lista de ventanas; solo su número, no su contenido. */
    private fun focusedAppWindow(): Int {
        val all = runCatching { svc.windows }.getOrDefault(emptyList())
        Log.d(AutoModeService.TAG, "ventanas: " + all.joinToString { "${it.id}/t${it.type}/f${if (it.isFocused) 1 else 0}a${if (it.isActive) 1 else 0}" })
        val apps = all.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        return (apps.firstOrNull { it.isFocused } ?: apps.firstOrNull { it.isActive } ?: apps.firstOrNull())?.id ?: -1
    }

    private fun fullScreenshot() {
        if (!svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)) toast(tr("No se pudo sacar la captura.", "Couldn't take the screenshot."))
    }

    /** Guarda la captura en Pictures/Screenshots, junto a las del sistema. */
    private fun saveScreenshot(bmp: Bitmap) {
        val name = "Screenshot_" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val cr = svc.contentResolver
        val uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("MediaStore no creó el archivo")
        try {
            cr.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cr.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            cr.delete(uri, null, null)
            throw e
        }
    }

    /**
     * Abre el panel de Game Assistant: el mismo deslizamiento que con el dedo, desde su pestaña lateral
     * hacia el centro (Game Assistant lo pide de al menos 100 px y en menos de 500 ms).
     */
    private fun openZuiPanel() {
        val bar = ZuiGame.sideBar(svc) ?: return toast(tr("Game Assistant no está activo en esta app.", "Game Assistant isn't active in this app."))
        val w = screenWidth()
        val x0 = if (bar.right) w - 8f else 8f
        val x1 = if (bar.right) x0 - 320f else x0 + 320f
        val path = Path().apply { moveTo(x0, bar.centerY); lineTo(x1, bar.centerY) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 120)).build()
        val sent = svc.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCancelled(g: GestureDescription?) = toast(tr("No se pudo abrir Game Assistant.", "Couldn't open Game Assistant."))
        }, main)
        if (!sent) toast(tr("No se pudo abrir Game Assistant.", "Couldn't open Game Assistant."))
    }

    private fun screenWidth(): Int =
        if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.width()
        else Point().also { @Suppress("DEPRECATION") wm.defaultDisplay.getRealSize(it) }.x

    private fun toast(text: String) = Toast.makeText(svc, text, Toast.LENGTH_SHORT).show()

    private fun create() {
        val o = OverlayOwner().also { it.start() }
        val cv = ComposeView(svc).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setContent { PanelCard(ui) { i -> ui.sel = i; ui.items.getOrNull(i)?.run?.invoke() } }
        }
        // Del tamaño del menú, al centro. No toma el foco (el juego lo conserva); tocar fuera lo cierra.
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        cv.setOnTouchListener { _, ev -> if (ev.action == MotionEvent.ACTION_OUTSIDE) { hide(); true } else false }
        runCatching { wm.addView(cv, lp) }.onSuccess { view = cv; owner = o }.onFailure { o.destroy() }
    }
}

private val PANEL_BG = Color(0xEE14171D)
private val TILE = Color(0xFF1F242C)
private val PANEL_ACCENT = Color(0xFF3DD6FF)
private val PANEL_MUTED = Color(0xFF8D97A5)

@Composable
private fun PanelCard(ui: PanelUi, onPick: (Int) -> Unit) {
    Surface(color = PANEL_BG, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ui.items.forEachIndexed { i, item ->
                    val on = i == ui.sel
                    Surface(
                        color = TILE, shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(2.dp, if (on) PANEL_ACCENT else Color.Transparent),
                        modifier = Modifier.size(width = 112.dp, height = 96.dp).clip(RoundedCornerShape(16.dp)).clickable { onPick(i) }
                    ) {
                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(item.icon, null, Modifier.size(30.dp), tint = if (on) PANEL_ACCENT else Color.White)
                            Spacer(Modifier.height(8.dp))
                            Text(item.label, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(tr("LB / RB mover · A elegir · B cerrar", "LB / RB move · A select · B close"), color = PANEL_MUTED, fontSize = 12.sp)
        }
    }
}

/**
 * Aviso de captura, como el de la Switch: una tarjeta con la miniatura y «Captura guardada» que entra desde
 * el borde (por defecto arriba a la izquierda; se elige en Ajustes), se queda 2 s y vuelve a salir. No recibe
 * toques ni foco y no sale en la captura (que toma solo la ventana del juego). Con Views simples: es liviano.
 */
private class ShotFlash(private val svc: AccessibilityService) {
    private val wm = svc.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val dp = svc.resources.displayMetrics.density
    private var frame: android.widget.FrameLayout? = null
    private val slideOut = Runnable { leave() }

    /** Miniatura de la captura (56 dp de alto). Se puede llamar desde cualquier hilo. */
    fun thumbnail(bmp: Bitmap): Bitmap {
        val h = (56 * dp).toInt()
        return Bitmap.createScaledBitmap(bmp, (bmp.width * h / bmp.height).coerceAtLeast(1), h, true)
    }

    fun show(thumb: Bitmap) {
        hide()
        val px = { v: Int -> (v * dp).toInt() }
        val image = android.widget.ImageView(svc).apply {
            setImageBitmap(thumb)
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(v: android.view.View, o: android.graphics.Outline) =
                    o.setRoundRect(0, 0, v.width, v.height, 8 * dp)
            }
            clipToOutline = true
        }
        val text = android.widget.TextView(svc).apply {
            text = tr("Captura guardada", "Screenshot saved")
            setTextColor(android.graphics.Color.WHITE)
            textSize = 15f
            setPadding(px(14), 0, px(6), 0)
        }
        val card = android.widget.LinearLayout(svc).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(8), px(8), px(16), px(8))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xEE14171D.toInt()); cornerRadius = 16 * dp
            }
            addView(image, android.widget.LinearLayout.LayoutParams(thumb.width, thumb.height))
            addView(text)
            visibility = android.view.View.INVISIBLE
        }
        // La ventana toca el borde de la pantalla (el margen va adentro): la tarjeta entra desde ese borde y lo
        // que queda fuera se recorta. En una esquina o a un lado entra de costado; arriba o abajo al centro, en
        // vertical; en el centro aparece sin moverse.
        val pos = AutoRules.shotPos(svc)
        val f = android.widget.FrameLayout(svc).apply { setPadding(px(16), px(16), px(16), px(16)); addView(card) }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = when (pos.h) { -1 -> Gravity.START; 1 -> Gravity.END; else -> Gravity.CENTER_HORIZONTAL } or
                when (pos.v) { -1 -> Gravity.TOP; 1 -> Gravity.BOTTOM; else -> Gravity.CENTER_VERTICAL }
        }
        if (runCatching { wm.addView(f, lp) }.isFailure) return
        frame = f
        shown = Shown(pos, AutoRules.shotAnim(svc))
        // Al primer dibujo (ya medida) se pone en su estado oculto y entra.
        card.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                card.viewTreeObserver.removeOnPreDrawListener(this)
                val out = outState(card, f)
                card.visibility = android.view.View.VISIBLE
                if (out != null) {
                    card.translationX = out.x; card.translationY = out.y
                    card.alpha = out.alpha; card.scaleX = out.scale; card.scaleY = out.scale
                    card.animate().translationX(0f).translationY(0f).alpha(1f).scaleX(1f).scaleY(1f).setDuration(220)
                        .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                }
                return true
            }
        })
        main.postDelayed(slideOut, 2200)
    }

    /** Posición y animación con que se mostró (se leen al mostrar: si cambian en Ajustes, sale igual que entró). */
    private class Shown(val pos: NoticePos, val anim: NoticeAnim)
    private var shown: Shown? = null

    /** Estado de la tarjeta antes de entrar y al salir. */
    private class Out(val x: Float = 0f, val y: Float = 0f, val alpha: Float = 1f, val scale: Float = 1f)

    /** Miniatura de muestra para la vista previa: un degradado del tamaño de una captura. */
    fun sample(): Bitmap {
        val h = (56 * dp).toInt()
        val w = h * 16 / 10
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            android.graphics.Canvas(it).drawPaint(android.graphics.Paint().apply {
                shader = android.graphics.LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
                    0xFF1F242C.toInt(), 0xFF3DD6FF.toInt(), android.graphics.Shader.TileMode.CLAMP)
            })
        }
    }

    /**
     * Deslizar: fuera del borde más cercano (en el centro, sin borde, aparece como Suave). Suave: transparente
     * y un poco más chica, en su lugar. Sin animación: null (aparece y desaparece de golpe).
     */
    private fun outState(card: android.view.View, f: android.view.View): Out? {
        val s = shown ?: return null
        val soft = Out(alpha = 0f, scale = 0.92f)
        return when (s.anim) {
            NoticeAnim.NONE -> null
            NoticeAnim.SMOOTH -> soft
            NoticeAnim.SLIDE -> when {
                s.pos.h == -1 -> Out(x = -(card.width + f.paddingLeft).toFloat())
                s.pos.h == 1 -> Out(x = (card.width + f.paddingRight).toFloat())
                s.pos.v == -1 -> Out(y = -(card.height + f.paddingTop).toFloat())
                s.pos.v == 1 -> Out(y = (card.height + f.paddingBottom).toFloat())
                else -> soft
            }
        }
    }

    private fun leave() {
        val f = frame ?: return
        val card = f.getChildAt(0)
        val out = outState(card, f) ?: return hide()
        card.animate().translationX(out.x).translationY(out.y).alpha(out.alpha).scaleX(out.scale).scaleY(out.scale)
            .setDuration(180).setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction { if (frame === f) hide() }.start()
    }

    fun hide() {
        main.removeCallbacks(slideOut)
        frame?.let { it.getChildAt(0).animate().cancel(); runCatching { wm.removeView(it) } }
        frame = null
        shown = null
    }
}

/**
 * Sonido de la captura, generado al vuelo (sin archivos de audio): un obturador (dos chasquidos) o un jingle
 * corto de dos notas. Los dos van por el volumen multimedia, el mismo del juego (el obturador del sistema,
 * `MediaActionSound`, usa el volumen del sistema y en esta tablet no se oye).
 */
private class ShotSounds {
    private val tracks = mutableMapOf<ShotSound, android.media.AudioTrack>()

    fun play(s: ShotSound) {
        if (s == ShotSound.NONE) return
        runCatching {
            tracks.getOrPut(s) { track(if (s == ShotSound.SHUTTER) shutter() else jingle()) }.run {
                stop(); reloadStaticData(); play()
            }
        }.onFailure { Log.w(AutoModeService.TAG, "no se pudo reproducir el sonido de captura", it) }
    }

    fun release() {
        tracks.values.forEach { it.release() }
        tracks.clear()
    }

    /** Do6 y Sol6 que se solapan apenas, cada uno con un ataque corto y una caída suave. */
    private fun jingle() = pcm(0.36) { t ->
        tone(t, 1046.5, 0.0, 0.045) + tone(t, 1568.0, 0.075, 0.09)
    }

    /** Dos chasquidos (cortina que abre y cierra): ruido suavizado con un golpe grave, el segundo más suave. */
    private fun shutter(): DoubleArray {
        val rnd = java.util.Random(9)
        var low = 0.0
        return pcm(0.14) { t ->
            low += 0.35 * (rnd.nextDouble() * 2 - 1 - low)
            fun click(start: Double, gain: Double, decay: Double): Double {
                val u = t - start
                return if (u < 0) 0.0 else gain * Math.exp(-u / decay) * (low * 1.6 + 0.5 * Math.sin(2 * Math.PI * 190 * u))
            }
            click(0.0, 0.9, 0.008) + click(0.065, 0.6, 0.014)
        }
    }

    private fun tone(t: Double, freq: Double, start: Double, decay: Double): Double {
        val u = t - start
        if (u < 0) return 0.0
        val w = 2 * Math.PI * freq * u
        return minOf(1.0, u / 0.005) * Math.exp(-u / decay) * 0.3 * (Math.sin(w) + 0.25 * Math.sin(2 * w))
    }

    private fun pcm(seconds: Double, f: (Double) -> Double) = DoubleArray((RATE * seconds).toInt()) { f(it.toDouble() / RATE) }

    private fun track(samples: DoubleArray): android.media.AudioTrack {
        val pcm = ShortArray(samples.size) { (samples[it].coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort() }
        return android.media.AudioTrack.Builder()
            .setAudioAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_GAME)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(android.media.AudioFormat.Builder()
                .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(RATE).setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(android.media.AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
            .also { it.write(pcm, 0, pcm.size) }
    }

    private companion object { const val RATE = 44100 }
}
