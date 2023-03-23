package com.jycompany.yunadiary.navigation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.jycompany.yunadiary.R
import com.jycompany.yunadiary.model.UsersInfoDTO
import com.theartofdev.edmodo.cropper.CropImage
import com.theartofdev.edmodo.cropper.CropImageView
import kotlinx.android.synthetic.main.fragment_info.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_info.view.*

class InfoFragment : Fragment() {
    var mainView : View? = null
    var firestore : FirebaseFirestore? = null

    var uid : String? = null

    var imageprofileListenerRegistration : ListenerRegistration? = null

    val PICK_PROFILE_FROM_ALBUM = 10

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        mainView = inflater.inflate(R.layout.fragment_info, container, false)

        firestore = FirebaseFirestore.getInstance()

        uid = FirebaseAuth.getInstance().currentUser!!.uid

        //처음 정보 입력화면에선 프로필 사진 넣는걸 일단 중지함.(Firebase랑 충돌인지 코드를 잘 못 짠건지 불명확)
//        mainView!!.account_info_profile?.setOnClickListener {
//            if(ContextCompat.checkSelfPermission(activity!!, Manifest.permission.READ_EXTERNAL_STORAGE)
//                    == PackageManager.PERMISSION_GRANTED){
//                val profileImageCropBuilder : CropImage.ActivityBuilder = CropImage.activity()
//                profileImageCropBuilder.setCropShape(CropImageView.CropShape.OVAL)
//                profileImageCropBuilder.setAspectRatio(1,1)
//                profileImageCropBuilder.setFixAspectRatio(true)
//                profileImageCropBuilder.setActivityTitle(getString(R.string.pick_profile_crop))
//                profileImageCropBuilder.setCropMenuCropButtonTitle(getString(R.string.crop))
//                val profileImageIntent = profileImageCropBuilder.getIntent(context!!)
//                activity?.startActivityForResult(profileImageIntent, CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE)
//
//                //기존 코드 : 폰 기본 사진 선택 앱 오픈 . 여기선 앨범 오픈
////                val photoPickerIntent = Intent(Intent.ACTION_PICK)
////                photoPickerIntent.type = "image/*"
////                activity?.startActivityForResult(photoPickerIntent, PICK_PROFILE_FROM_ALBUM)            //그냥 startAcitiviyForResult시 MainActivity로 전달이 안되는 증상 있음.
//            }else{      //외부저장장치 읽을 권한이 없을 때
//                Toast.makeText(activity, getString(R.string.permission_denied_msg), Toast.LENGTH_LONG).show()
//            }
//        }

        return mainView
    }



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity!!.bottom_navigation.visibility = View.INVISIBLE
        activity!!.nav_division.visibility = View.INVISIBLE



        btn_info_insert.setOnClickListener {
            if(editText_name_info.text.toString().isNullOrEmpty() ||
                editText_relation_info.text.toString().isNullOrEmpty() ||
                editText_wordwannasay_info.text.toString().isNullOrEmpty() ){
                Toast.makeText(activity, getString(R.string.submit_all_info), Toast.LENGTH_SHORT).show()
            }else{          //정보가 모두 기입되었으면
                progress_bar_info.visibility = View.VISIBLE
                val usersInfoDTO = UsersInfoDTO()
                usersInfoDTO.uid = uid
                usersInfoDTO.name = editText_name_info.text.toString()
                usersInfoDTO.relation = editText_relation_info.text.toString()
                usersInfoDTO.wordWannaSay = editText_wordwannasay_info.text.toString()

                val me = FirebaseAuth.getInstance().currentUser
                FirebaseFirestore.getInstance()
                    .collection("usersInfo")
                    .document(me!!.uid).set(usersInfoDTO).addOnCompleteListener { task ->
                        if(task.isSuccessful){
                            val detailViewFragment = DetailViewFragment()
                            activity!!.supportFragmentManager.beginTransaction().replace(R.id.main_content, detailViewFragment).commit()
                            Toast.makeText(activity, getString(R.string.submit_complete), Toast.LENGTH_SHORT).show()
                            activity!!.bottom_navigation.visibility = View.VISIBLE
                            activity!!.nav_division.visibility = View.VISIBLE
                        }
                    }
            }
        }
    }

    override fun onResume() {
        super.onResume()
//        getProfileImage()
    }

//    fun getProfileImage(){          //firestore DB에서 이전에 업로드한 사진을 가져와서 뿌려주는 코드. Push Driven
//        imageprofileListenerRegistration = firestore?.collection("profileImages")?.document(uid!!)
//                ?.addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
//                    if(documentSnapshot?.data != null){
//                        val url = documentSnapshot?.data!!["image"].toString()
//                        Log.d("tags", "url="+url)
//
//                        Glide.with(activity!!)
//                                .load(url)
//                                .apply(RequestOptions().circleCrop())
//                                .into(mainView!!.account_info_profile)
//                    }else{                              //이미지 사진이 없는 경우 파란 디폴트 얼굴로 하되, 본인과 타인 구분
//                        Glide.with(activity!!)
//                                .load(R.drawable.profile_default_withtext)
//                                .into(mainView!!.account_info_profile)
//                    }
//                }
//    }


    override fun onStop() {     //Fragment stop()시에 Snapshot 제거
        super.onStop()
        imageprofileListenerRegistration?.remove()
    }
}