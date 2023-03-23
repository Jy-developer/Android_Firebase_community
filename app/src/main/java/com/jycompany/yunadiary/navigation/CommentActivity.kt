package com.jycompany.yunadiary.navigation

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.jycompany.yunadiary.R
import com.jycompany.yunadiary.model.AlarmDTO
import com.jycompany.yunadiary.model.ContentDTO
import com.jycompany.yunadiary.model.UsersInfoDTO
import com.jycompany.yunadiary.util.FcmPush
import kotlinx.android.synthetic.main.activity_comment.*
import kotlinx.android.synthetic.main.item_comment.view.*

class CommentActivity : AppCompatActivity() {
    var contentUid : String? = null
    var user : FirebaseUser? = null
    var destinationUid : String? = null
    var commentSnapshot : ListenerRegistration? = null
    var fcmPush : FcmPush? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comment)

        user = FirebaseAuth.getInstance().currentUser
        fcmPush = FcmPush()
        destinationUid = intent.getStringExtra("destinationUid")    //글 작성자의 uid
        contentUid = intent.getStringExtra("contentUid")            //images 콜렉션 아래 다큐먼트명

        //아래는 코멘트 추가하려고 하는 그림을 서버에서 받아와서 보여줌.
        FirebaseFirestore.getInstance()
                .collection("images")
                .document(contentUid!!)
                .get().addOnCompleteListener { task ->
                    if(task.isSuccessful){
                        val url = task.result?.get("imageUrl").toString()
                        Glide.with(this)
                                .load(url)
                                .thumbnail(0.5f)
                                .into(comment_activity_main_picture)
                    }
                }

        comment_btn_send.setOnClickListener {
            if(comment_edit_message.text.toString().isNullOrBlank()){
                Toast.makeText(this, getString(R.string.comment_upload_error), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val comment = ContentDTO.Comment()
//            comment.userId = FirebaseAuth.getInstance().currentUser!!.email         //기존코드: 덧글 남긴이가 이메일로 표시됨.
            FirebaseFirestore.getInstance()
                .collection("usersInfo").document(user!!.uid)
                .get().addOnCompleteListener { task ->
                    if(task.isSuccessful){
                        var meInfoDTO = task.result?.toObject(UsersInfoDTO::class.java)
                        val relationStr = meInfoDTO?.relation
                        val commentStr = comment_edit_message.text.toString()
                        val uidStr = FirebaseAuth.getInstance().currentUser!!.uid
                        val timestampStr = System.currentTimeMillis().toString()

                        comment.userId = relationStr                        //덧글 남기는 이를 자신의 "관계호칭"으로 남김
                        comment.comment = commentStr
                        comment.uid = uidStr
                        comment.timestamp = timestampStr.toLong()
                        FirebaseFirestore.getInstance()
                                .collection("images")
                                .document(contentUid!!)
                                .collection("comments")
                                .document()     //하위 콜렉션인 comments 콜렉션에 무작위이름으로 다큐먼트 생성
                                .set(comment)

                        commentAlarm(destinationUid!!, comment_edit_message.text.toString())        //alarm 메소드 ( DB에 alarms 콜렉션에 document생성 )
                        comment_edit_message.setText("")

                        //이하는 my images>commentList 에 들어갈 DB업데이트 코드
                        var commentGotoArrayList : String?
                        // userId [] uid [] comment [] timestamp(Comment의 timestamp 는 원래 Long임에 주의. 맞게맞게 변환해줘야 함)  합체된 문자열 구조
                        commentGotoArrayList = relationStr +"[]"+uidStr+"[]"+commentStr+"[]"+timestampStr
                        val imageCommentRef = FirebaseFirestore.getInstance()
                                .collection("images")
                                .document(contentUid!!)
                        imageCommentRef.update("commentList", FieldValue.arrayUnion(commentGotoArrayList))
                        finish()        //코멘트 남기면 원래 MainActivity로 돌아가게 만듬.
                    }
                }
        }       //여기까지가 코멘트 보내기 버튼 눌렀을 때 내용 코드 (DB의 images 콜렉션을 수정하고, 각 다큐먼트에 comments라는 콜렉션을 만들고 그 아래 다큐먼트 만들어서 각 코멘트를 저장함

        //기존 코멘트를 표시하지 않고, 그냥 사진만 표시하게 바꾸므로 주석 처리함
//        comment_recyclerview.adapter = CommentRecyclerViewAdapter()
//        comment_recyclerview.layoutManager = LinearLayoutManager(this)
    }

    override fun onStop() {
        super.onStop()
        commentSnapshot?.remove()
    }

    fun commentAlarm(destinationUid : String, message : String){
        val alarmDTO = AlarmDTO()
        alarmDTO.destinationUid = destinationUid
        alarmDTO.userId = user?.email
        alarmDTO.uid = user?.uid
        alarmDTO.kind = 1
        alarmDTO.message = message
        alarmDTO.timestamp = System.currentTimeMillis()

        FirebaseFirestore.getInstance().collection("alarms").document().set(alarmDTO)   //db 생성
        var message = user?.email + getString(R.string.alarm_who)+
                message+getString(R.string.alarm_comment)
//        fcmPush?.sendMessage(destinationUid, getString(R.string.push_title), message)     //코멘트 남길떄마다 푸쉬 가는거 일단 정지함
    }

    //아래는 이제 코멘트 표시하지 않고 사진만 표시하기로 하였으므로, 필요없는 클래스가 되어 주석 처리함
//    inner class CommentRecyclerViewAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>(){
//        val comments : ArrayList<ContentDTO.Comment>
//
//        init {
//            comments = ArrayList()
//            commentSnapshot = FirebaseFirestore.getInstance()
//                .collection("images")
//                .document(contentUid!!)
//                .collection("comments")
//                .addSnapshotListener { querySnapshot, firebaseFirestoreException ->
//                    comments.clear()
//                    if(querySnapshot == null)return@addSnapshotListener
//                    for(snapshot in querySnapshot?.documents!!){
//                        comments.add(snapshot.toObject(ContentDTO.Comment::class.java)!!)
//                    }
//                    notifyDataSetChanged()
//                }
//        }
//
//        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
//            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
//            return CustomViewHolder(view)
//        }
//
//        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
//            var view = holder.itemView
//            //Profile Image
//            FirebaseFirestore.getInstance()
//                .collection("profileImages")
//                .document(comments[position].uid!!)
//                .addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
//                    if(documentSnapshot?.data != null){
//                        val url = documentSnapshot?.data!!["image"]
//                        Glide.with(holder.itemView.context)
//                            .load(url)
//                            .apply(RequestOptions().circleCrop())
//                            .into(view.commentviewitem_imageview_profile)
//                    }
//                }
//            view.commentviewitem_textview_profile.text = comments[position].userId
//            view.commentviewitem_textview_comment.text = comments[position].comment
//        }
//
//        override fun getItemCount(): Int {
//            return comments.size
//        }
//
//        inner class CustomViewHolder(itemView:View) : RecyclerView.ViewHolder(itemView)
//    }
}