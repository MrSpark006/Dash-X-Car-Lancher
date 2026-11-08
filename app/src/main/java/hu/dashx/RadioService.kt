package hu.dashx

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/** Internet radio playback in a foreground service, so it keeps playing under any other app. */
class RadioService : Service() {

    companion object {
        const val IDLE = 0
        const val LOADING = 1
        const val PLAYING = 2
        const val ERROR = 3

        private const val A_PLAY = "hu.dashx.PLAY"
        private const val A_TOGGLE = "hu.dashx.TOGGLE"
        private const val A_STOP = "hu.dashx.STOP"

        @Volatile
        var station: Station? = null

        @Volatile
        var state: Int = IDLE

        fun play(c: Context, s: Station) = send(
            c, Intent(c, RadioService::class.java).setAction(A_PLAY)
                .putExtra("n", s.name).putExtra("u", s.url)
        )

        fun toggle(c: Context) = send(c, Intent(c, RadioService::class.java).setAction(A_TOGGLE))

        fun stop(c: Context) {
            if (state == IDLE) return
            send(c, Intent(c, RadioService::class.java).setAction(A_STOP))
        }

        private fun send(c: Context, i: Intent) {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }
    }

    private var mp: MediaPlayer? = null
    private var focusGiven = false
    private lateinit var am: AudioManager

    private val focus = AudioManager.OnAudioFocusChangeListener { change ->
        try {
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS -> shutdown()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> mp?.pause()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> mp?.setVolume(0.25f, 0.25f)
                AudioManager.AUDIOFOCUS_GAIN -> {
                    mp?.setVolume(1f, 1f)
                    if (state == PLAYING && mp?.isPlaying == false) mp?.start()
                }
            }
        } catch (_: Exception) {
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        if (i == null) {
            goForeground("Radio", "")
            shutdown()
            return START_NOT_STICKY
        }
        when (i.action) {
            A_PLAY -> {
                val s = Station(i.getStringExtra("n") ?: "Radio", i.getStringExtra("u") ?: "")
                goForeground(s.name, getString(R.string.loading))
                start(s)
            }
            A_TOGGLE -> {
                val s = station
                if (state == PLAYING || state == LOADING) {
                    goForeground(s?.name ?: "Radio", "")
                    shutdown()
                } else if (s != null) {
                    goForeground(s.name, getString(R.string.loading))
                    start(s)
                } else {
                    goForeground("Radio", "")
                    shutdown()
                }
            }
            else -> {
                goForeground("Radio", "")
                shutdown()
            }
        }
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun start(s: Station) {
        releasePlayer()
        station = s
        state = LOADING
        Prefs.setLastStation(this, s)
        if (!focusGiven) {
            am.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            focusGiven = true
        }
        try {
            val p = MediaPlayer()
            mp = p
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            p.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            p.setOnPreparedListener {
                it.start()
                state = PLAYING
                updateNotification(s.name, getString(R.string.live))
            }
            p.setOnErrorListener { _, _, _ ->
                shutdown(ERROR)
                true
            }
            p.setOnCompletionListener { shutdown(ERROR) }
            p.setDataSource(s.url)
            p.prepareAsync()
        } catch (_: Exception) {
            shutdown(ERROR)
        }
    }

    private fun releasePlayer() {
        try {
            mp?.release()
        } catch (_: Exception) {
        }
        mp = null
    }

    @Suppress("DEPRECATION")
    private fun shutdown(newState: Int = IDLE) {
        releasePlayer()
        state = newState
        if (focusGiven) {
            am.abandonAudioFocus(focus)
            focusGiven = false
        }
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        releasePlayer()
        if (state != ERROR) state = IDLE
        super.onDestroy()
    }

    private fun goForeground(title: String, text: String) {
        val n = buildNotification(title, text)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, n)
        }
    }

    private fun updateNotification(title: String, text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(1, buildNotification(title, text))
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(title: String, text: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val b = if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("radio", getString(R.string.t_radio), NotificationManager.IMPORTANCE_LOW)
            )
            Notification.Builder(this, "radio")
        } else {
            Notification.Builder(this)
        }
        val flags = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val pi = PendingIntent.getActivity(this, 0, Intent(this, RadioActivity::class.java), flags)
        return b.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
