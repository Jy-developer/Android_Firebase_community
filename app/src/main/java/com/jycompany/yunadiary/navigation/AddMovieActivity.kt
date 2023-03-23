package com.jycompany.yunadiary.navigation

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.abedelazizshe.lightcompressorlibrary.CompressionListener
import com.abedelazizshe.lightcompressorlibrary.VideoCompressor
import com.abedelazizshe.lightcompressorlibrary.VideoQuality
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.jycompany.yunadiary.MainActivity
import com.jycompany.yunadiary.R
import com.jycompany.yunadiary.model.ContentDTO
import com.jycompany.yunadiary.model.UsersInfoDTO
import com.jycompany.yunadiary.util.FcmPush
import com.jycompany.yunadiary.util.PreviewVideoPlayerActivity
import kotlinx.android.synthetic.main.activity_add_movie.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.*
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.log10
import kotlin.math.pow

class AddMovieActivity : AppCompatActivity() {
    val REQUEST_SELECT_VIDEO = 777
    val PICK_MOVIE_FROM_ALBUM = 0
    val TAG = "AddMovieActivity_tag"
    val IMAGE_URL = "imageURL"
    val DIARY_CONTENT = "diary_content"
    val TIMESTAMP = "timeStamp"
    val IMAGEFILENAME = "imageFileName"

    var moviePathToPreview : String = ""

    var movieUri: Uri? = null
    var image_url : String? = null

    var storage : FirebaseStorage? = null
    var firestore : FirebaseFirestore? = null
    private var auth : FirebaseAuth? = null
    var watcher : TextWatcher? = null

    val minimumTextLength = 10                                       //일기내용 최소 글자 수
    var isForUpdate : Boolean = false
    var isVideoSelectedToVolDown : Boolean = false
    var isVideoNowBecomeSmall : Boolean = false

    var timeStamp : Long? = null
    var imageFileName : String? = null
    var isHiddenModeOpen : Boolean = false
    var documentId : String? = null

    var fcmPush : FcmPush? = null

    private lateinit var path: String

    var fileTodeleteList = ArrayList<String>()
    val thumbnailSize : Size? = Size(640, 370)          //video 썸네일 사이즈 가로, 세로

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_movie)

        storage = FirebaseStorage.getInstance()
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
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

        checkVideoSelectedChangeBottomBtn()
        volume_down_btn.isEnabled = true

        val receivedIntent : Intent = intent
        if(receivedIntent != null){
            if(receivedIntent.hasExtra(IMAGE_URL)){
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
                add_movie_btn_upload.isEnabled = true               //업로드 버튼은 업데이트이므로 사용가능하게 해야됨
            }
        }

        add_movie_image.setOnClickListener {
            if(isForUpdate){
                return@setOnClickListener
            }       //업데이트 시엔 영상 새롭게 지정 못하게 만듬. 영상일기는 글만 수정 가능
            isVideoSelectedToVolDown = false
            isVideoNowBecomeSmall = false
            checkVideoSelectedChangeBottomBtn()

            if(isHiddenModeOpen){
                preview_after_voldown_btn.isEnabled = true      //미리보기 활성화
                add_movie_btn_upload.isEnabled = true           //업로드 버튼 활성화
            }

            pickVideo()
        }

        add_movie_btn_upload.setOnClickListener {
            if(isForUpdate){
                movieUploadUpdate()         //업데이트 시엔 영상 새롭게 지정 못하게 만듬. 영상일기는 글만 수정 가능
            }else{
                movieUpload()
            }
        }
        preview_after_voldown_btn.setOnClickListener {
            if(movieUri != null){
                if(moviePathToPreview.equals("")){
                    GlobalScope.launch {
                        val job = async { getMediaPath(applicationContext, movieUri!!) }
                        moviePathToPreview = job.await()

//                            Log.d(TAG, "movieUri_noEdited : "+movieUri.toString())       //content://media/external/video/media/44xx 형태       content도, file도 모두 firebase 업로드 가능한 듯
//                            movieUri = Uri.fromFile(File(movieUri.toString()))      //file:///content%3A/media/external/video/media/4466형태

//                            Log.d(TAG, "moviePath : "+moviePathToPreview)       // 재생가능 형태 /storage/emulated/0/DCIM/Camera/20201111_143251.mp4
                        PreviewVideoPlayerActivity.start(this@AddMovieActivity, moviePathToPreview)
                    }
                }else{
                    PreviewVideoPlayerActivity.start(this@AddMovieActivity, moviePathToPreview)
                }
            }else{
                Toast.makeText(this, "아직 업로드할 영상을 선택하지 않으셨습니다.", Toast.LENGTH_LONG).show()
            }
        }

        //길게 누르면 동영상 최적화 안하고 바로 올릴 수 있게 함
        volume_down_btn.setOnLongClickListener {
            val openOriginalVideoUploadDialogBuilder : AlertDialog.Builder = AlertDialog.Builder(this)
            openOriginalVideoUploadDialogBuilder.setTitle(getString(R.string.video_volum_nolimit_title))
            openOriginalVideoUploadDialogBuilder.setIcon(R.drawable.push_icon2)
            openOriginalVideoUploadDialogBuilder.setMessage(R.string.movieupload_warningmessage)
            openOriginalVideoUploadDialogBuilder.setPositiveButton(R.string.yes_in_detailfrag, DialogInterface.OnClickListener { dialog, which ->
                isHiddenModeOpen = true         //히든모드 플래그 ON

                preview_after_voldown_btn.isEnabled = true          //미리보기 버튼 활성화


                if(movieUri == null){           //비디오를 선택하지 않고, 길게 [용량 최적화]를 눌렀을 때
                    val informHiddenModeDialogBuilder : AlertDialog.Builder = AlertDialog.Builder(this)
                    informHiddenModeDialogBuilder.setTitle("히든 모드 안내")
                    informHiddenModeDialogBuilder.setIcon(R.drawable.com_facebook_close)
                    informHiddenModeDialogBuilder.setMessage("히든 모드가 OPEN 되었습니다. 위 이미지를 눌러서 업로드할 비디오를 선택해 주세요.\n(원래 용량 그대로 올라가니 영상을 신중히 선택해 주세요)")
                    informHiddenModeDialogBuilder.setPositiveButton(getString(R.string.yes_in_updateinform), DialogInterface.OnClickListener { dialog, which ->
                        //do nothing
                    })
                    informHiddenModeDialogBuilder.create().show()
                }else{      //이미 영상은 선택한 상태에서 히든 모드 ON 했을 때
                    val informHiddenModeDialogBuilder : AlertDialog.Builder = AlertDialog.Builder(this)
                    informHiddenModeDialogBuilder.setTitle("히든 모드 안내")
                    informHiddenModeDialogBuilder.setIcon(R.drawable.com_facebook_close)
                    informHiddenModeDialogBuilder.setMessage("히든 모드가 OPEN 되었습니다. [일기올리기]를 누르시면 이미 선택하신 영상을 그대로 올리실 수 있습니다.")
                    informHiddenModeDialogBuilder.setPositiveButton(getString(R.string.yes_in_updateinform), DialogInterface.OnClickListener { dialog, which ->
                        //do nothing
                    })
                    informHiddenModeDialogBuilder.create().show()

                    add_movie_btn_upload.isEnabled = true               //업로드 버튼 활성화
                }

            })
            openOriginalVideoUploadDialogBuilder.setNegativeButton(R.string.no_in_detailfrag, DialogInterface.OnClickListener { dialog, which ->  })
            val openOriginalVideoUploadDialog = openOriginalVideoUploadDialogBuilder.create()
            openOriginalVideoUploadDialog.show()

            val titleId = resources.getIdentifier("alertTitle", "id", applicationContext.packageName)
            var textViewTitle : TextView? = null
            if(titleId > 0){
                textViewTitle = openOriginalVideoUploadDialog.findViewById<View>(titleId) as TextView
            }
            val textViewMessage = openOriginalVideoUploadDialog.window?.findViewById<View>(android.R.id.message) as TextView
            val buttonYes = openOriginalVideoUploadDialog.window?.findViewById<View>(android.R.id.button1) as Button
            val buttonNo = openOriginalVideoUploadDialog.window?.findViewById<View>(android.R.id.button2) as Button
            val font = ResourcesCompat.getFont(applicationContext, R.font.hanmaum_myungjo)
            textViewTitle?.setTypeface(font)
            textViewMessage.setTypeface(font)
            buttonYes.setTypeface(font)
            buttonNo.setTypeface(font)

            true
        }


    }

    private fun pickVideo(){
        val intent = Intent()
        intent.apply {
            type = "video/*"
            action = Intent.ACTION_PICK
        }

        if(isHiddenModeOpen){
            startActivityForResult(Intent.createChooser(intent, "Select video"), PICK_MOVIE_FROM_ALBUM)          //임시 활용 코드...였으나 히든모드에서 활용
        }else{
            startActivityForResult(Intent.createChooser(intent, "Select video"), REQUEST_SELECT_VIDEO)          //여기 원래 리퀘스트 코드가 REQUEST_SELECT_VIDEO 임. 이게 원 코드
        }
    }

    override fun onBackPressed() {
        if(!add_movie_edit_explain.text.toString().isNullOrEmpty() || movieUri != null){     //텍스트 란이 한 글자라도 써져 있거나, 영상이 선택되었다면
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
        deleteConstructedVideo()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {       //갤러리 등에서 비디오 선택해서 add_movie_activity로 왔을 때

        info_about_volume.visibility = View.GONE
        timeTaken.text = ""
        newSize.text = ""

        if (requestCode == REQUEST_SELECT_VIDEO && resultCode == Activity.RESULT_OK) {
            if (data != null && data.data != null) {
                val uri = data.data                 //uri 형태 => content://media/external/video/media/4757
                movieUri = data.data
                isVideoSelectedToVolDown = true
                isVideoNowBecomeSmall = false
                checkVideoSelectedChangeBottomBtn()
                uri?.let {
                    info_about_volume.visibility = View.VISIBLE
                    GlobalScope.launch {
                        val job = async { getMediaPath(applicationContext, uri) }
                        path = job.await()
                        originalSize.text =
                                "Original size: ${getFileSize(File(path).length())}"        //원 용량 표시
                    }

                    Glide.with(applicationContext).load(uri).into(add_movie_image)

                    volume_down_btn.setOnClickListener {            //용량 최적화 버튼 실제 클릭시 용량최적화 - 코드 세팅
                        GlobalScope.launch {
                            // run in background as it can take a long time if the video is big,
                            // this implementation is not the best way to do it,
                            // todo(abed): improve threading
                            val job = async { getMediaPath(applicationContext, uri) }
                            path = job.await()

                            val desFile = saveVideoFile(path)

                            desFile?.let {
                                var time = 0L
                                VideoCompressor.start(
                                        path,
                                        desFile.path,
                                        object : CompressionListener {
                                            override fun onProgress(percent: Float) {
                                                //Update UI
                                                if (percent <= 100 && percent.toInt() % 5 == 0)
                                                    runOnUiThread {
                                                        text_progress_volume_down.text = "${percent.toLong()}%"
                                                        progressBar_volume_down.progress = percent.toInt()
                                                    }
                                            }

                                            override fun onStart() {
                                                time = System.currentTimeMillis()
                                                text_progress_volume_down.visibility = View.VISIBLE
                                                progressBar_volume_down.visibility = View.VISIBLE
                                                originalSize.text =
                                                        "Original size: ${getFileSize(File(path).length())}"
                                                text_progress_volume_down.text = ""
                                                progressBar_volume_down.progress = 0
                                            }

                                            override fun onSuccess() {
                                                val newSizeValue = desFile.length()

                                                newSize.text =
                                                        "Size after compression: ${getFileSize(newSizeValue)}"

                                                time = System.currentTimeMillis() - time
                                                timeTaken.text =
                                                        "Duration: ${DateUtils.formatElapsedTime(time / 1000)}"

                                                path = desFile.path     // 형태 => /storage/emulated/0/Download/compressed_20200616_140850.mp4
                                                fileTodeleteList.add(path)

                                                var file = File(path)   // 형태 => /storage/emulated/0/Download/compressed_20200616_140850.mp4
                                                var localUri = Uri.fromFile(file)
                                                movieUri = localUri
                                                //file:///storage/emulated/0/Download/compressed_20200616_140850.mp4 형태 uri가 만들어짐,
                                                // 이걸로도 FIrebase엔 업로드 가능

                                                isVideoNowBecomeSmall = true

                                                Looper.myLooper()?.let {
                                                    Handler(it).postDelayed({
                                                        text_progress_volume_down.visibility = View.GONE
                                                        progressBar_volume_down.visibility = View.GONE

                                                        checkVideoSelectedChangeBottomBtn()     //이 시점에 미리보기, 일기 올리기 버튼만 활성화
                                                    }, 50)
                                                }
                                                //미리보기 click listener 설정. - 이거 근데 Handler안에서 안해도 되나?
                                                preview_after_voldown_btn.setOnClickListener {
                                                    PreviewVideoPlayerActivity.start(this@AddMovieActivity, path)
                                                }
                                                sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse(path)))
                                            }

                                            override fun onFailure(failureMessage: String) {
                                                text_progress_volume_down.text = failureMessage
                                                Log.wtf("failureMessage", failureMessage)
                                            }

                                            override fun onCancelled() {
                                                Log.wtf("TAG", "compression has been cancelled")
                                                // make UI changes, cleanup, etc
                                            }
                                        },
                                        VideoQuality.VERY_HIGH,
                                        isMinBitRateEnabled = true,
                                        keepOriginalResolution = true
                                )
                            }
                        }
                    }
                }
            }
        }       //일단 영상 선택시, 용량 최적화 버튼 누를 수 있는 상태까지만 만듦

        //아래는 기존 코드 : 갤러리나 기본 사진 선택앱으로 사진 선택해서 받아왔을 때 처리 코드
        if(requestCode == PICK_MOVIE_FROM_ALBUM){       //히든 모드가 open 되었을 때 여기로 오게 될 것임.
            if(resultCode == Activity.RESULT_OK){
                movieUri = data?.data

                Glide.with(applicationContext).load(movieUri).into(add_movie_image)          //가져온 비디오 이미지를 표시
                info_about_volume.visibility = View.VISIBLE
                GlobalScope.launch {
                    val job = async { getMediaPath(applicationContext, movieUri!!) }
                    path = job.await()
                    originalSize.text =
                            "Original size: ${getFileSize(File(path).length())}"        //원 용량 표시
                }
            }else{      // 업로드 액티비티를 그냥 캔슬해서 사진 선택 안하고 왔을 때
//                finish()      //아무 것도 안 해야 함
            }
        }

        super.onActivityResult(requestCode, resultCode, data)       //원 코드에선 뺐지만.. IDE 오류발생으로 해서 넣음
    }

    fun getFileSize(size: Long): String {
        if (size <= 0)
            return "0"

        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()

        return DecimalFormat("#,##0.#").format(
            size / 1024.0.pow(digitGroups.toDouble())
        ) + " " + units[digitGroups]
    }

    fun checkVideoSelectedChangeBottomBtn(){
        if(isVideoSelectedToVolDown == true && isVideoNowBecomeSmall == true){          //영상이 선택되고, 압축되고, 업로드 준비 완료시
            volume_down_btn.isEnabled = false
            preview_after_voldown_btn.isEnabled = true
            add_movie_btn_upload.isEnabled = true
        }else if(isVideoSelectedToVolDown == true && isVideoNowBecomeSmall == false){
            volume_down_btn.isEnabled = true
            preview_after_voldown_btn.isEnabled = false
            add_movie_btn_upload.isEnabled = false              //임시로 true 로 바꿈. 원래 false임 -> 그래서 원상복귀 : false
        }else if(isVideoSelectedToVolDown == false && isVideoNowBecomeSmall == false){          //처음 영상 업로드 액티비티 띄우고, 아직 용량 줄어든 비디오가 준비되지 않았을 때
            volume_down_btn.isEnabled = false
            preview_after_voldown_btn.isEnabled = false
            add_movie_btn_upload.isEnabled = false              //임시로 true 로 바꿈. 원래 false임 -> 그래서 원상복귀 : false
        }
    }

    @Suppress("DEPRECATION")
    private fun saveVideoFile(filePath: String?): File? {
        filePath?.let {
            val videoFile = File(filePath)
//            val videoFileName = "compressed_${System.currentTimeMillis()}_${videoFile.name}"
            val videoFileName = "compressed_${videoFile.name}"      //새롭게 만들어진 압축된 영상파일은 compressed_가 앞에 붙음
            val folderName = Environment.DIRECTORY_MOVIES
            if (Build.VERSION.SDK_INT >= 30) {

                val values = ContentValues().apply {

                    put(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        videoFileName
                    )
                    put(MediaStore.Images.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Images.Media.RELATIVE_PATH, folderName)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val collection =
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

                val fileUri = applicationContext.contentResolver.insert(collection, values)

                fileUri?.let {
                    application.contentResolver.openFileDescriptor(fileUri, "rw")
                        .use { descriptor ->
                            descriptor?.let {
                                FileOutputStream(descriptor.fileDescriptor).use { out ->
                                    FileInputStream(videoFile).use { inputStream ->
                                        val buf = ByteArray(4096)
                                        while (true) {
                                            val sz = inputStream.read(buf)
                                            if (sz <= 0) break
                                            out.write(buf, 0, sz)
                                        }
                                    }
                                }
                            }
                        }

                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    applicationContext.contentResolver.update(fileUri, values, null, null)

                    return File(getMediaPath(applicationContext, fileUri))
                }
            } else {
                val downloadsPath =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val desFile = File(downloadsPath, videoFileName)

                if (desFile.exists())
                    desFile.delete()

                try {
                    desFile.createNewFile()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                return desFile
            }
        }
        return null
    }

    fun getMediaPath(context: Context, uri: Uri): String {

        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.Video.Media.DATA)
        var cursor: Cursor? = null
        try {
            cursor = resolver.query(uri, projection, null, null, null)
            return if (cursor != null) {
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                cursor.moveToFirst()
                cursor.getString(columnIndex)

            } else ""

        } catch (e: Exception) {
            resolver.let {
                val filePath = (context.applicationInfo.dataDir + File.separator
                        + System.currentTimeMillis())
                val file = File(filePath)

                resolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        val buf = ByteArray(4096)
                        var len: Int
                        while (inputStream.read(buf).also { len = it } > 0) outputStream.write(
                            buf,
                            0,
                            len
                        )
                    }
                }
                return file.absolutePath
            }
        } finally {
            cursor?.close()
        }
    }

    fun movieUploadUpdate(){
        if(add_movie_edit_explain.text.toString().isNullOrEmpty() || add_movie_edit_explain.text.toString().trim().length < minimumTextLength){
            Toast.makeText(this, getString(R.string.diary_upload_error), Toast.LENGTH_LONG).show()
            return
        }
        progress_bar.visibility = View.VISIBLE

        //timeStamp, 수정 전 글의 데이터를 그대로 사용. ImageFileName 은 앞에 E붙임
        if(movieUri == null){           //기존 글의 사진을 수정하지 않았을 경우 ( Storage는 업데이트X, DB만 업데이트 )
            firestore?.collection("images")
                ?.whereEqualTo("imageUrl", image_url)
                ?.get()?.addOnCompleteListener {task ->
                    if(task.isSuccessful){
                        for (document in task.result!!.documents){
                            var map = mutableMapOf<String, Any>()
                            map["explain"] = add_movie_edit_explain.text.toString()

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
            storageRef?.putFile(movieUri!!)?.addOnSuccessListener { taskSnapshot ->
                Toast.makeText(this, getString(R.string.upload_success), Toast.LENGTH_SHORT).show()
                storageRef?.downloadUrl?.addOnSuccessListener { uri ->
                    var map2 = mutableMapOf<String, Any>()
                    map2["explain"] = add_movie_edit_explain.text.toString()      //게시물의 설명(일기내용)을 업데이트함
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

    @RequiresApi(Build.VERSION_CODES.Q)
    fun movieUpload(){
        if(add_movie_edit_explain.text.toString().isNullOrEmpty() || movieUri == null || add_movie_edit_explain.text.toString().trim().length < minimumTextLength){
            Toast.makeText(this, getString(R.string.movie_upload_error), Toast.LENGTH_LONG).show()
            return
        }
        progress_bar.visibility = View.VISIBLE

        var dateInFileName = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        imageFileName = "VIDEO_"+dateInFileName+"_.mp4"           //mp4 파일로 파일명 만듦. 나중에 구분방식은 파일명이 VIDEO로 시작하는지 확인

        val storageRef = storage?.reference?.child("images")?.child(imageFileName!!)
        storageRef?.putFile(movieUri!!)             //지정하는 파일 원본 크기 그대로 올리는 코드
//            storageRef?.putBytes(data)                  //지정하는 파일 CompressFormat.JPEF 50 로 압축해서 업로드 코드
            ?.addOnSuccessListener { taskSnapshot ->
                progress_bar.visibility = View.GONE
                Toast.makeText(this, getString(R.string.upload_success), Toast.LENGTH_SHORT).show()

                val contentDTO = ContentDTO()
                //이번 속도 업을 위해서 수정하는 아래 부분
                storageRef?.downloadUrl?.addOnSuccessListener { uri ->
                    contentDTO.videoUrl = uri.toString()            //영상 다운로드 URL주소. 였지만, 영상의 경우 videoUrl을 재생하는 원본으로
                                                                 //imageUrl에는 영상일기의 경우, 따로 제작한 섬네일만 넣기로 함
                    contentDTO.uid = auth?.currentUser?.uid     //현재자신의 UID
                    contentDTO.explain = add_movie_edit_explain.text.toString()      //게시물의 설명 ( 일기 내용 )
                    contentDTO.userId = auth?.currentUser?.email            //유저의 email을 데이터모델의 userId 로 넣음
                    contentDTO.timestamp = System.currentTimeMillis()       //현재 이미지 업로드 시간
                    contentDTO.imageFileName = imageFileName
                    contentDTO.imageUrl = uri.toString()            // 이번 영상 섬네일표시 업데이트때 수정부분. 일단 영상 url로 해놓고 아래에서 섬네일로 업데이트

                    var documentReference = firestore?.collection("images")?.document()
                    documentId = documentReference?.id
                    firestore?.collection("images")?.document(documentId!!)?.set(contentDTO)?.addOnCompleteListener { task ->        //게시물의 DB를 저장한다...
                        if(task.isSuccessful){
                            //thumbnail 생성해서 올리는 부분
                            var bitmap : Bitmap? = null
                            try{
                                var newPath : String? = null
                                if(!movieUri.toString().endsWith("mp4")){       //mp4로 끝나지 않고 movieUri =>"content://media/external/video/media/1860 등으로 끝나면
                                    GlobalScope.launch {
                                        val job = async { getMediaPath(
                                            applicationContext,
                                            movieUri!!
                                        ) }
                                        newPath = job.await()   //newPath => /storage/emulated/0/SdCardBackUp/DCIM/Camera/20190727_201714.mp4
                                        bitmap = ThumbnailUtils.createVideoThumbnail(File(newPath),thumbnailSize!!,CancellationSignal())

                                        var baos = ByteArrayOutputStream()
                                        bitmap!!.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                                        var data = baos.toByteArray()
                                        var videoThumbnailImageFileName = "VIDEO_"+dateInFileName+"_cover.png"           //_cover.png 파일로 섬네일파일명 만듦.

                                        val thumbnailStorageRef = storage?.reference
                                            ?.child("images/thumbnail")?.child(
                                                videoThumbnailImageFileName!!
                                            )
                                        thumbnailStorageRef?.putBytes(data)?.addOnSuccessListener { taskSnapshot ->      //썸네일 파일을 올리고 성공하면 DB 업데이트
                                            thumbnailStorageRef.downloadUrl.addOnSuccessListener { uri ->
                                                val thumbnailURL = uri.toString()

                                                val thumbnail_DBRef = firestore!!.collection("images").document(
                                                    documentId!!
                                                )
                                                thumbnail_DBRef.update("imageUrl", thumbnailURL)

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

                                                startActivity(completedIntent)
                                                finish()
                                            }
                                        }
                                    }
                                }else{
                                    Log.d(TAG, "movieUri가 mp4로 끝났을 때")
                                    Log.d(TAG, "movieuri.tostring : " + movieUri.toString())

                                    //아래 path -> /storage/emulated/0/SdCardBackUp/DCIM/Camera/20190727_201714.mp4 형태
                                    bitmap = ThumbnailUtils.createVideoThumbnail(File(path),thumbnailSize!!,CancellationSignal() )

                                    var baos = ByteArrayOutputStream()
                                    bitmap!!.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                                    var data = baos.toByteArray()
                                    var videoThumbnailImageFileName = "VIDEO_"+dateInFileName+"_cover.png"           //_cover.png 파일로 섬네일파일명 만듦.

                                    Log.d(TAG, videoThumbnailImageFileName)     //로깅용

                                    val thumbnailStorageRef = storage?.reference
                                        ?.child("images/thumbnail")?.child(
                                            videoThumbnailImageFileName!!
                                        )
                                    thumbnailStorageRef?.putBytes(data)?.addOnSuccessListener { taskSnapshot ->      //썸네일 파일을 올리고 성공하면 DB 업데이트
                                        thumbnailStorageRef.downloadUrl.addOnSuccessListener { uri ->
                                            val thumbnailURL = uri.toString()

                                            val thumbnail_DBRef = firestore!!.collection("images").document(documentId!!)
                                            thumbnail_DBRef.update("imageUrl", thumbnailURL)

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
                                            val completedIntent : Intent = Intent(applicationContext,MainActivity::class.java)
                                            completedIntent.putExtra("UploadCompleted","Success")

                                            startActivity(completedIntent)
                                            finish()
                                        }
                                    }
                                }
                            }catch (e: Exception){
                                Log.d(TAG, "bitmap 생성 중 오류가 났다.")
                                e.printStackTrace()
                            }
                        }
                    }
                }

            }?.addOnFailureListener {
                progress_bar.visibility = View.GONE
                Toast.makeText(this, getString(R.string.upload_fail), Toast.LENGTH_SHORT).show()    //업로드 실패시 메시지 띄움
            }
    }

    fun deleteConstructedVideo() {
        val iterator = fileTodeleteList.iterator()
        while (iterator.hasNext()) {
            var fileToDel = File(iterator.next())
            if (fileToDel.exists()) {
                if (fileToDel.delete()) {     //삭제 실행, 그리고 성공시
                    Log.d(TAG, "임시 생성된 video 삭제 완료")
                } else {                  //삭제 실패시
                    Log.d(TAG, "임시 생성된 video 삭제 실패")
                }
            }
            iterator.remove()           //iterator와 실제 Collection에서도 삭제
        }
    }

    fun contentUpload(){
        if(add_movie_edit_explain.text.toString().isNullOrEmpty() || movieUri == null || add_movie_edit_explain.text.toString().trim().length < minimumTextLength){
            Toast.makeText(this, getString(R.string.diary_upload_error), Toast.LENGTH_LONG).show()
            return
        }
        progress_bar.visibility = View.VISIBLE

        var dateInFileName = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        imageFileName = "JPEG_"+dateInFileName+"_.png"           //png로 올리는데... 이상하게 갤러리서 열 땐 작게 나오는데, png뒤에 붙는 ?alt=media&... 때문인거같은데

        val storageRef = storage?.reference?.child("images")?.child(imageFileName!!)
        storageRef?.putFile(movieUri!!)             //지정하는 파일 원본 크기 그대로 올리는 코드
//            storageRef?.putBytes(data)                  //지정하는 파일 CompressFormat.JPEF 50 로 압축해서 업로드 코드
            ?.addOnSuccessListener { taskSnapshot ->
                progress_bar.visibility = View.GONE
                Toast.makeText(this, getString(R.string.upload_success), Toast.LENGTH_SHORT).show()


                val contentDTO = ContentDTO()
                storageRef?.downloadUrl?.addOnSuccessListener { uri ->
                    contentDTO.imageUrl = uri.toString()            //이미지 다운로드 URL주소

                    contentDTO.uid = auth?.currentUser?.uid     //현재자신의 UID
                    contentDTO.explain = add_movie_edit_explain.text.toString()      //게시물의 설명 ( 일기 내용 )
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
        if(add_movie_edit_explain.text.toString().isNullOrEmpty() || add_movie_edit_explain.text.toString().trim().length < minimumTextLength){
            Toast.makeText(this, getString(R.string.diary_upload_error), Toast.LENGTH_LONG).show()
            return
        }
        progress_bar.visibility = View.VISIBLE

        //timeStamp, 수정 전 글의 데이터를 그대로 사용. ImageFileName 은 앞에 E붙임
        if(movieUri == null){           //기존 글의 사진을 수정하지 않았을 경우 ( Storage는 업데이트X, DB만 업데이트 )
            firestore?.collection("images")
                ?.whereEqualTo("imageUrl", image_url)
                ?.get()?.addOnCompleteListener {task ->
                    if(task.isSuccessful){
                        for (document in task.result!!.documents){
                            var map = mutableMapOf<String, Any>()
                            map["explain"] = add_movie_edit_explain.text.toString()

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
            storageRef?.putFile(movieUri!!)?.addOnSuccessListener { taskSnapshot ->
                Toast.makeText(this, getString(R.string.upload_success), Toast.LENGTH_SHORT).show()
                storageRef?.downloadUrl?.addOnSuccessListener { uri ->
                    var map2 = mutableMapOf<String, Any>()
                    map2["explain"] = add_movie_edit_explain.text.toString()      //게시물의 설명(일기내용)을 업데이트함
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

}