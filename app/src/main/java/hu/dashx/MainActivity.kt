package hu.dashx

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var tiles: Array<TileView>
    private lateinit var dial: DialView
    private lateinit var media: MediaBar

    private val handler = Handler(Looper.getMainLooper())
    private var speedKmh = -1f
    private var lastFix = 0L
    private var controller: MediaController? = null
    private var lm: LocationManager? = null
    private var locating = false

    private val timeFmt by lazy { android.text.format.DateFormat.getTimeFormat(this) }
    private val dateFmt by lazy { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }

    private val tick = object : Runnable {
        override fun run() {
            tickOnce()
            handler.postDelayed(this, 1000)
        }
    }

    private val locListener = object : LocationListener {
        override fun onLocationChanged(l: Location) {
            speedKmh = l.speed * 3.6f
            lastFix = SystemClock.elapsedRealtime()
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)

        tiles = arrayOf(R.id.t0, R.id.t1, R.id.t2, R.id.t3, R.id.t4, R.id.t5)
            .map { findViewById<TileView>(it) }.toTypedArray()
        dial = findViewById(R.id.dial)
        media = findViewById(R.id.media)

        for ((i, t) in tiles.withIndex()) {
            t.setOnClickListener { Actions.run(this, Prefs.slot(this, i)) }
            t.setOnLongClickListener {
                configure(i)
                true
            }
        }
        dial.setOnClickListener {
            Prefs.setClockMode(this, !Prefs.clockMode(this))
            tickOnce()
        }
        dial.setOnLongClickListener {
            showMenu()
            true
        }
        media.onAction = { handleMedia(it) }

        requestPerms()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
        applyImmersive()
        startLocation()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        stopLocation()
        super.onPause()
    }

    // A launcher must not close itself on Back.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    @Suppress("DEPRECATION")
    private fun applyImmersive() {
        window.decorView.systemUiVisibility = if (Prefs.immersive(this)) {
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        } else {
            View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun applyTheme() {
        val acc = Prefs.accent(this)
        for ((i, t) in tiles.withIndex()) {
            t.accent = acc
            t.info = Actions.info(this, Prefs.slot(this, i))
        }
        dial.accent = acc
        media.accent = acc
    }

    // ---- permissions / GPS -------------------------------------------------------------

    private fun requestPerms() {
        if (Build.VERSION.SDK_INT < 23) return
        val need = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            need.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        startLocation()
    }

    private fun startLocation() {
        if (locating) return
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return
        try {
            val m = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            m.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locListener)
            lm = m
            locating = true
        } catch (_: Exception) {
        }
    }

    private fun stopLocation() {
        try {
            lm?.removeUpdates(locListener)
        } catch (_: Exception) {
        }
        locating = false
    }

    // ---- 1 Hz tick ---------------------------------------------------------------------

    private fun tickOnce() {
        val now = Date()
        val fresh = SystemClock.elapsedRealtime() - lastFix < 5000
        dial.update(
            Prefs.clockMode(this),
            if (fresh) speedKmh else -1f,
            timeFmt.format(now),
            dateFmt.format(now),
            Calendar.getInstance().get(Calendar.SECOND)
        )
        pollMedia()
    }

    // ---- now playing -------------------------------------------------------------------

    private fun listenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?: return false
        return flat.contains(packageName)
    }

    private fun findController(): MediaController? {
        if (!listenerEnabled()) return null
        return try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val list = msm.getActiveSessions(ComponentName(this, MediaListener::class.java))
            list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: list.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    private fun radioActive(): Boolean =
        RadioService.station != null &&
            (RadioService.state == RadioService.PLAYING || RadioService.state == RadioService.LOADING)

    private fun pollMedia() {
        val rs = RadioService.station
        if (rs != null && radioActive()) {
            controller = null
            val playing = RadioService.state == RadioService.PLAYING
            val sub = if (playing) "RADIO · " + getString(R.string.live) else "RADIO · " + getString(R.string.loading)
            media.set(rs.name, sub, playing)
            return
        }
        val c = findController()
        controller = c
        if (c != null) {
            val md = c.metadata
            val app = appLabel(c.packageName)
            val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: app
            val sub = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: app
            media.set(title, sub, c.playbackState?.state == PlaybackState.STATE_PLAYING)
        } else {
            media.set(
                getString(R.string.nothing),
                if (listenerEnabled()) "" else getString(R.string.tap_enable),
                false
            )
        }
    }

    private fun handleMedia(idx: Int) {
        val c = controller
        if (idx == 3) {
            when {
                radioActive() -> startActivity(Intent(this, RadioActivity::class.java))
                c != null -> packageManager.getLaunchIntentForPackage(c.packageName)?.let { startActivity(it) }
                !listenerEnabled() -> openListenerSettings()
                else -> startActivity(Intent(this, RadioActivity::class.java))
            }
            return
        }
        if (radioActive()) {
            if (idx == 1) RadioService.toggle(this) else stepRadio(if (idx == 0) -1 else 1)
        } else if (c != null) {
            val t = c.transportControls
            when (idx) {
                0 -> t.skipToPrevious()
                2 -> t.skipToNext()
                else -> if (c.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play()
            }
        } else {
            val last = Prefs.lastStation(this)
            if (idx == 1 && last != null && listenerEnabled()) {
                RadioService.play(this, last)
            } else {
                sendKey(
                    when (idx) {
                        0 -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                        2 -> KeyEvent.KEYCODE_MEDIA_NEXT
                        else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    }
                )
            }
        }
        handler.postDelayed({ pollMedia() }, 400)
    }

    private fun stepRadio(dir: Int) {
        val favs = Prefs.favorites(this)
        if (favs.isEmpty()) return
        val cur = RadioService.station
        val idx = favs.indexOfFirst { it.url == cur?.url }
        val next = if (idx < 0) 0 else (idx + dir + favs.size) % favs.size
        RadioService.play(this, favs[next])
    }

    private fun sendKey(code: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun openListenerSettings() {
        try {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    // ---- dialogs -----------------------------------------------------------------------

    private fun configure(slot: Int) {
        val items = arrayOf(
            getString(R.string.choose_app),
            getString(R.string.b_radio),
            getString(R.string.b_apps),
            getString(R.string.reset)
        )
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.configure_button)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> startActivity(
                        Intent(this, AppDrawerActivity::class.java)
                            .putExtra(AppDrawerActivity.EXTRA_PICK_SLOT, slot)
                    )
                    1 -> {
                        Prefs.setSlot(this, slot, "builtin:radio")
                        applyTheme()
                    }
                    2 -> {
                        Prefs.setSlot(this, slot, "builtin:drawer")
                        applyTheme()
                    }
                    else -> {
                        Prefs.setSlot(this, slot, null)
                        applyTheme()
                    }
                }
            }
            .show()
    }

    private fun showMenu() {
        val items = arrayOf(
            getString(R.string.m_accent),
            getString(R.string.m_media),
            getString(R.string.m_home),
            getString(R.string.m_full)
        )
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.app_name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        Prefs.nextAccent(this)
                        applyTheme()
                    }
                    1 -> openListenerSettings()
                    2 -> try {
                        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                    } catch (_: Exception) {
                    }
                    3 -> {
                        Prefs.setImmersive(this, !Prefs.immersive(this))
                        applyImmersive()
                    }
                }
            }
            .show()
    }
}
