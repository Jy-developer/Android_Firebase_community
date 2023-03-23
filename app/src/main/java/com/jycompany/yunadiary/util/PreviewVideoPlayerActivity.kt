package com.jycompany.yunadiary.util

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.jycompany.yunadiary.R
import kotlinx.android.synthetic.main.activity_preview_video_player.*
import java.io.File

class PreviewVideoPlayerActivity : AppCompatActivity() {
    private lateinit var exoPlayer: SimpleExoPlayer
    private var uri = ""
    private var shouldDelete = ""

    companion object {
        fun start(activity: Activity, uri: String) {
            val intent = Intent(activity, PreviewVideoPlayerActivity::class.java)
                .putExtra("uri", uri)
            activity.startActivity(intent)
        }
        fun start(activity: Activity, uri: String, shouldDelete : String) {
            val intent = Intent(activity, PreviewVideoPlayerActivity::class.java)
                .putExtra("uri", uri)
            intent.putExtra("shouldDelete", shouldDelete)
            activity.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview_video_player)

        intent?.extras?.let {
            uri = it.getString("uri", "")
        }
        initializePlayer()
    }

    private fun initializePlayer() {

        val trackSelector = DefaultTrackSelector(this)
        val loadControl = DefaultLoadControl()
        val rendererFactory = DefaultRenderersFactory(this)

        exoPlayer = SimpleExoPlayer.Builder(this, rendererFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .build()
    }

    private fun play(uri: Uri) {

        val userAgent = Util.getUserAgent(this, getString(R.string.app_name))
        val mediaSource = ProgressiveMediaSource
            .Factory(DefaultDataSourceFactory(this, userAgent))
            .createMediaSource(uri)

        ep_video_view.player = exoPlayer

        exoPlayer.prepare(mediaSource)
        exoPlayer.playWhenReady = true
    }

    override fun onStart() {
        super.onStart()
        playVideo()
    }

    private fun playVideo() {
        val file = File(uri)
        val localUri = Uri.fromFile(file)
        play(localUri)
    }

    override fun onStop() {
        super.onStop()
        exoPlayer.stop()
        exoPlayer.release()
        if(shouldDelete == ""){
            //shouldDelete 값이 없으면 동작 X
        }else{
            val deleted = File(shouldDelete).delete()     //파일이 새롭게 생성됐으니 삭제함
            Log.d("AddYtubMovieAct_tag", "프리뷰 플레이어 끝날때 파일 삭제함 : "+deleted.toString())
        }
    }
}