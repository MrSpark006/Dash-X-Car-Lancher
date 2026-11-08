package hu.dashx

import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Free community station directory: https://www.radio-browser.info (no API key). */
object RadioDirectory {

    private val bases = listOf(
        "https://de1.api.radio-browser.info",
        "https://at1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "http://de1.api.radio-browser.info" // fallback for old head units with outdated TLS
    )

    /** Blocking, call from a background thread. Empty query = most popular stations of the country. */
    fun search(q: String, country: String): List<Station> {
        val params = StringBuilder("hidebroken=true&order=clickcount&reverse=true&limit=80")
        if (q.isBlank()) {
            if (country.length == 2) params.append("&countrycode=").append(country)
        } else {
            params.append("&name=").append(URLEncoder.encode(q.trim(), "UTF-8"))
        }
        var last: Exception? = null
        for (b in bases) {
            try {
                val con = URL("$b/json/stations/search?$params").openConnection() as HttpURLConnection
                con.connectTimeout = 6000
                con.readTimeout = 10000
                con.setRequestProperty("User-Agent", "DashX/0.1")
                if (con.responseCode != 200) throw IOException("HTTP " + con.responseCode)
                val body = con.inputStream.bufferedReader().use { it.readText() }
                con.disconnect()
                return parse(body)
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("no server")
    }

    private fun parse(body: String): List<Station> {
        val a = JSONArray(body)
        val out = ArrayList<Station>()
        val seen = HashSet<String>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val name = o.optString("name").trim()
            var url = o.optString("url_resolved").trim()
            if (url.isEmpty()) url = o.optString("url").trim()
            if (name.isEmpty() || url.isEmpty()) continue
            if (seen.add(name.lowercase())) out.add(Station(name, url))
        }
        return out
    }
}
