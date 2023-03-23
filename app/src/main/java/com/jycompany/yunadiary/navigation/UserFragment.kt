package com.jycompany.yunadiary.navigation

import android.Manifest
import android.annotation.TargetApi
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.jycompany.yunadiary.LoginActivity
import com.jycompany.yunadiary.MainActivity
import com.jycompany.yunadiary.R
import com.jycompany.yunadiary.model.AlarmDTO
import com.jycompany.yunadiary.model.ContentDTO
import com.jycompany.yunadiary.model.FollowDTO
import com.jycompany.yunadiary.util.FcmPush
import com.theartofdev.edmodo.cropper.CropImage
import com.theartofdev.edmodo.cropper.CropImageOptions
import com.theartofdev.edmodo.cropper.CropImageView
import com.theartofdev.edmodo.cropper.CropImageView.CropShape
import com.theartofdev.edmodo.cropper.CropImageView.Guidelines
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_user.*
import kotlinx.android.synthetic.main.fragment_user.view.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import java.io.IOException
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList


class UserFragment : Fragment() {
    val PICK_PROFILE_FROM_ALBUM = 10

    var auth : FirebaseAuth? = null
    var firestore : FirebaseFirestore? = null

    //private String destinationUdi
    var uid : String? = null
    var currentUserUid : String? = null
    var url : String? = null

    var fragmentView : View? = null
    var followListenerRegistration : ListenerRegistration? = null
    var followingListenerRegistration : ListenerRegistration? = null
    var imageprofileListenerRegistration : ListenerRegistration? = null
    var recyclerListenerRegistration : ListenerRegistration? = null

    var fcmPush : FcmPush? = null

    val MARKET_URL = "https://play.google.com/store/apps/details?id=com.jycompany.yunadiary"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        fragmentView = inflater.inflate(R.layout.fragment_user, container, false)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        fcmPush = FcmPush()

        currentUserUid = auth?.currentUser?.uid

        if(arguments != null){
            uid = requireArguments().getString("destinationUid")

            if(uid != null && uid == currentUserUid){           //본인 계정인 경우, 로그아웃 Toolbar기본으로 설정
                var mainActivity = activity as MainActivity
                mainActivity.toolbar_title_image.visibility = View.GONE
                mainActivity.toolbar_btn_back.visibility = View.VISIBLE
                mainActivity.toolbar_username.visibility = View.VISIBLE

                firestore!!.collection("usersInfo").document(auth!!.currentUser!!.uid)
                        .get().addOnCompleteListener { task ->
                            if(task.isSuccessful){
                                val relation : String = task.result?.get("relation").toString()
                                val email : String? = auth!!.currentUser?.email
                                mainActivity.toolbar_username.text = relation + " ( "+email+" )"
                            }
                        }
                fragmentView!!.account_btn_follow_signout.visibility = View.VISIBLE         //로그아웃 버튼 보이게
                fragmentView!!.account_btn_check_appversion.visibility= View.VISIBLE            //앱 버젼체크 버튼 보이게
                fragmentView!!.webpage_btn_guide_address.visibility= View.VISIBLE       //웹페이지 안내 버튼 보이게

                //앱 버젼체크 버튼 누를시 clickListener. 아래 AsyncTask 클래스 객체 만들고 실행
                fragmentView!!.account_btn_check_appversion.setOnClickListener {
                    getMarketVersion().execute()
                }
                //유나의 하루 웹페이지 주소 안내 버튼 리스너
                fragmentView!!.webpage_btn_guide_address.setOnClickListener {
                    val showWebPageGuideDialogBuilder : AlertDialog.Builder = AlertDialog.Builder(requireContext())
                    showWebPageGuideDialogBuilder.setTitle(getString(R.string.webpage_guide_dialog_title))
                    showWebPageGuideDialogBuilder.setMessage(getString(R.string.webpage_guide_dialog_content))
                    showWebPageGuideDialogBuilder.setIcon(R.drawable.icon_logo)
                    showWebPageGuideDialogBuilder.setPositiveButton(getString(R.string.yes_in_updateinform), DialogInterface.OnClickListener { dialog, which ->         //처음 실행이라면
                        //다이얼로그를 닫으며 웹주소를 복사함
                        val clipboard = requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip: ClipData = ClipData.newPlainText("web address", getString(R.string.webpage_address))
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(requireActivity(), getString(R.string.message_clipboard_copied), Toast.LENGTH_SHORT).show()
                    })
                    val webPageGuideDialog = showWebPageGuideDialogBuilder.create()
                    webPageGuideDialog.show()

                    val titleId = resources.getIdentifier("alertTitle", "id", requireContext().packageName)
                    var textviewTitle : TextView? = null
                    if(titleId > 0){
                        textviewTitle = webPageGuideDialog.findViewById<View>(titleId) as TextView
                    }
                    val textViewMessage = webPageGuideDialog.window?.findViewById(android.R.id.message) as TextView
                    val buttonYes = webPageGuideDialog.window?.findViewById<View>(android.R.id.button1) as Button
                    val font = ResourcesCompat.getFont(requireContext(), R.font.hanmaum_myungjo)
                    textviewTitle?.setTypeface(font)
                    textViewMessage.setTypeface(font)
                    buttonYes.setTypeface(font)
                }

                fragmentView!!.account_btn_follow_signout.text =
                        getString(R.string.signout)
                fragmentView?.account_btn_follow_signout?.setOnClickListener {
                    activity?.finish()      //현 MainActivity 종료함
                    startActivity(Intent(activity, LoginActivity::class.java))
                    auth?.signOut()     //현재 계정 접속 종료 코드 - 중요!!
                }
                //Profile Image Click Listener - 이건 원래 onCreateView 아래 있었으나, 본인일때만 사용가능하게 해야 할것 같아, 여기로 옮김
                fragmentView?.account_iv_profile?.setOnClickListener {

                    if(ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.READ_EXTERNAL_STORAGE)
                            == PackageManager.PERMISSION_GRANTED){

                        val profileImageCropBuilder : CropImage.ActivityBuilder = CropImage.activity()
                        profileImageCropBuilder.setCropShape(CropImageView.CropShape.RECTANGLE)
                        profileImageCropBuilder.setAspectRatio(1, 1)
                        profileImageCropBuilder.setFixAspectRatio(true)
                        profileImageCropBuilder.setActivityTitle(getString(R.string.pick_profile_crop))
                        profileImageCropBuilder.setCropMenuCropButtonTitle(getString(R.string.crop))
                        val profileImageIntent = profileImageCropBuilder.getIntent(requireContext())
                        activity?.startActivityForResult(profileImageIntent, CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE)
//                        profileImageCropBuilder.start(context!!, this)
                        //기존 코드 : 폰 기본 사진 선택 앱 오픈 . 여기선 앨범 오픈
//                        val photoPickerIntent = Intent(Intent.ACTION_PICK)
//                        photoPickerIntent.type = "image/*"
//                        activity?.startActivityForResult(photoPickerIntent, PICK_PROFILE_FROM_ALBUM)            //그냥 startAcitiviyForResult시 MainActivity로 전달이 안되는 증상 있음.
                    }else{      //외부저장장치 읽을 권한이 없을 때
                        Toast.makeText(activity, getString(R.string.permission_denied_msg), Toast.LENGTH_LONG).show()
                    }
                }
            }else{      //uid 가 현재 유저 아닌 다른 사람일 때 (팔로우 신청용)
                fragmentView!!.account_btn_follow_signout.visibility = View.INVISIBLE       //팔로우버튼 뷰 안보이게 하는 코드
                fragmentView!!.account_btn_check_appversion.visibility= View.INVISIBLE      //앱 버젼체크 버튼 안보이게 하는 코드
                fragmentView!!.webpage_btn_guide_address.visibility= View.INVISIBLE         //유나의 하루 웹페이지 주소 안내 버튼 안보이게 하는 코드

                fragmentView!!.account_btn_follow_signout.text =
                        getString(R.string.follow)
                var mainActivity = activity as MainActivity
                mainActivity.toolbar_title_image.visibility = View.GONE
                mainActivity.toolbar_btn_back.visibility = View.VISIBLE
                mainActivity.toolbar_username.visibility = View.VISIBLE
                firestore!!.collection("usersInfo").document(requireArguments().getString("destinationUid")!!)
                        .get().addOnCompleteListener { task ->
                            if(task.isSuccessful){
                                val relation : String = task.result?.get("relation").toString()
                                val email : String? = requireArguments().getString("userId")
                                mainActivity.toolbar_username.text = relation + " ( "+email+" )"
                            }
                        }
                mainActivity.toolbar_btn_back.setOnClickListener {
                    mainActivity.bottom_navigation.selectedItemId = R.id.action_home
                }
                fragmentView?.account_iv_profile?.setOnClickListener {
                    var viewIntent = Intent(context as MainActivity, ShowPictureActivity2::class.java)
                    viewIntent.putExtra("imageUri", url)
                    startActivity(viewIntent)
                }
                fragmentView?.account_btn_follow_signout?.setOnClickListener{
                    requestFollow()
                }
            }
        }
        getFollowing()
        getFollower()
        return fragmentView
    }



    override fun onResume() {
        getProfileImage()
        getFollowing()
        getFollower()
        fragmentView?.account_recyclerview?.layoutManager = GridLayoutManager(requireActivity(), 3)
        fragmentView?.account_recyclerview?.adapter = UserFragmentRecyclerViewAdapter()
        super.onResume()
    }

    fun getProfileImage(){          //firestore DB에서 이전에 업로드한 사진을 가져와서 뿌려주는 코드. Push Driven
        imageprofileListenerRegistration = firestore?.collection("profileImages")?.document(uid!!)
                ?.addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
                    if(documentSnapshot?.data != null){
                        url = documentSnapshot?.data!!["image"].toString()
                        Log.d("tags", "url=" + url)

                        Glide.with(requireActivity())
                                .load(url)
//                                .apply(RequestOptions().circleCrop())
                                .transform(RoundedCorners(20))
                                .into(fragmentView!!.account_iv_profile)
                    }else{                              //이미지 사진이 없는 경우 파란 디폴트 얼굴로 하되, 본인과 타인 구분
                        if(uid != null && uid == currentUserUid){
                            Glide.with(requireActivity())
                                    .load(R.drawable.profile_default_withtext)
                                    .into(fragmentView!!.account_iv_profile)
                        }else if(uid != null && uid != currentUserUid){
                            Glide.with(requireActivity())
                                    .load(R.drawable.profile_default)
                                    .into(fragmentView!!.account_iv_profile)
                        }
                    }
                }
    }

    fun getFollowing(){
        followingListenerRegistration = firestore?.collection("users")?.document(uid!!)
                ?.addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
                    val followDTO = documentSnapshot?.toObject(FollowDTO::class.java)
                    if(followDTO == null){
                        return@addSnapshotListener
                    }
                    fragmentView!!.account_tv_following_count.text = followDTO?.followingCount.toString()
                }
    }

    fun getFollower(){
        followListenerRegistration = firestore?.collection("users")?.document(uid!!)
                ?.addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
                    val followDTO = documentSnapshot?.toObject(FollowDTO::class.java)
                    if(followDTO == null){
                        return@addSnapshotListener
                    }
                    fragmentView?.account_tv_follower_count?.text = followDTO?.followerCount.toString()
                    if(followDTO?.followers?.containsKey(currentUserUid)!!){       //이미 팔로잉 하고 있는 상대인 경우
                        fragmentView?.account_btn_follow_signout?.text = getString(R.string.follow_cancel)
                        fragmentView?.account_btn_follow_signout?.background
                                ?.setColorFilter(ContextCompat.getColor(requireActivity(), R.color.colorLightGray), PorterDuff.Mode.MULTIPLY)
                    }else{
                        if( uid != currentUserUid){
                            fragmentView?.account_btn_follow_signout?.text = getString(R.string.follow)
                            fragmentView?.account_btn_follow_signout?.background?.colorFilter = null
                        }
                    }
                }
    }

    fun requestFollow(){        //팔로우 신청 메소드
        var tsDocFollowing = firestore!!.collection("users").document(currentUserUid!!)
        firestore?.runTransaction { transaction ->
            var followDTO = transaction.get(tsDocFollowing).toObject(FollowDTO::class.java)
            if(followDTO == null){
                followDTO = FollowDTO()
                followDTO.followingCount = 1
                followDTO.followings[uid!!] = true

                transaction.set(tsDocFollowing, followDTO)
                return@runTransaction
            }
            //Unstart the post and remove self from stars
            if(followDTO?.followings?.containsKey(uid)!!){
                followDTO?.followingCount = followDTO?.followingCount - 1
                followDTO?.followings.remove(uid)
            }else{      //재 팔로잉 때 코드?
                followDTO?.followingCount = followDTO?.followingCount + 1
                followDTO?.followings[uid!!] = true
                followerAlarm(uid!!)
            }
            transaction.set(tsDocFollowing, followDTO)
            return@runTransaction
        }

        var tsDocFollower = firestore!!.collection("users").document(uid!!)
        firestore?.runTransaction { transaction ->
            var followDTO = transaction.get(tsDocFollower).toObject(FollowDTO::class.java)
            if(followDTO == null){
                followDTO = FollowDTO()
                followDTO!!.followerCount = 1
                followDTO!!.followers[currentUserUid!!] = true

                transaction.set(tsDocFollower, followDTO!!)
                return@runTransaction
            }
            if(followDTO?.followers?.containsKey(currentUserUid)!!){
                followDTO!!.followerCount = followDTO!!.followerCount - 1
                followDTO!!.followers.remove(currentUserUid)
            }else{
                followDTO!!.followerCount = followDTO!!.followerCount + 1
                followDTO!!.followers[currentUserUid!!] = true
            }   // Star the post and add self to stars
            transaction.set(tsDocFollower, followDTO!!)
            return@runTransaction
        }
    }

    inner class UserFragmentRecyclerViewAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        val contentDTOs : ArrayList<ContentDTO>

        init {
            contentDTOs = ArrayList()

            //내 사진만 찾기
            recyclerListenerRegistration = firestore?.collection("images")?.whereEqualTo("uid", uid)
                    ?.addSnapshotListener { querySnapshot, firebaseFirestoreException ->
                        contentDTOs.clear()
                        if(querySnapshot == null){
                            return@addSnapshotListener
                        }
                        for(snapshot in querySnapshot?.documents!!){
                            contentDTOs.add(snapshot.toObject(ContentDTO::class.java)!!)
                        }
                        contentDTOs.sortByDescending { it.timestamp }                   //시간 순 정렬함
                        account_tv_post_count.text = contentDTOs.size.toString()

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
                    .thumbnail(0.1f)
                    .apply(RequestOptions().centerCrop())
                    .into(imageView)
        }

        override fun getItemCount(): Int {
            return contentDTOs.size
        }
    }
    inner class CustomViewHolder(var imageView: ImageView) : RecyclerView.ViewHolder(imageView){}

    override fun onStop() {
        super.onStop()
        followListenerRegistration?.remove()
        followingListenerRegistration?.remove()
        imageprofileListenerRegistration?.remove()
        recyclerListenerRegistration?.remove()
    }

    inner class getMarketVersion : AsyncTask<Any, Any, String>() {
        var marketVersion : String? = null
        var verSion : String? = null
        var mainActivity = activity as MainActivity

        override fun onPreExecute() {
            mainActivity.progress_bar.visibility = View.VISIBLE
            super.onPreExecute()
        }

        override fun doInBackground(vararg params: Any?): String? {
            try{
//                var doc : Document = Jsoup.connect(MARKET_URL).get()
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
            mainActivity.progress_bar.visibility = View.GONE
            try{
                verSion = getDeviceAppVersion(activity!!)
            }catch (e : PackageManager.NameNotFoundException){
                e.printStackTrace()
            }
            marketVersion = result

            if(!verSion.equals(marketVersion)){     //디바이스의 앱버젼(verSion)과 마켓버젼(marketVersion)이 다를 때
                val mDialog : AlertDialog.Builder = AlertDialog.Builder(context!!)
                mDialog.setMessage("마켓에 신버전이 올라와 있어요~\n신버전은 새 기능이 추가" +
                        "되었거나 앱 안정성이 높습니다.\n업데이트 후 사용을 추천드립니다.\n\n현재 버젼 : "+verSion+"\n신버전 : "+marketVersion)
                    .setCancelable(true)
                    .setPositiveButton(getString(R.string.update_btn_yes), DialogInterface.OnClickListener { dialog, which ->
                        var marketLaunch : Intent? = Intent(Intent.ACTION_VIEW)
                        marketLaunch?.setData(Uri.parse(MARKET_URL))
                        startActivity(marketLaunch)
                        activity?.finish()
                    })
                    .setNegativeButton(getString(R.string.update_btn_no), null)
                var alert = mDialog.create()
                alert.setTitle(getString(R.string.update_inform))
                alert.show()
            }else{          //디바이스의 앱버젼(verSion)과 마켓버젼(marketVersion)이 같을 때
                Toast.makeText(activity, getString(R.string.update_already_new)+verSion, Toast.LENGTH_LONG).show()
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

    fun followerAlarm(destinationUid: String){
        val alarmDTO = AlarmDTO()
        alarmDTO.destinationUid = destinationUid
        alarmDTO.userId = auth?.currentUser!!.email
        alarmDTO.uid = auth?.currentUser!!.uid
        alarmDTO.kind = 2
        alarmDTO.timestamp = System.currentTimeMillis()

        FirebaseFirestore.getInstance().collection("alarms").document().set(alarmDTO)
        var message = auth?.currentUser!!.email + getString(R.string.alarm_follow)
        fcmPush?.sendMessage(destinationUid, getString(R.string.push_title), message)
    }

}