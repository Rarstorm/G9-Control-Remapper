package io.github.sudodiego.g9control

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Íconos de ajustes rápidos "Android", "X-Input" y "DS5" (el ícono es el logo G9). Muestran cuál es el modo actual (encendido)
 * y al tocarlos:
 * - Desde Android a X-Input o DS5: cambia por USB, con la etiqueta del modo (si el servicio está activo).
 * - Desde X-Input o DS5: muestra el aviso pequeño de Vista + Menú hacia ese modo (servicio de
 *   accesibilidad) y cierra el panel para que se vea. Sin el servicio, abre la app, que guía con su franja.
 */
abstract class ModeTileService(private val mode: PadMode) : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        Lang.load(this)
        refresh()
    }

    private fun refresh(note: String? = null) {
        val now = ModeSwitcher.detect()
        val (sub, state) = when {
            now == null -> tr("No detectado", "Not detected") to Tile.STATE_UNAVAILABLE
            now == mode -> tr("Activo", "Active") to Tile.STATE_ACTIVE
            now == PadMode.ANDROID -> tr("Toca para cambiar", "Tap to switch") to Tile.STATE_INACTIVE
            else -> tr("Con Vista + Menú", "With View + Menu") to Tile.STATE_INACTIVE
        }
        val t = qsTile ?: return
        t.label = mode.label
        t.subtitle = note ?: sub
        t.state = state
        t.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val now = ModeSwitcher.detect() ?: return refresh()
        if (now == mode) return refresh()
        val usb = mode.target
        val service = AutoRules.serviceEnabled(this)
        val d = G9Usb.findDevice(this)
        val canUsb = now == PadMode.ANDROID && usb != null && d != null && G9Usb.hasPermission(this, d)
        when {
            // El servicio se encarga: cambio por USB con la etiqueta, o el aviso de Vista + Menú.
            service && (canUsb || now != PadMode.ANDROID) -> {
                AutoRules.requestSwitch(this, mode)
                refresh(if (canUsb) tr("Cambiando…", "Switching…") else tr("Mantén Vista + Menú", "Hold View + Menu"))
            }
            canUsb -> {
                refresh(tr("Cambiando…", "Switching…"))
                scope.launch {
                    refresh(ModeSwitcher.switchTo(this@ModeTileService, usb!!))
                    delay(4000)
                    refresh()
                }
            }
            // Falta el permiso USB o el servicio: la app lo pide o guía con su franja.
            else -> openApp(Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SWITCH_TO, mode.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp(intent: Intent) {
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(
                this, mode.ordinal, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Pide a Android que vuelva a dibujar los íconos (al cambiar el modo del control). */
        fun refreshAll(ctx: Context) {
            for (c in listOf(AndroidTileService::class.java, XInputTileService::class.java, Ds5TileService::class.java)) {
                runCatching { requestListeningState(ctx, ComponentName(ctx, c)) }
            }
        }
    }
}

class AndroidTileService : ModeTileService(PadMode.ANDROID)
class XInputTileService : ModeTileService(PadMode.XINPUT)
class Ds5TileService : ModeTileService(PadMode.DS5)
