package io.github.sudodiego.g9control

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Página "Tablet": lo que es de la tablet y no del control. A la izquierda la batería (bypass manual y automático) y
 * la pantalla y los botones; a la derecha Game Assistant (rendimiento, ocultarlo, Shizuku).
 */
@Composable
fun TabletPage(s: AppState, a: MainActivity) {
    TwoPane(
        first = {
            BatteryPanel(a)
            ScreenButtonsPanel(s, a)
        },
        second = {
            GameAssistantSettings(s, a)
            ShizukuPanel()
        }
    )
}

/** Estado de la batería ahora (de ACTION_BATTERY_CHANGED, sin permisos). */
private data class BatteryNow(val level: Int, val tempC: Float, val plugged: Boolean, val charging: Boolean, val cycles: Int)

private fun batteryNow(a: MainActivity): BatteryNow? {
    val i = a.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0)
    return BatteryNow(
        level, i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f, i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0,
        status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
        if (android.os.Build.VERSION.SDK_INT >= 34) i.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) else -1
    )
}

/**
 * Batería: estado, bypass manual (el cargador alimenta la tablet y la batería no carga) y bypass automático con los
 * rangos que se elijan. El bypass va con Shizuku (el servicio de batería de ZUI solo acepta apps del sistema).
 */
@Composable
private fun BatteryPanel(a: MainActivity) {
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(batteryNow(a)) }
    var bypass by remember { mutableStateOf(ZuiBattery.bypassNow) }
    var smart by remember { mutableStateOf(AutoRules.smartBypass(a)) }
    var level by remember { mutableIntStateOf(AutoRules.smartBypassLevel(a)) }
    var temp by remember { mutableIntStateOf(AutoRules.smartBypassTemp(a)) }
    var games by remember { mutableStateOf(AutoRules.smartBypassGames(a)) }
    var limit by remember { mutableStateOf<Int?>(null) }
    val ready = Shell.state == Shell.State.READY
    // Se refresca cada pocos segundos mientras la página está abierta.
    LaunchedEffect(ready) {
        while (true) {
            now = batteryNow(a)
            if (ready) {
                ZuiBattery.readBypass()?.let { bypass = it }
                limit = ZuiBattery.readChargeLimit()
            }
            delay(3000)
        }
    }
    CompositionLocalProvider(LocalEditable provides true) {
        Panel(tr("Batería", "Battery"), trailing = {
            now?.let { b ->
                // Enchufada y sin cargar: el bypass, el límite de carga de ZUI o algo más (la batería decide).
                val state = when {
                    bypass == true && b.plugged -> "Bypass"
                    b.charging -> tr("Cargando", "Charging")
                    b.plugged && limit != null -> tr("Límite $limit %", "Limit $limit %")
                    b.plugged -> tr("No carga", "Not charging")
                    else -> tr("Sin cargador", "Unplugged")
                }
                Text("${b.level} % · ${"%.0f".format(b.tempC)} °C · $state", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }) {
            // Sin Shizuku todo el bypass se ve atenuado (no haría nada); la tarjeta Shizuku dice qué falta.
            CompositionLocalProvider(LocalEditable provides ready) {
                SwitchRow(
                    tr("Bypass", "Bypass"), bypass == true,
                    tr("El cargador alimenta la tablet; la batería no carga. Se apaga al desconectar.", "The charger powers the tablet; the battery doesn't charge. Off when unplugged.")
                ) { want ->
                    if (ready) scope.launch { if (ZuiBattery.setBypass(want)) bypass = want }
                }
                SwitchRow(
                    tr("Bypass automático", "Automatic bypass"), smart,
                    tr("Enchufada y con lo que elijas abajo; se apaga si deja de cumplirse.", "When plugged in and what you choose below; off when it isn't.")
                ) {
                    smart = it; AutoRules.setSmartBypass(a, it)
                    // Que no peleen: el inteligente de Game Assistant queda en Manual (se puede con WRITE_SECURE_SETTINGS).
                    if (it && Aspect.canWrite(a)) runCatching { Settings.Global.putInt(a.contentResolver, "intelligent_bypass_charging_value", 0) }
                }
                if (smart) {
                    SwitchRow(tr("Solo en juegos", "Only in games"), games) { games = it; AutoRules.setSmartBypassGames(a, it) }
                    Text(tr("Batería mínima", "Minimum battery"), fontSize = 14.sp)
                    ChoiceChips(listOf(20, 40, 60, 80).map { it to "$it %" }, level) { level = it; AutoRules.setSmartBypassLevel(a, it) }
                    limit?.takeIf { level >= it - 5 }?.let {
                        Hint(tr("Con el límite de carga de ZUI en $it %, este mínimo casi nunca se cumple.", "With ZUI's charge limit at $it %, this minimum will rarely be met."))
                    }
                    Text(tr("Temperatura mínima (batería)", "Minimum temperature (battery)"), fontSize = 14.sp)
                    ChoiceChips(listOf(0 to tr("Sin mínimo", "None"), 35 to "35 °C", 38 to "38 °C", 41 to "41 °C"), temp) { temp = it; AutoRules.setSmartBypassTemp(a, it) }
                }
            }
            OutlinedButton(onClick = { runCatching { a.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) } }) {
                Text(tr("Optimización de carga (Ajustes)", "Charge optimization (Settings)"))
            }
            now?.cycles?.takeIf { it >= 0 }?.let { Text(tr("Ciclos de carga: $it", "Charge cycles: $it"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** Pantalla 16:9 y volumen invertido. */
@Composable
private fun ScreenButtonsPanel(s: AppState, a: MainActivity) {
    var swapVol by remember { mutableStateOf(AutoRules.swapVolume(a)) }
    CompositionLocalProvider(LocalEditable provides true) {
        Panel(tr("Pantalla y botones", "Screen and buttons")) {
            ScreenRow(a)
            SwitchRow(
                tr("Invertir el volumen", "Swap volume buttons"), swapVol,
                tr("El botón de abajo sube y el de arriba baja. Los del control no cambian.", "The lower button turns it up and the upper one down. The controller's don't change.")
            ) { swapVol = it; AutoRules.setSwapVolume(a, it) }
            if (!s.autoServiceOn) Hint(tr("El volumen invertido necesita el servicio de accesibilidad (en Juegos).", "Swapped volume needs the accessibility service (in Games)."))
        }
    }
}

/**
 * Game Assistant, el de la ROM de la tablet: si hay un juego ahora (se actualiza solo), ocultarlo y un acceso a sus
 * ajustes. Ocultarlo es quitarle "Mostrar sobre otras apps": con Shizuku G9 Control lo hace solo (y lo muestra un
 * momento para cambiar el rendimiento); sin Shizuku se puede a mano en Ajustes. Solo si la tablet lo tiene.
 */
@Composable
private fun GameAssistantSettings(s: AppState, a: MainActivity) {
    val zui = remember { ZuiGame.installed(a) }
    if (!zui) return
    var game by remember { mutableStateOf(ZuiGame.inGame(a)) }
    DisposableEffect(Unit) {
        val o = ZuiGame.observe(a, android.os.Handler(a.mainLooper)) { game = ZuiGame.inGame(a) }
        onDispose { ZuiGame.unobserve(a, o) }
    }
    val ready = Shell.state == Shell.State.READY
    val gaScope = rememberCoroutineScope()
    var gaHidden by remember { mutableStateOf(AutoRules.gaHidden(a)) }
    // Lo que el usuario eligió, no cómo está ahora: puede estar visible un rato porque G9 Control lo está usando
    // (copiar el estado actual a la preferencia hacía que no se volviera a ocultar). Solo la primera vez, sin
    // preferencia guardada, se toma de cómo está.
    LaunchedEffect(ready) { if (ready && gaHidden == null) ZuiGame.hidden()?.let { gaHidden = it; AutoRules.setGaHidden(a, it) } }
    CompositionLocalProvider(LocalEditable provides true) {
        Panel("Game Assistant", trailing = {
            Text(if (game) tr("En un juego", "In a game") else tr("Sin juego", "No game"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }) {
            CompositionLocalProvider(LocalEditable provides ready) {
                SwitchRow(
                    tr("Ocultar Game Assistant", "Hide Game Assistant"), gaHidden == true,
                    tr("Sin pestaña ni panel. G9 Control lo muestra solo mientras lo usa.", "No side tab or panel. G9 Control shows it only while using it.")
                ) { want ->
                    if (ready) gaScope.launch { if (ZuiGame.setHidden(want)) { AutoRules.setGaHidden(a, want); gaHidden = want } }
                }
            }
            if (!ready) OutlinedButton(onClick = { runCatching { a.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) } }) {
                Text(tr("Ocultarlo a mano (Mostrar sobre otras apps)", "Hide it manually (Display over other apps)"))
            }
            OutlinedButton(onClick = { if (!ZuiGame.openSettings(a)) s.fail(tr("No se pudieron abrir los ajustes de Game Assistant.", "Couldn't open Game Assistant settings.")) }) {
                Text(tr("Ajustes de Game Assistant", "Game Assistant settings"))
            }
        }
    }
}

private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

/** Shizuku: su estado, para qué se usa (una línea) y, si falta algo, el botón para arreglarlo. */
@Composable
private fun ShizukuPanel() {
    val a = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { Shell.init(a); Shell.refresh() }
    val st = Shell.state
    Panel("Shizuku", tr("Para el bypass, el rendimiento y ocultar Game Assistant solo.", "For bypass, performance and hiding Game Assistant automatically."), trailing = {
        Text(
            when (st) {
                Shell.State.READY -> tr("Listo", "Ready")
                Shell.State.NO_PERMISSION -> tr("Falta el permiso", "Permission missing")
                Shell.State.NOT_RUNNING -> tr("No está iniciado", "Not started")
            },
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }) {
        when (st) {
            Shell.State.READY -> Unit
            Shell.State.NO_PERMISSION -> OutlinedButton(onClick = { Shell.requestPermission() }) { Text(tr("Dar permiso a G9 Control", "Grant permission to G9 Control")) }
            Shell.State.NOT_RUNNING -> {
                val launch = remember { a.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) }
                if (launch != null) OutlinedButton(onClick = { runCatching { a.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) {
                    Text(tr("Abrir Shizuku", "Open Shizuku"))
                } else Hint(tr("Shizuku no está instalado (shizuku.rikka.app).", "Shizuku isn't installed (shizuku.rikka.app)."))
            }
        }
    }
}

/**
 * Pantalla 16:9 para un monitor externo. Sin Shizuku si la app tiene WRITE_SECURE_SETTINGS (se da una vez con
 * adb); si no, con Shizuku; si no hay ninguno, el comando para darlo.
 */
@Composable
private fun ScreenRow(a: MainActivity) {
    val scope = rememberCoroutineScope()
    var wide by remember { mutableStateOf(Aspect.is169(a)) }
    if (!Aspect.available(a)) {
        Text(tr("Pantalla 16:9", "16:9 screen"), fontSize = 14.sp)
        Hint(tr("Para un monitor externo sin bordes. Se activa una vez desde un computador: ${Aspect.GRANT_COMMAND}",
            "For an external monitor without borders. Enable it once from a computer: ${Aspect.GRANT_COMMAND}"))
        return
    }
    SwitchRow(
        tr("Pantalla 16:9", "16:9 screen"), wide,
        tr("Monitor externo sin bordes. Cambiarla cierra Dolphin si está abierto.", "External monitor without borders. Switching closes Dolphin if it's open.")
    ) { want ->
        scope.launch {
            if (want != Aspect.is169(a)) Aspect.toggle(a)
            kotlinx.coroutines.delay(500)
            wide = Aspect.is169(a)
        }
    }
}
