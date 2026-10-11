package io.github.sudodiego.g9control

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Respaldo COMPLETO de la app en un solo archivo: los perfiles guardados, la configuración que se está
 * usando en este momento (como un perfil más) y TODOS los ajustes de la app en la tablet (gestos generales
 * y por app, apps con cambio de modo automático, tema, idioma, ajustes de desarrollo…).
 *
 * Para qué sirve: el APK de cada build va firmado con una clave distinta (debug), así que para instalar uno
 * nuevo hay que desinstalar el anterior, y con él se van los datos de la app. Exportar antes, importar
 * después: queda todo como estaba.
 *
 * Los ajustes se guardan como están en la tablet (nombre del archivo → clave → valor), sin traducirlos ni
 * filtrarlos: así el mismo respaldo sigue sirviendo cuando la app gana ajustes nuevos (los que no existan
 * en el respaldo se quedan como estén, y los que ya no existan se ignoran).
 *
 * Los respaldos viejos (versión 1, solo perfiles) también se pueden importar.
 */
object Backup {
    private const val APP = "G9 Control Remapper"
    private const val VERSION = 2

    private fun stamp() = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
    private fun sessionName() = tr("Sesión del ", "Session of ") + SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date())

    /** Nombre sugerido del archivo al exportar (el sistema deja cambiarlo). */
    fun fileName() = tr("G9Control-respaldo", "G9Control-backup") + "-" + stamp() + ".json"

    // ---- Exportar ----

    /** Arma el respaldo completo: ajustes de la app + perfiles + la sesión que se está usando. */
    fun build(ctx: Context, s: AppState): String {
        val o = JSONObject()
            .put("app", APP)
            .put("version", VERSION)
            .put("date", stamp())
            .put("prefs", dumpPrefs(ctx))
            .put("profiles", JSONArray().apply { s.profiles.forEach { put(Profiles.toJson(it)) } })
        // La configuración que se usa ahora mismo, como un perfil más (solo si se leyó del control: si no,
        // serían los valores por defecto de la app y no lo que el control tiene puesto).
        if (s.configKnown) o.put("current", Profiles.toJson(Profile.fromState(0L, sessionName(), s)))
        return o.toString(2)
    }

    /** Nombre de cada archivo de ajustes de la app (los que hay ahora en la tablet). */
    private fun prefNames(ctx: Context): List<String> =
        (File(ctx.dataDir, "shared_prefs").listFiles()?.mapNotNull { it.name.removeSuffix(".xml") } ?: emptyList()).sorted()

    private fun dumpPrefs(ctx: Context): JSONObject = JSONObject().apply {
        prefNames(ctx).forEach { name ->
            val a = JSONObject()
            ctx.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (k, v) -> value(v)?.let { a.put(k, it) } }
            put(name, a)
        }
    }

    /** Un ajuste como [tipo, valor]: así se puede volver a escribir con su tipo original. */
    private fun value(v: Any?): JSONArray? = when (v) {
        is Boolean -> JSONArray().put("b").put(v)
        is Int -> JSONArray().put("i").put(v)
        is Long -> JSONArray().put("l").put(v)
        is Float -> JSONArray().put("f").put(v.toDouble())
        is String -> JSONArray().put("s").put(v)
        is Set<*> -> JSONArray().put("set").put(JSONArray(v.map { it.toString() }))
        else -> null
    }

    // ---- Importar ----

    /**
     * Restaura un respaldo: primero los ajustes de la app, después los perfiles del archivo (los de igual
     * nombre se reemplazan) y por último la sesión que se estaba usando (entra como perfil, para aplicarla
     * a un toque). Los ajustes que ya estaban y no vienen en el respaldo se quedan como están.
     * Devuelve un resumen para mostrar. Lanza si el archivo no es un respaldo.
     */
    fun restore(ctx: Context, text: String): String {
        val o = JSONObject(text)
        o.optJSONObject("prefs")?.let { applyPrefs(ctx, it) }
        val file = o.optJSONArray("profiles")?.let { a ->
            (0 until a.length()).mapNotNull { runCatching { Profiles.fromJson(a.getJSONObject(it)) }.getOrNull() }
        } ?: emptyList()
        if (file.isEmpty() && o.optJSONObject("prefs") == null) throw IllegalArgumentException("no es un respaldo")
        val list = Profiles.load(ctx).toMutableList()
        var id = System.currentTimeMillis()
        var added = 0
        file.forEach { p ->
            val i = list.indexOfFirst { it.name == p.name }
            if (i >= 0) list[i] = p.copy(id = list[i].id) else { list.add(p.copy(id = id++)); added++ }
        }
        // La sesión que estaba en uso: entra como perfil, con su nombre (si no estaba ya).
        val session = o.optJSONObject("current")?.let { c -> runCatching { Profiles.fromJson(c) }.getOrNull() }
            ?.takeIf { p -> list.none { it.name == p.name } }
            ?.also { list.add(it.copy(id = id++)) }
        Profiles.save(ctx, list)
        val nuevos = added + (if (session != null) 1 else 0)
        val resumen = tr("${list.size} perfiles · ${file.size - added} actualizados",
            "${list.size} profiles · ${file.size - added} updated")
        return if (nuevos > 0) "$resumen · $nuevos " + tr(
            if (nuevos == 1) "nuevo (con la sesión del respaldo)" else "nuevos (con la sesión del respaldo)",
            if (nuevos == 1) "new (with the backup session)" else "new (with the backup session)"
        ) else resumen
    }

    /** Escribe los ajustes del respaldo; lo que ya estaba y no viene en el respaldo se queda como está. */
    private fun applyPrefs(ctx: Context, o: JSONObject) {
        o.keys().forEach outer@{ name ->
            val a = runCatching { o.getJSONObject(name) }.getOrNull() ?: return@outer
            val e = ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            a.keys().forEach inner@{ k ->
                val v = runCatching { a.getJSONArray(k) }.getOrNull() ?: return@inner
                runCatching { put(e, k, v) }
            }
            e.apply()
        }
    }

    /** Un ajuste, con el tipo con el que se guardó. */
    private fun put(e: SharedPreferences.Editor, k: String, v: JSONArray) {
        when (v.getString(0)) {
            "b" -> e.putBoolean(k, v.getBoolean(1))
            "i" -> e.putInt(k, v.getInt(1))
            "l" -> e.putLong(k, v.getLong(1))
            "f" -> e.putFloat(k, v.getDouble(1).toFloat())
            "s" -> e.putString(k, v.getString(1))
            "set" -> {
                val a = v.getJSONArray(1)
                e.putStringSet(k, (0 until a.length()).map { i -> a.getString(i) }.toSet())
            }
        }
    }
}
