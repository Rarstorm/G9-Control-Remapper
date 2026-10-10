package io.github.sudodiego.g9control

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit

/**
 * Comandos de shell con Shizuku (si está corriendo y dio permiso): mostrar u ocultar Game Assistant, leer su
 * registro y, si falta WRITE_SECURE_SETTINGS, `wm size`. Solo los de [allowed]: nada más, aunque se lo pidan.
 *
 * Los corre el propio servidor de Shizuku (`newProcess`), no un servicio aparte (UserService): en ZUI el
 * servicio no arranca si la app de Shizuku no está abierta ("provider is null"; ZUI no deja al shell pedir
 * proveedores de una app sin proceso). Key Mapper usa lo mismo como respaldo. `newProcess` es privado desde
 * la API 13 de Shizuku: se llama por reflexión.
 */
object Shell {
    enum class State { NOT_RUNNING, NO_PERMISSION, READY }

    const val GA_GET = "appops get ${ZuiGame.PACKAGE} SYSTEM_ALERT_WINDOW"
    const val GA_SHOW = "appops set ${ZuiGame.PACKAGE} SYSTEM_ALERT_WINDOW allow"
    const val GA_HIDE = "appops set ${ZuiGame.PACKAGE} SYSTEM_ALERT_WINDOW ignore"
    // Sin -t: logcat aplica -t al registro completo antes de filtrar, y la línea del modo quedaba fuera.
    const val GA_LOG = "logcat -d -s ZuiGameHelper -e writeSavageMode"
    const val WM_RESET = "wm size reset"
    fun wmSize(w: Int, h: Int) = "wm size ${w}x$h"
    private val WM_SIZE = Regex("wm size (reset|\\d{3,4}x\\d{3,4})")
    private fun allowed(cmd: String) = cmd in setOf(GA_GET, GA_SHOW, GA_HIDE, GA_LOG) || WM_SIZE.matches(cmd)

    /** Para Ajustes (Compose lo vuelve a leer al cambiar). */
    var state by mutableStateOf(State.NOT_RUNNING)
        private set

    private const val REQUEST = 9
    private val main = Handler(Looper.getMainLooper())
    private var app: Context? = null

    private val newProcess: Method? by lazy {
        runCatching {
            Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                .apply { isAccessible = true }
        }.onFailure { Log.w(AutoModeService.TAG, "Shizuku sin newProcess", it) }.getOrNull()
    }

    private val onBinder = Shizuku.OnBinderReceivedListener { main.post(::refresh) }
    private val onDead = Shizuku.OnBinderDeadListener { main.post(::refresh) }
    private val onPermission = Shizuku.OnRequestPermissionResultListener { _, _ -> main.post(::refresh) }

    /** Una vez, al arrancar la app o el servicio de accesibilidad (los dos viven en el mismo proceso). */
    fun init(ctx: Context) {
        if (app != null) return
        app = ctx.applicationContext
        Shizuku.addBinderReceivedListenerSticky(onBinder)
        Shizuku.addBinderDeadListener(onDead)
        Shizuku.addRequestPermissionResultListener(onPermission)
        refresh()
    }

    fun refresh() {
        state = when {
            !runCatching { Shizuku.pingBinder() }.getOrDefault(false) -> State.NOT_RUNNING
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> State.NO_PERMISSION
            else -> State.READY
        }
    }

    fun requestPermission() {
        if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) Shizuku.requestPermission(REQUEST)
    }

    /** Corre un comando permitido (fuera del hilo principal) y devuelve su salida. Null si no está listo, no está permitido o falló. */
    suspend fun run(command: String): String? = withContext(Dispatchers.IO) {
        refresh()
        if (state != State.READY || !allowed(command)) return@withContext null
        val m = newProcess ?: return@withContext null
        runCatching {
            val p = m.invoke(null, command.split(' ').toTypedArray(), null, null) as Process
            val out = StringBuilder()
            val reader = Thread { runCatching { p.inputStream.bufferedReader().forEachLine { synchronized(out) { out.appendLine(it) } } } }
            reader.start()
            // waitFor(tiempo) no sirve: pregunta exitValue() mientras corre y el proceso de Shizuku responde con un
            // error ("process hasn't exited") en vez de "todavía no". Se espera con waitFor() en otro hilo.
            var code: Int? = null
            val waiter = Thread { code = runCatching { p.waitFor() }.getOrNull() }
            waiter.start()
            waiter.join(TimeUnit.SECONDS.toMillis(4))
            if (waiter.isAlive) { p.destroy(); error("tardó demasiado") }
            reader.join(500)
            if (code != 0) error("salió con $code")
            synchronized(out) { out.toString() }
        }.onFailure { Log.w(AutoModeService.TAG, "Shizuku: $command falló", it) }.getOrNull()
    }
}
