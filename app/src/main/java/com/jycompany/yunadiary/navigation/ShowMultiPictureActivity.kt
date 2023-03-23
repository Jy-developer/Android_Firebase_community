package com.jycompany.yunadiary.navigation

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.firebase.storage.FirebaseStorage
import com.jycompany.yunadiary.R
import com.jycompany.yunadiary.util.GlideApp
import kotlinx.android.synthetic.main.activity_show_multi_picture.*
import kotlinx.android.synthetic.main.activity_show_picture2.view.*

class ShowMultiPictureActivity : AppCompatActivity() {
    var imageUriArr = ArrayList<String>()
    val storage = FirebaseStorage.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_show_multi_picture)

        var myPagerAdater = MyMultiPictureShowPager2Adapter()

        imageUriArr = intent.getStringArrayListExtra("imageUri") as ArrayList<String>

        for(imageUri in imageUriArr){
            myPagerAdater.addItem(imageUri)
        }
        show_multiple_pager.adapter = myPagerAdater
        show_multiple_pager.offscreenPageLimit = 2      //미리 2페이지 정도 로딩하게 설정
        multiple_pic_show_indicator.setViewPager(show_multiple_pager)
        var startPosition = intent.getIntExtra("whereThePageIs", 0)
        show_multiple_pager.setCurrentItem(startPosition, false)        //원래 보고 있던 사진으로 바로 뷰페이저 페이지 바로 이동
    }

    inner class MyMultiPictureShowPager2Adapter(var items:ArrayList<String> = arrayListOf()) : RecyclerView.Adapter<RecyclerView.ViewHolder>(){
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.activity_show_picture2, parent, false)
            view.progressbar_onzoompicture_loading.visibility = View.GONE
            return CustomViewHolder(view)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val viewHolder = (holder as CustomViewHolder).itemView

            val imageRef = storage!!.reference.child(
                "images/"+items[position].replaceBefore("JPEG", "").replaceAfter("_.png", ""))
            GlideApp.with(viewHolder.context)
                .load(imageRef)
                .placeholder(R.drawable.face_static)
                .thumbnail(Glide.with(viewHolder.context).load(R.raw.loading_gif))
                .dontAnimate()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(viewHolder.photo_view_for_zoom)
        }

        override fun getItemCount(): Int {
            return items.size
        }

        fun addItem(item: String){
            items.add(item)
        }
    }

    inner class CustomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}