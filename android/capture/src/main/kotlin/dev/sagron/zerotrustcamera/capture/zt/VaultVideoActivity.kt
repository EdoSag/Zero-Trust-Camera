package dev.sagron.zerotrustcamera.capture.zt

import android.app.Activity
import android.graphics.Color
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.Toast
import dev.sagron.zerotrustcamera.capture.R
import java.io.RandomAccessFile

/**
 * Plays a video from the Private vault without ever writing it out in the clear: the player
 * reads the sealed file through MediaVault.Reader, one opened chunk at a time, in memory.
 * Needs the vault to be unlocked; leaving this screen for another app ends it.
 */
class VaultVideoActivity : Activity(), SurfaceHolder.Callback, MediaController.MediaPlayerControl {
    private var player: MediaPlayer? = null
    private var source: SealedSource? = null
    private lateinit var surface: SurfaceView
    private lateinit var controller: MediaController
    private var prepared = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val id = intent.getStringExtra(EXTRA_ID)
        if (id == null || !VaultSession.isUnlocked) {
            finish()
            return
        }

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        surface = SurfaceView(this)
        root.addView(surface, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        setContentView(root)
        controller = MediaController(this)
        controller.setMediaPlayer(this)
        controller.setAnchorView(root)

        try {
            source = SealedSource(RandomAccessFile(VaultStore.mediaFile(this, id), "r"), VaultSession.privateKey())
        } catch (e: Exception) {
            Log.e(TAG, "open", e)
            Toast.makeText(this, R.string.vault_video_failed, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        surface.holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val src = source ?: return
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setDataSource(src)
            mp.setDisplay(holder)
            mp.setOnPreparedListener {
                prepared = true
                fitVideo(it.videoWidth, it.videoHeight)
                it.start()
                controller.show()
            }
            mp.setOnErrorListener { _, _, _ ->
                Toast.makeText(this, R.string.vault_video_failed, Toast.LENGTH_LONG).show()
                finish()
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "play", e)
            Toast.makeText(this, R.string.vault_video_failed, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun fitVideo(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val parent = surface.parent as FrameLayout
        val pw = parent.width.toFloat()
        val ph = parent.height.toFloat()
        val scale = minOf(pw / w, ph / h)
        surface.layoutParams = FrameLayout.LayoutParams((w * scale).toInt(), (h * scale).toInt(), Gravity.CENTER)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        release()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && prepared) {
            if (controller.isShowing) controller.hide() else controller.show()
        }
        return true
    }

    override fun onStop() {
        super.onStop()
        // Gone to another app or the screen went off: the video stops and the vault locks.
        if (!isChangingConfigurations) {
            release()
            if (!isFinishing) {
                VaultSession.lock()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        release()
        try {
            source?.close()
        } catch (_: Exception) {
        }
        source = null
    }

    private fun release() {
        prepared = false
        player?.release()
        player = null
    }

    // MediaPlayerControl
    override fun start() { player?.start() }
    override fun pause() { player?.pause() }
    override fun getDuration(): Int = if (prepared) player?.duration ?: 0 else 0
    override fun getCurrentPosition(): Int = if (prepared) player?.currentPosition ?: 0 else 0
    override fun seekTo(pos: Int) { player?.seekTo(pos) }
    override fun isPlaying(): Boolean = prepared && player?.isPlaying == true
    override fun getBufferPercentage(): Int = 100
    override fun canPause(): Boolean = true
    override fun canSeekBackward(): Boolean = true
    override fun canSeekForward(): Boolean = true
    override fun getAudioSessionId(): Int = player?.audioSessionId ?: 0

    /** The sealed file as a MediaDataSource. MediaPlayer calls it from its own thread. */
    private class SealedSource(file: RandomAccessFile, priv: ByteArray) : MediaDataSource() {
        private val reader = MediaVault.Reader(file.channel, priv)

        @Synchronized
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int = try {
            reader.read(position, buffer, offset, size)
        } catch (e: Exception) {
            Log.e(TAG, "readAt", e)
            -1
        }

        override fun getSize(): Long = reader.size()

        @Synchronized
        override fun close() {
            reader.close()
        }
    }

    companion object {
        private const val TAG = "VaultVideoActivity"
        const val EXTRA_ID = "id"
    }
}
