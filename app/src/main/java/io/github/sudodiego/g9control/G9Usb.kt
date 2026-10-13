package io.github.sudodiego.g9control

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Conexión USB con el G9. Igual que la app oficial: se usa la ÚLTIMA interfaz del dispositivo,
 * que tiene dos endpoints interrupt (entrada y salida), y se intercambian paquetes GCM.
 * Todas las funciones son bloqueantes: llamarlas fuera del hilo principal.
 */
object G9Usb {
    private const val TAG = "G9Usb"
    const val ACTION_PERMISSION = "io.github.sudodiego.g9control.USB_PERMISSION"
    private const val TIMEOUT_MS = 300

    /** Volátil: [isOpen] la lee sin el candado (lo llama el hilo principal). */
    @Volatile private var connection: UsbDeviceConnection? = null
    private var iface: UsbInterface? = null
    private var epIn: UsbEndpoint? = null
    private var epOut: UsbEndpoint? = null
    private var openedDevice: UsbDevice? = null

    /** Registro de lo enviado/recibido, para mostrar en la app. */
    val log = ArrayDeque<String>()
    private fun addLog(s: String) {
        Log.d(TAG, s)
        synchronized(log) {
            log.addLast(s)
            while (log.size > 200) log.removeFirst()
        }
    }

    private const val VID_SONY = 0x054C
    private const val VID_MICROSOFT = 0x045E

    fun isG9(d: UsbDevice): Boolean {
        val name = (d.productName ?: "") + " " + (d.manufacturerName ?: "")
        return name.contains("G9", ignoreCase = true) && name.contains("Legion", ignoreCase = true)
    }

    /**
     * En modo DS5 el control conserva el nombre "Legion Gaming Controller G9", pero su última interfaz
     * es la del mando de Sony: mandarle paquetes GCM lo hace vibrar y fallar. Solo se habla con él
     * si no se presenta como Sony ni Microsoft.
     */
    fun isOtherMode(d: UsbDevice): Boolean {
        val m = d.manufacturerName ?: ""
        return d.vendorId == VID_SONY || d.vendorId == VID_MICROSOFT ||
            m.contains("Sony", true) || m.contains("Microsoft", true)
    }

    /** G9 conectado pero en X-Input/DS5 (la app no puede configurarlo en esos modos). */
    fun findOtherMode(ctx: Context): UsbDevice? {
        val um = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        return um.deviceList.values.firstOrNull { isOtherMode(it) && (isG9(it) || it.vendorId == VID_MICROSOFT) }
    }

    fun findDevice(ctx: Context): UsbDevice? {
        val um = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        return um.deviceList.values.firstOrNull { isG9(it) && !isOtherMode(it) && !(it.productName ?: "").contains("DFU", true) }
    }

    fun allDevices(ctx: Context): List<UsbDevice> {
        val um = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        return um.deviceList.values.toList()
    }

    fun describe(d: UsbDevice): String = buildString {
        append("${d.productName ?: "?"} (${d.manufacturerName ?: "?"})\n")
        append("VID %04X  PID %04X\n".format(d.vendorId, d.productId))
        for (i in 0 until d.interfaceCount) {
            val itf = d.getInterface(i)
            append("  ${tr("Interfaz", "Interface")} $i: ${tr("clase", "class")} ${itf.interfaceClass}/${itf.interfaceSubclass}/${itf.interfaceProtocol}, ")
            append("${itf.endpointCount} endpoints\n")
        }
    }

    fun hasPermission(ctx: Context, d: UsbDevice): Boolean {
        val um = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        return um.hasPermission(d)
    }

    /**
     * Pide el permiso USB sin esperar la respuesta: lo usa el SERVICIO, que no puede quedarse callado cuando el
     * mando vuelve a aparecer (antes la reconexión sola se rendía en silencio sin el permiso guardado).
     */
    fun askPermission(ctx: Context, d: UsbDevice) {
        val um = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        if (um.hasPermission(d)) return
        val app = ctx.applicationContext
        val intent = Intent(ACTION_PERMISSION).setPackage(app.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        runCatching { um.requestPermission(d, PendingIntent.getBroadcast(app, 0, intent, flags)) }
    }

    /** Pide permiso USB y espera la respuesta del usuario. */
    suspend fun requestPermission(ctx: Context, d: UsbDevice): Boolean {
        val um = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        if (um.hasPermission(d)) return true
        val app = ctx.applicationContext
        return suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    if (i.action != ACTION_PERMISSION) return
                    runCatching { app.unregisterReceiver(this) }
                    if (cont.isActive) cont.resume(um.hasPermission(d))
                }
            }
            val filter = IntentFilter(ACTION_PERMISSION)
            if (Build.VERSION.SDK_INT >= 33) {
                app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                app.registerReceiver(receiver, filter)
            }
            val intent = Intent(ACTION_PERMISSION).setPackage(app.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            um.requestPermission(d, PendingIntent.getBroadcast(app, 0, intent, flags))
            cont.invokeOnCancellation { runCatching { app.unregisterReceiver(receiver) } }
        }
    }

    /**
     * Sin el candado: la pantalla lo pregunta seguido desde el hilo principal, y con el candado esperaba a que
     * terminaran escrituras largas (macro + traseros, una tras otra): la app se trababa (ANR, 10 oct 2026).
     */
    fun isOpen(): Boolean = connection != null

    @Synchronized
    fun open(ctx: Context): String? {
        val d = findDevice(ctx) ?: return tr("No encuentro el G9. ¿Está conectado y en modo Android?", "G9 not found. Is it connected and in Android mode?")
        if (openedDevice?.deviceId == d.deviceId && connection != null) return null
        close()
        val um = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        if (!um.hasPermission(d)) return tr("Falta el permiso USB.", "USB permission missing.")
        if (d.interfaceCount == 0) return tr("El dispositivo no tiene interfaces.", "The device has no interfaces.")
        val itf = d.getInterface(d.interfaceCount - 1)
        var inEp: UsbEndpoint? = null
        var outEp: UsbEndpoint? = null
        for (i in 0 until itf.endpointCount) {
            val ep = itf.getEndpoint(i)
            if (ep.type != UsbConstants.USB_ENDPOINT_XFER_INT) continue
            if (ep.direction == UsbConstants.USB_DIR_IN) inEp = ep else outEp = ep
        }
        if (inEp == null || outEp == null) return tr("La última interfaz no tiene los endpoints esperados.", "The last interface doesn't have the expected endpoints.")
        val conn = um.openDevice(d) ?: return tr("No se pudo abrir el dispositivo.", "Couldn't open the device.")
        if (!conn.claimInterface(itf, true)) {
            conn.close()
            return tr("No se pudo tomar la interfaz de configuración.", "Couldn't claim the configuration interface.")
        }
        connection = conn; iface = itf; epIn = inEp; epOut = outEp; openedDevice = d
        addLog(tr("Conectado: ${d.productName} (interfaz ${d.interfaceCount - 1})", "Connected: ${d.productName} (interface ${d.interfaceCount - 1})"))
        return null
    }

    @Synchronized
    fun close() {
        runCatching { iface?.let { connection?.releaseInterface(it) } }
        runCatching { connection?.close() }
        connection = null; iface = null; epIn = null; epOut = null; openedDevice = null
    }

    /** Vacía respuestas pendientes que hayan quedado en el endpoint de entrada. */
    private fun drain(conn: UsbDeviceConnection, ep: UsbEndpoint) {
        val buf = ByteArray(ep.maxPacketSize)
        repeat(5) { if (conn.bulkTransfer(ep, buf, buf.size, 10) <= 0) return }
    }

    /**
     * Envía un paquete y, si [expectReply], espera la respuesta que coincide con comando y subcomando.
     * Devuelve null si no hubo respuesta válida.
     */
    /** Aviso de guardado: lo enciende cada escritura de verdad (la barra de abajo lo muestra en toda la app). */
    var onWrite: (() -> Unit)? = null

    @Synchronized
    fun transact(cmd: ByteArray, expectReply: Boolean = true): ByteArray? {
        val conn = connection ?: run { addLog(tr("Sin conexión", "Not connected")); return null }
        val out = epOut!!
        val inp = epIn!!
        if (expectReply) drain(conn, inp)
        val w = conn.bulkTransfer(out, cmd, cmd.size, TIMEOUT_MS)
        addLog("→ ${Gcm.hex(cmd)}" + if (w < 0) tr("  (falló el envío)", "  (send failed)") else "")
        if (w < 0) {
            close()
            return null
        }
        if (Gcm.isWrite(cmd)) runCatching { onWrite?.invoke() }
        if (!expectReply) return null
        val buf = ByteArray(inp.maxPacketSize)
        val deadline = System.currentTimeMillis() + 600
        while (System.currentTimeMillis() < deadline) {
            val n = conn.bulkTransfer(inp, buf, buf.size, TIMEOUT_MS)
            if (n <= 0) continue
            var resp = buf.copyOf(n)
            // El comando 31 (mapeo) no lleva largo: su tercer byte es el botón. La respuesta mide lo mismo que el envío.
            val declared = if (cmd[0].u() == 0x1F) cmd.size else resp.getOrNull(2)?.u() ?: 0
            if (declared in 3..resp.size) resp = resp.copyOf(declared)
            addLog("← ${Gcm.hex(resp)}")
            // Información básica: 01 01 se responde con un paquete que empieza en C9.
            if (cmd.size >= 2 && cmd[0].u() == 0x01 && cmd[1].u() == 0x01) {
                if (resp[0].u() == 0xC9) return resp else continue
            }
            // Comandos de un byte (50 = identificador): basta con el primer byte.
            if (cmd.size == 1) { if (resp[0] == cmd[0]) return resp else continue }
            // Macros (0x53): al leer (FE) el control responde con el número de paquete en vez de FE.
            // Distribución (0x1E): al leer (1E 00) responde con la distribución actual (1E 10 o 1E 04).
            val sameSub = resp.size >= 2 && (resp[1] == cmd[1] || (cmd[0].u() == 0x53 && cmd[1].u() == 0xFE) ||
                (cmd[0].u() == 0x1E && cmd[1].u() == 0x00))
            if (resp.size >= 2 && resp[0] == cmd[0] && sameSub) return resp
        }
        addLog(tr("   (sin respuesta)", "   (no reply)"))
        return null
    }
}
