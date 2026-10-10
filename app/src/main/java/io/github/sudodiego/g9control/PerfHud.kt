package io.github.sudodiego.g9control

import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Monitor de rendimiento sobre el juego: una barra compacta (FPS, CPU, GPU, batería) en la esquina elegida. Se
 * actualiza cada segundo, fuera del hilo principal. Casi todo se lee sin permisos (las reglas de SELinux de las
 * apps dejan leer `gpubusy`, las frecuencias de la CPU y las zonas térmicas, aunque el shell no pueda): CPU = la
 * frecuencia más alta y la temperatura más alta de sus núcleos; GPU = % ocupado y su temperatura más alta. Los FPS
 * de otra app solo los sabe SurfaceFlinger: con Shizuku, de los tiempos de los últimos cuadros de la capa del juego.
 * No recibe toques.
 */
class PerfHud(private val svc: AutoModeService) {
    private val wm = svc.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private var task: ScheduledFuture<*>? = null
    private var view: TextView? = null

    val isOn get() = view != null

    /** Según la opción: encendido o apagado (también al cambiar la posición). */
    fun update() {
        hide()
        if (AutoRules.perfHud(svc)) show()
    }

    private fun show() {
        val dp = svc.resources.displayMetrics.density
        val v = TextView(svc).apply {
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
            background = GradientDrawable().apply { setColor(0xB0101418.toInt()); cornerRadius = 12 * dp }
            text = "…"
        }
        val pos = AutoRules.perfPos(svc)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = when (pos.h) { -1 -> Gravity.START; 1 -> Gravity.END; else -> Gravity.CENTER_HORIZONTAL } or
                when (pos.v) { -1 -> Gravity.TOP; 1 -> Gravity.BOTTOM; else -> Gravity.CENTER_VERTICAL }
            x = (12 * dp).toInt(); y = (12 * dp).toInt()
        }
        if (runCatching { wm.addView(v, lp) }.onFailure { Log.w(AutoModeService.TAG, "monitor de rendimiento", it) }.isFailure) return
        view = v
        task = worker.scheduleWithFixedDelay({ runCatching { sample() }.onFailure { Log.w(AutoModeService.TAG, "monitor: lectura", it) } },
            0, 1, TimeUnit.SECONDS)
    }

    fun hide() {
        task?.cancel(false)
        task = null
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }

    fun release() { hide(); worker.shutdownNow() }

    // ——— Lecturas ———

    private val cpus = (0..15).map { File("/sys/devices/system/cpu/cpu$it/cpufreq/scaling_cur_freq") }.filter { it.exists() }
    private val gpuBusy = File("/sys/class/kgsl/kgsl-3d0/gpubusy")
    /** Zonas térmicas por grupo (se buscan una vez): núcleos de CPU y GPU. */
    private val zones: Map<String, List<File>> by lazy {
        val all = File("/sys/class/thermal").listFiles()?.filter { it.name.startsWith("thermal_zone") }.orEmpty()
        all.mapNotNull { z ->
            val type = runCatching { File(z, "type").readText().trim() }.getOrNull() ?: return@mapNotNull null
            val group = when {
                type.startsWith("cpu-") || type.startsWith("cpuss-") -> "cpu"
                type.startsWith("gpuss-") -> "gpu"
                else -> return@mapNotNull null
            }
            group to File(z, "temp")
        }.groupBy({ it.first }, { it.second })
    }

    private fun readLong(f: File) = runCatching { f.readText().trim().toLong() }.getOrNull()
    private fun maxTemp(group: String) = zones[group]?.mapNotNull { readLong(it) }?.maxOrNull()?.let { (it / 1000).toInt() }

    private fun sample() {
        val cpuGhz = cpus.mapNotNull { readLong(it) }.maxOrNull()?.let { it / 1_000_000.0 }
        val gpu = runCatching {
            val (busy, total) = gpuBusy.readText().trim().split(Regex("\\s+")).map { it.toLong() }
            if (total > 0) (busy * 100 / total).toInt() else 0
        }.getOrNull()
        val bat = svc.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bat?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) }
        val batTemp = bat?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.let { it / 10 }
        val fps = fps()

        val sb = SpannableStringBuilder()
        fun label(s: String) { val st = sb.length; sb.append(s); sb.setSpan(ForegroundColorSpan(ACCENT), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        fun sep() { val st = sb.length; sb.append("  │  "); sb.setSpan(ForegroundColorSpan(MUTED), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        if (fps != null) { sb.append("$fps "); label("FPS"); sep() }
        label("CPU "); sb.append(cpuGhz?.let { "%.1f GHz".format(it) } ?: "—"); maxTemp("cpu")?.let { sb.append("  $it°") }
        sep()
        label("GPU "); sb.append(gpu?.let { "$it %" } ?: "—"); maxTemp("gpu")?.let { sb.append("  $it°") }
        sep()
        label("BAT "); sb.append(level?.let { "$it %" } ?: "—"); batTemp?.let { sb.append("  $it°") }
        main.post { view?.text = sb }
    }

    // ——— FPS (con Shizuku) ———

    /** La capa del juego (se busca de nuevo al cambiar de app o si deja de responder). */
    private var layerApp: String? = null
    private var layer: String? = null
    private var layerAt = 0L

    /** Cuadros presentados en el último segundo por la capa de la app al frente; null sin Shizuku. */
    private fun fps(): Int? {
        if (Shell.state != Shell.State.READY) return null
        val app = svc.foregroundApp ?: return null
        val now = System.nanoTime()
        if (app != layerApp || layer == null || now - layerAt > 10_000_000_000L) {
            layerApp = app
            layerAt = now
            layer = findLayer(app)
        }
        val name = layer ?: return null
        val presented = latency(name) ?: return null
        // La capa ya no existe o no dibuja: se busca otra en la próxima.
        if (presented.isEmpty()) { layer = null; return null }
        return presented.count { it > now - 1_000_000_000L }
    }

    /**
     * La capa que dibuja el juego. En Android 15 `--list` da "RequestedLayerState{[id] nombre parentId=…}": la app
     * tiene varias (la de su actividad, la de los toques, la ventana y, debajo, la que recibe los cuadros; o una
     * SurfaceView). Se prueban las suyas y gana la que tenga el cuadro más reciente.
     */
    private fun findLayer(app: String): String? {
        val out = runBlocking { Shell.run(Shell.SF_LIST) } ?: return null
        val names = out.lines().map { it.trim() }
            .filter { app in it && "ActivityRecord" !in it && "InputSink" !in it && "Surface(name=" !in it && "Background" !in it }
            .map { it.removePrefix("RequestedLayerState{").removeSuffix("}").split(" parentId=", " z=").first().split(' ').last() }
            .distinct().takeLast(8)
        val pick = names.maxByOrNull { lastFrame(it) ?: 0L }?.takeIf { (lastFrame(it) ?: 0L) > 0 }
        Log.d(AutoModeService.TAG, "monitor: capa de $app = $pick (de ${names.size})")
        return pick
    }

    /** Cuándo presentó su último cuadro una capa (ns), o null si no tiene. */
    private fun lastFrame(layer: String): Long? = latency(layer)?.maxOrNull()

    /** Tiempos de presentación de los últimos cuadros de una capa (sin los pendientes). */
    private fun latency(layer: String): List<Long>? {
        val out = runBlocking { Shell.run(Shell.sfLatency(layer)) } ?: return null
        // Primera línea: período de refresco; después "pedido presentado listo" en ns (CLOCK_MONOTONIC, como nanoTime).
        return out.lines().drop(1).mapNotNull { it.trim().split(Regex("\\s+")).getOrNull(1)?.toLongOrNull() }
            .filter { it in 1 until Long.MAX_VALUE }
    }

    private companion object {
        val ACCENT = 0xFF7DD8F5.toInt()
        val MUTED = 0xFF6B7480.toInt()
    }
}
