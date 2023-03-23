package com.jycompany.yunadiary.navigation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.jycompany.yunadiary.R
import kotlinx.android.synthetic.main.activity_show_picture2.*
import java.io.IOException
import java.net.URL

class ShowPictureActivity2 : AppCompatActivity() {
    var imageUri : String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_show_picture2)

        imageUri= intent.getStringExtra("imageUri")

//        Glide.with(this)
//                .load(R.drawable.loading_image2)
//                .placeholder(R.drawable.loading_image2)
//                .into(photo_view_for_zoom)          //미리 로딩중 이미지 로드
//        Glide.with(this)
//            .asGif()
//            .load(R.raw.loading_gif)
//            .into(photo_view_for_zoom)          //gif 로딩 코드
        Glide.with(this)
            .load(imageUri)
            .placeholder(R.drawable.face_static)
            .thumbnail(Glide.with(this).load(R.raw.loading_gif))
            .dontAnimate()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(photo_view_for_zoom)

//        var asyncTaskRunner : AsyncTaskRunner = AsyncTaskRunner(imageUri!!)
//        asyncTaskRunner.execute()
    }

    inner class AsyncTaskRunner: AsyncTask<String, String, Bitmap> {
        var Imageurl : String? = null
        constructor(Imageurl : String){
            this.Imageurl = Imageurl
        }

        override fun onPreExecute() {
            super.onPreExecute()
            progressbar_onzoompicture_loading.visibility = View.VISIBLE
        }

        override fun doInBackground(vararg params: String?): Bitmap? {
            try {
                var url : URL = URL(Imageurl)
                var image = BitmapFactory.decodeStream(url.openConnection().getInputStream())
                return image
            }catch (e : IOException){
            }
            return null
        }

        override fun onPostExecute(result: Bitmap?) {
            super.onPostExecute(result)

            if (result != null) {
                progressbar_onzoompicture_loading.visibility = View.GONE
                photo_view_for_zoom.setImageBitmap(result)
            }
        }
    }
}