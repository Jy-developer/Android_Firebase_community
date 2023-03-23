package com.jycompany.yunadiary

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jycompany.yunadiary.model.AuthenticDTO
import com.jycompany.yunadiary.model.UsersInfoDTO
import kotlinx.android.synthetic.main.activity_info.*

class InfoActivity : AppCompatActivity() {
    var firestore : FirebaseFirestore? = null
    var uid : String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info)

        firestore = FirebaseFirestore.getInstance()
        uid = FirebaseAuth.getInstance().currentUser!!.uid

        btn_info_insert.setOnClickListener {
            if(editText_name_info.text.toString().isNullOrEmpty() ||
                editText_relation_info.text.toString().isNullOrEmpty() ||
                editText_wordwannasay_info.text.toString().isNullOrEmpty() ){
                Toast.makeText(this, getString(R.string.submit_all_info), Toast.LENGTH_SHORT).show()
            }else{          //정보가 모두 기입되었으면
                progress_bar_info.visibility = View.VISIBLE
                val usersInfoDTO = UsersInfoDTO()
                usersInfoDTO.uid = uid
                usersInfoDTO.name = editText_name_info.text.toString()
                usersInfoDTO.relation = editText_relation_info.text.toString()
                usersInfoDTO.wordWannaSay = editText_wordwannasay_info.text.toString()

                val me = FirebaseAuth.getInstance().currentUser
                firestore!!.collection("usersInfo")
                    .document(me!!.uid).set(usersInfoDTO).addOnCompleteListener { task ->
                        if(task.isSuccessful){      //usersInfo에 입력정보는 들어갔으나 역시 authentic과 일치하는지 확인
                            FirebaseFirestore.getInstance().collection("authentic")
                                .document("auth").get().addOnCompleteListener { tasks ->
                                    if(tasks.isSuccessful){
                                        var authDTO = tasks.result!!.toObject(AuthenticDTO::class.java)
                                        if(usersInfoDTO.wordWannaSay.equals(authDTO?.auths)){        //일치시
                                            progress_bar_info.visibility = View.GONE
                                            Toast.makeText(applicationContext, getString(R.string.submit_complete), Toast.LENGTH_SHORT).show()
                                            startActivity(Intent(this, MainActivity::class.java))
                                            finish()
                                        }else{
                                            Toast.makeText(applicationContext, getString(R.string.submit_failed), Toast.LENGTH_SHORT).show()
                                            finish()
                                        }
                                    }
                                }

//                            Toast.makeText(applicationContext, getString(R.string.submit_complete), Toast.LENGTH_SHORT).show()
//                            progress_bar_info.visibility = View.GONE
//                            startActivity(Intent(this, MainActivity::class.java))
//                            finish()
                        }
                    }
            }
        }
    }
}