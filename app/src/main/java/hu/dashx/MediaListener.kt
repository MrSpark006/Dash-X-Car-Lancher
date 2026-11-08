package hu.dashx

import android.service.notification.NotificationListenerService

/**
 * Empty on purpose. Having the user grant notification access to this service is what
 * allows MediaSessionManager.getActiveSessions() to show what Spotify, YouTube Music, etc. are playing.
 */
class MediaListener : NotificationListenerService()
