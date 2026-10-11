package io.github.sudodiego.g9control

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Perfil: una combinación guardada de luces, zonas muertas, gatillos, cruceta, vibración y botones
 * traseros (con sus macros). Se guarda en
 * la tablet (no en el control); al aplicarlo se escribe en el control solo lo que cambia.
 */
data class Profile(
    val id: Long,
    val name: String,
    val left: Light,
    val right: Light,
    val home: Light,
    val homeOff: Boolean,
    val deadZones: Map<Int, Int>,
    val quickL: Boolean,
    val quickR: Boolean,
    val vibration: Vibration,
    val dpadLock: Boolean = false,
    val swapDpad: Boolean = false,
    /** Traseros (id 1–4 → destino, FF = macro). Vacío en perfiles guardados antes de existir esto. */
    val backKeys: Map<Int, Int> = emptyMap(),
    val macros: Map<Int, Macro> = emptyMap(),
) {
    /** true si la configuración actual de la app es exactamente este perfil. */
    fun matches(s: AppState) = same(left, s.left) && same(right, s.right) &&
        same(home.copy(effect = 1, lum = if (homeOff) 0 else home.lum), s.homeToSend()) &&
        deadZones == s.deadZones.toMap() && quickL == s.quickL && quickR == s.quickR && vibration == s.vibration &&
        dpadLock == s.dpadLock && swapDpad == s.swapDpad &&
        (backKeys.isEmpty() || (backKeys == s.backKeys.toMap() &&
            backKeys.filterValues { it == BACK_MACRO }.keys.all { macros[it] == s.macros[it] }))

    companion object {
        /**
         * El control guarda el tono en un byte (0–255), así que al releerlo puede volver con 1° de
         * diferencia: se comparan las luces como las guarda el control.
         */
        private fun same(a: Light, b: Light) = a.relevant() == b.relevant()

        fun fromState(id: Long, name: String, s: AppState) = Profile(
            id, name, s.left, s.right, s.home, s.homeOff, s.deadZones.toMap(), s.quickL, s.quickR, s.vibration,
            s.dpadLock, s.swapDpad,
            if (s.appliedBackKeys != null) s.backKeys.toMap() else emptyMap(),
            s.macros.toMap()
        )
    }
}

/**
 * Solo lo que el control guarda en cada efecto: en arcoíris no guarda el color y en fijo no
 * guarda velocidad ni brillo (comprobado releyendo). Apagado no guarda nada que importe.
 */
fun Light.relevant(): Light {
    val h = copy(hue = Gcm.hueToByte(hue))
    return when (effect) {
        255 -> Light(id, effect = 255, hue = 0, sat = 0, lum = 0, speed = 0, brightness = 0)
        4 -> h.copy(hue = 0, sat = 0, lum = 0)
        2 -> h.copy(brightness = 0)
        else -> h.copy(speed = 0, brightness = 0)
    }
}

object Profiles {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("g9", Context.MODE_PRIVATE)

    fun load(ctx: Context): List<Profile> = runCatching {
        val a = JSONArray(sp(ctx).getString("profiles", "[]"))
        (0 until a.length()).mapNotNull { runCatching { fromJson(a.getJSONObject(it)) }.getOrNull() }
    }.getOrDefault(emptyList())

    /** Respaldo: todos los perfiles en un JSON (el mismo formato que se guarda en la tablet). */
    fun exportJson(list: List<Profile>): String =
        JSONObject().put("app", "G9 Control").put("version", 1)
            .put("profiles", JSONArray().apply { list.forEach { put(toJson(it)) } }).toString(2)

    /** Lee un respaldo; los perfiles que no se entiendan se saltan. */
    fun importJson(text: String): List<Profile> {
        val o = JSONObject(text)
        val a = o.getJSONArray("profiles")
        return (0 until a.length()).mapNotNull { runCatching { fromJson(a.getJSONObject(it)) }.getOrNull() }
    }

    fun save(ctx: Context, list: List<Profile>) {
        val a = JSONArray()
        list.forEach { a.put(toJson(it)) }
        sp(ctx).edit().putString("profiles", a.toString()).apply()
    }

    private fun lightJson(l: Light) = JSONArray(listOf(l.id, l.hue, l.sat, l.lum, l.effect, l.speed, l.brightness))
    private fun light(a: JSONArray) = Light(a.getInt(0), a.getInt(1), a.getInt(2), a.getInt(3), a.getInt(4), a.getInt(5), a.getInt(6))

    /** Perfil → JSON (también lo usa [Backup] para el respaldo completo). */
    fun toJson(p: Profile) = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("left", lightJson(p.left))
        put("right", lightJson(p.right))
        put("home", lightJson(p.home))
        put("homeOff", p.homeOff)
        put("dz", JSONObject().apply { p.deadZones.forEach { (k, v) -> put(k.toString(), v) } })
        put("quickL", p.quickL)
        put("quickR", p.quickR)
        put("dpadLock", p.dpadLock)
        put("swapDpad", p.swapDpad)
        put("back", JSONObject().apply { p.backKeys.forEach { (k, v) -> put(k.toString(), v) } })
        put("macros", JSONObject().apply {
            p.macros.forEach { (k, m) ->
                put(k.toString(), JSONObject().apply {
                    put("mode", m.mode); put("loop", m.loop); put("loopMs", m.loopMs)
                    put("steps", JSONArray().apply { m.steps.forEach { put(JSONArray(listOf(it.key, it.holdMs, it.waitMs))) } })
                })
            }
        })
        val v = p.vibration
        put("vib", JSONArray(listOf(
            v.leftGrip, v.leftTrigger, v.leftTriggerPressed, v.leftTriggerSync,
            v.rightGrip, v.rightTrigger, v.rightTriggerPressed, v.rightTriggerSync
        )))
    }

    /** JSON → perfil (también lo usa [Backup] al importar un respaldo completo). */
    fun fromJson(o: JSONObject): Profile {
        val dz = o.getJSONObject("dz")
        val v = o.getJSONArray("vib")
        return Profile(
            id = o.getLong("id"),
            name = o.getString("name"),
            left = light(o.getJSONArray("left")),
            right = light(o.getJSONArray("right")),
            home = light(o.getJSONArray("home")),
            homeOff = o.getBoolean("homeOff"),
            deadZones = dz.keys().asSequence().associate { it.toInt() to dz.getInt(it) },
            quickL = o.getBoolean("quickL"),
            quickR = o.getBoolean("quickR"),
            vibration = Vibration(
                v.getInt(0), v.getInt(1), v.getBoolean(2), v.getBoolean(3),
                v.getInt(4), v.getInt(5), v.getBoolean(6), v.getBoolean(7)
            ),
            // Perfiles guardados antes de existir estos ajustes: quedan desactivados.
            dpadLock = o.optBoolean("dpadLock", false),
            swapDpad = o.optBoolean("swapDpad", false),
            backKeys = o.optJSONObject("back")?.let { b -> b.keys().asSequence().associate { it.toInt() to b.getInt(it) } } ?: emptyMap(),
            macros = o.optJSONObject("macros")?.let { ms ->
                ms.keys().asSequence().associate { k ->
                    val m = ms.getJSONObject(k)
                    val st = m.getJSONArray("steps")
                    k.toInt() to Macro(
                        steps = (0 until st.length()).map { i ->
                            val e = st.getJSONArray(i); MacroStep(e.getInt(0), e.getInt(1), e.getInt(2))
                        },
                        mode = m.optInt("mode", 0), loop = m.optBoolean("loop", false), loopMs = m.optInt("loopMs", 100)
                    )
                }
            } ?: emptyMap(),
        )
    }
}
