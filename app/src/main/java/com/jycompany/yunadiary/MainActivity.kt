package com.jycompany.yunadiary

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.jycompany.yunadiary.model.UsersInfoDTO
import com.jycompany.yunadiary.navigation.*
import com.theartofdev.edmodo.cropper.CropImage
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), BottomNavigationView.OnNavigationItemSelectedListener {
    val PICK_PROFILE_FROM_ALBUM = 10
    val UPLOAD_COMPLETED_FROM_ACTIVITY = 11
    val TAG = "MainActivity_TAG"
//    var backPressedTime : Long = 0          //back 버튼 두번 눌러서 액티비티 종료하기 위한 변수

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progress_bar.visibility = View.VISIBLE      //로딩 바 작동

        bottom_navigation.setOnNavigationItemSelectedListener(this)
        bottom_navigation.selectedItemId = R.id.action_home     //아래 메뉴에서 home 버튼 기본 선택 ( 실제 버튼 클릭한 것과 동일한 효과 )


        //스토리지 읽기 권한 요청 코드, 리퀘스트 코드 => 1
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)!=
                PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)!=
                PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }

        registerPushToken()                                     //푸쉬 토큰을 서버에 등록

        // 처음 들어오는 사람 ( db콜렉션 usersInfo에 자료가 없는 사람이면 ) 이면 이름 입력 프래그먼트로, 아니면 아래 실행안함
        val me = FirebaseAuth.getInstance().currentUser
        //일단 콜렉션 생성함
        FirebaseFirestore.getInstance()
            .collection("usersInfo")
            .document("forMakingCollection")
            .set(UsersInfoDTO("noName", "noClassname", "noMajor"))
            .addOnCompleteListener { task ->
                if(task.isSuccessful){          //usersInfo 콜렉션에 dummy가 하나 생성됨
                }
            }
        val showNewFeatureDialogBuilder : AlertDialog.Builder = AlertDialog.Builder(this)

        val pref = getSharedPreferences("pref", MODE_PRIVATE)
        if(pref != null && pref.contains("1_3UpdateAlertAccepted")){
            //이미 업데이트 알림 다이얼로그를 보고 "OK"버튼을 눌렀다면 아무것도 안함
        }else if(pref != null && !pref.contains("1_3UpdateAlertAccepted")){
            showNewFeatureDialogBuilder.setTitle(getString(R.string.update_inform_1_3_title))
            showNewFeatureDialogBuilder.setMessage(getString(R.string.update_inform_1_3_explain))
            showNewFeatureDialogBuilder.setIcon(R.drawable.icon_logo)
            showNewFeatureDialogBuilder.setPositiveButton(getString(R.string.yes_in_updateinform), DialogInterface.OnClickListener { dialog, which ->         //처음 실행이라면
                pref.edit().putString("1_3UpdateAlertAccepted", "accepted").apply()           //SharedPreferences에 키 UpdateAlertAccepted, 값 accepted 를 저장함
            })
//                showNewFeatureDialogBuilder.setNegativeButton(getString(R.string.no_in_detailfrag), DialogInterface.OnClickListener { dialog, which ->  })
            val newFeatureDialog = showNewFeatureDialogBuilder.create()
            newFeatureDialog.show()

            val titleId = resources.getIdentifier("alertTitle", "id", applicationContext.packageName)
            var textviewTitle : TextView? = null
            if(titleId > 0){
                textviewTitle = newFeatureDialog.findViewById<View>(titleId) as TextView
            }
            val textViewMessage = newFeatureDialog.window?.findViewById(android.R.id.message) as TextView
            val buttonYes = newFeatureDialog.window?.findViewById<View>(android.R.id.button1) as Button
            val font = ResourcesCompat.getFont(applicationContext, R.font.hanmaum_myungjo)
            textviewTitle?.setTypeface(font)
            textViewMessage.setTypeface(font)
            buttonYes.setTypeface(font)
        }
    }

    override fun onBackPressed() {
        val exitDialogBuilder : AlertDialog.Builder = AlertDialog.Builder(this)
//        exitDialogBuilder.setMessage(getString(R.string.exit_dialog_message))
        exitDialogBuilder.setTitle(R.string.exit_dialog_message)
        exitDialogBuilder.setIcon(R.drawable.exit3)
        exitDialogBuilder.setPositiveButton(getString(R.string.yes_in_detailfrag), DialogInterface.OnClickListener { dialog, which ->  finish() })
        exitDialogBuilder.setNegativeButton(getString(R.string.no_in_detailfrag), DialogInterface.OnClickListener { dialog, which ->  })
        val exitDialog = exitDialogBuilder.create()
        exitDialog.show()

        val titleId = resources.getIdentifier("alertTitle", "id", applicationContext.packageName)
        var textviewTitle : TextView? = null
        if(titleId > 0){
            textviewTitle = exitDialog.findViewById<View>(titleId) as TextView
        }
        val buttonYes = exitDialog.window?.findViewById<View>(android.R.id.button1) as Button
        val buttonNo = exitDialog.window?.findViewById<View>(android.R.id.button2) as Button
        val face = ResourcesCompat.getFont(applicationContext, R.font.hanmaum_myungjo)
        textviewTitle?.setTypeface(face)
        buttonYes.setTypeface(face)
        buttonNo.setTypeface(face)

//        var clickedTime = System.currentTimeMillis()
//
//        if(clickedTime - backPressedTime > 2000){       //2초 이상 간격이면
//            Toast.makeText(this, "한번 더 누르면 종료됩니다.", Toast.LENGTH_SHORT).show()
//            backPressedTime = clickedTime
//        }else{
//            finish()
//        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        //아래는 기존 코드 : 갤러리나 기본 사진 선택앱으로 프로필사진 선택해서 받아왔을 때 처리 코드
//        if(requestCode == PICK_PROFILE_FROM_ALBUM && resultCode == Activity.RESULT_OK){
//            progress_bar.visibility = View.VISIBLE      //로딩 바 표시
//            var imageUri = data?.data
        if(requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE && resultCode == RESULT_OK){
            progress_bar.visibility = View.VISIBLE      //로딩 바 표시
            val result : CropImage.ActivityResult = CropImage.getActivityResult(data)
            var imageUri = result.uri

//            var bmp = resizeAndRotateBmp(imageUri)       //bmp 리사이징 및 회전.        시작 부분
//            var baos = ByteArrayOutputStream()
//            bmp?.compress(Bitmap.CompressFormat.JPEG, 50, baos)      //이미지 압축
//            var data : ByteArray = baos.toByteArray()       //여기까지 리사이징 관련 코드. 리사이징 불필요시 이 단락 주석 처리

            val uid = FirebaseAuth.getInstance().currentUser!!.uid      //유저 uid
            //파일 업로드 하고, db에 uri정보 등 만들기
            val storageRef = FirebaseStorage.getInstance().reference.child("userProfileImages").child(uid)              //프로필 이미지는 그냥 uid로 해서 확장자 없이 올라가고 있음
            //아래 putBytes에, 원문에선 addOnCompleteListener 붙여놨음.. ( 근데 AddPHotoActivity에선, SuccessListener로 해서 잘 작동했었음 )
//            storageRef?.putBytes(data)?.addOnSuccessListener { taskSnapshot ->            //그림 용량 줄여서 업로드시 코드
                storageRef?.putFile(imageUri!!)?.addOnSuccessListener { taskSnapshot ->     //원 용량 그대로 프로필 사진 업로드
                progress_bar.visibility = View.GONE
                Toast.makeText(this, getString(R.string.profile_image_edit_success), Toast.LENGTH_SHORT).show()
                storageRef?.downloadUrl?.addOnSuccessListener { uri ->
                    val url = uri.toString()
                    val map = HashMap<String, Any>()
                    map["image"] = url
                    map["user_email"] = FirebaseAuth.getInstance().currentUser!!.email.toString()
                    FirebaseFirestore.getInstance().collection("profileImages").document(uid).set(map)
                }
            }

                        //원래 책 코드. FirebaseStorage 업로드 후 url 받는 것 업데이트 이후 변경된 점이 있어 작동 불가
//                    .putFile(imageUri!!).addOnCompleteListener { task ->
//                        val url = task.result.downloadUrl.tostring()
//                        val map = HashMap<String, Any>()
//                        map["image"] = url
//
//                        //db에 콜렉션명 "profileImages" 란 이름으로 db정보 생성 - (UserFragment 에서 이미지 가져 오기 위해서)
//                        FirebaseFirestore.getInstance().collection("profileImages").document("uid").set(map)
//                    }
        }//유저 프로필 사진 변경시 result처리하는 코드는 여기까지
    }

    override fun onNewIntent(intent: Intent?) {
        if(intent!!.getStringExtra("UploadCompleted") != null){
            if(intent.getStringExtra("UploadCompleted").equals("Success")){
                Log.d("MainActivity_TAG", "onNewIntent-AddPhoto 액티비티에서 정상 완료되고 돌아왔음")
                val detailViewFragment = DetailViewFragment()
                supportFragmentManager.beginTransaction().replace(R.id.main_content, detailViewFragment).commit()
            }
        }
        super.onNewIntent(intent)
    }

    fun setToolbarDefault(){        //다음 이미지에서 UserFragment 사용시 툴바, 뒤로가기 버튼, 유저 이메일 타이틀을 숨기는 기능
        toolbar_title_image.visibility = View.VISIBLE       //메인 화면 위 툴바의 메인 로고 이미지
        toolbar_btn_back.visibility = View.GONE
        toolbar_username.visibility = View.GONE
    }



    override fun onNavigationItemSelected(item: MenuItem): Boolean {        //아래 Bottom 네비게이션 아이템 클릭시 리스너 정의
        setToolbarDefault()
        when(item.itemId){
            R.id.action_home ->{
                val detailViewFragment = DetailViewFragment()
                supportFragmentManager.beginTransaction().replace(R.id.main_content, detailViewFragment).commit()
                return true
            }
            R.id.action_search->{           //사진 모아보기에서 바로 일기로 가는 신기능 추가하면서 알림 다이얼로그 추가
                val gridFragment = GridFragment()
                supportFragmentManager.beginTransaction().replace(R.id.main_content, gridFragment).commit()

                val showNewFeatureDialogBuilder : AlertDialog.Builder = AlertDialog.Builder(this)

                val pref = getSharedPreferences("pref", MODE_PRIVATE)
                if(pref != null && pref.contains("1_09UpdateAlertAccepted")){
                    //이미 업데이트 알림 다이얼로그를 보고 "OK"버튼을 눌렀다면 아무것도 안함
                }else if(pref != null && !pref.contains("1_09UpdateAlertAccepted")){
                    showNewFeatureDialogBuilder.setTitle(getString(R.string.update_inform_1_09_title))
                    showNewFeatureDialogBuilder.setMessage(getString(R.string.update_inform_1_09_explain))
                    showNewFeatureDialogBuilder.setIcon(R.drawable.push_icon2)
                    showNewFeatureDialogBuilder.setPositiveButton(getString(R.string.yes_in_updateinform), DialogInterface.OnClickListener { dialog, which ->         //처음 실행이라면
                        pref.edit().putString("1_09UpdateAlertAccepted", "accepted").apply()           //SharedPreferences에 키 UpdateAlertAccepted, 값 accepted 를 저장함
                    })
//                showNewFeatureDialogBuilder.setNegativeButton(getString(R.string.no_in_detailfrag), DialogInterface.OnClickListener { dialog, which ->  })
                    val newFeatureDialog = showNewFeatureDialogBuilder.create()
                    newFeatureDialog.show()

                    val titleId = resources.getIdentifier("alertTitle", "id", applicationContext.packageName)
                    var textviewTitle : TextView? = null
                    if(titleId > 0){
                        textviewTitle = newFeatureDialog.findViewById<View>(titleId) as TextView
                    }
                    val textViewMessage = newFeatureDialog.window?.findViewById(android.R.id.message) as TextView
                    val buttonYes = newFeatureDialog.window?.findViewById<View>(android.R.id.button1) as Button
                    val font = ResourcesCompat.getFont(applicationContext, R.font.hanmaum_myungjo)
                    textviewTitle?.setTypeface(font)
                    textViewMessage.setTypeface(font)
                    buttonYes.setTypeface(font)
                }

                return true
            }
            R.id.action_add_photo->{        //사진 추가하는 액티비티 실행, 따라서 권한 체크 다시
                if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)==
                    PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)==
                    PackageManager.PERMISSION_GRANTED){     //권한 승인되어 있을 때

                    val anchorMenuItemView =findViewById<View>(R.id.action_add_photo)
                    val popupMenu = PopupMenu(this, anchorMenuItemView)

                    menuInflater.inflate(R.menu.upload_whether_video_pic, popupMenu.menu)
                    popupMenu.setOnMenuItemClickListener { it ->
                        if(it.itemId == R.id.upload_video){
                            startActivityForResult(Intent(this, AddYoutubeMovieActivity::class.java), UPLOAD_COMPLETED_FROM_ACTIVITY)          //유튜브 업로드 액티비티로
                            return@setOnMenuItemClickListener false
                        }else if(it.itemId == R.id.upload_multi_pic){
                            startActivityForResult(Intent(this, AddMultiplePhotoActivity::class.java), UPLOAD_COMPLETED_FROM_ACTIVITY)            //멀티 사진 업로드 액티비티로 연결하는 코드
                            return@setOnMenuItemClickListener false
                        }
                        return@setOnMenuItemClickListener true
                    }
                    showMenuIcon(popupMenu)
                    popupMenu.show()
                }else{      //권한 없으면. [ 원본 코드는 Toast 메시지만 표시하고 끝나지만... 권한 요청 코드 다시 넣어야 할 듯]
                    Toast.makeText(this, getString(R.string.permission_denied_msg), Toast.LENGTH_LONG).show()
                }
                return true
            }
            R.id.action_favorite_alarm->{
                val alarmFragment = AlarmFragment()
                supportFragmentManager.beginTransaction().replace(R.id.main_content, alarmFragment).commit()
                return true
            }
            R.id.action_account->{
                val userFragment = UserFragment()
                var uid = FirebaseAuth.getInstance().currentUser!!.uid
                val bundle = Bundle()
                bundle.putString("destinationUid", uid)
                userFragment.arguments = bundle
                supportFragmentManager.beginTransaction().replace(R.id.main_content, userFragment).commit()
                return true
            }
        }
        return false
    }

    @SuppressLint("RestrictedApi")
    fun showMenuIcon(popupMenu : PopupMenu){
        val menuBuilder = popupMenu.menu as MenuBuilder
        menuBuilder.setOptionalIconsVisible(true)
    }

    fun resizeAndRotateBmp(photoUri : Uri?) : Bitmap? {
        val options = BitmapFactory.Options()
        //이미지의 크기를 options에 담음
        var bmp = BitmapFactory.decodeStream(applicationContext.contentResolver.openInputStream(photoUri!!), null, options)

        var width = options.outWidth
        var height = options.outHeight
        var samplesize : Int = 1;
        val wantedPictureWidth : Double = 128.0
        val wantedPictureHeight : Double = 100.0

        var doubleWidth = width.toDouble()
        var doubleHeight = height.toDouble()

        //대략 wantedPictureWidth * wantedPictureHeight ( 128 * 100) 사이즈로 만들 생각으로 구성- 프로필 사진이므로 작게 리사이즈
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
        return rotateImageIfRequired(bmp!!, photoUri)      //이미지 회전
    }

    fun rotateImageIfRequired(img : Bitmap, photoUri : Uri?) : Bitmap{
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

    fun registerPushToken(){
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener {
            if (!it.isSuccessful){
                Log.d(TAG, "Fetching FCM Registration token failed", it.exception)
                return@OnCompleteListener
            }

            //Get new FCM registration token
            val pushToken = it.result
            var uid = FirebaseAuth.getInstance().currentUser?.uid
            var map = mutableMapOf<String, Any>()
            map["pushtoken"] = pushToken!!
            FirebaseFirestore.getInstance().collection("pushtokens").document(uid!!).set(map)
        })
    }
}