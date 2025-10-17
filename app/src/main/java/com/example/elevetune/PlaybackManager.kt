package com.example.elevetune

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class PlaybackManager(private val context: Context) {
    private var player: ExoPlayer? = null

    fun init() {
        player = ExoPlayer.Builder(context).build()
    }

    fun setSource(uri: Uri) {
        val mediaItem = MediaItem.fromUri(uri)
        player?.setMediaItem(mediaItem)
        player?.prepare()
    }

    fun play() {
        player?.play()
    }

    fun pause() {
        player?.pause()
    }

    fun stop() {
        player?.stop()
    }

    fun getPlayer(): ExoPlayer? = player

    fun release(){
        player?.release()
        player = null
    }
}
