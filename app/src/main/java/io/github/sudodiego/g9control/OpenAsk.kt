package io.github.sudodiego.g9control

import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Aviso de UNA sola vez: la primera vez que se conecta el control pregunta, con su casilla, si quiere que la
 * app se abra sola al conectar el control. Sin marcar la casilla no se abre nunca más (y no vuelve a preguntar).
 *
 * Lo muestra el SERVICIO en una ventana de accesibilidad: así se pregunta sin abrir la app (que es justo lo que
 * se quiere decidir) y sin depender de un permiso de notificaciones.
 */
class OpenAsk(private val svc: AutoModeService) {

    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private val wm get() = svc.getSystemService(WindowManager::class.java)

    val isOpen get() = view != null

    /** Pregunta una sola vez. Si ya se preguntó (o ya está en pantalla), no hace nada. */
    fun askOnce() {
        if (view != null || Prefs.openAsked(svc)) return
        val o = OverlayOwner().also { it.start() }
        val cv = ComposeView(svc).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setContent { MaterialTheme(darkColorScheme()) { Card(onSave = ::guardar) } }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        runCatching { wm.addView(cv, lp) }.onSuccess { view = cv; owner = o }
            .onFailure { Log.w(AutoModeService.TAG, "no se pudo mostrar el aviso de abrir al conectar", it); o.destroy() }
    }

    /** Guarda lo elegido y recuerda que ya se preguntó (para no volver a preguntar). */
    private fun guardar(abrir: Boolean) {
        Prefs.setOpenOnConnect(svc, abrir)
        Prefs.setOpenAsked(svc, true)
        Log.d(AutoModeService.TAG, "aviso al conectar: la app se abrirá sola = $abrir")
        hide()
    }

    fun hide() {
        view?.let { v -> runCatching { wm.removeView(v) } }
        view = null
        owner?.destroy()
        owner = null
    }

    /** La tarjeta: el aviso, la casilla y el botón. Texto en llano, sin tecnicismos. */
    @Composable
    private fun Card(onSave: (Boolean) -> Unit) {
        var marcar by remember { mutableStateOf(Prefs.openOnConnect(svc)) }
        Column(
            Modifier.padding(16.dp).widthIn(max = 520.dp)
                .background(Color(0xF00E1424), RoundedCornerShape(22.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                tr("Control conectado", "Controller connected"),
                color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp
            )
            Text(
                tr("¿Quieres que la app se abra sola cada vez que conectes el control?",
                    "Do you want the app to open by itself every time you connect the controller?"),
                color = Color(0xFF9AA6BF), fontSize = 13.sp
            )
            Row(
                Modifier.fillMaxWidth().clickable { marcar = !marcar },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = marcar, onCheckedChange = { marcar = it })
                Text(
                    tr("Abrir la app al conectar el control", "Open the app when the controller is connected"),
                    color = Color.White, fontSize = 14.sp
                )
            }
            Text(
                tr("Si lo dejas sin marcar, la app no se pondrá al frente al conectar el control. No se vuelve a preguntar.",
                    "If you leave it unchecked, the app won't come to the front when you connect it. It won't ask again."),
                color = Color(0xFF9AA6BF), fontSize = 12.sp
            )
            Button(onClick = { onSave(marcar) }, modifier = Modifier.align(Alignment.End)) {
                Text(tr("Guardar", "Save"))
            }
        }
    }
}
