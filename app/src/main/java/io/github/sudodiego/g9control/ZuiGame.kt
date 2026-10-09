package io.github.sudodiego.g9control

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.provider.Settings

/**
 * Lo que la ROM de la tablet (ZUI) deja ver de su Game Assistant sin permisos especiales.
 *
 * Mientras ZUI está en modo juego, Game Assistant guarda en `Settings.System` dónde está su pestaña
 * lateral (`lado#arriba#abajo`, lado 1 izquierda y 2 derecha, en píxeles). Fuera de un juego vale
 * `0#0#0`. Sirve para saber si ZUI tomó la app como juego y para abrir su panel con un gesto.
 */
object ZuiGame {
    const val PACKAGE = "com.zui.game.service"
    private const val KEY = "game_helper_side_bar"
    private const val SETTINGS_ACTIVITY = "com.zui.ugame.gamesetting.ui.options.OptionActivity"

    /** Pestaña lateral de Game Assistant (solo existe en modo juego). */
    data class SideBar(val right: Boolean, val top: Int, val bottom: Int) {
        val centerY get() = (top + bottom) / 2f
    }

    fun installed(ctx: Context) =
        runCatching { ctx.packageManager.getApplicationInfo(PACKAGE, 0).enabled }.getOrDefault(false)

    /** Pestaña actual, o null si ZUI no está en modo juego (o la clave no se puede leer). */
    fun sideBar(ctx: Context): SideBar? =
        parse(runCatching { Settings.System.getString(ctx.contentResolver, KEY) }.getOrNull())

    fun parse(v: String?): SideBar? {
        val p = v?.split('#')?.mapNotNull { it.trim().toIntOrNull() } ?: return null
        if (p.size != 3 || p[0] !in 1..2 || p[2] <= p[1]) return null
        return SideBar(p[0] == 2, p[1], p[2])
    }

    /** Avisa cada vez que ZUI entra o sale del modo juego. Devuelve el observador para soltarlo después. */
    fun observe(ctx: Context, handler: Handler, onChange: (SideBar?) -> Unit): ContentObserver {
        val o = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) = onChange(sideBar(ctx))
        }
        ctx.contentResolver.registerContentObserver(Settings.System.getUriFor(KEY), false, o)
        return o
    }

    fun unobserve(ctx: Context, o: ContentObserver) = runCatching { ctx.contentResolver.unregisterContentObserver(o) }

    /** Abre los ajustes de Game Assistant (la pantalla está abierta a otras apps). */
    fun openSettings(ctx: Context) = runCatching {
        ctx.startActivity(Intent().setClassName(PACKAGE, SETTINGS_ACTIVITY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}
