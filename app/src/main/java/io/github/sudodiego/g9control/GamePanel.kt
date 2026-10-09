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
        io.shutdown()
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
                    // El aviso apenas llega la imagen; guardarla (PNG) sigue en segundo plano.
                    main.post { flash.show() }
                    val saved = runCatching { saveScreenshot(r) }
                    r.hardwareBuffer.close()
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
    @androidx.annotation.RequiresApi(34)
    private fun saveScreenshot(r: AccessibilityService.ScreenshotResult) {
        val hw = Bitmap.wrapHardwareBuffer(r.hardwareBuffer, r.colorSpace) ?: error("sin imagen")
        val bmp = hw.copy(Bitmap.Config.ARGB_8888, false)
        hw.recycle()
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
        } finally {
            bmp.recycle()
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
 * Aviso de captura: una etiqueta pequeña «✓ Captura» abajo a la derecha durante 1 s. No recibe toques ni
 * foco y no sale en la captura (que toma solo la ventana del juego). Con una View simple: es liviano.
 */
private class ShotFlash(private val svc: AccessibilityService) {
    private val wm = svc.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var label: android.widget.TextView? = null
    private val remove = Runnable { hide() }

    fun show() {
        hide()
        val dp = svc.resources.displayMetrics.density
        val v = android.widget.TextView(svc).apply {
            text = tr("✓ Captura", "✓ Screenshot")
            setTextColor(android.graphics.Color.WHITE)
            textSize = 13f
            setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xCC14171D.toInt()); cornerRadius = 12 * dp
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = (20 * dp).toInt(); y = (20 * dp).toInt()
        }
        if (runCatching { wm.addView(v, lp) }.isFailure) return
        label = v
        main.postDelayed(remove, 1000)
    }

    fun hide() {
        main.removeCallbacks(remove)
        label?.let { runCatching { wm.removeView(it) } }
        label = null
    }
}
