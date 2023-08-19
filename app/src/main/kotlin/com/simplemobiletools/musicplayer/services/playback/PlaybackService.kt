package com.simplemobiletools.musicplayer.services.playback

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.os.postDelayed
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.*
import com.simplemobiletools.commons.extensions.hasPermission
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.isPlayingOrBuffering
import com.simplemobiletools.musicplayer.extensions.nextMediaItem
import com.simplemobiletools.musicplayer.helpers.NotificationHelper
import com.simplemobiletools.musicplayer.helpers.getPermissionToRequest
import com.simplemobiletools.musicplayer.services.playback.library.MediaItemProvider
import com.simplemobiletools.musicplayer.services.playback.player.PlayerListener
import com.simplemobiletools.musicplayer.services.playback.player.SimpleMusicPlayer
import com.simplemobiletools.musicplayer.services.playback.player.initializeSessionAndPlayer

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    internal lateinit var player: SimpleMusicPlayer
    internal lateinit var playerThread: HandlerThread
    internal lateinit var handler: Handler
    internal lateinit var mediaSession: MediaLibrarySession
    internal lateinit var mediaItemProvider: MediaItemProvider

    internal var listener: PlayerListener? = null
    internal var currentRoot = ""

    override fun onCreate() {
        super.onCreate()
        initializeSessionAndPlayer(handleAudioFocus = true, handleAudioBecomingNoisy = true, skipSilence = config.gaplessPlayback)
        mediaItemProvider = MediaItemProvider(this)

        // we may or may not have storage permission at this time
        if (hasPermission(getPermissionToRequest())) {
            mediaItemProvider.reload()
        } else {
            showNoPermissionNotification()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    private fun releaseMediaSession() {
        mediaSession.release()
        withPlayer {
            removeListener(listener!!)
            release()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaSession()
        clearListener()
        stopSleepTimer()
    }

    fun stopService() {
        withPlayer {
            player.pause()
            player.stop()
        }

        stopSelf()
    }

    internal fun withPlayer(callback: Player.() -> Unit) {
        handler.post {
            callback(player)
        }
    }

    private fun showNoPermissionNotification() {
        Handler(Looper.getMainLooper()).postDelayed(delayInMillis = 100L) {
            try {
                startForeground(
                    NotificationHelper.NOTIFICATION_ID,
                    NotificationHelper.createInstance(this).createNoPermissionNotification()
                )
            } catch (ignored: Exception) {
            }
        }
    }

    companion object {
        // Initializing a media controller might take a noticeable amount of time thus we expose current playback info here to keep things as quick as possible.
        var isPlaying: Boolean = false
        var currentMediaItem: MediaItem? = null
        var nextMediaItem: MediaItem? = null

        fun updatePlaybackInfo(player: Player) {
            currentMediaItem = player.currentMediaItem
            nextMediaItem = player.nextMediaItem
            isPlaying = player.isPlayingOrBuffering
        }
    }
}

