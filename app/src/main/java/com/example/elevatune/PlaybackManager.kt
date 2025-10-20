package com.example.elevatune

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class PlaybackManager(private val context: Context) {
    private var player: ExoPlayer? = null

    var isPaused: Boolean = false;
    var isStopped: Boolean = false;

    init {
        player = ExoPlayer.Builder(context).build()
    }

    fun setSource(uri: Uri) {
        val mediaItem = MediaItem.fromUri(uri)
        player?.setMediaItem(mediaItem)
        player?.prepare()
    }

    fun play() {
        if (isStopped) {
            player?.seekTo(0);
            player?.prepare()
            player?.playWhenReady = true;
        }

        player?.play()
        isPaused = false
        isStopped = false
    }

    fun pause() {
        player?.pause()
        isPaused = true
    }

    fun stop() {
        player?.stop()
        isStopped = true;
    }

    fun getPlayer(): ExoPlayer? = player

    fun release(){
        player?.release()
        player = null
    }

    fun getCurrentPosition(): Long = player?.currentPosition ?: 0L
}
