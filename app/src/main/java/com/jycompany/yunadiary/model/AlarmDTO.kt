package com.jycompany.yunadiary.model

data class AlarmDTO (
    var destinationUid : String? = null,            //메시지 받는 대상의 UID
    var userId: String? = null,                     //메시지를 보내는 사람의 이메일
    var uid: String? = null,                        //메시지를 보내는 사람의 UID
    var kind:Int = 0,       //0 : 좋아요, 1: 댓글, 2: 팔로우    //메시지 종류
    var message : String? = null,                   //메시지 내용
    var timestamp : Long? = null                    //메시지 보내는 시간
)