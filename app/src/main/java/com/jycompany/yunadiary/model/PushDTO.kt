package com.jycompany.yunadiary.model

data class PushDTO(
    var to:String? = null,
    var notification : Notification? = Notification()
){
    data class Notification(
        var body : String? = null,
        var title : String? = null,
        var tag : String? = "same_tag"      //항상 같은 tag를 갖게 notification 오브젝트 안에 추가함
    )
}