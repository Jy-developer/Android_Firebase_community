package com.jycompany.yunadiary.navigation

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import com.jycompany.yunadiary.R
import com.jycompany.yunadiary.model.ContentDTO
import kotlinx.android.synthetic.main.fragment_detail.*
import kotlinx.android.synthetic.main.fragment_grid.view.*


class GridFragment : Fragment() {
    val TAG = "GridFragment_tag"
    var imagesSnapshot : ListenerRegistration? = null
    var mainView : View? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mainView = inflater.inflate(R.layout.fragment_grid, container, false)

//        mainView?.gridfragment_recyclerview?.adapter = GridFragmentRecyclerViewAdapter()        //원 코드는 onResume안에서 어댑터 설정했는데, 이렇게 하면 왠지 사진 받아놓은걸 grid 끌 때마다 다 지우는 것 같아서 onCreateView로 옮김 =>다시 resume으로 옮김. 이유는
                                                                                                    //grid 열었다가 detail 열면 사진 받는게 엄청 느려짐. grid서 계속 다운로드 작업을 백에서 진행하는 느낌.
        // 그리드 격자 무늬로 가로에 3 개 레이아웃으로 설정
//        mainView?.gridfragment_recyclerview?.layoutManager = GridLayoutManager(activity, 3)

        return mainView
    }

    override fun onResume() {
        super.onResume()

        mainView?.gridfragment_recyclerview?.adapter = GridFragmentRecyclerViewAdapter()        //원 코드는 onResume안에서 어댑터 설정했는데, 이렇게 하면 왠지 사진 받아놓은걸 grid 끌 때마다 다 지우는 것 같아서 onCreateView로 옮김
        // 그리드 격자 무늬로 가로에 3 개 레이아웃으로 설정
        mainView?.gridfragment_recyclerview?.layoutManager = GridLayoutManager(activity, 3)
    }

    override fun onStop() {     //Fragment stop()시에 Snapshot 제거
        super.onStop()
        imagesSnapshot?.remove()
    }

    inner class GridFragmentRecyclerViewAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var contentDTOs : ArrayList<ContentDTO>

        init {
            contentDTOs = ArrayList()
            imagesSnapshot = FirebaseFirestore.getInstance().collection("images").orderBy("timestamp")  //orderBy가 문제가 좀 있음
                    ?.addSnapshotListener { querySnapshot, firebaseFirestoreException ->
                        contentDTOs.clear()
                        for(snapshot in querySnapshot!!.documents){
                            contentDTOs.add(snapshot.toObject(ContentDTO::class.java)!!)
                        }
                        contentDTOs.sortByDescending { it.timestamp }
                        notifyDataSetChanged()
                    }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val width = resources.displayMetrics.widthPixels / 3
            val imageView = ImageView(parent.context)
            imageView.layoutParams = LinearLayoutCompat.LayoutParams(width, width)

            return CustomViewHolder(imageView)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            var imageView = (holder as CustomViewHolder).imageView
            val width = resources.displayMetrics.widthPixels / 3

            Glide.with(holder.itemView.context)
                    .load(contentDTOs[position].imageUrl)
                    .override(width, width)
                    .placeholder(R.drawable.loading_image2)
                    .thumbnail(0.1f)
                    .diskCacheStrategy(DiskCacheStrategy.ALL).dontTransform().dontAnimate()
                    .apply(RequestOptions().centerCrop())       //이미지 종횡비 보존 안하는 것 같은 데, 나중에 옵션 재지정 필요할지도.
                    .into(imageView)

            imageView.setOnClickListener {
                //기존 사진 모아보기에서 사진 클릭하면 업로더한 유저의 유저 정보로 들어가던 코드
//                val fragment = UserFragment()
//                val bundle = Bundle()
//
//                bundle.putString("destinationUid", contentDTOs[position].uid)
//                bundle.putString("userId", contentDTOs[position].userId)
//
//                fragment.arguments = bundle
//
//                activity!!.supportFragmentManager.beginTransaction().replace(R.id.main_content, fragment).commit()

                var diaryPosition = contentDTOs.indexOf(contentDTOs[position])

                val detailFrag = DetailViewFragment()
                var arg = Bundle()
                arg.putString("param1", diaryPosition.toString())
                detailFrag.arguments = arg
                activity!!.supportFragmentManager.beginTransaction().replace(R.id.main_content, detailFrag, "detailFragTag").commit()
            }
        }

        override fun getItemCount(): Int {
            return contentDTOs.size
        }
    }

    inner class CustomViewHolder(var imageView : ImageView) : RecyclerView.ViewHolder(imageView){
    }

}