package com.jycompany.yunadiary.navigation

import android.app.*
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.*
import com.google.firebase.storage.FirebaseStorage
import com.jycompany.yunadiary.MainActivity
import com.jycompany.yunadiary.R
import com.jycompany.yunadiary.model.AlarmDTO
import com.jycompany.yunadiary.model.ContentDTO
import com.jycompany.yunadiary.model.ProfileImagesDTO
import com.jycompany.yunadiary.model.UsersInfoDTO
import com.jycompany.yunadiary.util.FcmPush
import com.jycompany.yunadiary.util.GlideApp
import com.jycompany.yunadiary.util.YoutubePlayActivity
import com.tonyodev.fetch2.*
import com.tonyodev.fetch2core.DownloadBlock
import com.tonyodev.fetch2core.Func
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_detail.*
import kotlinx.android.synthetic.main.fragment_detail.view.*
import kotlinx.android.synthetic.main.fragment_picasso_image_pager.view.*
import kotlinx.android.synthetic.main.item_comment.view.*
import kotlinx.android.synthetic.main.item_comment.view.commentviewitem_imageview_profile
import kotlinx.android.synthetic.main.item_comment.view.commentviewitem_textview_comment
import kotlinx.android.synthetic.main.item_comment.view.commentviewitem_textview_profile
import kotlinx.android.synthetic.main.item_comment_under_detailview.view.*
import kotlinx.android.synthetic.main.item_detail.*
import kotlinx.android.synthetic.main.item_detail.view.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


class DetailViewFragment : Fragment() {
    val ARG_PARAM1 = "param1"
    val ARG_PARAM2 = "param2"

    var mParam1 : String? = null
    var mParam2 : String? = null

    val UPDATE_CONTENT_REQUEST_CODE = 1002
    val IMAGE_URL = "imageURL"
    val DIARY_CONTENT = "diary_content"
    val TIMESTAMP = "timeStamp"
    val IMAGEARR = "imageArr"
    val IMAGEFILENAME = "imageFileName"
    val TAG = "DetailView_tag"

    var user : FirebaseUser? = null
    var firestore : FirebaseFirestore? = null
    var storage : FirebaseStorage? = null
    var imagesSnapshot : ListenerRegistration? = null
    var nameSnapshot : ListenerRegistration? = null
    var mainView : View? = null
    var fcmPush : FcmPush? = null

    var myActivity : Activity? = null

    private lateinit var downloadManager : DownloadManager
    var downloadId : Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(arguments != null){
            mParam1 = requireArguments().getString(ARG_PARAM1)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        user = FirebaseAuth.getInstance().currentUser
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        mainView = inflater.inflate(R.layout.fragment_detail, container, false)

        fcmPush = FcmPush()
        myActivity = activity
        downloadManager = myActivity?.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager //다운로드 매니저 초기화

        mainView?.detailviewfragment_recyclerview?.layoutManager = LinearLayoutManager(activity)
        mainView?.detailviewfragment_recyclerview?.adapter = DetailRecyclerViewAdapter()

        val intentFilter = IntentFilter()
        intentFilter.addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        intentFilter.addAction(DownloadManager.ACTION_NOTIFICATION_CLICKED)
        myActivity?.registerReceiver(onDownloadComplete, intentFilter)

        return mainView
    }

    override fun onDestroyView() {
        myActivity?.unregisterReceiver(onDownloadComplete)
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        //원 코드는 onResume안에서 어댑터 설정했는데, 이렇게 하면 DetailView보일때마다 목록이 계속 제일 위 아이템으로 가버리니 onCreateView로 옮김
//        mainView?.detailviewfragment_recyclerview?.layoutManager = LinearLayoutManager(activity)
//        mainView?.detailviewfragment_recyclerview?.adapter = DetailRecyclerViewAdapter()
        var mainActivity = activity as MainActivity     //activity 는 Fragment클래스의 getActivity임
        mainActivity.progress_bar.visibility = View.INVISIBLE       //MainActivity에서 표시했던 로딩바 제거

        scrollToThatDiary()     //사진 모아보기에서 들어온 경우 해당 위치로 이동
    }

    override fun onStop() {
        super.onStop()
        //이하 리스너를 해제하지 않고 유지함으로서 코멘트를 새로 달때 바로바로 반영해서 화면에 표시되게 바꿈. 그전엔 코멘트 달아도 다른 메뉴 다녀와야 보였음
//        imagesSnapshot?.remove()            //화면 목록 push Driven snapshot 제거
//        nameSnapshot?.remove()

    }

    fun scrollToThatDiary(){
        if(mParam1 != null){
            mainView?.detailviewfragment_recyclerview?.post {
                mainView?.detailviewfragment_recyclerview?.scrollToPosition(mParam1!!.toInt())
                activity?.bottom_navigation?.menu?.getItem(0)?.setChecked(true)      //눌린 걸로 "표시"만 하는 것
                mParam1 = null              //계속 같은 스크롤 위치에 고정되는 현상 수정위한 코드
            }
        }
    }

    val onDownloadComplete = object : BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if(DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.action)){
                if(downloadId == id){
                    val query : DownloadManager.Query = DownloadManager.Query()
                    query.setFilterById(id)
                    var cursor = downloadManager.query(query)
                    if(!cursor.moveToFirst()){      //커서가 비었을 때
                        return
                    }

                    var columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    var status = cursor.getInt(columnIndex)
                    if(status == DownloadManager.STATUS_SUCCESSFUL){
                        Toast.makeText(context, getString(R.string.download_success_msg), Toast.LENGTH_SHORT).show()
                    }else if(status == DownloadManager.STATUS_FAILED){
                        Toast.makeText(context, getString(R.string.download_failed_msg), Toast.LENGTH_SHORT).show()
                    }
                }
            }else if(DownloadManager.ACTION_NOTIFICATION_CLICKED.equals(intent.action)){
                Toast.makeText(context, getString(R.string.notification_clicked_msg), Toast.LENGTH_SHORT).show()
            }
        }
    }

    inner class DetailRecyclerViewAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>(){
        val contentDTOs : ArrayList<ContentDTO>
        val contentUidList : ArrayList<String>
        var userInfoDTOMap : HashMap<String, UsersInfoDTO>

        init {
            contentDTOs = ArrayList()
            contentUidList = ArrayList()
            userInfoDTOMap = HashMap<String, UsersInfoDTO>()

            //팔로잉 된 사람 글만 보이는 코드
//            var uid = FirebaseAuth.getInstance().currentUser?.uid
//            firestore?.collection("users")?.document(uid!!)?.get()
//                ?.addOnCompleteListener { task ->
//                    if(task.isSuccessful){
//                        var userDTO = task.result?.toObject(FollowDTO::class.java)
//                        if(userDTO?.followings != null){
//                            getContents(userDTO?.followings)
//                        }
//                    }
//                }

//            모든 사람이 글 올린거 다 보이는 코드 ( 단, 그림은 좀 늦게 뜨는 경향 )
            imagesSnapshot = firestore?.collection("images")?.orderBy("timestamp", Query.Direction.DESCENDING)
                    ?.addSnapshotListener(MetadataChanges.INCLUDE){ querySnapshot, firebaseFirestoreException ->
                        contentDTOs.clear()
                        contentUidList.clear()

                        if(querySnapshot == null){
                            return@addSnapshotListener
                        }

                        for(snapshot in querySnapshot!!.documents){
                            contentDTOs.add(snapshot.toObject(ContentDTO::class.java)!!)
                            contentUidList.add(snapshot.id)         //images콜렉션 아래 문서의 자동생성된 다큐먼트명을 모은 것
                        }
//                        contentDTOs.sortByDescending { it.timestamp }
//                        contentUidList.reverse()          //첨에 쿼리 받아올때 DESCENDING으로 하는게 간단함

                        notifyDataSetChanged()
                    }

            nameSnapshot = firestore?.collection("usersInfo")
                ?.addSnapshotListener { querySnapshot, firebaseFirestoreException ->

                    if(querySnapshot == null){
                        return@addSnapshotListener
                    }

                    for(snapshot in querySnapshot!!.documents){
                        var userInfoDTO = snapshot.toObject(UsersInfoDTO::class.java)
                        userInfoDTOMap.put(userInfoDTO?.uid!!, userInfoDTO)
                    }
                    notifyDataSetChanged()
                }
        }

        fun getContents(followers: MutableMap<String, Boolean>?){
            imagesSnapshot = firestore?.collection("images")?.orderBy("timestamp")
                ?.addSnapshotListener { querySnapshot, firebaseFirestoreException ->        //데이터 변경시 실시간 동기화 Push Driven
                    contentDTOs.clear()
                    contentUidList.clear()
                    if(querySnapshot == null){
                        return@addSnapshotListener      //querySnapshot 값이 null인 경우 이벤트를 종료하는 것..return@가 아래 지역적(local) 함수만 종료시킴.
                    }
                    for(snapshot in querySnapshot!!.documents){
                        var item = snapshot.toObject(ContentDTO::class.java)!!

                        if(followers?.keys?.contains(item.uid)!!){
                            contentDTOs.add(item)
                            contentUidList.add(snapshot.id)
                        }
                    }
                    notifyDataSetChanged()
                }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_detail, parent, false)
            return CustomViewHolder(view)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val viewHolder = (holder as CustomViewHolder).itemView
            //Profile Image가져오기
            firestore?.collection("profileImages")?.document(contentDTOs[position].uid!!)?.get()
                ?.addOnCompleteListener { task ->
                    if(task.isSuccessful){
                        val url = task.result?.get("image")     //원문 task.result["image"]
                        Glide.with(holder.itemView.context)
                            .load(url)
                            .thumbnail(0.1f)
//                            .apply(RequestOptions().circleCrop())
                                .transform(RoundedCorners(20))
                            .override(100, 100)
                            .into(viewHolder.detailviewitem_profile_image)
                        if(url == null){        //그림 지정 안했을 때
                            Glide.with(holder.itemView.context)
                                    .load(R.drawable.profile_default)
                                    .into(viewHolder.detailviewitem_profile_image)
                        }
                    }
                }

            //유저 얼굴 사진 클릭시 UserFragment로 이동 하는 리스너 설정
            viewHolder.detailviewitem_profile_image.setOnClickListener {
                val fragment = UserFragment()
                val bundle = Bundle()

                bundle.putString("destinationUid", contentDTOs[position].uid)
                bundle.putString("userId", contentDTOs[position].userId)

                fragment.arguments = bundle
                activity!!.supportFragmentManager.beginTransaction().replace(R.id.main_content, fragment).commit()
            }

//            viewHolder.detailviewitem_profile_textview.text = contentDTOs[position].userId        //유저 아이디 가져와서 표시
//            viewHolder.detailviewitem_profile_textview.text = userInfoDTOMap.get(contentDTOs[position].uid.toString())?.relation.toString()+" ( "+contentDTOs[position].userId+" )"     //관계 및 userId를 표시
            viewHolder.detailviewitem_profile_textview.text = userInfoDTOMap.get(contentDTOs[position].uid.toString())?.relation.toString()     //관계만 표시 ( userId 생략 )

            //유저 아이디 클릭시 UserFragment로 이동하는 리스너 설정
            viewHolder.detailviewitem_profile_textview.setOnClickListener {
                val fragment = UserFragment()
                val bundle = Bundle()

                bundle.putString("destinationUid", contentDTOs[position].uid)
                bundle.putString("userId", contentDTOs[position].userId)

                fragment.arguments = bundle
                activity!!.supportFragmentManager.beginTransaction().replace(R.id.main_content, fragment).commit()
            }

            //가운데 그림 두는 곳이 사진인지, 영상인지에, 멀티 사진인지에 따라
            viewHolder.detailviewitem_download_imageview.visibility = View.VISIBLE      //다운로드 버튼. 영상인 경우에만 숨김. 뷰 재활용할때 '사진'인데 안보이면 안되므로 일단 보이게 설정
            if(contentDTOs[position].imageArr?.size != 0){     //멀티 사진인 경우
                var imageArray = contentDTOs[position].imageArr
                viewHolder.detailviewitem_imageview_content.visibility = View.GONE     //기존 사진 한장 뷰를 안 보이게
                viewHolder.detailviewitem_video_play_btn.visibility = View.GONE        //재생버튼 이미지뷰 gone

//                var myPagerAdapter = MyPagerAdapter(activity!!.supportFragmentManager)
                var myPagerAdapter = MyViewPager2Adapter()
                if (imageArray != null) {
                    for(url in imageArray){
//                        var args = Bundle()
//                        args.putString("param1", url)
//                        var frag = PicassoImagePagerFrag.newInstance(url, "")
//                        frag.arguments = args

                        myPagerAdapter.addItem(url)
                    }
//                    myPagerAdapter.notifyDataSetChanged()
                }
                viewHolder.detail_pager.adapter = myPagerAdapter
                viewHolder.detail_pager.offscreenPageLimit = 2         //미리 2페이지 정도 로딩하게 설정
                viewHolder.detail_pager.visibility = View.VISIBLE           //뷰페이저 보이게
                viewHolder.circle_indicator.setViewPager(viewHolder.detail_pager)   //써클 인디케이터와 뷰페이저 연결
//                myPagerAdapter.registerDataSetObserver(viewHolder.circle_indicator.dataSetObserver)     //어댑터 내 항목 변경시 circle인디케이터도 반영->사진 바뀔 위험없어서 주석처리

                viewHolder.circle_indicator.visibility = View.VISIBLE       //사진 인디케이터 보이게

            }else if(contentDTOs[position].imageFileName!!.startsWith("JPEG")){       //사진인 경우
                viewHolder.detailviewitem_imageview_content.visibility = View.VISIBLE       //기존 사진 뷰 보이게
                viewHolder.detailviewitem_video_play_btn.visibility = View.GONE         //재생버튼 이미지뷰 gone
                viewHolder.detail_pager.visibility = View.GONE                          //뷰페이저 gone
                viewHolder.circle_indicator.visibility = View.GONE       //사진 인디케이터 안 보이게
                //가운데 이미지 가져와서 표시
                Glide.with(holder.itemView.context)
                    .load(contentDTOs[position].imageUrl)
                    .placeholder(R.drawable.face_static)
                    .thumbnail(Glide.with(holder.itemView.context).load(R.raw.loading_gif))
                    .centerCrop()
//                    .apply(RequestOptions.bitmapTransform(RoundedCorners(18)))      //사실 이건 작동을 안함. centerCrop과는 같이 사용불가
                    .into(viewHolder.detailviewitem_imageview_content)
                //가운데 이미지 클릭 시 리스너 설정 -> 현재 default 갤러리 뷰어 앱으로 열게 설정
                viewHolder.detailviewitem_imageview_content.setOnClickListener {
                    //아래는 chrisbanes의 Photoview 라이브러리 활용해서 intent거기로 보내는 코드
                    var viewIntent = Intent(context as MainActivity, ShowPictureActivity2::class.java)
                    viewIntent.putExtra("imageUri", contentDTOs[position].imageUrl)
                    startActivity(viewIntent)
                }
            }else if(contentDTOs[position].imageFileName!!.startsWith("VIDEO")){        //영상인 경우
                viewHolder.detailviewitem_imageview_content.visibility = View.VISIBLE       //기존 사진 뷰 보이게
                viewHolder.detailviewitem_video_play_btn.visibility = View.VISIBLE         //재생버튼 이미지뷰 표시
                viewHolder.detail_pager.visibility = View.GONE                              //뷰페이저 gone
                viewHolder.circle_indicator.visibility = View.GONE       //사진 인디케이터 안 보이게
                viewHolder.detailviewitem_download_imageview.visibility = View.GONE      //다운로드 버튼 gone
                //가운데 이미지 가져와서 표시
                val imageRef = storage!!.reference.child(
                    "images/thumbnail/"+contentDTOs[position].imageUrl!!.replaceBefore("VIDEO", "").replaceAfter(".png", ""))
                GlideApp.with(holder.itemView.context)
                    .load(imageRef)
//                    .thumbnail(0.1f)
                    .placeholder(R.drawable.face_static)
                    .thumbnail(Glide.with(holder.itemView.context).load(R.raw.loading_gif))
                    .dontTransform().dontAnimate()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .centerCrop()
                    .into(viewHolder.detailviewitem_imageview_content)
                //사진 이미지 클릭 시 리스너 설정 -> YoutubePlayActivity 실행되야. intent에 유튜브의 videoId 넣어줄 것임. 단 기존 비디오들은 youtubeId==""이므로 기존대로 처리
                val videoPlayListener : View.OnClickListener = View.OnClickListener {
                    if(contentDTOs[position].youtubeId == ""){
                        //과거 PlayVideoActivity 인텐트로 보냈던 곳, 이젠 PlayVideoActivity자체를 전부 삭제함
                    }else{
                        var playYoutubeVideoIntent = Intent(context as MainActivity, YoutubePlayActivity::class.java)
                        playYoutubeVideoIntent.putExtra("youtubeKey", contentDTOs[position].youtubeId)         //재생시엔 youtubeId를 보냄
                        startActivity(playYoutubeVideoIntent)
                    }
                }
                viewHolder.detailviewitem_imageview_content.setOnClickListener(videoPlayListener)
                //재생 버튼 눌렀을 때 리스너 설정 -> 바로 위의 사진 이미지 클릭시와 똑같이 리스너 설정
                viewHolder.detailviewitem_video_play_btn.setOnClickListener(videoPlayListener)
            }

            //설명 텍스트 가져와서 표시 ( 일기 내용 )
            viewHolder.detailviewitem_explain_textview.text = contentDTOs[position].explain
            //좋아요 버튼(하트표) 에다 클릭리스너 설정
            viewHolder.detailviewitem_favorite_imageview.setOnClickListener {
                favoriteEvent(position)
            }
            //좋아요 하트모양 그림 설정. 현재 사용하는 유저가 여기에 좋아요를 눌렀다면
            if(contentDTOs[position].favorites.containsKey(FirebaseAuth.getInstance().currentUser!!.uid)){
                //[안이 꽉 찬 하트] 로 표시
                viewHolder.detailviewitem_favorite_imageview.setImageResource(R.drawable.ic_favorite)
            }else{      //아니면 가장자리만 그려진 빈 하트로 표시
                viewHolder.detailviewitem_favorite_imageview.setImageResource(R.drawable.ic_favorite_border)
            }
            //좋아요의 카운터 숫자 가져와서 표시
            viewHolder.detailviewitem_favoritecounter_textview.text =
                getString(R.string.good)+contentDTOs[position].favoriteCount+getString(R.string.good_count)

            //일기 작성한 날짜 가져와서 표시 - 기존 코드
//            val writedDate : Long = contentDTOs[position].timestamp!!
//            val sdf = SimpleDateFormat("yyyy년 MM월 dd일")
//            viewHolder.detailviewitem_writed_date.setText(sdf.format(Date(writedDate)))

            //일기 작성한 날짜 가져와서 표시
            val writedDate : Long = contentDTOs[position].timestamp!!
            var sdf = SimpleDateFormat("yyyy-MM-dd")
            val yBirthday = "2020-10-19"

            try {
                val birthDate = sdf.parse(yBirthday)
                val secondDate = sdf.parse(sdf.format(Date(writedDate)))

                val calDate = secondDate.time - birthDate.time
                var calDateDays : Long = calDate/(24*60*60*1000) + 1           //한국은 아기 태어난 백일 계산은 태어난날도 1일로 봄. 태어난 날 = D+1

                var dDays : String? = null
                if(calDateDays > 1 ){               //여기에 다 걸려버리니 실제로 밑에 (백일)은 (D+100)로 표시되었음.;;
                    dDays = "(D+"+calDateDays.toString()+", "
                }else if(calDateDays.toString().equals("1")){
                    dDays = "(태어난 첫날, "
                }else if(calDateDays.toString().equals("100")){           //각종 기념일 미리 넣기
                    dDays = "(백일, "
                }else{
                    dDays = "(D"+(calDateDays-1).toString()+", "
                }
                var letter_dDays : String = (calDateDays/30).toString()+"개월 "+(calDateDays%30).toString()+"일)"

                val stringToday = sdf.format(Date(writedDate))
                if(stringToday.endsWith("-10-19")){        //생일날인 경우
                    if(stringToday.startsWith("2021")||stringToday.startsWith("2020")){     //첫 돌 및 태어난 날은 제외
                        //do nothing
                    }else{
                        var yearString = stringToday.substring(0,4)
                        dDays = "("+(yearString.toInt() - 2020).toString()+"번째 생일, "
                    }
                }

                sdf = SimpleDateFormat("yyyy년 MM월 dd일")
                viewHolder.detailviewitem_writed_date.setText(sdf.format(Date(writedDate))+dDays+letter_dDays)

            }catch (e : Exception){
                Log.d(TAG, "D-day 계산 및 변환에서 Exception 발생")
            }

            //카드 제일 아래 comment 나열하기 위한 뷰 생성을 코드에서 진행함..일단 일기내용에 배열내용 제대로 가져오는지 테스트를 위해
            var commentArrayList = contentDTOs[position].commentList
            viewHolder.comment_list_linearlayout.removeAllViews()
            if(commentArrayList!!.size != 0){
                var layoutInflater = activity?.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                for( aComment in commentArrayList!!){           //각각의 합쳐진 코멘트에 대해서 실행
                    val splitedStringArray = aComment.split("[]")
                    //1. 관계호칭 ( ex-아빠, 할아버지 )   2. user Uid      3. 덧글 내용        4. 타임스탬프
//                    Log.d(TAG, splitedStringArray[0]+"[]"+splitedStringArray[1]+"[]"+splitedStringArray[2]+"[]"+splitedStringArray[3])
                    var viewCommentLinearLayout = layoutInflater.inflate(R.layout.item_comment_under_detailview,
                            viewHolder.comment_list_linearlayout, false)

                    var commenterName = splitedStringArray[0]
                    if(commenterName.contains(" ")){
                        commenterName = commenterName.replace(" ", "\n")
                    }
                    viewCommentLinearLayout.commentviewitem_textview_profile.text = commenterName       //코멘터 관계호칭

                    viewCommentLinearLayout.commentviewitem_textview_comment.text = splitedStringArray[2]       //코멘트 내용
                    //자기 코멘트인 경우(댓글의 uid == 자기 uid 인 경우), x 버튼 표시하고, ClickListener 설정
                    if(splitedStringArray[1].equals(user!!.uid)){
                        viewCommentLinearLayout.commentviewitem_imageview_del_comment_btn.visibility = View.VISIBLE
                        viewCommentLinearLayout.commentviewitem_imageview_del_comment_btn.setOnClickListener {
                            val deleteCommentBuilder: AlertDialog.Builder = AlertDialog.Builder(context as MainActivity)

                            deleteCommentBuilder.setTitle(getString(R.string.comment_really_delete))
                            deleteCommentBuilder.setIcon(R.drawable.delete_confirm_dialog)
                            deleteCommentBuilder.setPositiveButton(R.string.yes_in_detailfrag, DialogInterface.OnClickListener { dialog, which ->
                                val commentRef = firestore!!.collection("images")
                                        .document(contentUidList[position])
                                commentRef.update("commentList", FieldValue.arrayRemove(aComment))
                            })
                            deleteCommentBuilder.setNegativeButton(R.string.no_in_detailfrag, DialogInterface.OnClickListener { dialog, which -> })
                            val deleteCommentDialog = deleteCommentBuilder.create()
                            deleteCommentDialog.show()


                            val titleId = resources.getIdentifier("alertTitle", "id", requireContext().packageName)
                            var textViewTitle : TextView? = null
                            if(titleId > 0){
                                textViewTitle = deleteCommentDialog.findViewById<View>(titleId) as TextView
                            }
                            val textViewMessage = deleteCommentDialog.window?.findViewById<View>(android.R.id.message) as TextView
                            val buttonYes = deleteCommentDialog.window?.findViewById<View>(android.R.id.button1) as Button
                            val buttonNo = deleteCommentDialog.window?.findViewById<View>(android.R.id.button2) as Button
                            val font = ResourcesCompat.getFont(requireContext(), R.font.hanmaum_myungjo)
                            textViewTitle?.setTypeface(font)
                            textViewMessage.setTypeface(font)
                            buttonYes.setTypeface(font)
                            buttonNo.setTypeface(font)
                        }
                    }else{
                        viewCommentLinearLayout.commentviewitem_imageview_del_comment_btn.visibility = View.GONE
                    }
                    //코멘터 프로필 이미지 가져오기
                    firestore?.collection("profileImages")?.document(splitedStringArray[1])     //코멘터의 uid가 다큐먼트명
                            ?.get()?.addOnCompleteListener { task ->
                                if(task.isSuccessful){
                                    val urls = task.result?.toObject(ProfileImagesDTO::class.java)?.image.toString()
                                    if(urls.equals("null")){       //유저의 uid가 profileImages DB에 없어서 못 받아오는 경우
                                        viewCommentLinearLayout.commentviewitem_imageview_profile.setImageResource(R.drawable.profile_default)
                                    }else{      //유저 uid가 profileImages DB에 있어서 제대로 받아오는 경우
                                        Glide.with(myActivity!!)
                                                .load(urls)
                                                .apply(RequestOptions().circleCrop())
                                                .into(viewCommentLinearLayout.commentviewitem_imageview_profile)
                                        viewCommentLinearLayout.commentviewitem_imageview_profile.setOnClickListener {
                                            var viewIntent = Intent(context as MainActivity, ShowPictureActivity2::class.java)
                                            viewIntent.putExtra("imageUri", urls)
                                            startActivity(viewIntent)
                                        }
                                    }
                                }else{          //task.result 를 제대로 못 받아온 경우
                                    viewCommentLinearLayout.commentviewitem_imageview_profile.setImageResource(R.drawable.profile_default)
                                }
                            }
                    //덧글 버튼들에 클릭리스너 달기. 단 얼굴 그림 눌러서 그림 크게 띄우는 건, 그림이 들어있는 경우에 한하므로 위에서 Listener 설정함
                    viewCommentLinearLayout.commentviewitem_textview_profile.setOnClickListener {
                        val fragment = UserFragment()
                        val bundle = Bundle()

                        bundle.putString("destinationUid", splitedStringArray[1])       //유저 uid를 넣고
                        bundle.putString("userId", splitedStringArray[0])               //유저의 "관계명" 을 넣음

                        fragment.arguments = bundle
                        activity!!.supportFragmentManager.beginTransaction().replace(R.id.main_content, fragment).commit()
                    }
                    viewCommentLinearLayout.commentviewitem_textview_comment.setOnClickListener {
                        val fragment = UserFragment()
                        val bundle = Bundle()

                        bundle.putString("destinationUid", splitedStringArray[1])       //유저 uid를 넣고
                        bundle.putString("userId", splitedStringArray[0])               //유저의 "관계명" 을 넣음

                        fragment.arguments = bundle
                        activity!!.supportFragmentManager.beginTransaction().replace(R.id.main_content, fragment).commit()
                    }
                    if(aComment.equals(commentArrayList.last())){
                        viewCommentLinearLayout.comment_horizon_line.visibility = View.GONE
                    }

                    viewHolder.comment_list_linearlayout.addView(viewCommentLinearLayout)
                }
            }

            // 덧글 남기기 버튼 클릭시
            viewHolder.detailviewitem_comment_imageview.setOnClickListener {
                val intent = Intent(activity, CommentActivity::class.java)
                intent.putExtra("contentUid", contentUidList[position])
                intent.putExtra("destinationUid", contentDTOs[position].uid)
                startActivity(intent)
            }

            viewHolder.detailviewitem_download_imageview.setOnClickListener {
                val downloadDialogBuilder : AlertDialog.Builder = AlertDialog.Builder(context as MainActivity)

                downloadDialogBuilder.setTitle(getString(R.string.ask_download_title))
                downloadDialogBuilder.setMessage(getString(R.string.ask_download))
                downloadDialogBuilder.setIcon(R.drawable.download_btn)
                downloadDialogBuilder.setPositiveButton(R.string.yes_in_detailfrag, DialogInterface.OnClickListener { dialog, which ->
                    //사진 또는 영상을 다운로드 받는 코드
                    if(contentDTOs[position].imageArr?.size != 0){      //멀티 사진인 경우
                        for(imageUrl in contentDTOs[position].imageArr!!){
                            var strArr = imageUrl.split("JPEG", "_.png")
                            var rightImageName = "JPEG"+strArr[1]+"_.png"
                            downloadImageOrMovie(imageUrl, rightImageName)      //매개변수 ( 다운로드 url, 새롭게 생길 파일명 )
                        }
                    }else{                                              //1장 사진이나 영상인 경우
                        if(contentDTOs[position].imageFileName!!.startsWith("VIDEO")){         //영상 일기면
                            //이제 Youtube 로 모두 올렸으므로, 다운로드 허용하지 않음 ( 뭐..애초에 영상인 경우 다운로드 버튼이 invisible 임 )
                            Toast.makeText(activity,
                                getString(R.string.download_movie_guide_message), Toast.LENGTH_SHORT).show()
//                            if(contentDTOs[position].videoUrl ==null || contentDTOs[position].videoUrl.toString().length <= 10){    //섬네일 없는 영상 일기일 때
//                                downloadImageOrMovie(contentDTOs[position].imageUrl, contentDTOs[position].imageFileName)   //영상을 받긴 하지만, imageUrl에 영상 원본이 들어가 있음
//                            }else{
//                                downloadImageOrMovie(contentDTOs[position].videoUrl, contentDTOs[position].imageFileName)   //영상을 받음. (DB의 imageUrl은 섬네일 url이고, videoUrl이 영상 url )
//                            }
                        }else{                                              //video로 시작하지 않고 imageFileName이 JPEG로 시작시. 즉 1장 사진 일기일 때
                            downloadImageOrMovie(contentDTOs[position].imageUrl, contentDTOs[position].imageFileName)   //사진을 받음
                        }
                    }
                })
                downloadDialogBuilder.setNegativeButton(R.string.no_in_detailfrag, DialogInterface.OnClickListener { dialog, which ->  })

                val downloadDialog = downloadDialogBuilder.create()
                downloadDialog.show()

                val titleId = resources.getIdentifier("alertTitle", "id", requireContext().packageName)
                var textViewTitle : TextView? = null
                if(titleId > 0){
                    textViewTitle = downloadDialog.findViewById<View>(titleId) as TextView
                }
                val textViewMessage = downloadDialog.window?.findViewById<View>(android.R.id.message) as TextView
                val buttonYes = downloadDialog.window?.findViewById<View>(android.R.id.button1) as Button
                val buttonNo = downloadDialog.window?.findViewById<View>(android.R.id.button2) as Button
                val font = ResourcesCompat.getFont(requireContext(), R.font.hanmaum_myungjo)
                textViewTitle?.setTypeface(font)
                textViewMessage.setTypeface(font)
                buttonYes.setTypeface(font)
                buttonNo.setTypeface(font)
            }

            //자신이 쓴 글이면 삭제, 수정 버튼 표시
            if(contentDTOs[position].uid == FirebaseAuth.getInstance().currentUser!!.uid){
                viewHolder.detailviewitem_delete_imageview.visibility = View.VISIBLE
                viewHolder.detailviewitem_edit_imageview.visibility = View.VISIBLE

                //글 삭제 버튼 클릭리스너 설정
                viewHolder.detailviewitem_delete_imageview.setOnClickListener {
                    val deleteDialogBuilder : AlertDialog.Builder = AlertDialog.Builder(context as MainActivity)

                    deleteDialogBuilder.setTitle(getString(R.string.ask_really_delete))
                    deleteDialogBuilder.setIcon(R.drawable.delete_confirm_dialog)
                    deleteDialogBuilder.setPositiveButton(R.string.yes_in_detailfrag, DialogInterface.OnClickListener { dialog, which ->
                        //Storage에서 실제 파일들(사진 또는 영상) 삭제
                        if(contentDTOs[position].imageFileName!!.startsWith("VIDEO")){     //일단 영상 일기인 경우 여기로
                            //섬네일파일 삭제 부분. Youtube 에 올라 있는 원본 영상은... 안 건드리기로 했음. ( 직접 유저가 웹브라우저에서 삭제 권장 )
                            val thumbFileName = contentDTOs[position].imageFileName!!.replace("_.mp4", "_cover.png")
                            var thumbRef = storage?.reference?.child("images/thumbnail")?.child(thumbFileName)
                            thumbRef?.delete()?.addOnCompleteListener { task ->
                                if(task.isSuccessful){
                                    Toast.makeText(activity, getString(R.string.delete_complete), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }else{             //멀티 사진 또는 1장 일기의 경우. 일단 처음 imageFileName의 한장은 지워야 하므로
                            var ref = storage?.reference?.child("images")?.child(contentDTOs[position].imageFileName!!)
                            ref?.delete()?.addOnCompleteListener { task ->
                                if(task.isSuccessful){
                                    Toast.makeText(activity, getString(R.string.delete_complete), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        //멀티 사진일 경우 Storage서 추가 삭제함.
                        if(contentDTOs[position].imageArr?.size != 0){
                            var fileToDeleteArr = ArrayList<String>()
                            for(imageName in contentDTOs[position].imageArr!!){
                                fileToDeleteArr.add(imageName)
                            }
                            for(imageName in fileToDeleteArr){
                                var newStr = imageName.split("JPEG", "_.png")
                                var rightImageName = "JPEG"+newStr[1]+"_.png"
                                var multiPicRef = storage?.reference?.child("images")?.child(rightImageName)
                                multiPicRef?.delete()?.addOnCompleteListener { task ->
                                    if(task.isSuccessful){
                                        Log.d(TAG, "멀티 사진들 삭제됨 :"+imageName)
                                    }else{
                                        Log.d(TAG, "멀티 사진들 삭제실패 :"+imageName)
                                    }
                                }
                            }
                        }
                        //Storage 삭제 이후에 cloud DB에서 정보 삭제
                        var query = firestore?.collection("images")?.whereEqualTo("imageUrl", contentDTOs[position].imageUrl)
                        query?.get()?.addOnCompleteListener { task ->
                            if(task.isSuccessful){
                                for(dc in task.result?.documents!!){
                                    dc.reference.delete()
                                }
                            }
                        }
                    })
                    deleteDialogBuilder.setNegativeButton(R.string.no_in_detailfrag, DialogInterface.OnClickListener { dialog, which ->  })

                    val deleteDiaryDialog = deleteDialogBuilder.create()
                    deleteDiaryDialog.show()

                    val titleId = resources.getIdentifier("alertTitle", "id", requireContext().packageName)
                    var textViewTitle : TextView? = null
                    if(titleId > 0){
                        textViewTitle = deleteDiaryDialog.findViewById<View>(titleId) as TextView
                    }
                    val textViewMessage = deleteDiaryDialog.window?.findViewById<View>(android.R.id.message) as TextView
                    val buttonYes = deleteDiaryDialog.window?.findViewById<View>(android.R.id.button1) as Button
                    val buttonNo = deleteDiaryDialog.window?.findViewById<View>(android.R.id.button2) as Button
                    val font = ResourcesCompat.getFont(requireContext(), R.font.hanmaum_myungjo)
                    textViewTitle?.setTypeface(font)
                    textViewMessage.setTypeface(font)
                    buttonYes.setTypeface(font)
                    buttonNo.setTypeface(font)
                }
                //글 수정 버튼 클릭 리스너 설정
                viewHolder.detailviewitem_edit_imageview.setOnClickListener {
                    //글 수정할 것인가요? AlertDialog 생성
                    val updateDialogBuilder : AlertDialog.Builder = AlertDialog.Builder(context as MainActivity)
                    updateDialogBuilder.setTitle(getString(R.string.ask_editing_diary))
                    updateDialogBuilder.setIcon(R.drawable.ic_edit_pen)
                    updateDialogBuilder.setPositiveButton(R.string.yes_in_detailfrag, DialogInterface.OnClickListener { dialog, which ->
                        //AddPhotoActivity 띄울 때 이건, 글 업데이트이므로 기존 글 정보를 Intent에 집어넣어서 실행
                        //단, 사진과 영상은 다른 액티비티를 콜한다.
                        var updateContentIntent : Intent? = null
                        if(contentDTOs[position].imageFileName!!.startsWith("JPEG")){           //사진인 경우 => 멀티와 단일 사진 2개로 또 나뉨
                            if(contentDTOs[position].imageArr?.size != 0){       //멀티 사진일 때
                                updateContentIntent = Intent(context, AddMultiplePhotoActivity::class.java)
                            }else{                                              //단일 사진일 때
                                updateContentIntent = Intent(context, AddPhotoActivity::class.java)
                            }
                        }else if(contentDTOs[position].imageFileName!!.startsWith("VIDEO")){    //영상인 경우
                            updateContentIntent = Intent(context, AddYoutubeMovieActivity::class.java)
                        }
                        updateContentIntent!!.putExtra(IMAGE_URL, contentDTOs[position].imageUrl)        //1. 이미지 URL
                        if(contentDTOs[position].imageArr?.size != 0){
                            updateContentIntent.putStringArrayListExtra(IMAGEARR, contentDTOs[position].imageArr)       //집합 이미지 url 인텐트에 담음
                        }
                        updateContentIntent.putExtra(DIARY_CONTENT, contentDTOs[position].explain)    //2. 일기 내용
                        updateContentIntent.putExtra(TIMESTAMP, contentDTOs[position].timestamp)      //3. timeStamp
                        updateContentIntent.putExtra(IMAGEFILENAME, contentDTOs[position].imageFileName)  //4. Storage의 이미지파일명

                        activity?.startActivityForResult(updateContentIntent, UPDATE_CONTENT_REQUEST_CODE)
                    })
                    updateDialogBuilder.setNegativeButton(R.string.no_in_detailfrag, DialogInterface.OnClickListener { dialog, which ->  })
                    val updateDiaryInformDialog = updateDialogBuilder.create()
                    updateDiaryInformDialog.show()

                    val titleId = resources.getIdentifier("alertTitle", "id", requireContext().packageName)
                    var textViewTitle : TextView? = null
                    if(titleId > 0){
                        textViewTitle = updateDiaryInformDialog.findViewById<View>(titleId) as TextView
                    }
                    val textViewMessage = updateDiaryInformDialog.window?.findViewById<View>(android.R.id.message) as TextView
                    val buttonYes = updateDiaryInformDialog.window?.findViewById<View>(android.R.id.button1) as Button
                    val buttonNo = updateDiaryInformDialog.window?.findViewById<View>(android.R.id.button2) as Button
                    val font = ResourcesCompat.getFont(requireContext(), R.font.hanmaum_myungjo)
                    textViewTitle?.setTypeface(font)
                    textViewMessage.setTypeface(font)
                    buttonYes.setTypeface(font)
                    buttonNo.setTypeface(font)
                }
            }else{
                viewHolder.detailviewitem_delete_imageview.visibility = View.INVISIBLE
                viewHolder.detailviewitem_edit_imageview.visibility = View.INVISIBLE
            }
        }

        fun downloadImageOrMovie(url : String?, filename : String?){
            if(Build.VERSION.SDK_INT >= 30){        //안드로이드 API 30 (Q) 이상시
                val fetchConfiguration = FetchConfiguration.Builder(context!!)
                    .setDownloadConcurrentLimit(3)
                    .build()
                val fetch = Fetch.Impl.getInstance(fetchConfiguration)
                var file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename)
                val request = Request(url!!, file.toString());
                request.priority = Priority.HIGH
                request.networkType = NetworkType.ALL
//                request.addHeader("clientKey", "dv")      //header 가 없어도 다운로드 되는 것은 확인함

                var builder : Notification.Builder? = null;
                var nmc : NotificationManagerCompat? = null;
                var channel = NotificationChannel("channelid1", "001", NotificationManager.IMPORTANCE_DEFAULT)
                channel.description = "Multi image file downloads notification"
                var manager = activity!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
                fun showNotification(id : Int){
                    if(Build.VERSION.SDK_INT >=Build.VERSION_CODES.O){
                        builder = Notification.Builder(context, "channelid1")
                        builder!!.setContentTitle("일기 사진 다운로드")
                        builder!!.setAutoCancel(false)
                        builder!!.setSmallIcon(R.drawable.push_icon2)
                        builder!!.setProgress(100, 0, false)
                        builder!!.setWhen(System.currentTimeMillis())
                        builder!!.setPriority(Notification.PRIORITY_DEFAULT)

                        nmc = NotificationManagerCompat.from(context!!)
                        nmc!!.notify(id, builder!!.build())
                    }else{
                        builder = Notification.Builder(context)
                        builder!!.setContentTitle("일기 사진 다운로드")
                        builder!!.setAutoCancel(false)
                        builder!!.setSmallIcon(R.drawable.push_icon2)
                        builder!!.setProgress(100, 0, false)
                        builder!!.setWhen(System.currentTimeMillis())
                        builder!!.setPriority(Notification.PRIORITY_DEFAULT)

                        nmc = NotificationManagerCompat.from(context!!)
                        nmc!!.notify(id, builder!!.build())
                    }
                }

                val fetchListener = object : FetchListener {
                    override fun onAdded(download: Download) {
                        showNotification(download.id)
                    }

                    override fun onCancelled(download: Download) {}

                    override fun onCompleted(download: Download) {
                        //갤러리앱에 브로드캐스팅 해야
//                        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
//                        File file = new File(path);
//                        intent.setData(Uri.fromFile(file));
//                        sendBroadcast(intent);
                        context!!.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)))
                        Toast.makeText(context, "일기 사진 다운로드가 완료되었습니다", Toast.LENGTH_SHORT).show()
                    }

                    override fun onDeleted(download: Download) {}

                    override fun onDownloadBlockUpdated(
                        download: Download,
                        downloadBlock: DownloadBlock,
                        totalBlocks: Int
                    ) {

                    }

                    override fun onError(download: Download, error: Error, throwable: Throwable?) {}

                    override fun onPaused(download: Download) {}

                    override fun onProgress(
                        download: Download,
                        etaInMilliSeconds: Long,
                        downloadedBytesPerSecond: Long
                    ) {
                        var progress = download.progress
                        builder!!.setProgress(100, progress, false)
                        nmc!!.notify(download.id, builder!!.build())

                    }

                    override fun onQueued(download: Download, waitingOnNetwork: Boolean) {
                        if (request.id == download.id){

                        }
                    }

                    override fun onRemoved(download: Download) {}

                    override fun onResumed(download: Download) {}

                    override fun onStarted(
                        download: Download,
                        downloadBlocks: List<DownloadBlock>,
                        totalBlocks: Int
                    ) {

                    }

                    override fun onWaitingNetwork(download: Download) {}

                }
                fetch.addListener(fetchListener)
                fetch.enqueue(request, Func {result ->
                    //사진 다운로드 시작됨
                }, Func { error ->
                    Toast.makeText(myActivity,
                        "Error ="+error.toString(),
                        Toast.LENGTH_LONG).show()
                })
            }else{                                  //안드로이드 API 30 Q 미만 시
                val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename)
                val request = DownloadManager.Request(Uri.parse(url))
                    .setTitle("영상(사진)을 다운받는 중")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationUri(Uri.fromFile(file))
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                downloadId = downloadManager.enqueue(request)
            }
        }

        override fun getItemCount(): Int {
            return contentDTOs.size
        }

        fun favoriteAlarm(destinationUid: String){
            val alarmDTO = AlarmDTO()

            alarmDTO.destinationUid = destinationUid
            alarmDTO.userId = user?.email
            alarmDTO.uid = user?.uid
            alarmDTO.kind = 0
            alarmDTO.timestamp = System.currentTimeMillis()
            FirebaseFirestore.getInstance().collection("alarms").document().set(alarmDTO)
            var message = user?.email + getString(R.string.alarm_favorite)
//            fcmPush?.sendMessage(destinationUid, getString(R.string.push_title), message)       //누가 좋아요 누르면 푸시 알람 가게 하는 코드 -> 일시 정지
        }

        private fun favoriteEvent(position: Int){  //좋아요 값을 올리되, 중복안되게 transaction 활용. 근데 반영이 잘 안되는 것 같아, set인 기본데이터입력방법으로 변경

            var tsDoc = firestore?.collection("images")?.document(contentUidList[position])
            firestore?.runTransaction { transaction ->
                val uid = FirebaseAuth.getInstance().currentUser!!.uid
                var contentDTO = transaction.get(tsDoc!!).toObject(ContentDTO::class.java)

                if(contentDTO!!.favorites.containsKey(uid)){    //이미 좋아요 눌러져 있다면. Unstar the post and remove self from stars
                    //취소는 안되게 그냥 Do nothing
//                    contentDTO?.favoriteCount = contentDTO?.favoriteCount!! - 1
//                    contentDTO?.favorites.remove(uid)
                }else{      //좋아요 처음 누르는 거면, Star the post and add self to stars
                    contentDTO?.favoriteCount = contentDTO?.favoriteCount!! + 1
                    contentDTO?.favorites[uid] = true
                    favoriteAlarm(contentDTOs[position].uid!!)
                }
                transaction.set(tsDoc, contentDTO)
            }
        }
    }

    inner class CustomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    inner class MyViewPager2Adapter(var items: ArrayList<String> = arrayListOf()) : RecyclerView.Adapter<RecyclerView.ViewHolder>(){
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_picasso_image_pager, parent, false)
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
                .override(resources.displayMetrics.widthPixels, Math.round(280f*(resources.displayMetrics.density)))    //딱 사이즈 맞게 override, dp를 px로 변환한 코드, 280px로 일기에 사진뷰 크기 잡아놨으므로
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .into(viewHolder.picasso_image_fragment_view)

            viewHolder.picasso_image_fragment_view.setOnClickListener {
                //아래는 chrisbanes의 Photoview 라이브러리 활용해서 intent거기로 보내는 코드
//                var viewIntent = Intent(context as MainActivity, ShowPictureActivity2::class.java)
//                viewIntent.putExtra("imageUri", items[position])
//                startActivity(viewIntent)

                //ViewPager2 액티비티로 보내는 코드
                var viewIntent = Intent(context as MainActivity, ShowMultiPictureActivity::class.java)
                viewIntent.putExtra("imageUri", items)      //아이템 전부 보냄.
                viewIntent.putExtra("whereThePageIs", position)     //현재 보고 있던 사진 위치도 보냄 ( 0, 1, 2...)
                startActivity(viewIntent)
            }
        }

        fun addItem(item : String){
            items.add(item)
        }

        override fun getItemCount(): Int {
            return items.size
        }

    }

}