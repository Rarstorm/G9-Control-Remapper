package io.github.sudodiego.g9control

import android.content.Context
import android.view.InputDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Modos a los que la app puede cambiar el control desde Android. Desde X-Input o DS5 no se puede
 * volver por software (el control ya no expone el canal de configuración): solo con Start + Select.
 */
enum class Target(val label: String) { XINPUT("X-Input"), DS5("DS5") }

object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("g9", Context.MODE_PRIVATE)

    /** Ya se vio la configuración inicial. */
    fun setupDone(ctx: Context) = sp(ctx).getBoolean("setup_done", false)
    fun setSetupDone(ctx: Context) = sp(ctx).edit().putBoolean("setup_done", true).apply()

    /** Abrir la app al conectar el control en modo Android (por defecto no). */
    /** Dibujo del control al centro (arriba, con lo demás debajo) en vez de en una columna. */
    fun theme(ctx: Context) = runCatching { AppTheme.valueOf(sp(ctx).getString("theme", null)!!) }.getOrDefault(AppTheme.SYSTEM)
    fun setTheme(ctx: Context, v: AppTheme) = sp(ctx).edit().putString("theme", v.name).apply()

    /** Dónde va el dibujo del control (Ajustes). Antes era un sí/no "al centro": se migra la preferencia vieja. */
    fun drawPos(ctx: Context) = runCatching { DrawPos.valueOf(sp(ctx).getString("draw_pos", null)!!) }
        .getOrElse { if (sp(ctx).getBoolean("centered", false)) DrawPos.CENTER else DrawPos.LEFT }
    fun setDrawPos(ctx: Context, v: DrawPos) = sp(ctx).edit().putString("draw_pos", v.name).apply()

    /** Pestaña a la que volver en el próximo arranque (importar un respaldo reinicia la app): se usa una vez. */
    fun restoreTo(ctx: Context, sec: Section, page: Page) = sp(ctx).edit()
        .putString("restore_section", sec.name).putString("restore_page", page.name).apply()
    fun takeRestoreTo(ctx: Context): Pair<Section, Page>? {
        val sec = sp(ctx).getString("restore_section", null)?.let { runCatching { Section.valueOf(it) }.getOrNull() }
        val page = sp(ctx).getString("restore_page", null)?.let { runCatching { Page.valueOf(it) }.getOrNull() }
        sp(ctx).edit().remove("restore_section").remove("restore_page").apply()
        return if (sec != null && page != null && page in sec.pages) sec to page else null
    }

    fun openOnConnect(ctx: Context) = sp(ctx).getBoolean("open_on_connect", false)
    fun setOpenOnConnect(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("open_on_connect", v).apply()

    /** Ya se preguntó (una sola vez) si quiere que la app se abra sola al conectar el control. */
    fun openAsked(ctx: Context) = sp(ctx).getBoolean("open_on_connect_asked", false)
    fun setOpenAsked(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("open_on_connect_asked", v).apply()

    /** Herramientas de desarrollo visibles (se activan tocando la versión 5 veces). */
    fun devTools(ctx: Context) = sp(ctx).getBoolean("dev_tools", false)
    fun setDevTools(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("dev_tools", v).apply()

    /** Estilo del dibujo del control. */
    fun skin(ctx: Context) = runCatching { ControllerSkin.valueOf(sp(ctx).getString("skin", null)!!) }
        .getOrDefault(ControllerSkin.CARBON)
    fun setSkin(ctx: Context, v: ControllerSkin) = sp(ctx).edit().putString("skin", v.name).apply()
}

/** Dónde se dibuja el control en las páginas que lo dibujan (se elige en Ajustes). */
enum class DrawPos(private val es: String, private val en: String) {
    LEFT("A la izquierda", "On the left"),
    CENTER("Al centro", "Centered"),
    RIGHT("A la derecha", "On the right");

    val label get() = tr(es, en)
}

/** Cómo se presenta el control a Android en cada modo (nombre del dispositivo de entrada). */
enum class PadMode(val label: String) {
    ANDROID("Android"), XINPUT("X-Input"), DS5("DS5");

    /** Cambio por USB (solo desde Android); null para Android, al que solo se vuelve con Vista + Menú. */
    val target: Target? get() = when (this) { XINPUT -> Target.XINPUT; DS5 -> Target.DS5; ANDROID -> null }
}

object ModeSwitcher {

    data class ModeInfo(val main: Int, val sub: Int)

    /** Modo deducido del nombre con que Android ve el control, o null si no está conectado. */
    fun detect(): PadMode? {
        val names = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it)?.name }
        return when {
            names.any { it.contains("x-box 360", true) || it.contains("xbox 360", true) } -> PadMode.XINPUT
            names.any { it.contains("sony interactive", true) && it.contains("g9", true) } -> PadMode.DS5
            names.any { it.contains("gamesir", true) && it.contains("g9", true) } -> PadMode.ANDROID
            else -> null
        }
    }

    suspend fun readMode(): ModeInfo? = withContext(Dispatchers.IO) {
        val r = G9Usb.transact(Gcm.readMode()) ?: return@withContext null
        if (r.size < 6) return@withContext null
        ModeInfo(r[4].u(), r[5].u())
    }

    /**
     * Cambia el modo del control. Requiere que ya haya permiso USB.
     * Devuelve un texto corto con el resultado.
     */
    suspend fun switchTo(ctx: Context, target: Target): String = withContext(Dispatchers.IO) {
        G9Usb.findDevice(ctx) ?: return@withContext tr("G9 no encontrado (¿está en modo Android?)", "G9 not found (is it in Android mode?)")
        G9Usb.open(ctx)?.let { return@withContext it }
        val id = when (target) {
            Target.XINPUT -> Gcm.MODE_XINPUT
            Target.DS5 -> Gcm.MODE_DS5
        }
        G9Usb.transact(Gcm.setMode(id, 1), expectReply = false)
        G9Usb.close()
        tr("Cambiando a ${target.label}…", "Switching to ${target.label}…")
    }
}
