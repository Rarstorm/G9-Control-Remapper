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
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.EnergySavingsLeaf
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import kotlin.coroutines.resume
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
        inPerfMenu = false
        ui.items = items()
        ui.sel = 0
        if (view == null) create()
        poke()
        // Bypass: igual, lo que se sabe al instante y se confirma en segundo plano (también fuera de los juegos).
        if (Shell.state == Shell.State.READY) scope.launch {
            val before = ZuiBattery.bypassNow
            ZuiBattery.readBypass()
            if (ZuiBattery.bypassNow != before && isOpen && !inPerfMenu) {
                val sel = ui.sel
                ui.items = items()
                ui.sel = sel.coerceAtMost(ui.items.size - 1)
            }
        }
        // Lo que ya se sabe (Game Assistant visible, modo de rendimiento) se usa al instante; en segundo plano se
        // confirma con Shizuku y solo si cambió se corrige. Así los ítems no aparecen ni se mueven un momento después.
        if (ZuiGame.inGame(svc) && Shell.state == Shell.State.READY) scope.launch {
            val before = ZuiGame.hiddenNow
            ZuiGame.hidden()
            if (ZuiGame.hiddenNow != before && isOpen && !inPerfMenu) {
                val sel = ui.sel
                ui.items = items()
                ui.sel = sel.coerceAtMost(ui.items.size - 1)
            }
            val app = svc.foregroundApp
            val mode = ZuiGame.perfOf(ZuiGame.lastPerfLine()) ?: return@launch
            val changed = mode != ZuiGame.knownPerf(app)
            ZuiGame.rememberPerf(app, mode)
            if (changed && isOpen && inPerfMenu) showPerfMenu()
        }
    }

    /** El panel muestra el submenú de rendimiento (no se reemplaza por la lista principal al confirmar algo). */
    private var inPerfMenu = false

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
        scope.cancel()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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
        if (ZuiGame.inGame(svc)) {
            add(PanelItem("Game Assistant", Icons.Outlined.Tune) { afterHide { openGameAssistant() } })
            // Se ve ahora: ocultarlo ya (y que quede oculto), sin esperar a cambiar de app.
            if (gaShownByUs || ZuiGame.hiddenNow == false)
                add(PanelItem(tr("Ocultar Game Assistant", "Hide Game Assistant"), Icons.Outlined.VisibilityOff) {
                    hide()
                    gaShownByUs = false
                    scope.launch {
                        if (!ZuiGame.setHidden(true)) toast(tr("No se pudo ocultar Game Assistant.", "Couldn't hide Game Assistant."))
                        else AutoRules.setGaHidden(svc, true)
                    }
                })
            // Cambiar el modo necesita Shizuku: para mostrar Game Assistant si está oculto y leer su registro.
            if (Shell.state == Shell.State.READY)
                add(PanelItem(tr("Rendimiento", "Performance"), Icons.Outlined.Speed) { showPerfMenu() })
        }
        // Bypass (con Shizuku): con la tablet enchufada, o si ya está activo para poder quitarlo.
        val bypassOn = ZuiBattery.bypassNow == true
        if (Shell.state == Shell.State.READY && (bypassOn || ZuiBattery.plugged(svc)))
            add(PanelItem(if (bypassOn) tr("Bypass ✓", "Bypass ✓") else tr("Bypass", "Bypass"), Icons.Outlined.Power) {
                hide()
                val want = !bypassOn
                scope.launch {
                    if (!ZuiBattery.setBypass(want)) toast(tr("No se pudo cambiar el bypass.", "Couldn't change bypass."))
                    else toast(if (want) tr("Bypass activado: el cargador alimenta la tablet y la batería no carga.", "Bypass on: the charger powers the tablet and the battery doesn't charge.")
                        else tr("Bypass desactivado: la batería vuelve a cargar.", "Bypass off: the battery charges again."))
                }
            })
        if (Aspect.available(svc))
            add(PanelItem(if (Aspect.is169(svc)) tr("Pantalla 16:10", "Screen 16:10") else tr("Pantalla 16:9", "Screen 16:9"), Icons.Outlined.AspectRatio) {
                hide()
                scope.launch { if (!Aspect.toggle(svc)) toast(tr("No se pudo cambiar la pantalla.", "Couldn't change the screen.")) }
            })
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
    private val sounds = ShotSounds(svc)

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

    /** G9 Control mostró Game Assistant (que el usuario tiene oculto) para abrir su panel: se oculta al cambiar de app. */
    private var gaShownByUs = false
    /** Cambiando el modo de rendimiento (son varios gestos seguidos). */
    private var perfBusy = false
    /** Mientras cambia el rendimiento la imagen está congelada: los botones del control no llegan al juego. */
    val busy get() = perfBusy

    /** Si Game Assistant debe quedar oculto. La primera vez se toma de cómo está ahora y se recuerda. */
    private suspend fun keepGaHidden(): Boolean {
        AutoRules.gaHidden(svc)?.let { return it }
        val now = ZuiGame.hidden() ?: return false
        AutoRules.setGaHidden(svc, now)
        return now
    }

    /**
     * Abre el panel de Game Assistant. Si el usuario lo tiene oculto (appops) y Shizuku está listo, primero lo
     * muestra; se vuelve a ocultar al cambiar de app ([onAppChanged]).
     */
    private fun openGameAssistant() {
        if (!ZuiGame.inGame(svc)) return toast(tr("Game Assistant no está activo en esta app.", "Game Assistant isn't active in this app."))
        scope.launch {
            if (keepGaHidden()) {
                // Oculto y sin Shizuku: su panel se abriría invisible y parecería que no pasó nada.
                if (!ZuiGame.setHidden(false))
                    return@launch toast(tr("Game Assistant está oculto y Shizuku no responde.", "Game Assistant is hidden and Shizuku isn't responding."))
                gaShownByUs = true
                delay(400)
            }
            // Con Game Assistant oculto la pestaña a veces no se publica: se espera a que la publique al mostrarlo.
            val bar = ZuiGame.awaitSideBar(svc)
            // Si mientras tanto se volvió a abrir el panel, el deslizamiento caería sobre él: se cancela.
            if (isOpen) return@launch
            if (bar == null || !swipe(bar, open = true)) toast(tr("No se pudo abrir Game Assistant.", "Couldn't open Game Assistant."))
        }
    }

    /**
     * La app al frente cambió: si G9 Control mostró Game Assistant, lo vuelve a ocultar. Si es un juego, se lee ya
     * (en segundo plano) si Game Assistant se ve y en qué modo quedó, para que el panel lo sepa al abrirse.
     */
    fun onAppChanged() {
        if (gaShownByUs && !perfBusy) {
            gaShownByUs = false
            scope.launch { if (keepGaHidden()) ZuiGame.setHidden(true) }
        }
        val app = svc.foregroundApp
        scope.launch {
            delay(2500)   // ZUI decide si es juego y Game Assistant aplica el modo guardado de esa app
            if (svc.foregroundApp != app || !ZuiGame.inGame(svc) || Shell.state != Shell.State.READY) return@launch
            ZuiGame.hidden()
            ZuiGame.rememberPerf(app, ZuiGame.perfOf(ZuiGame.lastPerfLine()))
        }
    }

    /**
     * Submenú de modos de rendimiento. Marca el actual al instante con lo que ya se sabe de esta app (se lee en
     * segundo plano al abrir el panel y se anota al cambiarlo); leerlo del registro de Game Assistant tarda ~1 s.
     * Solo si todavía no se sabe se espera esa lectura.
     */
    private fun showPerfMenu() {
        inPerfMenu = true
        fun build(current: ZuiGame.PerfMode?) = ZuiGame.PerfMode.entries.map { m ->
            val icon = when (m) {
                ZuiGame.PerfMode.SAVER -> Icons.Outlined.EnergySavingsLeaf
                ZuiGame.PerfMode.BALANCED -> Icons.Outlined.Balance
                ZuiGame.PerfMode.BEAST -> Icons.Outlined.Bolt
            }
            PanelItem(if (m == current) "${m.label} ✓" else m.label, icon) { setPerf(m) }
        }
        val app = svc.foregroundApp
        val known = ZuiGame.knownPerf(app)
        ui.items = build(known)
        ui.sel = known?.let { ZuiGame.PerfMode.entries.indexOf(it) } ?: 1
        poke()
        if (known == null) scope.launch {
            val cur = ZuiGame.perfOf(ZuiGame.lastPerfLine()) ?: return@launch
            ZuiGame.rememberPerf(app, cur)
            if (!isOpen || !inPerfMenu) return@launch
            ui.items = build(cur)
            ui.sel = ZuiGame.PerfMode.entries.indexOf(cur)
        }
    }

    /**
     * Cambia el modo de rendimiento como lo haría el dedo, detrás de una imagen congelada del juego (así no se ve
     * Game Assistant): lo muestra si está oculto, abre su panel, toca su botón de modo (que va pasando de uno a
     * otro) hasta que su registro dice el pedido (como mucho tres toques), cierra el panel deslizando hacia el
     * borde y lo vuelve a ocultar.
     */
    private fun setPerf(target: ZuiGame.PerfMode) {
        hide()
        if (!ZuiGame.inGame(svc)) return toast(tr("Game Assistant no está activo en esta app.", "Game Assistant isn't active in this app."))
        if (perfBusy) return
        perfBusy = true
        val cover = FreezeCover(svc)
        scope.launch {
            // Shizuku tiene que responder antes de congelar nada (si no, la imagen quedaba mucho rato).
            if (Shell.run(Shell.GA_GET) == null) {
                perfBusy = false
                toast(tr("Shizuku no responde: revisa que esté iniciado.", "Shizuku isn't responding: check that it's started."))
                return@launch
            }
            var now: ZuiGame.PerfMode? = null
            var hidden = false
            var opened = false
            var bar: ZuiGame.SideBar? = null
            try {
                delay(300)   // que el panel de G9 Control desaparezca antes de congelar la imagen
                cover.show(screenshotDisplay(), target.label)
                // La imagen aparece recién en el siguiente cuadro: si Game Assistant se muestra antes, su pestaña
                // alcanza a verse unos milisegundos.
                delay(200)
                hidden = keepGaHidden()
                // Como mucho unos segundos con la imagen congelada, pase lo que pase.
                withTimeoutOrNull(12_000) {
                    if (hidden && !ZuiGame.setHidden(false)) return@withTimeoutOrNull
                    delay(400)   // que Game Assistant vuelva a dibujar su pestaña
                    // Con Game Assistant oculto la pestaña a veces no se publica: se espera a que la publique.
                    val b = ZuiGame.awaitSideBar(svc) ?: return@withTimeoutOrNull
                    bar = b
                    if (!swipe(b, open = true)) return@withTimeoutOrNull
                    opened = true
                    delay(900)
                    var line = ZuiGame.lastPerfLine()
                    now = ZuiGame.perfOf(line)
                    val (x, y) = ZuiGame.perfButton(b, screenWidth(), screenHeight())
                    var taps = 0
                    while (now != target && taps < 3) {
                        tap(x, y)
                        taps++
                        // Espera a que Game Assistant escriba el modo nuevo (si no, el siguiente toque se pasaría).
                        var tries = 0
                        var next = ZuiGame.lastPerfLine()
                        while (next == line && tries++ < 10) { delay(250); next = ZuiGame.lastPerfLine() }
                        line = next
                        now = ZuiGame.perfOf(line)
                    }
                    Log.d(AutoModeService.TAG, "modo de rendimiento: pedido $target, quedó $now ($taps toques)")
                    ZuiGame.rememberPerf(svc.foregroundApp, now)
                }
                // Solo si se abrió: si no, el deslizamiento le llegaría al juego.
                bar?.let { if (opened) { swipe(it, open = false); delay(400) } }
            } finally {
                // Aunque algo falle a mitad de camino, Game Assistant vuelve a quedar oculto.
                withContext(NonCancellable) { if (hidden && !gaShownByUs) ZuiGame.setHidden(true) }
                cover.done(now == target)
                perfBusy = false
            }
        }
    }

    /** Imagen de toda la pantalla ahora (Android 11+; como mucho una por segundo). Null si no se pudo. */
    private suspend fun screenshotDisplay(): Bitmap? {
        if (Build.VERSION.SDK_INT < 30) return null
        return suspendCancellableCoroutine { c ->
            svc.takeScreenshot(android.view.Display.DEFAULT_DISPLAY, io, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                    val bmp = runCatching {
                        Bitmap.wrapHardwareBuffer(r.hardwareBuffer, r.colorSpace)?.let { hw -> hw.copy(Bitmap.Config.ARGB_8888, false).also { hw.recycle() } }
                    }.getOrNull()
                    r.hardwareBuffer.close()
                    if (c.isActive) c.resume(bmp)
                }
                override fun onFailure(code: Int) { if (c.isActive) c.resume(null) }
            })
        }
    }

    /**
     * Deslizamiento sobre la pestaña lateral: abrir (desde el borde hacia el centro, como pide Game Assistant: al
     * menos 100 px y en menos de 500 ms) o cerrar (hacia el borde).
     */
    private suspend fun swipe(bar: ZuiGame.SideBar, open: Boolean): Boolean {
        val w = screenWidth()
        val edge = if (bar.right) w - 8f else 8f
        val inner = if (bar.right) edge - (if (open) 320f else 450f) else edge + (if (open) 320f else 450f)
        val path = Path().apply {
            if (open) { moveTo(edge, bar.centerY); lineTo(inner, bar.centerY) } else { moveTo(inner, bar.centerY); lineTo(edge, bar.centerY) }
        }
        return gesture(path, if (open) 120 else 150)
    }

    private suspend fun tap(x: Float, y: Float) = gesture(Path().apply { moveTo(x, y) }, 50)

    private suspend fun gesture(path: Path, ms: Long): Boolean = suspendCancellableCoroutine { c ->
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, ms)).build()
        val sent = svc.dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { if (c.isActive) c.resume(true) }
            override fun onCancelled(d: GestureDescription?) { if (c.isActive) c.resume(false) }
        }, main)
        if (!sent && c.isActive) c.resume(false)
    }

    private fun screenWidth(): Int =
        if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.width()
        else Point().also { @Suppress("DEPRECATION") wm.defaultDisplay.getRealSize(it) }.x

    private fun screenHeight(): Int =
        if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.height()
        else Point().also { @Suppress("DEPRECATION") wm.defaultDisplay.getRealSize(it) }.y

    private var noticeView: android.view.View? = null
    private val noticeOff = Runnable { noticeView?.let { runCatching { wm.removeView(it) } }; noticeView = null }

    /**
     * Aviso corto abajo al centro, en una ventana de accesibilidad: un Toast de Android no siempre se ve sobre un
     * juego en ZUI. No recibe toques.
     */
    private fun toast(text: String) {
        main.removeCallbacks(noticeOff)
        noticeOff.run()
        val dp = svc.resources.displayMetrics.density
        val v = android.widget.TextView(svc).apply {
            this.text = text
            setTextColor(android.graphics.Color.WHITE)
            textSize = 14f
            setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply { setColor(0xEE14171D.toInt()); cornerRadius = 16 * dp }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = (48 * dp).toInt() }
        if (runCatching { wm.addView(v, lp) }.isFailure) return
        noticeView = v
        main.postDelayed(noticeOff, 2500)
    }

    private fun create() {
        val o = OverlayOwner().also { it.start() }
        val cv = ComposeView(svc).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setContent {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    // La tarjeta se mide con el ancho que necesite (sin el límite de la ventana) y la ventana toma
                    // ese ancho: así no tapa el juego a los lados (un gesto o un toque ahí cerraba el panel).
                    PanelCard(ui, Modifier.wrapContentWidth(unbounded = true).onSizeChanged { fitWidth(it.width) }) { i ->
                        ui.sel = i; ui.items.getOrNull(i)?.run?.invoke()
                    }
                }
            }
        }
        // Al principio todo el ancho (con WRAP_CONTENT Android la mide con el ancho de un diálogo, ~580 dp, y Compose
        // no pide más: con seis ítems se cortaban); en cuanto se mide la tarjeta, su ancho justo. Al centro. No toma
        // el foco (el juego lo conserva); tocar fuera lo cierra.
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        cv.setOnTouchListener { _, ev -> if (ev.action == MotionEvent.ACTION_OUTSIDE) { hide(); true } else false }
        runCatching { wm.addView(cv, lp) }.onSuccess { view = cv; owner = o; params = lp }.onFailure { o.destroy() }
    }

    private var params: WindowManager.LayoutParams? = null

    /** La ventana del panel toma el ancho de la tarjeta (cambia si aparecen o se van ítems). */
    private fun fitWidth(w: Int) {
        val v = view ?: return
        val lp = params ?: return
        if (w <= 0 || lp.width == w) return
        lp.width = w
        runCatching { wm.updateViewLayout(v, lp) }
    }
}

private val PANEL_BG = Color(0xEE14171D)
private val TILE = Color(0xFF1F242C)
private val PANEL_ACCENT = Color(0xFF3DD6FF)
private val PANEL_MUTED = Color(0xFF8D97A5)

@Composable
private fun PanelCard(ui: PanelUi, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    Surface(color = PANEL_BG, shape = RoundedCornerShape(24.dp), modifier = modifier) {
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
 * Mientras G9 Control cambia el modo de rendimiento: la pantalla congelada (una imagen del juego tomada justo
 * antes, solo en memoria: no se guarda) a pantalla completa. Primero se ve igual que el juego y enseguida se
 * oscurece, con una tarjeta que dice qué pasa; así no se ve el panel de Game Assistant. No recibe toques: los
 * gestos simulados la atraviesan y llegan a Game Assistant (las ventanas de accesibilidad quedan por encima de
 * las suyas). Sin imagen, un fondo negro.
 */
private class FreezeCover(private val svc: AccessibilityService) {
    private val wm = svc.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var view: android.view.View? = null
    private var dim: android.view.View? = null
    private var card: android.view.View? = null
    private var spinner: android.view.View? = null
    private var check: android.view.View? = null
    private var title: android.widget.TextView? = null
    private var subtitle: android.widget.TextView? = null
    private var shot: Bitmap? = null
    private var mode = ""

    fun show(image: Bitmap?, modeLabel: String) {
        mode = modeLabel
        val dp = svc.resources.displayMetrics.density
        val px = { v: Int -> (v * dp).toInt() }
        val bar = android.widget.ProgressBar(svc).apply { isIndeterminate = true }
        // El ✓ ocupa el lugar de la ruedita al terminar: la tarjeta no cambia de tamaño.
        val ok = android.widget.TextView(svc).apply {
            text = "✓"
            setTextColor(0xFF3DD6FF.toInt())
            textSize = 22f
            gravity = Gravity.CENTER
            visibility = android.view.View.INVISIBLE
        }
        val icon = android.widget.FrameLayout(svc).apply {
            addView(bar, android.widget.FrameLayout.LayoutParams(px(28), px(28), Gravity.CENTER))
            addView(ok, android.widget.FrameLayout.LayoutParams(px(28), px(28), Gravity.CENTER))
        }
        val t = android.widget.TextView(svc).apply {
            // El título no cambia en todo el proceso (si cambia al final no se alcanza a leer).
            text = tr("Rendimiento: $modeLabel", "Performance: $modeLabel")
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val s = android.widget.TextView(svc).apply {
            text = tr("La imagen queda quieta un momento", "The picture freezes for a moment")
            setTextColor(0xFF8D97A5.toInt())
            textSize = 13f
        }
        val texts = android.widget.LinearLayout(svc).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(px(14), 0, 0, 0)
            addView(t)
            addView(s)
        }
        val c = android.widget.LinearLayout(svc).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(18), px(14), px(22), px(14))
            background = android.graphics.drawable.GradientDrawable().apply { setColor(0xEE14171D.toInt()); cornerRadius = 16 * dp }
            addView(icon, android.widget.LinearLayout.LayoutParams(px(28), px(28)))
            addView(texts)
            alpha = 0f
        }
        val d = android.view.View(svc).apply { setBackgroundColor(android.graphics.Color.BLACK); alpha = 0f }
        val match = android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        val root = android.widget.FrameLayout(svc).apply {
            if (image != null) addView(android.widget.ImageView(svc).apply {
                setImageBitmap(image)
                scaleType = android.widget.ImageView.ScaleType.FIT_XY
            })
            else setBackgroundColor(android.graphics.Color.BLACK)
            addView(d, android.widget.FrameLayout.LayoutParams(match, match))
            addView(c, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Toma el foco (sin FLAG_NOT_FOCUSABLE): así los sticks y gatillos del control le llegan a ella y no al
            // juego mientras la imagen está congelada (el servicio solo puede filtrar botones, no movimientos). El
            // juego recupera el foco al quitarla. Sigue sin recibir toques: los gestos llegan a Game Assistant.
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        ).apply {
            if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        root.isFocusable = true
        root.isFocusableInTouchMode = true
        root.setOnKeyListener { _, _, _ -> true }
        root.setOnGenericMotionListener { _, _ -> true }
        if (runCatching { wm.addView(root, lp) }.isFailure) { image?.recycle(); return }
        root.requestFocus()
        view = root; dim = d; card = c; spinner = bar; check = ok; title = t; subtitle = s; shot = image
        // Se oscurece y aparece la tarjeta: se nota que es una pausa y no que el juego se colgó.
        d.animate().alpha(0.55f).setDuration(250).start()
        c.animate().alpha(1f).setDuration(250).start()
    }

    /**
     * Resultado: el modo que quedó lo confirma el registro de Game Assistant (no se supone). El título no cambia:
     * la ruedita pasa a ✓ y la línea de abajo dice «Listo». Se ve 1,2 s y la imagen se va con un fundido.
     */
    fun done(ok: Boolean) {
        if (view == null) return
        // Que el cambio de texto no achique la tarjeta.
        (subtitle?.parent as? android.view.View)?.let { it.minimumWidth = it.width }
        spinner?.visibility = android.view.View.INVISIBLE
        if (ok) {
            check?.visibility = android.view.View.VISIBLE
            subtitle?.text = tr("Listo", "Done")
        } else {
            title?.text = tr("No se pudo cambiar el rendimiento", "Couldn't change performance")
            subtitle?.text = tr("Prueba otra vez o usa Game Assistant", "Try again or use Game Assistant")
        }
        main.postDelayed({
            val v = view ?: return@postDelayed
            v.animate().alpha(0f).setDuration(250).withEndAction { hide() }.start()
        }, if (ok) 1200 else 2200)
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        // Sin recycle(): removeView es asíncrono y la ventana todavía se dibuja una vez (se caía la app). Al
        // soltar la referencia, la memoria se libera sola.
        shot = null
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
private class ShotSounds(ctx: android.content.Context) {
    private val tracks = mutableMapOf<ShotSound, android.media.AudioTrack>()
    private val audio = ctx.getSystemService(android.media.AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val attrs = android.media.AudioAttributes.Builder()
        .setUsage(android.media.AudioAttributes.USAGE_GAME)
        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

    /**
     * Foco de audio pasajero que deja bajar a los demás: Android baja el volumen del juego mientras suena (si no,
     * los dos sonidos se mezclaban y no se distinguía) y lo devuelve al soltarlo, como con una notificación.
     */
    private val focus = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs).setWillPauseWhenDucked(false).build()
    private val unfocus = Runnable { audio.abandonAudioFocusRequest(focus) }

    fun play(s: ShotSound) {
        if (s == ShotSound.NONE) return
        runCatching {
            val t = tracks.getOrPut(s) { track(if (s == ShotSound.SHUTTER) shutter() else jingle()) }
            main.removeCallbacks(unfocus)
            audio.requestAudioFocus(focus)
            t.stop(); t.reloadStaticData(); t.play()
            // El sonido dura lo que sus muestras; un poco más para que el juego no vuelva de golpe.
            main.postDelayed(unfocus, (t.bufferSizeInFrames * 1000L / RATE) + 250)
        }.onFailure { Log.w(AutoModeService.TAG, "no se pudo reproducir el sonido de captura", it) }
    }

    fun release() {
        main.removeCallbacks(unfocus)
        audio.abandonAudioFocusRequest(focus)
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
            .setAudioAttributes(attrs)
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
