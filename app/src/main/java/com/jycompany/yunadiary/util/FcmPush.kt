package com.jycompany.yunadiary.util

import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.jycompany.yunadiary.model.PushDTO
import com.squareup.okhttp.*
import okhttp3.internal.notify
import java.io.IOException

class FcmPush {
    val JSON = MediaType.parse("application/json; charset=utf-8")
    val url = "https://fcm.googleapis.com/fcm/send"
    val serverKey = "AAABBDsi7IFao:APbBDeDbzOJ38H6sXuQVBB" +
            "WSUU31Jgnhkve5XnFzr4EWcc52KdF_-aDR4cH_587vMqc_n1a" +
            "XDDjp0qDer4YWF_6D_HZMpfGuZdebdrfBR-THk3nRFnn7QP68" +
            "aaXX-bAWdC32G"

    var okHttpClient : OkHttpClient? = null
    var gson : Gson? = null
    init {
        gson = Gson()
        okHttpClient = OkHttpClient()
    }

    fun sendMessage(destinationUid : String, title : String, message : String){
        FirebaseFirestore.getInstance()
            .collection("pushtokens")
            .document(destinationUid)
            .get()
            .addOnCompleteListener { task ->
                if(task.isSuccessful){
                    var token = task.result?.get("pushtoken")?.toString()
                    var pushDTO = PushDTO()
                    pushDTO.to = token
                    pushDTO.notification?.title = title
                    pushDTO.notification?.body = message

                    var body = RequestBody.create(JSON, gson?.toJson(pushDTO))

                    var request = Request
                        .Builder()
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Authorization", "key="+serverKey)
                        .url(url)
                        .post(body)
                        .build()
                    okHttpClient?.newCall(request)?.enqueue(object : Callback{
                        override fun onFailure(request: Request?, e: IOException?) {
                        }

                        override fun onResponse(response: Response?) {
                            println(response?.body()?.string())             //로그용 메시지 출력
                        }
                    })
                }
            }
    }
}