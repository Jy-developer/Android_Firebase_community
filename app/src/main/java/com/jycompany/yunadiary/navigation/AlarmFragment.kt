package com.jycompany.yunadiary.navigation

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jycompany.yunadiary.R
import com.jycompany.yunadiary.model.AlarmDTO
import kotlinx.android.synthetic.main.fragment_alarm.view.*
import kotlinx.android.synthetic.main.item_comment.view.*

class AlarmFragment : Fragment() {
    var myActivity : Activity? = null
    val TAG = "AlarmFrag_tag"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_alarm, container, false)
        view.alarmfragment_recyclerview.adapter = AlarmRecyclerViewAdapter()
        view.alarmfragment_recyclerview.layoutManager = LinearLayoutManager(activity)           //Fragment내에선 context를 activity(getActivity()) 로 넣는게 좋음

        myActivity = activity
        return view
    }

    inner class AlarmRecyclerViewAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>(){
        val alarmDTOList = ArrayList<AlarmDTO>()

        init {
            val uid = FirebaseAuth.getInstance().currentUser!!.uid

            FirebaseFirestore.getInstance()
                .collection("alarms")
                .whereEqualTo("destinationUid", uid)
                .addSnapshotListener { querySnapshot, firebaseFirestoreException ->
                    alarmDTOList.clear()
                    if(querySnapshot == null)return@addSnapshotListener
                    for(snapshot in querySnapshot?.documents!!){
                        alarmDTOList.add(snapshot.toObject(AlarmDTO::class.java)!!)
                    }
                    alarmDTOList.sortByDescending { it.timestamp }      //오오... 가져온 데이터 정렬코드. UserFragment에 활용할 것
                    notifyDataSetChanged()
                }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(activity).inflate(R.layout.item_comment, parent, false)
            return CustomViewHolder(view)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val profileImage = holder.itemView.commentviewitem_imageview_profile
            val commentTextView = holder.itemView.commentviewitem_textview_profile
            val viewHolder = (holder as CustomViewHolder).itemView

            FirebaseFirestore.getInstance()
                .collection("profileImages")
                .document(alarmDTOList[position].uid!!)
                .get()
                .addOnCompleteListener { task ->
                    if(task.isSuccessful){
                        val url = task.result?.get("image")
                        if(url == null){            //프로필 사진 지정안했으면
                            Glide.with(holder.itemView.context)
                                .load(R.drawable.profile_default)
                                .into(viewHolder.commentviewitem_imageview_profile)
                        }else{                      //프로필 지정 했으면
                            Glide.with(myActivity!!)
                                .load(url)
                                .apply(RequestOptions().circleCrop())
                                .into(profileImage)
                        }
                    }
                }
            when(alarmDTOList[position].kind){
                0 -> {
                    val str_0 = alarmDTOList[position].userId + getString(R.string.alarm_favorite)
                    commentTextView.text = str_0
                }
                1 -> {
                    val str_1 = alarmDTOList[position].userId +
                            getString(R.string.alarm_who)+
                            alarmDTOList[position].message+
                            getString(R.string.alarm_comment)
                    commentTextView.text = str_1
                }
                2 -> {
                    val str_2 = alarmDTOList[position].userId + getString(R.string.alarm_follow)
                    commentTextView.text = str_2
                }
            }
        }

        override fun getItemCount(): Int {
            return alarmDTOList.size
        }

        inner class CustomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
    }
}