package com.jycompany.yunadiary.navigation

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.jycompany.yunadiary.MainActivity
import com.jycompany.yunadiary.R
import com.jycompany.yunadiary.model.ContentDTO
import com.jycompany.yunadiary.model.UsersInfoDTO
import com.jycompany.yunadiary.util.FcmPush
import com.theartofdev.edmodo.cropper.CropImage
import com.theartofdev.edmodo.cropper.CropImageView
import kotlinx.android.synthetic.main.activity_add_photo.*
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*

class AddPhotoActivity : AppCompatActivity() {
    val PICK_IMAGE_FROM_ALBUM = 0
    val TAG = "AddPhotoActivity_tag"
    val IMAGE_URL = "imageURL"
    val DIARY_CONTENT = "diary_content"
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

    var fcmPush : FcmPush? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_photo)

        storage = FirebaseStorage.getInstance()
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        fcmPush = FcmPush()

        watcher = object : TextWatcher{
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
//                addphoto_show_byte_count.text = addphoto_edit_explain.text.toString().trim().length.toString() + getString(R.string.word_count)
                if(addphoto_edit_explain.text.toString().trim().length >= minimumTextLength){
                    addphoto_show_byte_count.setTextColor(resources.getColor(R.color.colorTextOver140Green))
                    addphoto_show_byte_count.text = addphoto_edit_explain.text.toString().trim().length.toString() + getString(R.string.word_count)
                }else{
                    addphoto_show_byte_count.setTextColor(resources.getColor(R.color.red))
                    addphoto_show_byte_count.text = getString(R.string.minimun_length_show)+addphoto_edit_explain.text.toString().trim().length.toString() + getString(R.string.word_count)
                }
            }
            override fun afterTextChanged(s: Editable?) {
            }
        }

        when((Math.random()*2).toInt()){        //랜덤으로 업로드 기본 그림 정해서 보여주기
            0 -> addphoto_image.setImageResource(R.drawable.add_photo_background_fox)
            1 -> addphoto_image.setImageResource(R.drawable.add_photo_background_sea)
        }

        val receivedIntent : Intent = intent
        if(receivedIntent != null){
            if(receivedIntent.hasExtra(IMAGE_URL)){
                isForUpdate = true      //업데이트이므로 플래그 수정

                image_url = receivedIntent.getStringExtra(IMAGE_URL)

                //1. 업로드했던 그림 표시
                Glide.with(applicationContext)
                    .load(receivedIntent.getStringExtra(IMAGE_URL))
                    .thumbnail(0.5f)
                    .into(addphoto_image)

                //2. 업로드했던 일기 내용 표시
                addphoto_edit_explain.setText(receivedIntent.getStringExtra(DIARY_CONTENT))
                //3. Storage에 올라가 있는 기존 이미지 파일명 ( 추후 업로드시 수정을 위해서 )
                imageFileName = receivedIntent.getStringExtra(IMAGEFILENAME)
                //4. timeStamp 가져오기
                timeStamp = receivedIntent.getLongExtra(TIMESTAMP, 0)
            }
        }

        addphoto_image.setOnClickListener {
            //기존 코드 : 각 스마트폰 기본 이미지 선택 앱 실행해서 사진 선택
//            val photoPickerIntent = Intent(Intent.ACTION_PICK)
//            photoPickerIntent.type = "image/*"
//            startActivityForResult(photoPickerIntent, PICK_IMAGE_FROM_ALBUM)        //그림 선택 취소 후 다시 돌아왔을 때, 사진 누르면 다시 업로드 이미지 선택 액티비티 실행

            val cropImageBuilder : CropImage.ActivityBuilder = CropImage.activity()
            cropImageBuilder.setAspectRatio(91,52)
            cropImageBuilder.setFixAspectRatio(false)
            cropImageBuilder.setActivityTitle(getString(R.string.pick_image_title))
            cropImageBuilder.setCropMenuCropButtonTitle(getString(R.string.crop))
            cropImageBuilder.setRotationDegrees(30)

            cropImageBuilder.setGuidelines(CropImageView.Guidelines.ON).start(this)
//            cropImageBuilder.start(this)
        }

        addphoto_btn_upload.setOnClickListener {
            if(isForUpdate){
                contentUploadUpdate()
            }else{
                contentUpload()
            }
        }
    }

    override fun onBackPressed() {
        if(!addphoto_edit_explain.text.toString().isNullOrEmpty() || photoUri != null){     //텍스트 란이 한 글자라도 써져 있거나, 사진이 선택되었다면
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
        addphoto_edit_explain.addTextChangedListener(watcher)
    }

    override fun onStop() {
        super.onStop()
        addphoto_edit_explain.removeTextChangedListener(watcher)
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
        if(requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE){
            val result : CropImage.ActivityResult
            if(resultCode == RESULT_OK){
                result = CropImage.getActivityResult(data)
                photoUri = result.uri
                addphoto_image.setImageURI(photoUri)
            }else if(resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE){
                result = CropImage.getActivityResult(data)
                val error : Exception = result.error
                error.printStackTrace()
            }
        }
    }

    fun contentUpload(){
        if(addphoto_edit_explain.text.toString().isNullOrEmpty() || photoUri == null || addphoto_edit_explain.text.toString().trim().length < minimumTextLength){
            Toast.makeText(this, getString(R.string.diary_upload_error), Toast.LENGTH_LONG).show()
            return
        }
        progress_bar.visibility = View.VISIBLE

//        var bmp = resizeAndRotateBmp(photoUri)       //bmp 리사이징 및 회전.        시작 부분
//        var baos = ByteArrayOutputStream()
//        bmp?.compress(Bitmap.CompressFormat.JPEG, 50, baos)      //이미지 압축
//        var data : ByteArray = baos.toByteArray()       //여기까지 리사이징 관련 코드. 리사이징 불필요시 이 단락 주석 처리

        var dateInFileName = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        imageFileName = "JPEG_"+dateInFileName+"_.png"           //png로 올리는데... 이상하게 갤러리서 열 땐 작게 나오는데, png뒤에 붙는 ?alt=media&... 때문인거같은데

        val storageRef = storage?.reference?.child("images")?.child(imageFileName!!)
        storageRef?.putFile(photoUri!!)             //지정하는 파일 원본 크기 그대로 올리는 코드
//            storageRef?.putBytes(data)                  //지정하는 파일 CompressFormat.JPEF 50 로 압축해서 업로드 코드
                ?.addOnSuccessListener { taskSnapshot ->
            progress_bar.visibility = View.GONE
            Toast.makeText(this, getString(R.string.upload_success), Toast.LENGTH_SHORT).show()

            /*    원본 책의 기존 코드       // 이건 이제 imageUrl 이 Glide에서 받을 수 없는 형식으로 만들어져버리므로 사용하지 말 것.
            //DB에 바인딩 할 위치 생성 및 컬렉션(테이블)에 데이터 집합 생성
            val uri = storageRef.downloadUrl        //원코드의 task.downloadUrl 은 사라지고, 새 버젼의 fileRef.getDownloadURL() 로 바뀜
            val contentDTO = ContentDTO()
            contentDTO.imageUrl = uri!!.toString()      //이미지 다운로드 URL주소
            contentDTO.uid = auth?.currentUser?.uid     //현재자신의 UID
            contentDTO.explain = addphoto_edit_explain.text.toString()      //게시물의 설명 ( 일기 내용 )
            contentDTO.userId = auth?.currentUser?.email            //유저의 email을 데이터모델의 userId 로 넣음
            contentDTO.timestamp = System.currentTimeMillis()       //현재 이미지 업로드 시간
            */

            val contentDTO = ContentDTO()
            storageRef?.downloadUrl?.addOnSuccessListener { uri ->
                contentDTO.imageUrl = uri.toString()            //이미지 다운로드 URL주소

                contentDTO.uid = auth?.currentUser?.uid     //현재자신의 UID
                contentDTO.explain = addphoto_edit_explain.text.toString()      //게시물의 설명 ( 일기 내용 )
                contentDTO.userId = auth?.currentUser?.email            //유저의 email을 데이터모델의 userId 로 넣음
                contentDTO.timestamp = System.currentTimeMillis()       //현재 이미지 업로드 시간
                contentDTO.imageFileName = imageFileName

                firestore?.collection("images")?.document()?.set(contentDTO)        //게시물의 DB를 저장한다...
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

                val completedIntent : Intent = Intent(applicationContext, MainActivity::class.java)
                completedIntent.putExtra("UploadCompleted", "Success")

                startActivity(completedIntent)
                finish()
            }

       }?.addOnFailureListener {
                    progress_bar.visibility = View.GONE
                    Toast.makeText(this, getString(R.string.upload_fail), Toast.LENGTH_SHORT).show()    //업로드 실패시 메시지 띄움
        }
    }

    fun contentUploadUpdate(){
        if(addphoto_edit_explain.text.toString().isNullOrEmpty() || addphoto_edit_explain.text.toString().trim().length < minimumTextLength){
            Toast.makeText(this, getString(R.string.diary_upload_error), Toast.LENGTH_LONG).show()
            return
        }
        progress_bar.visibility = View.VISIBLE

//        var bmp = resizeAndRotateBmp(photoUri)       //bmp 리사이징 및 회전.        시작 부분
//        var baos = ByteArrayOutputStream()
//        bmp?.compress(Bitmap.CompressFormat.JPEG, 50, baos)      //이미지 압축
//        var data : ByteArray = baos.toByteArray()       //여기까지 리사이징 관련 코드. 리사이징 불필요시 이 단락 주석 처리

        //timeStamp, 수정 전 글의 데이터를 그대로 사용. ImageFileName 은 앞에 E붙임
        if(photoUri == null){           //기존 글의 사진을 수정하지 않았을 경우 ( Storage는 업데이트X, DB만 업데이트 )
            firestore?.collection("images")
                ?.whereEqualTo("imageUrl", image_url)
                ?.get()?.addOnCompleteListener {task ->
                    if(task.isSuccessful){
                        for (document in task.result!!.documents){
                            var map = mutableMapOf<String, Any>()
                            map["explain"] = addphoto_edit_explain.text.toString()

                            document.reference.update(map)?.addOnCompleteListener { task ->
                                if(task.isSuccessful){
                                    Log.d(TAG, "일기에서 텍스트 부분만 정상적으로 업데이트 되었습니다.")
                                    progress_bar.visibility = View.GONE

                                    setResult(Activity.RESULT_OK)
                                    val completedIntent : Intent = Intent(applicationContext, MainActivity::class.java)
                                    completedIntent.putExtra("UploadCompleted", "Success")

                                    startActivity(completedIntent)
                                    finish()
                                }
                            }
                        }
                    }
                }
        }else{              //사진까지 수정하였을 때  ( 즉, 사진을 새로 지정한 것 , photoUri 가 null이 아닌 것 ). 각 필드를 업데이트함
            var storageRef = storage?.reference?.child("images")?.child(imageFileName!!)
            storageRef?.delete()?.addOnCompleteListener { task ->
                if(task.isSuccessful){
                    Log.d(TAG, "기존 업로드되어 있던 사진 파일을 스토리지에서 실제로 삭제함")
                }
            }  //기존 스토리지의 파일 삭제 부분

//            var query = firestore?.collection("images")?.whereEqualTo("imageUrl", image_url)
//            query?.get()?.addOnCompleteListener { task ->
//                if(task.isSuccessful){
//                    for(dc in task.result?.documents!!){
//                        dc.reference.delete()
//                    }
//                }
//            }

            //아래는 지정하는 파일 원본 크기 그대로 올리는 코드
            storageRef?.putFile(photoUri!!)?.addOnSuccessListener { taskSnapshot ->
                Toast.makeText(this, getString(R.string.upload_success), Toast.LENGTH_SHORT).show()
                storageRef?.downloadUrl?.addOnSuccessListener { uri ->
                    var map2 = mutableMapOf<String, Any>()
                    map2["explain"] = addphoto_edit_explain.text.toString()      //게시물의 설명(일기내용)을 업데이트함
                    map2["imageUrl"] = uri.toString()        //이미지를 새롭게 업로드하고 받은 URL주소로 DB를 업데이트함
                    map2["imageFileName"] = imageFileName!!        //이미지를 새롭게 업로드할때 이미지파일명 앞에 E를 더 붙엿음

                    firestore?.collection("images")
                            ?.whereEqualTo("timestamp", timeStamp)
                            ?.get()?.addOnCompleteListener { task ->
                                if(task.isSuccessful){
                                    for(dc in task.result!!.documents){
                                        dc.reference.update(map2)?.addOnCompleteListener { task ->
                                            Log.d(TAG, "사진과 일기내용 모두 업데이트 되었습니다.")
                                        }
                                    }
                                    progress_bar.visibility = View.GONE
                                }
                            }
                    setResult(Activity.RESULT_OK)
                    val completedIntent : Intent = Intent(applicationContext, MainActivity::class.java)
                    completedIntent.putExtra("UploadCompleted", "Success")

                    startActivity(completedIntent)
                    finish()
                }?.addOnFailureListener { it ->
                }
            }?.addOnFailureListener {
                it ->
                progress_bar.visibility = View.GONE
                Toast.makeText(this, getString(R.string.upload_fail), Toast.LENGTH_SHORT).show()    //업로드 실패시 메시지 띄움
            }

        }
    }

    fun resizeAndRotateBmp(photoUri : Uri?) : Bitmap? {
        val options = BitmapFactory.Options()
        //이미지의 크기를 options에 담음
        var bmp = BitmapFactory.decodeStream(applicationContext.contentResolver.openInputStream(photoUri!!), null, options)

        var width = options.outWidth
        var height = options.outHeight
        var samplesize : Int = 1;
        val wantedPictureWidth : Double = 1280.0
        val wantedPictureHeight : Double = 720.0

        var doubleWidth = width.toDouble()
        var doubleHeight = height.toDouble()

        //대략 wantedPictureWidth * wantedPictureHeight ( 1280 * 720) 사이즈로 만들 생각으로 구성
        if(width >= height){    //가로가 세로보다 길 때, 즉 세로 기준으로 축소비를 정한다.
            while (doubleHeight >= wantedPictureHeight){
                doubleHeight = doubleHeight * 0.9
            }
            val ratio = doubleHeight / options.outHeight.toDouble()

            while (true){
                if( (1/samplesize.toDouble()) < ratio ){
                    samplesize--
                    break;
                }
                samplesize++
            }
        }else{              //세로가 가로보다 길 때, 즉 가로 기준으로 축소비를 정한다.
            while (doubleWidth >= wantedPictureWidth){
                doubleWidth = doubleWidth * 0.9
            }
            val ratio = doubleWidth / options.outWidth.toDouble()
            while (true){
                if( (1/samplesize.toDouble()) < ratio ){
                    samplesize--
                    break;
                }
                samplesize++
            }
        }
        options.inSampleSize = samplesize
        //bmp 를 옵션 샘플 사이즈로 다시 리사이징 함
        bmp = BitmapFactory.decodeStream(applicationContext.contentResolver.openInputStream(photoUri!!), null, options)
        return rotateImageIfRequired(bmp!!)      //이미지 회전
    }

    fun rotateImageIfRequired(img : Bitmap) : Bitmap{
        var input = applicationContext.contentResolver.openInputStream(photoUri!!)
        var ei : ExifInterface? = null
        if(Build.VERSION.SDK_INT > 23){
            ei = ExifInterface(input!!)
        }else {
            ei = ExifInterface(photoUri?.path!!)
        }
            var orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            when(orientation){
                ExifInterface.ORIENTATION_ROTATE_90 -> return rotateImage(img, 90)
                ExifInterface.ORIENTATION_ROTATE_180 -> return rotateImage(img, 180)
                ExifInterface.ORIENTATION_ROTATE_270 -> return rotateImage(img, 270)
                else -> return img
            }
    }

    fun rotateImage(img : Bitmap, degree:Int) : Bitmap{
        val matrix = Matrix()
        matrix.postRotate(degree.toFloat())
        var rotatedImg = Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
        img.recycle()
        return rotatedImg
    }
}