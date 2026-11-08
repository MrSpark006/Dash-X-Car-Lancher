package hu.dashx

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Station(val name: String, val url: String)

object Prefs {
    val ACCENTS = intArrayOf(
        0xFF22E3FF.toInt(), // cyan
        0xFFFF4FD8.toInt(), // magenta
        0xFF9DFF4F.toInt(), // lime
        0xFFFFB02E.toInt(), // amber
        0xFF8C7BFF.toInt(), // violet
        0xFFFF5D5D.toInt()  // red
    )

    // Slot specs: builtin:radio | builtin:drawer | intent:navi|music|phone|web | app:<package>
    val DEFAULT_SLOTS = arrayOf(
        "builtin:radio", "intent:navi", "intent:music",
        "intent:phone", "intent:web", "builtin:drawer"
    )

    private fun sp(c: Context) = c.getSharedPreferences("orbit", Context.MODE_PRIVATE)

    fun accent(c: Context): Int = ACCENTS[sp(c).getInt("accent", 0).mod(ACCENTS.size)]

    fun nextAccent(c: Context) {
        sp(c).edit().putInt("accent", (sp(c).getInt("accent", 0) + 1) % ACCENTS.size).apply()
    }

    fun slot(c: Context, i: Int): String = sp(c).getString("slot$i", null) ?: DEFAULT_SLOTS[i]

    fun setSlot(c: Context, i: Int, v: String?) {
        val e = sp(c).edit()
        if (v == null) e.remove("slot$i") else e.putString("slot$i", v)
        e.apply()
    }

    fun clockMode(c: Context): Boolean = sp(c).getBoolean("clock", false)
    fun setClockMode(c: Context, v: Boolean) { sp(c).edit().putBoolean("clock", v).apply() }

    fun immersive(c: Context): Boolean = sp(c).getBoolean("immersive", false)
    fun setImmersive(c: Context, v: Boolean) { sp(c).edit().putBoolean("immersive", v).apply() }

    fun favorites(c: Context): MutableList<Station> {
        val out = mutableListOf<Station>()
        try {
            val a = JSONArray(sp(c).getString("favs", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Station(o.getString("n"), o.getString("u")))
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun saveFavorites(c: Context, list: List<Station>) {
        val a = JSONArray()
        for (s in list) a.put(JSONObject().put("n", s.name).put("u", s.url))
        sp(c).edit().putString("favs", a.toString()).apply()
    }

    /** Returns true if the station is now a favorite. */
    fun toggleFavorite(c: Context, s: Station): Boolean {
        val l = favorites(c)
        val idx = l.indexOfFirst { it.url == s.url }
        return if (idx >= 0) {
            l.removeAt(idx)
            saveFavorites(c, l)
            false
        } else {
            l.add(s)
            saveFavorites(c, l)
            true
        }
    }

    fun lastStation(c: Context): Station? {
        val n = sp(c).getString("last_n", null) ?: return null
        val u = sp(c).getString("last_u", null) ?: return null
        return Station(n, u)
    }

    fun setLastStation(c: Context, s: Station) {
        sp(c).edit().putString("last_n", s.name).putString("last_u", s.url).apply()
    }
}
