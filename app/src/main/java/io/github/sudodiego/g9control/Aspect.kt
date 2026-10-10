package io.github.sudodiego.g9control

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Display
import org.lsposed.hiddenapibypass.HiddenApiBypass
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pantalla en 16:9 (para un monitor externo: la imagen llena el monitor sin bordes; en la tablet quedan franjas
 * negras) o en su 16:10 original. Es lo mismo que `wm size`. Sin Shizuku si la app tiene WRITE_SECURE_SETTINGS
 * (se da una vez con adb y queda); si no, con Shizuku. Android lo recuerda tras reiniciar: se vuelve con el
 * mismo ítem o con `adb shell wm size reset`. Algunas apps que no esperan el cambio de tamaño se cierran
 * (Dolphin): conviene cambiarlo antes de abrirlas.
 */
object Aspect {
    const val GRANT_COMMAND = "adb shell pm grant io.github.sudodiego.g9control android.permission.WRITE_SECURE_SETTINGS"

    private fun display(ctx: Context): Display? =
        ctx.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)

    /** La app puede cambiar el tamaño por su cuenta (sin Shizuku). */
    fun canWrite(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Se puede cambiar ahora (con el permiso o con Shizuku). */
    fun available(ctx: Context) = canWrite(ctx) || Shell.state == Shell.State.READY

    /** La pantalla está en 16:9 ahora. */
    fun is169(ctx: Context): Boolean {
        val p = android.graphics.Point()
        @Suppress("DEPRECATION") display(ctx)?.getRealSize(p) ?: return false
        return abs(max(p.x, p.y).toFloat() / min(p.x, p.y) - 16f / 9f) < 0.02f
    }

    /** Tamaño 16:9 en la orientación natural de la pantalla (la Y700 es vertical: 1440 × 2560). */
    private fun size169(ctx: Context): Pair<Int, Int>? {
        val mode = display(ctx)?.mode ?: return null
        val w = mode.physicalWidth
        val h = mode.physicalHeight
        return if (w < h) (h * 9 / 16) to h else w to (w * 9 / 16)
    }

    /** Pasa de 16:10 a 16:9 o al revés. False si no se pudo. */
    suspend fun toggle(ctx: Context): Boolean {
        val wide = is169(ctx)
        val size = if (wide) null else size169(ctx) ?: return false
        val ok = (canWrite(ctx) && runCatching { setDirect(size) }.onFailure { Log.w(AutoModeService.TAG, "wm size directo falló", it) }.isSuccess) ||
            Shell.run(if (size == null) Shell.WM_RESET else Shell.wmSize(size.first, size.second)) != null
        // Con un monitor en espejo, la elección queda para ese monitor (la próxima vez que se conecte se aplica sola).
        if (ok) Monitor.rememberChoice(ctx, !wide)
        return ok
    }

    /** IWindowManager.setForcedDisplaySize / clearForcedDisplaySize, lo que usa `wm size` (pide WRITE_SECURE_SETTINGS). */
    private fun setDirect(size: Pair<Int, Int>?) {
        if (Build.VERSION.SDK_INT >= 28) HiddenApiBypass.addHiddenApiExemptions("Landroid/os/ServiceManager;", "Landroid/view/IWindowManager")
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, Context.WINDOW_SERVICE) as IBinder
        val wm = Class.forName("android.view.IWindowManager\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
        val int = Int::class.javaPrimitiveType
        if (size == null) wm.javaClass.getMethod("clearForcedDisplaySize", int).invoke(wm, Display.DEFAULT_DISPLAY)
        else wm.javaClass.getMethod("setForcedDisplaySize", int, int, int).invoke(wm, Display.DEFAULT_DISPLAY, size.first, size.second)
    }
}
