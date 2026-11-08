package hu.dashx

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/** Internet radio: favorites + worldwide search (radio-browser.info). Tap = play, long-press = favorite. */
class RadioActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var items: List<Station> = emptyList()
    private var favUrls: Set<String> = emptySet()
    private var showingFavs = true
    private var accent = 0
    private var d = 1f
    private var lastState = -1

    private lateinit var sa: StationAdapter
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var now: TextView
    private lateinit var playBtn: TextView

    private val tick = object : Runnable {
        override fun run() {
            refreshNow()
            handler.postDelayed(this, 1000)
        }
    }

    private fun dp(v: Int) = (v * d).toInt()

    private fun pill(label: String): TextView = TextView(this).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(12), dp(16), dp(12))
        isClickable = true
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(0x22FFFFFF)
            setStroke(dp(1), withAlpha(accent, 0x99))
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        d = resources.displayMetrics.density
        accent = Prefs.accent(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackgroundColor(0xFF080A0F.toInt())
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val favBtn = pill(getString(R.string.favorites)).apply { setOnClickListener { showFavorites() } }
        input = EditText(this).apply {
            hint = getString(R.string.search_hint)
            setHintTextColor(0x88FFFFFF.toInt())
            setTextColor(Color.WHITE)
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, id, _ ->
                if (id == EditorInfo.IME_ACTION_SEARCH) {
                    search(text.toString())
                    true
                } else false
            }
        }
        val goBtn = pill(getString(R.string.search)).apply { setOnClickListener { search(input.text.toString()) } }
        top.addView(favBtn)
        top.addView(
            input,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(10), 0, dp(10), 0)
            }
        )
        top.addView(goBtn)

        status = TextView(this).apply {
            setTextColor(0x99FFFFFF.toInt())
            textSize = 13f
            setPadding(dp(4), dp(8), dp(4), dp(4))
        }

        sa = StationAdapter()
        val list = ListView(this).apply {
            adapter = sa
            divider = ColorDrawable(0x14FFFFFF)
            dividerHeight = 1
            selector = ColorDrawable(0x22FFFFFF)
            setOnItemClickListener { _, _, pos, _ -> play(items[pos]) }
            setOnItemLongClickListener { _, _, pos, _ ->
                toggleFav(items[pos])
                true
            }
        }

        now = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        playBtn = pill(getString(R.string.play)).apply { setOnClickListener { toggle() } }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        bottom.addView(now, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bottom.addView(playBtn)

        root.addView(top)
        root.addView(status)
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottom)
        setContentView(root)

        showFavorites()
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun refreshFavs() {
        favUrls = Prefs.favorites(this).map { it.url }.toSet()
    }

    private fun showFavorites() {
        showingFavs = true
        refreshFavs()
        val f = Prefs.favorites(this)
        if (f.isEmpty()) {
            search("")
        } else {
            items = f
            sa.notifyDataSetChanged()
            status.text = ""
        }
    }

    private fun search(q: String) {
        showingFavs = false
        status.text = getString(R.string.searching)
        val cc = Locale.getDefault().country
        Thread {
            val res = try {
                RadioDirectory.search(q, cc)
            } catch (_: Exception) {
                null
            }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (res == null) {
                    status.text = getString(R.string.net_error)
                } else {
                    items = res
                    sa.notifyDataSetChanged()
                    status.text = if (q.isBlank() && favUrls.isEmpty()) getString(R.string.no_favs)
                    else getString(R.string.results, res.size)
                }
            }
        }.start()
    }

    private fun play(s: Station) {
        RadioService.play(this, s)
        handler.postDelayed({ refreshNow() }, 300)
    }

    private fun toggle() {
        val st = RadioService.state
        if (st == RadioService.PLAYING || st == RadioService.LOADING) {
            RadioService.stop(this)
        } else {
            (RadioService.station ?: Prefs.lastStation(this))?.let { play(it) }
        }
        handler.postDelayed({ refreshNow() }, 300)
    }

    private fun toggleFav(s: Station) {
        val added = Prefs.toggleFavorite(this, s)
        Toast.makeText(this, if (added) R.string.fav_added else R.string.fav_removed, Toast.LENGTH_SHORT).show()
        refreshFavs()
        if (showingFavs) showFavorites() else sa.notifyDataSetChanged()
    }

    private fun refreshNow() {
        val s = RadioService.station
        val st = RadioService.state
        now.text = when {
            s == null -> getString(R.string.nothing)
            st == RadioService.LOADING -> s.name + "  …"
            st == RadioService.PLAYING -> "● " + s.name
            st == RadioService.ERROR -> s.name + " – " + getString(R.string.stream_error)
            else -> s.name
        }
        playBtn.text = getString(
            if (st == RadioService.PLAYING || st == RadioService.LOADING) R.string.stop else R.string.play
        )
        if (st != lastState) {
            lastState = st
            sa.notifyDataSetChanged()
        }
    }

    private inner class StationAdapter : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(p: Int): Any = items[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, v: View?, parent: ViewGroup?): View {
            val tv = v as? TextView ?: TextView(this@RadioActivity).apply {
                textSize = 18f
                setPadding(dp(14), dp(14), dp(14), dp(14))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val s = items[p]
            val cur = RadioService.station?.url == s.url &&
                (RadioService.state == RadioService.PLAYING || RadioService.state == RadioService.LOADING)
            tv.text = (if (favUrls.contains(s.url)) "★  " else "") + s.name
            tv.setTextColor(if (cur) accent else Color.WHITE)
            return tv
        }
    }
}
