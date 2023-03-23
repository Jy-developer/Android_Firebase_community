package com.jycompany.yunadiary.navigation

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.jycompany.yunadiary.MainActivity
import com.jycompany.yunadiary.R
import com.jycompany.yunadiary.model.AuthenticDTO
import com.jycompany.yunadiary.model.ContentDTO
import com.jycompany.yunadiary.model.UsersInfoDTO
import com.jycompany.yunadiary.util.FcmPush
import com.squareup.okhttp.*
import kotlinx.android.synthetic.main.activity_add_youtube_movie.*
import java.io.*
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class AddYoutubeMovieActivity : AppCompatActivity() {
    val REQUEST_SELECT_VIDEO_FOR_YOUTUBE = 888
    val TAG = "AddYtubMovieAct_tag"
    val IMAGE_URL = "imageURL"
    val DIARY_CONTENT = "diary_content"
    val TIMESTAMP = "timeStamp"
    val IMAGEFILENAME = "imageFileName"
    var youtubeIdPart : String? = ""

    var inStream : InputStream? = null

    var image_url : String? = null

    var storage : FirebaseStorage? = null
    var firestore : FirebaseFirestore? = null
    private var auth : FirebaseAuth? = null
    var watcher : TextWatcher? = null

    val minimumTextLength = 10                                       //일기내용 최소 글자 수
    var isForUpdate : Boolean = false

    var timeStamp : Long? = null
    var imageFileName : String? = null
    var documentId : String? = null

    var fcmPush : FcmPush? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_youtube_movie)

        storage = FirebaseStorage.getInstance()
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()           //한번 제대로 가입한 user의 정보를 담고 있음. 가입 안한 초기상태거나 로그아웃 했으면 auth.currentuser -> null이 됨.
        fcmPush = FcmPush()

        watcher = object : TextWatcher{
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if(add_movie_edit_explain.text.toString().trim().length >= minimumTextLength){
                    addmovie_show_byte_count.setTextColor(resources.getColor(R.color.colorTextOver140Green))
                    addmovie_show_byte_count.text = add_movie_edit_explain.text.toString().trim().length.toString() + getString(R.string.word_count)
                }else{
                    addmovie_show_byte_count.setTextColor(resources.getColor(R.color.red))
                    addmovie_show_byte_count.text = getString(R.string.minimun_length_show)+add_movie_edit_explain.text.toString().trim().length.toString() + getString(R.string.word_count)
                }
            }
            override fun afterTextChanged(s: Editable?) {
            }
        }

        val receivedIntent : Intent = intent
        if(receivedIntent != null){
            if(receivedIntent.hasExtra(IMAGE_URL)){         //DetailView에서 글 수정버튼 눌러서 들어오는 부분. 따라서 유튭링크주소와, 영상표지 버튼은 안보이게
                Log.d(TAG, "어느 인텐트로 오는지 확인 : 첫번째 imageUrl 번들로 옴")
                isForUpdate = true      //업데이트이므로 플래그 수정

                image_url = receivedIntent.getStringExtra(IMAGE_URL)

                //1. 업로드했던 그림 표시
                Glide.with(applicationContext)
                    .load(receivedIntent.getStringExtra(IMAGE_URL))
                    .thumbnail(0.5f)
                    .into(add_movie_image)

                //2. 업로드했던 일기 내용 표시
                add_movie_edit_explain.setText(receivedIntent.getStringExtra(DIARY_CONTENT))
                //3. Storage에 올라가 있는 기존 이미지 파일명 ( 추후 업로드시 수정을 위해서 )
                imageFileName = receivedIntent.getStringExtra(IMAGEFILENAME)
                //4. timeStamp 가져오기
                timeStamp = receivedIntent.getLongExtra(TIMESTAMP, 0)
                hideLinkAndCoverBtn()       //정상적인 로딩 됐으므로 주소창 등 숨김
            }else if(Intent.ACTION_SEND.equals(receivedIntent.action) && receivedIntent.type != null){      //유튜브에서 공유 인텐트 받을때, 타앱에서 잘못된 문자 인텐트공유도 여기로. (유나 앱 실행중 여부는 무관)
                checkIsUserRight(auth?.currentUser)           ////외부 앱에서 공유링크로 바로 들어오기 때문에, 정상 사용자인지 체크필요. 못 통과하면 finish() 될 것임

                Log.d(TAG, "어느 인텐트로 오는지 확인 : 중간 공유 링크로 옴")
                if(receivedIntent.type.equals("text/plain")){
                    val youtubeFullLink : String?  = receivedIntent.getStringExtra(Intent.EXTRA_TEXT)        //형태 : https://youtu.be/kBo8L1NuGGA
                    //가능한 링크 형태들. 단 반드시 뒤 영상 id는 11자리라고 함.
//http://youtu.be/dQw4w9WgXcQ
//http://www.youtube.com/embed/dQw4w9WgXcQ
//http://www.youtube.com/watch?v=dQw4w9WgXcQ
//http://www.youtube.com/?v=dQw4w9WgXcQ
//http://www.youtube.com/v/dQw4w9WgXcQ
//http://www.youtube.com/e/dQw4w9WgXcQ
//http://www.youtube.com/user/username#p/u/11/dQw4w9WgXcQ
//http://www.youtube.com/sandalsResorts#p/c/54B8C800269D7C1B/0/dQw4w9WgXcQ
//http://www.youtube.com/watch?feature=player_embedded&v=dQw4w9WgXcQ
//http://www.youtube.com/?feature=player_embedded&v=dQw4w9WgXcQ

//                    if(!youtubeFullLink!!.startsWith("https://youtu.be/")){
//                        Toast.makeText(this, "유튜브 영상 링크만 가능합니다.\n(https://youtu.be/rBo6gxxuGA 등의 형태)", Toast.LENGTH_LONG).show()
//                        finish()
//                    }   //유튜브 아닌 엉뚱한 앱에서 인텐트 공유로 넘어 왔을 때 액티비티 종료함. 유튜브링크 아닐때 여기서 다 거름.
                    if(youtubeFullLink!!.length < 11){      //100% 엉뚱한 공유링크를 들고 왔을 때 바로 kick
                        Toast.makeText(this, "유튜브 영상 링크만 가능합니다.", Toast.LENGTH_LONG).show()
                        finish()
                    }else{      //일단 공유링크가 최소 글자 조건은 충족했을 때
                        val startIndex = youtubeFullLink!!.length - 11      //총 글자수에서 아이디11자리 빼면 아이디시작 인덱스임
                        youtubeIdPart = youtubeFullLink.substring(startIndex)           //맞는지 틀린지는 아래 Glide에서 체크
                        Glide.with(applicationContext)
                            .load("https://img.youtube.com/vi/"+youtubeIdPart+"/0.jpg")           //유튜브 섬네일 주소.
                            .placeholder(R.drawable.face_static)
                            .thumbnail(Glide.with(applicationContext).load(R.raw.loading_gif))
                            .listener(object : RequestListener<Drawable> {
                                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?,
                                                          isFirstResource: Boolean): Boolean {
                                    Toast.makeText(this@AddYoutubeMovieActivity, getString(R.string.abnormal_youtube_link), Toast.LENGTH_SHORT).show()
                                    youtubeIdPart = ""      //유튜브 ID 초기화
                                    finish()        //앱 끝내기
                                    return false
                                }
                                override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?,
                                                             dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                                    Toast.makeText(this@AddYoutubeMovieActivity, getString(R.string.normal_youtube_link), Toast.LENGTH_SHORT).show()
                                    add_movie_edit_explain.setHint(getString(R.string.youtube_link_insert_success))     //내용 부분에 이후 과정 안내 표시
                                    hideLinkAndCoverBtn()       //정상적인 로딩 됐으므로 주소창 등 숨김
                                    return false
                                }
                            })
                            .error(Glide.with(applicationContext).load(R.drawable.load_failed))
                            .dontAnimate()
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .into(add_movie_image)
                    }
                    video_link_for_tnail.setText(youtubeFullLink)         //유튜브 링크 주소창에 자동 붙여넣기
                }//여기 else할 필요는 없다. 애초에 intent가 text/plain 아니면 공유앱 중에 유나의일기가 안 뜨므로.
            }else{          //그냥 유나 앱 켜서 영상 업로드 버튼 누를때 오는 부분
                Log.d(TAG, "어느 인텐트로 오는지 확인 : 제일 마지막 else로 옴")
            }
        }

        //영상표지 버튼 클릭 리스너 ( 인텐트로 공유해서 받아왔을땐 이미 숨겨져 있음 )
        check_youtube_tnail_btn.setOnClickListener {                 //이 버튼은 결국 유나 앱 내에서 영상 일기 올리기 눌러서 직접 주소 붙여넣기 시만 보이고 활용됨
            if(video_link_for_tnail.text.length < 11){
                Glide.with(applicationContext)
                    .load(R.drawable.load_failed)
                    .into(add_movie_image)
                Toast.makeText(applicationContext, getString(R.string.abnormal_youtube_link), Toast.LENGTH_LONG).show()
            }else{
                val startIndex = video_link_for_tnail.text.length - 11      //총 글자수에서 아이디11자리 빼면 아이디시작 인덱스임
                youtubeIdPart = video_link_for_tnail.text.substring(startIndex)           //맞는지 틀린지는 아래 Glide에서 체크
                Glide.with(applicationContext)
                    .load("https://img.youtube.com/vi/"+youtubeIdPart+"/0.jpg")           //유튜브 섬네일 주소.
                    .placeholder(R.drawable.face_static)
                    .thumbnail(Glide.with(applicationContext).load(R.raw.loading_gif))
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?,model: Any?,target: Target<Drawable>?,
                                                  isFirstResource: Boolean): Boolean {
                            Toast.makeText(this@AddYoutubeMovieActivity, getString(R.string.abnormal_youtube_link), Toast.LENGTH_SHORT).show()
                            youtubeIdPart = ""      //유튜브 ID 초기화
                            return false
                        }
                        override fun onResourceReady(resource: Drawable?,model: Any?,target: Target<Drawable>?,
                                                     dataSource: DataSource?,isFirstResource: Boolean): Boolean {
                            Toast.makeText(this@AddYoutubeMovieActivity, getString(R.string.normal_youtube_link), Toast.LENGTH_SHORT).show()
                            add_movie_edit_explain.setHint(getString(R.string.youtube_link_insert_success))     //내용 부분에 이후 과정 안내 표시
                            hideLinkAndCoverBtn()       //정상적인 로딩 됐으므로 주소창 등 숨김
                            return false
                        }
                    })
                    .error(Glide.with(applicationContext).load(R.drawable.load_failed))
                    .dontAnimate()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .into(add_movie_image)
            }
        }

        add_movie_image.setOnClickListener {
//            if(isForUpdate){
//                return@setOnClickListener
//            }       //업데이트 시엔 영상 새롭게 지정 못하게 만듬. 영상일기는 글만 수정 가능
//
//            pickVideo()
        }

        add_movie_btn_upload.setOnClickListener {
            if(isForUpdate){
                movieUploadUpdate()         //업데이트 시엔 영상 새롭게 지정 못하게 만듬. 영상일기는 글만 수정 가능
            }else{
                movieUploadToYoutube()
            }
        }
    }

    fun hideLinkAndCoverBtn(){      //유튜브 링크 주소 넣는 란과 옆 [영상 표지]버튼 숨기는 메소드
        check_youtube_tnail_btn.visibility = View.GONE       //이미 youtubeIdPart가 입력되었으므로 영상 표지 버튼 숨김
        video_link_for_tnail.visibility = View.GONE          //정상적인 로딩 됐으므로 주소창 숨김

        youtube_link_and_btn.layoutParams.height = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 1.0f, resources.displayMetrics).toInt()        //1dp로 리니어레이아웃 높이 설정
    }

    fun checkIsUserRight(user : FirebaseUser?){     //유저가 로그인했다면, MainActivity를 실행하고 LoginActivity를 종료한다.
        progress_bar.visibility = View.VISIBLE      //회전문 표시
        if(user != null){       //유저가 로그인 했다면
            FirebaseFirestore.getInstance()
                .collection("usersInfo")
                .document(user.uid).get()
                .addOnCompleteListener { task ->
                    val word = task.result?.get("wordWannaSay")
                    if(word != null){
                        FirebaseFirestore.getInstance().collection("authentic")
                            .document("auth").get().addOnCompleteListener { tasks ->
                                if(tasks.isSuccessful){
                                    var authDTO = tasks.result!!.toObject(AuthenticDTO::class.java)
                                    if(word.equals(authDTO?.auths)){        //wordWannaSay 일치시
                                        Toast.makeText(this, getString(R.string.signin_complete), Toast.LENGTH_SHORT).show()
                                        getMarketVersion().execute()    //버젼체크 백그라운드 asyncTask 실행
                                    }else{          //usersInfo에 정보입력한 적은 있으나 auths와 일치하지 않을 땐 다시 InfoAcitivity로
//                                        startActivity(Intent(this, InfoActivity::class.java))
                                        Toast.makeText(this, getString(R.string.unauthrized_user), Toast.LENGTH_SHORT).show()
                                        finish()
                                    }
                                }
                            }
                    }else{      //usersInfo에 기록한 적이 없다면
                        Toast.makeText(this, getString(R.string.unauthrized_user), Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
        }else{      //user가 null이면 로그인이 풀려 있는 사람 or 미가입자
            Toast.makeText(this, getString(R.string.unauthrized_user), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onBackPressed() {
        if(!add_movie_edit_explain.text.toString().isNullOrEmpty() || youtubeIdPart != ""){     //텍스트 란이 한 글자라도 써져 있거나, 영상이 선택되었다면
            val checkStopWritingDialogBuilder : AlertDialog.Builder = AlertDialog.Builder(this)
            checkStopWritingDialogBuilder.setTitle(getString(R.string.warning))
            checkStopWritingDialogBuilder.setIcon(R.drawable.com_facebook_tooltip_blue_xout)
            checkStopWritingDialogBuilder.setMessage(R.string.warningmessage)
            checkStopWritingDialogBuilder.setPositiveButton(R.string.yes_in_detailfrag, DialogInterface.OnClickListener { dialog, which -> finish() })
            checkStopWritingDialogBuilder.setNegativeButton(R.string.no_in_detailfrag, DialogInterface.OnClickListener { dialog, which ->  })
            val checkStopWritingDialog = checkStopWritingDialogBuilder.create()
            checkStopWritingDialog.show()

            val titleId = resources.getIdentifier("alertTitle", "id", applicationContext.packageName)
            var textViewTitle : TextView? = null
            if(titleId > 0){
                textViewTitle = checkStopWritingDialog.findViewById<View>(titleId) as TextView
            }
            val textViewMessage = checkStopWritingDialog.window?.findViewById<View>(android.R.id.message) as TextView
            val buttonYes = checkStopWritingDialog.window?.findViewById<View>(android.R.id.button1) as Button
            val buttonNo = checkStopWritingDialog.window?.findViewById<View>(android.R.id.button2) as Button
            val font = ResourcesCompat.getFont(applicationContext, R.font.hanmaum_myungjo)
            textViewTitle?.setTypeface(font)
            textViewMessage.setTypeface(font)
            buttonYes.setTypeface(font)
            buttonNo.setTypeface(font)
        }else{
            super.onBackPressed()
        }
    }

    override fun onResume() {
        add_movie_edit_explain.addTextChangedListener(watcher)
        super.onResume()
    }

    override fun onStop() {
        add_movie_edit_explain.removeTextChangedListener(watcher)
//        deleteConstructedVideo()      //미리보기 때도 삭제해버려서 문제됨
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    fun movieUploadToYoutube(){
        //유튭 섬네일 제대로 불러 왔는지 와 일기 내용 부족한지 검토 관문.
        if(add_movie_edit_explain.text.toString().isNullOrEmpty() || youtubeIdPart == "" || add_movie_edit_explain.text.toString().trim().length < minimumTextLength){
            Toast.makeText(this, getString(R.string.no_youtube_or_short_diary), Toast.LENGTH_LONG).show()
            return
        }

        //화면 계속 켜지게 하기
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Toast.makeText(this@AddYoutubeMovieActivity, getString(R.string.waiting_beg_msg), Toast.LENGTH_LONG).show()     //아래 다이얼로그 띄우고 나서 토스트 띄우면 에러남. 주의!!

        //업로드 중 오동작 막기 위한 다이얼로그 띄움 ( cancellable = false로 )
        val uploadingDialog = ProgressDialog(this)
        uploadingDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
        uploadingDialog.setMessage(getString(R.string.youtube_uploading_message))
        uploadingDialog.setCancelable(false)        //터치로 취소 불가 다이얼로그 설정
        uploadingDialog.show()

        //Firebase 에 썸네일 올리고 DB 세팅
        val contentDTO = ContentDTO()
        var dateInFileName = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        imageFileName = "VIDEO_"+dateInFileName+"_.mp4"           //mp4 파일로 파일명 만듦. ( 단, 유튜브 서버 기반에선 오로지 영상과 사진 구분용도로만 필요 )
        // 나중에 detailview등에서 영상 구분방식이 파일명이 VIDEO로 시작하는지 확인

        contentDTO.explain = add_movie_edit_explain.text.toString()      //게시물의 설명 ( 일기 내용 )
        contentDTO.imageFileName = imageFileName
        //imageUrl에는 영상일기의 경우, 따로 제작한 섬네일만 넣기로 함
        contentDTO.imageUrl = imageFileName            // 일단 이미지 파일 네임(VIDEO_2020xxx_.mp4)으로 해놓고 아래에서 섬네일 생성 후 업데이트 =>아래 주석으로
        // 안드Q이하에선 섬네일 비생성으로 video.mp4들어가서 미리보기 이미지 업로드 불가. 단, 클릭시 유튜브 업로드는 되었으므로 재생은 될 것임.
        contentDTO.timestamp = System.currentTimeMillis()       //현재 이미지 업로드 시간
        contentDTO.uid = auth?.currentUser?.uid     //현재자신의 UID
        contentDTO.userId = auth?.currentUser?.email            //유저의 email을 데이터모델의 userId 로 넣음

        //videoUrl 은 사실 현재로서 큰 의미 없는 필드가 됨. DetailView에서 다운로드 기능(이제없어질 것) 및 삭제기능(여전히유지) 에서 조건체크할 때 잠깐 쓰임
        contentDTO.videoUrl = "https://youtu.be/"+ youtubeIdPart       // https://youtu.be/L9_OtgJeuCY 형태, detailView에서 삭제할때 유튜브에서도 삭제하려면 필요하려나
        contentDTO.youtubeId = youtubeIdPart       //유튜브 영상 아디. L9_OqQJsvCY 등의 형태

        var documentReference = firestore?.collection("images")?.document()     //임의의 document ID 생성을 하고, 거기에 영상 DB를 담을 것임
        documentId = documentReference?.id
        firestore?.collection("images")?.document(documentId!!)?.set(contentDTO)?.addOnCompleteListener { task ->        //게시물의 DB 저장
            if(task.isSuccessful){
                //thumbnail 업로드 부분 ( 유튜브 섬네일 링크 주소를 그대로 FireStorage에 올리자 ) ( URL(String주소).openStream() => InputStream 생성 )
                var videoThumbnailImageFileName = "VIDEO_"+dateInFileName+"_cover.png"           //_cover.png 파일로 섬네일파일명 만듦.

                NetworkOpenStream().execute()
                //네트워크 작업 (https://에서 이미지 파일 받아오는 거 ) 때문에 메인 UI 스레드 0.1초씩 반복 대기
                while (inStream == null){ Thread.sleep(100) }

                val thumbnailStorageRef = storage?.reference?.child("images/thumbnail")?.child(videoThumbnailImageFileName!!)
                thumbnailStorageRef?.putStream(inStream!!)
                    ?.addOnSuccessListener { taskSnapshot ->      //썸네일 파일을 올리고 성공하면 DB 업데이트
                        thumbnailStorageRef.downloadUrl.addOnSuccessListener { uri ->
                            val thumbnailURL = uri.toString()

                            val thumbnail_DBRef = firestore!!.collection("images").document(documentId!!)
                            thumbnail_DBRef.update("imageUrl", thumbnailURL)        //생성된 썸네일 파일로 imageUrl을 업데이트
                            setResult(Activity.RESULT_OK)
                            //아래는 글 업로드 성공후 푸쉬 보내는 코드
                            firestore?.collection("usersInfo")
                                ?.document(auth?.currentUser!!.uid)?.get()?.addOnCompleteListener { task ->
                                    if(task.isSuccessful){
                                        var myInfoDTO = task.result!!.toObject(
                                            UsersInfoDTO::class.java
                                        )
                                        var message = myInfoDTO?.relation+"("+auth?.currentUser!!.email?.substring(0,9)+"...)"+getString(R.string.alarm_new_content)

                                        firestore?.collection("pushtokens")?.get()?.addOnCompleteListener { task2->
                                            if(task2.isSuccessful){
                                                for (dc in task2.result!!.documents){        //pushtokens에 등록된 모든 다큐먼트명(유저uid)로 push보냄
                                                    fcmPush?.sendMessage(
                                                        dc.id, getString(
                                                            R.string.push_title
                                                        ), message
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }       //여기까지가 푸쉬 보내는 동작 코드

                            val completedIntent : Intent = Intent(applicationContext,MainActivity::class.java)
                            completedIntent.putExtra("UploadCompleted","Success")
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            uploadingDialog.dismiss()

                            Toast.makeText(this@AddYoutubeMovieActivity, getString(R.string.upload_success), Toast.LENGTH_SHORT).show()
                            startActivity(completedIntent)
                            finish()
                        }
                    }?.addOnProgressListener {
                        uploadingDialog.progress = it.bytesTransferred.toInt()      //일단 테스트 해보고 삭제할지 정하자.
                    }?.addOnFailureListener {
                        Log.d(TAG, "섬네일 파일 DB업로드 실패 메시지: "+it.message.toString())
                    }
            }else{
                Toast.makeText(this@AddYoutubeMovieActivity, getString(R.string.upload_fail_db), Toast.LENGTH_SHORT).show()
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                uploadingDialog.dismiss()
            }
        }
    }

    fun movieUploadUpdate(){
        if(add_movie_edit_explain.text.toString().isNullOrEmpty() || add_movie_edit_explain.text.toString().trim().length < minimumTextLength){
            Toast.makeText(this, getString(R.string.too_short_diary_warning), Toast.LENGTH_LONG).show()
            return
        }
        progress_bar.visibility = View.VISIBLE

        //timeStamp, 수정 전 글의 데이터를 그대로 사용. ImageFileName 은 앞에 E붙임
        firestore?.collection("images")?.whereEqualTo("imageUrl", image_url)?.get()?.addOnCompleteListener {task ->
            if(task.isSuccessful){
                for (document in task.result!!.documents){
                    var map = mutableMapOf<String, Any>()
                    map["explain"] = add_movie_edit_explain.text.toString()

                    document.reference.update(map)?.addOnCompleteListener { task ->
                        if(task.isSuccessful){
                            progress_bar.visibility = View.GONE

                            setResult(Activity.RESULT_OK)
                            val completedIntent : Intent = Intent(applicationContext, MainActivity::class.java)
                            completedIntent.putExtra("UploadCompleted", "Success")
                            Toast.makeText(this, getString(R.string.diary_text_updated), Toast.LENGTH_SHORT).show()
                            startActivity(completedIntent)
                            finish()
                        }
                    }
                }
            }
        }
    }

    inner class getMarketVersion : AsyncTask<Any, Any, String>() {
        var marketVersion : String? = null
        var verSion : String? = null
        var firestore = FirebaseFirestore.getInstance()         //이미 outer class에서 활용하고 있긴 함.
        val MARKET_URL = "https://play.google.com/store/apps/details?id=com.jycompany.yunadiary"

        override fun onPreExecute() {
            super.onPreExecute()
        }

        override fun doInBackground(vararg params: Any?): String? {
            try{
//                var doc : Document = Jsoup.connect(MARKET_URL).get()      //기존 사용하던 구글플레이스토어 마켓 버젼 숫자 가져오기
//                var Version : Elements = doc.select(".htlgb")
//
//                for(i in 0 until Version.size){
//                    marketVersion = Version.get(i).text()
//                    if(Pattern.matches("^[0-9]{1}.[0-9]{1}.[0-9]{1}$", marketVersion)){
//                        return marketVersion
//                    }
//                }
//                return marketVersion
                firestore?.collection("authentic")?.document("version")?.get()
                    ?.addOnCompleteListener {task ->
                        if(task.isSuccessful){
                            val version : String? = task.result?.get("ver").toString()
                            marketVersion = version
                            return@addOnCompleteListener
                        }
                    }
                while(marketVersion == null){
                    Thread.sleep(300)
                }
                if(marketVersion != null){
                    return marketVersion
                }
            }catch (e : IOException){
                e.printStackTrace()
            }
            return null
        }

        override fun onPostExecute(result: String?) {
            try{
                verSion = getDeviceAppVersion(applicationContext!!)
            }catch (e : PackageManager.NameNotFoundException){
                e.printStackTrace()
            }
            marketVersion = result

            if(verSion!!.toFloat() < marketVersion!!.toFloat()){        //디바이스의 앱버젼(verSion)이 마켓버젼(marketVersion)보다 낮을 때
                progress_bar.visibility = View.GONE         //로딩 회전 끄기
                val mDialog : AlertDialog.Builder = AlertDialog.Builder(this@AddYoutubeMovieActivity!!)
                mDialog.setMessage("마켓에 신버전이 올라와 있어요~" +
                        "\n신버전은 새 기능이 추가되었거나 앱 안정성이 높습니다." +
                        "\n업데이트 후 사용을 추천드립니다." +
                        "\n(마켓버젼이 예전 버전인 "+verSion+"으로 표시되는 경우가 있" +
                        "습니다. 마켓을 껐다가 다시 확인하시면 신버전("+marketVersion+")이 제대로 나옵니다.)" +
                        "\n\n현재 버젼 : "+verSion+"\n신버전 : "+marketVersion)
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.update_btn_yes), DialogInterface.OnClickListener { dialog, which ->
                        var marketLaunch : Intent? = Intent(Intent.ACTION_VIEW)
                        marketLaunch?.setData(Uri.parse(MARKET_URL))
                        startActivity(marketLaunch)
//                            loginActivity?.finish()     //이 시점에 어느 액티비티가 켜져 있는지 모르겠음; 일단 무효화
                    })
                    .setNegativeButton(getString(R.string.terminate_app), DialogInterface.OnClickListener { dialog, which ->
                        finish()
                    })
                var alert = mDialog.create()
                alert.setTitle(getString(R.string.update_inform))
                alert.show()

                val titleId = resources.getIdentifier("alertTitle", "id", applicationContext.packageName)
                var textViewTitle : TextView? = null
                if(titleId > 0){
                    textViewTitle = alert.findViewById<View>(titleId) as TextView
                }
                val textViewMessage = alert.window?.findViewById<View>(android.R.id.message) as TextView
                val buttonYes = alert.window?.findViewById<View>(android.R.id.button1) as Button
                val buttonNo = alert.window?.findViewById<View>(android.R.id.button2) as Button
                val font = ResourcesCompat.getFont(applicationContext, R.font.hanmaum_myungjo)
                textViewTitle?.setTypeface(font)
                textViewMessage.setTypeface(font)
                buttonYes.setTypeface(font)
                buttonNo.setTypeface(font)
            }else{          //디바이스의 앱버젼(verSion)과 마켓버젼(marketVersion)이 같거나 심지어 디바이스 앱버젼이 더 최신이면
                //do nothing. 왜냐면 앱버젼 최신이고, 여기선 유튭 링크 잘 받아와서 이미 일기writing 액티비티 들어와 있으므로.
                progress_bar.visibility = View.GONE         //로딩 회전 끄기
            }
            super.onPostExecute(result)
        }

        fun getDeviceAppVersion(context : Context): String?{        //현재 디바이스에 설치된 앱버젼 확인( ex)1.043 ...)
            var versionName = ""
            try{
                val pm = context.packageManager.getPackageInfo(context.packageName, 0)
                versionName = pm.versionName
            }catch (e: Exception){
                e.printStackTrace()
            }
            return versionName
        }
    }

    inner class NetworkOpenStream : AsyncTask<Any, Any, String>() {
        override fun onPreExecute() {
            super.onPreExecute()
        }
        override fun doInBackground(vararg params: Any?): String? {
            try{
                inStream = URL("https://img.youtube.com/vi/"+youtubeIdPart+"/0.jpg").openStream()
            }catch (e : IOException){
                e.printStackTrace()
            }
            return null
        }
        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
        }
    }
}