package com.jycompany.yunadiary.navigation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.LinearLayout
import com.jycompany.yunadiary.R
import com.jycompany.yunadiary.views.ImageDisplayView
import kotlinx.android.synthetic.main.activity_show_picture.*
import java.io.IOException
import java.net.URL

class ShowPictureActivity : AppCompatActivity() {
    var imageUri : String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_show_picture)

        imageUri= intent.getStringExtra("imageUri")

        var asyncTaskRunner : AsyncTaskRunner = AsyncTaskRunner(imageUri!!)
        asyncTaskRunner.execute()
    }

    inner class AsyncTaskRunner: AsyncTask<String, String, Bitmap> {
        var Imageurl : String? = null
        constructor(Imageurl : String){
            this.Imageurl = Imageurl
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

            var view : ImageDisplayView = ImageDisplayView(applicationContext)
            if (result != null) {
                view.setImageData(result)
            }
            var params : LinearLayout.LayoutParams =
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            pictureContainer.addView(view, params)
        }
    }
}