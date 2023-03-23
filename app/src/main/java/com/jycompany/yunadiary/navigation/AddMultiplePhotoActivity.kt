package com.jycompany.yunadiary.navigation

import android.app.Activity
import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.jycompany.yunadiary.MainActivity
import com.jycompany.yunadiary.R
import com.jycompany.yunadiary.model.ContentDTO
import com.jycompany.yunadiary.model.UsersInfoDTO
import com.jycompany.yunadiary.util.FcmPush
import com.theartofdev.edmodo.cropper.CropImage
import com.theartofdev.edmodo.cropper.CropImageView
import com.zhihu.matisse.Matisse
import com.zhihu.matisse.MimeType
import com.zhihu.matisse.engine.impl.GlideEngine
import kotlinx.android.synthetic.main.activity_add_multiple_photo.*
import kotlinx.android.synthetic.main.fragment_picasso_image_pager.view.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class AddMultiplePhotoActivity : AppCompatActivity() {
    val PICK_IMAGE_FROM_ALBUM = 0
    val PICK_MULTI_FROM_ALBUM = 1009
    val TAG = "AddMultiplePhoto_tag"
    val IMAGE_URL = "imageURL"
    val DIARY_CONTENT = "diary_content"
    val IMAGEARR = "imageArr"
    val TIMESTAMP = "timeStamp"
    val IMAGEFILENAME = "imageFileName"

    var photoUri: Uri? = null
    var image_url : String? = null

    var storage : FirebaseStorage? = null
    var firestore : FirebaseFirestore? = null
    private var auth : FirebaseAuth? = null
    var watcher : TextWatcher? = null

    val minimumTextLength = 10                                       //일기내용 최소 글자 수
    var isForUpdate : Boolean = false

    var timeStamp : Long? = null
    var imageFileName : String? = null
    var imageArr : ArrayList<String>? = ArrayList<String>()
    var imageArrEdited : ArrayList<String>? = ArrayList<String>()
    var isEvenOnePicHasChanged = false
    var changedPicIndexArray : ArrayList<Int> = ArrayList<Int>()

    var fcmPush : FcmPush? = null

    var multiPickContentUri : ArrayList<String> = ArrayList<String>()           //처음 사진들을 임의적으로 선택하면 원본 사진의 uri를 모두 담는 배열
    var multiPickAfterCropUri : ArrayList<String> = ArrayList<String>()             //위 배열요소를 다시 차례로 CROP하여 그 이미지 uri 들을 담은 배열
    var pictureChoosedCount : Int = 0
    val imgDownloadUrlArr : ArrayList<String> = ArrayList<String>()         //Firebase Storage에 파일 올리고나면 다운로드 url 받아서 원래 순서대로 담는 배열
    var imgUploadedCount : Int = 0                                          //Firebase Storage에 파일 올린 개수. pictureChooseCount와 비교해서 이후 DB완성 작업 진행

    var evenOnceCroppedFileToDeleteArr : ArrayList<String> = ArrayList<String>()        //한번이라도 생성한 임시파일 다 모아놨다가 지우려는 배열

    var documentId : String? = null
    var updatePagerAdapter : MyViewPagerAdapter? = null
    val limitPictureChoose : Int = 10           //최대 올릴수 있는 사진 숫자

    var multiPicUploadingDialog : ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_multiple_photo)

        storage = FirebaseStorage.getInstance()
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        fcmPush = FcmPush()

        watcher = object : TextWatcher{
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if(multiple_addphoto_edit_explain.text.toString().trim().length >= minimumTextLength){
                    multiple_addphoto_show_byte_count.setTextColor(resources.getColor(R.color.colorTextOver140Green))
                    multiple_addphoto_show_byte_count.text = multiple_addphoto_edit_explain.text.toString().trim().length.toString() + getString(R.string.word_count)
                }else{
                    multiple_addphoto_show_byte_count.setTextColor(resources.getColor(R.color.red))
                    multiple_addphoto_show_byte_count.text = getString(R.string.minimun_length_show)+multiple_addphoto_edit_explain.text.toString().trim().length.toString() + getString(R.string.word_count)
                }
            }
            override fun afterTextChanged(s: Editable?) {
            }
        }

        when((Math.random()*2).toInt()){        //랜덤으로 업로드 기본 그림 정해서 보여주기
            0 -> multiple_addphoto_image.setImageResource(R.drawable.add_photo_background_fox)
            1 -> multiple_addphoto_image.setImageResource(R.drawable.add_photo_background_sea)
        }

        val receivedIntent : Intent = intent
        if(receivedIntent != null){
            if(receivedIntent.hasExtra(IMAGE_URL)){
                isForUpdate = true      //업데이트이므로 플래그 수정

                image_url = receivedIntent.getStringExtra(IMAGE_URL)

                imageArr = receivedIntent.getStringArrayListExtra(IMAGEARR)
                imageArrEdited?.addAll(imageArr!!)

                //1. 업로드했던 그림 표시
//                Glide.with(applicationContext)
//                    .load(receivedIntent.getStringExtra(IMAGE_URL))
//                    .thumbnail(0.5f)
//                    .into(multiple_addphoto_image)
                multiple_addphoto_image.visibility = View.GONE
                updatePagerAdapter = MyViewPagerAdapter()
                for(uri in imageArr!!){
                    updatePagerAdapter!!.addItem(uri)
                }
                upload_multi_pic_pager.adapter = updatePagerAdapter
                upload_multi_cir_indicator.setViewPager(upload_multi_pic_pager)         //써클 인디케이터와 뷰페이저 연결
                upload_multi_pic_pager.visibility = View.VISIBLE
                upload_multi_cir_indicator.visibility = View.VISIBLE

                //2. 업로드했던 일기 내용 표시
                multiple_addphoto_edit_explain.setText(receivedIntent.getStringExtra(DIARY_CONTENT))
                //3. Storage에 올라가 있는 기존 이미지 파일명 ( 추후 업로드시 수정을 위해서 )
                imageFileName = receivedIntent.getStringExtra(IMAGEFILENAME)
                //4. timeStamp 가져오기
                timeStamp = receivedIntent.getLongExtra(TIMESTAMP, 0)
            }
        }

        multiple_addphoto_image.setOnClickListener {
            if(isForUpdate){
                return@setOnClickListener
            }           //업데이트 시엔 멀티 사진 새로 지정 불가. 글만 수정 가능하게 함.;; 어차피 수정으로 들어오면 pager가 보이고 있어서 이 코드는 작동될 일이 없음

            //여러 장 사진 기본앱으로 선택하기
//            val multiPhotoPickerIntent = Intent(Intent.ACTION_PICK)
//            multiPhotoPickerIntent.type = "image/*"
//            multiPhotoPickerIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
//            intent.setData(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
//            startActivityForResult(multiPhotoPickerIntent, PICK_MULTI_FROM_ALBUM)

            //Matisse 멀티 사진 피커 라이브러리 활용 코드
            Matisse.from(this)
                    .choose(MimeType.ofImage())
                    .countable(true)
                    .maxSelectable(limitPictureChoose)
                    .restrictOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                    .thumbnailScale(0.85f)
                    .imageEngine(GlideEngine())
                    .showPreview(false)
                    .forResult(PICK_MULTI_FROM_ALBUM)
        }

        multiple_addphoto_btn_upload.setOnClickListener {
            if(isForUpdate){
                multiContentUploadUpdate()
            }else{
                multiContentUpload()
            }
        }
    }

    override fun onBackPressed() {
        if(!multiple_addphoto_edit_explain.text.toString().isNullOrEmpty() || photoUri != null){     //텍스트 란이 한 글자라도 써져 있거나, 사진이 선택되었다면
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
        super.onResume()
        multiple_addphoto_edit_explain.addTextChangedListener(watcher)
    }

    override fun onStop() {
        super.onStop()
        multiple_addphoto_edit_explain.removeTextChangedListener(watcher)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {       //갤러리 등에서 사진 선택해서 다시 add_photo activity로 왔을 때
        super.onActivityResult(requestCode, resultCode, data)       //원 코드에선 뺐지만.. IDE 오류발생으로 해서 넣음
        //아래는 기존 코드 : 갤러리나 기본 사진 선택앱으로 사진 선택해서 받아왔을 때 처리 코드
//        if(requestCode == PICK_IMAGE_FROM_ALBUM){
//            if(resultCode == Activity.RESULT_OK){
//                photoUri = data?.data
//                addphoto_image.setImageURI(photoUri)
//            }else{      // 업로드 액티비티를 그냥 캔슬해서 사진 선택 안하고 왔을 때
////                finish()      //아무 것도 안 해야 함
//            }
//        }
        //사진 자르고 나서 액티비티 result 받아서 처리하는 부분. 단, 새롭게 업로드 하는 경우에 result처리 부분
        if(requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE && isForUpdate == false){
            val result : CropImage.ActivityResult
            if(resultCode == RESULT_OK){
                result = CropImage.getActivityResult(data)
                multiPickAfterCropUri.add(result.uri.toString())
                evenOnceCroppedFileToDeleteArr.add(result.uri.toString())       //글 올리고 나면 임시파일 삭제하기 위해 별개 배열에 string저장
                cropAfterMultiSelect()          //사진을 자르는 이 메소드는 처음에 총 고른 사진 수만큼 반복되는 재귀적 함수임

                if(multiPickAfterCropUri.size == pictureChoosedCount){      //모든 사진이 crop되어 uri배열에 담김. 업로드 준비 완료.
                    multiple_addphoto_image.visibility = View.GONE
                    var afterCropPagerAdapter = MyViewPagerAdapter()
                    for(uri in multiPickAfterCropUri){
                        afterCropPagerAdapter.addItem(uri)          // 준비되고 Crop된 사진을 업로드하려는 유저에게 시각적으로 표시(뷰페이저 연결)
                    }
                    upload_multi_pic_pager.adapter = afterCropPagerAdapter
                    upload_multi_cir_indicator.setViewPager(upload_multi_pic_pager)         //써클 인디케이터와 뷰페이저 연결
                    upload_multi_pic_pager.visibility = View.VISIBLE
                    upload_multi_cir_indicator.visibility = View.VISIBLE
                }
//                photoUri = result.uri
//                multiple_addphoto_image.setImageURI(photoUri)
            }else if(resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE){
                result = CropImage.getActivityResult(data)
                val error : Exception = result.error
                error.printStackTrace()
            }
        }else if(requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE && isForUpdate == true){     //수정으로 들어와서 사진 교체한 경우(크롭 이후)
            val result : CropImage.ActivityResult
            if(resultCode == RESULT_OK){
                //멀티 사진 일기, 업데이트시, 개별 사진 크롭 후 처리 부분
                result = CropImage.getActivityResult(data)
                imageArrEdited?.set(upload_multi_pic_pager.currentItem, result.uri.toString())      //배열에 위치포지션 : pager.currentItem
                evenOnceCroppedFileToDeleteArr.add(result.uri.toString())               //글 올리고 나면 임시파일 삭제하기 위해 별개 배열에 string저장
                isEvenOnePicHasChanged = true           //사진 하나라도 바꾼 경우 플래그 변수
                if(!changedPicIndexArray.contains(upload_multi_pic_pager.currentItem)){     //같은 위치의 사진을 다시 바꾼 경우에 문제를 예방위해
                    changedPicIndexArray.add(upload_multi_pic_pager.currentItem)            //어디가 바뀌엇는지 int 배열에 저장
                }
                (upload_multi_pic_pager.adapter as MyViewPagerAdapter).setItemArray(imageArrEdited!!)   //setItemArray 안에 notifyDataSetchanged 있음
            }
        }

        //Matisse 멀티 사진 피커 라이브러리 활용 result 코드
        if(requestCode == PICK_MULTI_FROM_ALBUM && resultCode == RESULT_OK){
            multiPickContentUri.clear()
            multiPickAfterCropUri.clear()
            pictureChoosedCount = 0
            if(data != null){
                var mSelected : List<Uri>? = null
                mSelected = Matisse.obtainResult(data) as ArrayList<Uri>

                if(mSelected.size == 1){
                    Toast.makeText(this, "다중 선택시 2장 이상 선택해 주세요", Toast.LENGTH_SHORT).show()
                }else if(mSelected.size > 1 && mSelected.size <= limitPictureChoose){           //2장 이상 ~ 최대 허용수(현재 10장) 이하 일때
                    for(mfiles in mSelected){
                        multiPickContentUri.add(mfiles.toString())      //가져온 모든 사진의 Uri를 multiPickContentUri 배열에 순서대로 담음
                    }
                    pictureChoosedCount = multiPickContentUri.size          //총 가져온 사진의 갯수를 저장
                    cropAfterMultiSelect()          //받아온 배열에서 crop하기 위해 CropImage Activity call
                }else if(mSelected.size > limitPictureChoose){      //사진 최대 허용 수를 넘었을 때 ( 현재 10장 )
                    //여기 부분으로 올 일이 없음. 애초에 Matisse에서 max를 10장으로 해놔서 그 초과하는 숫자만큼 선택이 안 되므로.
                    Toast.makeText(this, "사진은 최대 10장까지 선택 가능합니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun cropAfterMultiSelect(){
        if(multiPickContentUri.size != 0){
            var uriToCrop = Uri.parse(multiPickContentUri.removeAt(0))          //첫번째 사진부터 순서대로 빼서, Crop
            CropImage.activity(uriToCrop).start(this)
        }else{      //모든 이미지 다 크롭했으면
            return
        }
//        cropAfterMultiSelect()      //ArrayList 다 비어질 때까지
    }

    fun multiContentUpload(){
        if(multiple_addphoto_edit_explain.text.toString().isNullOrEmpty() || multiPickAfterCropUri.size == 0
            || multiple_addphoto_edit_explain.text.toString().trim().length < minimumTextLength){
            Toast.makeText(this, getString(R.string.diary_upload_error), Toast.LENGTH_LONG).show()
            return
        }       //일기없거나 사진 1장도 안 골랐을때 메소드 return 관문


        //본 메소드 구조 : 우선 준비작업실행(모달 다이얼로그 show, 폰 화면 계속 켜짐 ON )
        // 일단 골라놓은 사진을 스레드풀에 넣어서 최대한 동시에 전부 Firebase Storage에 올림. 각 태스크가
        //끝날때마다, synchronized된, 배열이나 어떤 객체에 downloadUrl을 저장함. 혹시 각 작업 중에 1개가 에러가 나면 shutdownNow()?
        //하고 작업 실패를 유저에게 알림. 아무튼 작업이 전부 완료되면(모든 태스크에 포함된 마지막 작업 - 배열의 사이즈 확인 등으로)
        //그때, Firebase DB firestore 에 DB 정보를 올리고, DB정보의 SuccessListener에서 푸쉬 보내고, "작업성공" 유저에게 알리고
        //마무리 처리(다이얼로그 닫고, 화면 계속 켜짐 스위치 off )

        // 1. 준비작업
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)     //화면 ON
        //업로드 중 오동작 막기 위한 다이얼로그 띄움 ( cancellable = false로 )
        multiPicUploadingDialog = ProgressDialog(this)
        multiPicUploadingDialog!!.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)       //막대형
        multiPicUploadingDialog!!.max = pictureChoosedCount                   //올려야 할 총 파일 갯수
        multiPicUploadingDialog!!.setMessage(getString(R.string.multipic_uploading_message))
        multiPicUploadingDialog!!.setCancelable(false)        //터치로 취소 불가 다이얼로그 설정
        multiPicUploadingDialog!!.show()
//            multiple_progress_bar.visibility = View.VISIBLE     //프로그래스바 표시 -> modal 다이얼로그로 대체해 봄

        // 2. Thread Pool생성 및 Task 실행. (Task, 즉 Runnable 클래스는 이 메소드 위에 UploadTask 클래스 임 ) + 작업결과 담을 배열 초기화
        val executorService : ExecutorService =             //사용가능한 CPU수만큼 동시 작업 스레드 수를 가진 스레드풀 생성
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
//        Log.d(TAG, "pictureChoosedCount 개수 : "+ pictureChoosedCount)
        //ArrayList<String>초기화 후 에 값 통째로 옮김 . 이게 초기화 코드임. (for문으로 그냥 배열값 하나씩 집어넣으려 하면 loop되서 안 들어가니 주의 !! )
        imgDownloadUrlArr.clear()
        imgDownloadUrlArr.addAll(multiPickAfterCropUri)

        for (i in imgDownloadUrlArr.indices){
            Log.d(TAG, "개별 elem : "+imgDownloadUrlArr[i])
        }
        imgUploadedCount = 0                                            //업로드 성공한 파일 개수도 초기화

        //동시에 업로드 진행하니 파일명이 같아져버려서 미리 파일명을 다르게 지정해주는 부분
        val dateInFileName = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        var tailCount : Int = 0
        multiPickContentUri.clear()         //이 배열을 업로드 파일명 배열로 재활용 할 것임
        multiPickContentUri.addAll(multiPickAfterCropUri)       //개수만 똑같이 하기 위해 일단 요소를 집어넣은 코드
//        val iterator = multiPickContentUri.iterator()
        for(index in multiPickContentUri.indices){
            var fileName : String = dateInFileName + tailCount.toString()
            tailCount++
            multiPickContentUri.set(index, fileName)
//            Log.d(TAG, "multiPickContentUri에 들어간 파일명 : "+fileName)
        }
//        Log.d(TAG, "multiPickContentUri.size : "+multiPickContentUri.size)

        for (index in multiPickAfterCropUri.indices){       //사진 개수만큼 스레드 큐에 집어넣음 ( 업로드 파일명을 미리 지정해줌 )
            //매개변수 : 순서, cropped파일uri, 스토리지에 업로드할 파일명(파일명 겹치는 것 방지)
            executorService.execute(UploadTask(index, Uri.parse(multiPickAfterCropUri.get(index)), multiPickContentUri.get(index)))
        }
        executorService.shutdown()

        if(!executorService.awaitTermination(60, TimeUnit.SECONDS)){    //1분 지나도 업로드 안 끝나면
            executorService.shutdownNow()       //당장 종료
            multiPicUploadingDialog!!.dismiss()
            Toast.makeText(this@AddMultiplePhotoActivity,
                getString(R.string.upload_fail_timeout), Toast.LENGTH_SHORT).show()    //시간초과 업로드 실패 메시지 띄움
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val completedIntent : Intent = Intent(applicationContext, MainActivity::class.java)
            startActivity(completedIntent)
            finish()
        }
    }

    //멀티사진 업로드 메소드에서 사용할 Runnable. 배열의 index와 Uri를 매개변수로 넣음
    inner class UploadTask constructor(val index : Int, val picUri : Uri, val realFileName : String) : Runnable {
        override fun run() {
            val dateInFileName = realFileName
            val imageFileName = "JPEG_"+dateInFileName+"_.png"

            val storageRef = storage?.reference?.child("images")?.child(imageFileName)
            storageRef?.putFile(picUri)?.addOnSuccessListener { taskSnapshot ->
                storageRef!!.downloadUrl.addOnSuccessListener { uri ->
                    //addUri() 는 synchronized 메소드. downloadUrl을 배열에 넣으면서, 카운트 하는 부분.
                    addUriToContentDTO(index, uri.toString())                    //스레드 동시접근시 제대로 카운트 되지 않으므로 싱크로 처리

                    //사진 업로드 성공 수가 처음 사진 선택한 개수pictureChoosedCount와 같으면. 즉 파일 다 전송했으면
                    if(imgUploadedCount == pictureChoosedCount){
                        val contentDTO = ContentDTO()
                        contentDTO.explain = multiple_addphoto_edit_explain.text.toString()      //게시물의 설명 ( 일기 내용 )
                        contentDTO.imageArr = imgDownloadUrlArr             //downloadUrl 배열 넣어줌
                        contentDTO.imageFileName = imgDownloadUrlArr.get(0).replaceBefore("JPEG", "")
                            .replaceAfter("_.png", "")         //첫번째 url을 이미지 파일명으로
                        contentDTO.imageUrl = imgDownloadUrlArr.get(0)            //사진첩 일기 첫 장 cover 이미지
                        contentDTO.timestamp = System.currentTimeMillis()       //사진첩 일기 최종 업로드 완료 시간
                        contentDTO.uid = auth?.currentUser?.uid     //현재자신의 UID
                        contentDTO.userId = auth?.currentUser?.email            //유저의 email을 데이터모델의 userId 로 넣음

                        firestore?.collection("images")?.document()?.set(contentDTO)?.addOnCompleteListener { task ->
                            if(task.isSuccessful){      //모든 DB 생성 성공 및 완료시

                                setResult(Activity.RESULT_OK)

                                //아래는 글 업로드 성공후 푸쉬 보내는 코드
                                firestore?.collection("usersInfo")
                                    ?.document(auth?.currentUser!!.uid)?.get()?.addOnCompleteListener { task ->
                                        if(task.isSuccessful){
                                            var myInfoDTO = task.result!!.toObject(UsersInfoDTO::class.java)
                                            var message = myInfoDTO?.relation+"("+auth?.currentUser!!.email?.substring(0,9)+"...)"+getString(R.string.alarm_new_content)

                                            firestore?.collection("pushtokens")?.get()?.addOnCompleteListener { task2->
                                                if(task2.isSuccessful){
                                                    for (dc in task2.result!!.documents){        //pushtokens에 등록된 모든 다큐먼트명(유저uid)로 push보냄
                                                        fcmPush?.sendMessage(dc.id, getString(R.string.push_title), message)
                                                    }
                                                }
                                            }
                                        }
                                    }       //여기까지가 푸쉬 보내는 동작 코드
                                multiPicUploadingDialog!!.dismiss()

                                //생성된 파일 삭제 부분
                                deleteTempFileCreated()

                                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                Toast.makeText(this@AddMultiplePhotoActivity, getString(R.string.upload_success), Toast.LENGTH_SHORT).show()
                                val completedIntent : Intent = Intent(applicationContext, MainActivity::class.java)
                                completedIntent.putExtra("UploadCompleted", "Success")

                                startActivity(completedIntent)
                                finish()
                            }else{      //파일은 다 올리고 나서, 최종 ContentDto 객체를 DB 업로드하는데 실패 시
                                deleteTempFileCreated()
                                multiPicUploadingDialog!!.dismiss()
                                Toast.makeText(this@AddMultiplePhotoActivity,
                                    getString(R.string.upload_fail_db), Toast.LENGTH_SHORT).show()    //업로드 실패시 메시지 띄움
                                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                val completedIntent : Intent = Intent(applicationContext, MainActivity::class.java)
                                startActivity(completedIntent)
                                finish()
                            }
                        }
                    }
                }?.addOnFailureListener {               //올린 파일의 downloadUrl 리콜받는데 실패
                    deleteTempFileCreated()
                    multiPicUploadingDialog!!.dismiss()
                    Toast.makeText(this@AddMultiplePhotoActivity,
                        getString(R.string.upload_fail), Toast.LENGTH_SHORT).show()    //업로드 실패시 메시지 띄움
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    val completedIntent : Intent = Intent(applicationContext, MainActivity::class.java)
                    startActivity(completedIntent)
                    finish()
                }
            }?.addOnFailureListener { it->      //Storage 파일 업로드 실패
                deleteTempFileCreated()
                multiPicUploadingDialog!!.dismiss()
                Toast.makeText(this@AddMultiplePhotoActivity,
                    getString(R.string.upload_fail), Toast.LENGTH_SHORT).show()    //업로드 실패시 메시지 띄움
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val completedIntent : Intent = Intent(applicationContext, MainActivity::class.java)
                startActivity(completedIntent)
                finish()
            }
        }
    }

    @Synchronized fun addUriToContentDTO(index : Int, downloadUrl : String){
        imgDownloadUrlArr.set(index, downloadUrl)
        imgUploadedCount++
        multiPicUploadingDialog!!.progress = imgUploadedCount       //다이얼로그에 올린 개수 표시
    }

    fun multiContentUploadUpdate(){
        if(multiple_addphoto_edit_explain.text.toString().isNullOrEmpty() || multiple_addphoto_edit_explain.text.toString().trim().length < minimumTextLength){
            Toast.makeText(this, getString(R.string.too_short_diary_warning), Toast.LENGTH_LONG).show()
            return
        }
        multiple_progress_bar.visibility = View.VISIBLE

        if(!isEvenOnePicHasChanged){       //멀티 사진 글의 사진을 수정하지 않았을 경우 ( Stroage는 업데이트 X, DB만 업데이트 )
            firestore?.collection("images")
                    ?.whereEqualTo("imageUrl", image_url)
                    ?.get()?.addOnCompleteListener {task ->
                        if(task.isSuccessful){
                            for (document in task.result!!.documents){
                                var map = mutableMapOf<String, Any>()
                                map["explain"] = multiple_addphoto_edit_explain.text.toString()

                                document.reference.update(map)?.addOnCompleteListener { task ->
                                    if(task.isSuccessful){
//                                        Log.d(TAG, "일기에서 텍스트 부분만 정상적으로 업데이트 되었습니다.")
                                        multiple_progress_bar.visibility = View.GONE

                                        setResult(Activity.RESULT_OK)
                                        val completedIntent : Intent = Intent(applicationContext, MainActivity::class.java)
                                        completedIntent.putExtra("UploadCompleted", "Success")
                                        Toast.makeText(this, getString(R.string.upload_update_success), Toast.LENGTH_SHORT).show()    //수정 업로드 성공시 토스트

                                        startActivity(completedIntent)
                                        finish()
                                    }
                                }
                            }
                        }
                    }
        }else{              //멀티 글에서 사진을 하나라도 수정한 경우. 각 필드 모두를 업데이트함
            //1. 기존 스토리지 파일 삭제
            for(index in changedPicIndexArray){
                var strArr = imageArr!!.get(index).split("JPEG", "_.png")
                var rightImageName = "JPEG"+strArr[1]+"_.png"
                var storageRef = storage?.reference?.child("images")?.child(rightImageName)
                storageRef?.delete()?.addOnCompleteListener { task ->
                    if(task.isSuccessful){
                        Log.d(TAG, "기존 멀티 사진을 삭제함 : "+rightImageName)
                    }
                }
            }   //여기까지 기존 파일'들' Storage 삭제 코드

            //2. 수정한 사진 파일 업로드 코드. 파일 업로드 이후 complete 되면 DB 업데이트 코드 넣을 것
            var uploadedImagesList : ArrayList<String> = ArrayList<String>()            //업로드 되고 나면 새롭게 받은 URL 담을 배열
            var map = mutableMapOf<String, Any>()
            map.put("imageArr", imageArr!!)

            for(index in changedPicIndexArray){     //여긴 여전히 순차적으로 차례대로 진행하는 구 코드... 위에 업로드가 제대로 되는지 파악하고 여기도 수정해보든가..
                var strArr = imageArr!!.get(index).split("JPEG", "_.png")
                var rightImageName = "JPEG"+strArr[1]+"_.png"
                var storageRef = storage?.reference?.child("images")?.child(rightImageName)
                storageRef?.putFile(Uri.parse(imageArrEdited?.get(index)))?.addOnSuccessListener { taskSnapshot ->
                    storageRef?.downloadUrl?.addOnSuccessListener { uri ->
                        var newDownloadUrl = uri.toString()
                        if(index == 0){         //첫번째 사진을 올리고 downloadUrl 받을 때만
                            map["imageUrl"] = newDownloadUrl
                        }
                        (map["imageArr"] as ArrayList<String>)[index] = newDownloadUrl      //imageArr 배열 내부 값 수정
                        uploadedImagesList.add(newDownloadUrl)

                        if(uploadedImagesList.size == changedPicIndexArray.size){       //수정한 사진파일이 모두 download Url을 얻었을 때 비로소 DB업데이트
                            map["explain"] = multiple_addphoto_edit_explain.text.toString()         //수정한 일기 텍스트 내용을 반영
                            map["imageFileName"] = imageFileName!!            //imageFileName은 DB에서 바뀔 일이 없음.
                            //파일이 바뀔 때 바뀌는 것은 imageUrl(첫번째 사진을 수정했을 때)과, imageArr 필드(배열) 의 각 배열값임
                            firestore?.collection("images")
                                    ?.whereEqualTo("timestamp", timeStamp)
                                    ?.get()?.addOnCompleteListener { task ->
                                        if(task.isSuccessful){
                                            for(dc in task.result!!.documents){     //for이라고 해도 , timestamp일치하는건 1개임.
                                                dc.reference.update(map)?.addOnCompleteListener { task ->
                                                    if(task.isSuccessful){
//                                                        Log.d(TAG, "멀티 사진의 수정부분이 모두 업데이트 되었습니다.")
                                                        multiple_progress_bar.visibility = View.GONE

                                                        setResult(Activity.RESULT_OK)
                                                        val completedIntent : Intent = Intent(applicationContext, MainActivity::class.java)
                                                        completedIntent.putExtra("UploadCompleted", "Success")
                                                        Toast.makeText(this, getString(R.string.upload_update_success), Toast.LENGTH_SHORT).show()    //수정 업로드 성공시 토스트

                                                        startActivity(completedIntent)
                                                        finish()
                                                    }
                                                }
                                            }
                                        }
                                    }
                        }
                    }
                }?.addOnFailureListener { it ->
                    multiple_progress_bar.visibility = View.GONE
                    Toast.makeText(this, getString(R.string.multi_update_pic_upload_failed), Toast.LENGTH_SHORT).show()    //업로드 실패시 메시지 띄움
                }
            }
        }
    }

    inner class CustomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    inner class MyViewPagerAdapter(var items: ArrayList<String> = arrayListOf()) : RecyclerView.Adapter<RecyclerView.ViewHolder>(){
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_picasso_image_pager, parent, false)
            return CustomViewHolder(view)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val viewHolder = (holder as CustomViewHolder).itemView

            Glide.with(viewHolder.context)
                    .load(items[position])
                    .thumbnail(0.5f)
                    .into(viewHolder.picasso_image_fragment_view)

            viewHolder.picasso_image_fragment_view.setOnClickListener {
                if(!isForUpdate){       //새 일기를 올리는 거면, clickListener를 빼버림
                    Toast.makeText(applicationContext,
                            "처음 일기 작성시에는 사진 개별교체가 불가능합니다. 글 업로드 이후에 [수정] 기능으로 개별 사진 교체 가능합니다.",
                            Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                //수정할 사진 한장만 선택할려고 CropImage 호출함
                val cropImageBuilder : CropImage.ActivityBuilder = CropImage.activity()
                cropImageBuilder.setAspectRatio(91,52)
                cropImageBuilder.setFixAspectRatio(false)
                cropImageBuilder.setActivityTitle(getString(R.string.pick_image_title))
                cropImageBuilder.setCropMenuCropButtonTitle(getString(R.string.crop))
                cropImageBuilder.setRotationDegrees(30)

                cropImageBuilder.setGuidelines(CropImageView.Guidelines.ON).start(this@AddMultiplePhotoActivity)

                //아래는 chrisbanes의 Photoview 라이브러리 활용해서 intent거기로 보내는 코드
//                var viewIntent = Intent(context as MainActivity, ShowPictureActivity2::class.java)
//                viewIntent.putExtra("imageUri", items[position])
//                startActivity(viewIntent)

                //ViewPager2 액티비티로 보내는 코드
//                var viewIntent = Intent(this as AddMultiplePhotoActivity, ShowMultiPictureActivity::class.java)
//                viewIntent.putExtra("imageUri", items)      //아이템 전부 보냄.
//                viewIntent.putExtra("whereThePageIs", position)     //현재 보고 있던 사진 위치도 보냄 ( 0, 1, 2...)
//                startActivity(viewIntent)
            }
        }

        fun addItem(item : String){
            items.add(item)
        }

        fun setItemArray(itemArray : ArrayList<String>){
            items = itemArray
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int {
            return items.size
        }
    }

    //파일 지우는 법에 대해서 제대로 정리해 두자.
    //Cropped 액티비티서 자동 생성된 파일은 app의 cache 디렉토리(내부저장소)에 생성된다. 그러면 File(지울파일path-문자열 형태) .delete로 지우려면
    //문자열 형태는 /data/user/0/패키지명/cache/ 로 시작해야 하며, 풀 path는 /data/user/0/패키지명/cache/ + 파일명 형태 여야 한다.
    //그래야 file.delete()하면 지워진다.  여기서 /data/user/0/패키지명/cache/ 이건, 폰마다 다를수 있지만 어차피 불러오는 메소드가 고정되어 있다.
    //applicationContext.cacheDir 가 File객체를 반환하는데 그걸 toString()하면 바로 위 경로(/data/user/0/패키지명/cache/)가 나온다.
    //URL 문자열(file:///data/user/0/com.jycompany.yunadiaryfortest/cache/cropped849701926634958310.jpg) 갖고 암만 File객체 만들고 delete해도 안 지워진다...!!
    //즉 앞에 file:// 부분을 지우고 파일객체 만들어서 delete했어야 했다!!

    //또 추가 정리 . applicationContext.cacheDir() 가 캐쉬 디렉토리 파일,
    //  applicationContext.filesDir() 가 앱 일반파일 저장 영역이다.
    // 앱 일반파일 저장영역 파일은 toString()시 /data/user/0/com.jycompany.yunadiaryfortest/files/ 로 나옴.
    //내부 캐쉬파일이라 미디어Resolver 등록도 안되있으니 File관리시에 뻘짓 말자...
    fun deleteTempFileCreated(){
        if(evenOnceCroppedFileToDeleteArr.size != 0){
            val iterator = evenOnceCroppedFileToDeleteArr.iterator()
            while (iterator.hasNext()){
                val e : String = iterator.next()
                Log.d(TAG, "e : "+e)    //e 형태 : file:///data/user/0/com.jycompany.yunadiaryfortest/cache/cropped849701926634958310.jpg

                Log.d(TAG, "지워짐? -> "+ File(e.replaceBefore(applicationContext.cacheDir.toString(), "")).delete().toString())
                //File(안에 문자열 형태 : /data/user/0/com.jycompany.yunadiaryfortest/cache/cropped849701926634958310.jpg)  <= 이게 삭제가능 형태

//                Log.d(TAG, "applicationContext.cacheDir() : "+applicationContext.cacheDir.toString())     앱 캐쉬디렉토리 경로
//                Log.d(TAG, "applicationContext.filesDir() : "+applicationContext.filesDir)                앱 일반파일 경로   ( 둘다 내부저장소임 )
            }
        }
    }

}