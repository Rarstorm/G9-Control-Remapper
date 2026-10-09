package io.github.sudodiego.g9control

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Recibe el aviso de Android al conectar el G9 en modo Android. Existe para que Android guarde el
 * permiso USB ("Usar siempre") sin abrir la app: no muestra nada y se cierra al instante.
 * Solo abre la pantalla principal si está activado "Abrir la app al conectar el control".
 */
class UsbAttachActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Prefs.openOnConnect(this)) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }
}
