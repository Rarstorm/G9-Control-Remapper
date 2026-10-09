package io.github.sudodiego.g9control

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Idioma elegido en Ajustes. */
enum class AppLang { SYSTEM, ES, EN;
    val label get() = when (this) { SYSTEM -> tr("Del sistema", "System"); ES -> "Español"; EN -> "English" }
}

/**
 * Idioma de la app: español o inglés. Los textos se escriben juntos en el código con [tr], así se
 * leen en los dos idiomas donde se usan. Cambiar el idioma redibuja la app al instante (es un estado
 * de Compose) y también lo usan la guía y los avisos del servicio (mismo proceso).
 */
object Lang {
    var choice by mutableStateOf(AppLang.SYSTEM)
        private set

    /** true = inglés. "Del sistema" usa español si el teléfono está en español, si no inglés. */
    val en: Boolean get() = when (choice) {
        AppLang.EN -> true
        AppLang.ES -> false
        AppLang.SYSTEM -> Locale.getDefault().language != "es"
    }

    private fun sp(ctx: Context) = ctx.getSharedPreferences("g9", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        choice = runCatching { AppLang.valueOf(sp(ctx).getString("lang", null)!!) }.getOrDefault(AppLang.SYSTEM)
    }

    fun set(ctx: Context, v: AppLang) {
        choice = v
        sp(ctx).edit().putString("lang", v.name).apply()
    }
}

/** Texto en el idioma elegido. */
fun tr(es: String, en: String) = if (Lang.en) en else es
