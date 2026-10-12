package io.github.sudodiego.g9control

import android.os.Handler
import android.util.Log
import android.view.KeyEvent

/**
 * Firmas de los traseros. El firmware solo deja que un trasero copie un botón o ejecute una macro, así que
 * «qué trasero se apretó» se resuelve dándole a cada uno una macro cortísima y única (su FIRMA): el servicio
 * la reconoce en la entrada, se la come y ya sabe cuál fue. Los tiempos van en pasos de 5 ms (el mínimo del
 * firmware) para que la secuencia completa dure menos de lo que un dedo puede repetir a mano.
 */
data class PaddleSignature(val back: BackKey, val codes: List<Int>)

// Ninguna firma es el principio de otra: si lo fuera, al apretar el trasero «largo» se reconocería el «corto».
val PADDLE_SIGNATURES: List<PaddleSignature> = listOf(
    PaddleSignature(BackKey.L4, listOf(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START)),
    PaddleSignature(BackKey.R4, listOf(KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT)),
    PaddleSignature(BackKey.L5, listOf(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START)),
    PaddleSignature(BackKey.R5, listOf(KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT)),
)

/** Los botones que forman las firmas (Vista y Menú), con el byte que entiende el mando. */
private val SIG_KEY_BYTE = mapOf(KeyEvent.KEYCODE_BUTTON_SELECT to 0xBC, KeyEvent.KEYCODE_BUTTON_START to 0xBD)

/** Los dos botones que vigila el detector. */
val SIG_KEYCODES: Set<Int> = SIG_KEY_BYTE.keys

/** La macro que se graba en el trasero para que mande su firma: 10 ms por paso y 5 ms entre pasos. */
fun signatureMacro(sig: PaddleSignature) = Macro(
    steps = sig.codes.map { MacroStep(key = SIG_KEY_BYTE.getValue(it), holdMs = 10, waitMs = 5) },
    mode = 0, loop = false,
)

/**
 * Códigos internos de los traseros para las reglas de gestos. No son teclas de Android: son solo el nombre con
 * el que se guarda «qué hace este trasero en esta app». El motor de gestos los trata como a cualquier botón,
 * así que los traseros entran en el mismo «Aplica a» que los demás botones.
 */
val PADDLE_KEYCODE: Map<BackKey, Int> = mapOf(BackKey.L4 to -11, BackKey.R4 to -12, BackKey.L5 to -13, BackKey.R5 to -14)

/**
 * Detector de firmas. Mientras dura la ventana retiene Vista y Menú (no llegan a la app): si la secuencia se
 * completa, dispara el trasero; si no, los reenvía tal cual ([resend]) para que un toque normal de Vista o
 * Menú no se pierda. Ese toque cuesta lo que dure la ventana (80 ms).
 */
class SignatureDetector(
    private val handler: Handler,
    private val resend: (Int) -> Unit,
    private val onPaddle: (BackKey) -> Unit,
) {
    private val held = mutableListOf<Int>()

    private val expire = Runnable { if (held.isNotEmpty()) finish(null) }

    /** @return true si el evento es nuestro (se consume y no llega a la app). */
    fun onKey(keyCode: Int, down: Boolean): Boolean {
        Log.d("G9Auto", "DIAG sig k=$keyCode down=$down held=$held")
        if (keyCode !in SIG_KEYCODES) {
            // Otro botón: lo retenido no era una firma.
            if (held.isNotEmpty()) finish(null)
            return false
        }
        if (!down) return held.isNotEmpty()
        if (held.isEmpty()) {
            held += keyCode
            handler.postDelayed(expire, WINDOW_MS)
            return true
        }
        held += keyCode
        PADDLE_SIGNATURES.firstOrNull { it.codes == held }?.let { finish(it.back); return true }
        // Ya no se parece a ninguna firma: se devuelve lo retenido.
        if (PADDLE_SIGNATURES.none { it.codes.take(held.size) == held }) { finish(null); return false }
        return true
    }

    private fun finish(paddle: BackKey?) {
        handler.removeCallbacks(expire)
        val codes = held.toList()
        held.clear()
        Log.d("G9Auto", "DIAG sig FIN ${paddle?.label ?: "sin firma"} $codes")
        if (paddle != null) onPaddle(paddle) else codes.forEach(resend)
    }

    companion object {
        /** Lo que se espera entre el primer botón y el último (en el mando la firma dura ~25 ms). */
        const val WINDOW_MS = 80L
    }
}
