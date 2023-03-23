package com.jycompany.yunadiary.model

data class ContentDTO(var explain : String? = null,
                      var imageUrl : String? = null,
                      var videoUrl : String? = "",
                      var youtubeId : String? = "",
                      var uid : String? = null,
                      var userId : String? = null,
                      var timestamp : Long? = null,
                      var favoriteCount : Int = 0,
                      var imageFileName : String? = null,   //Storage에 업로드되는 파일명
                      var commentList : ArrayList<String>? = ArrayList<String>(),
                      var imageArr : ArrayList<String>? = ArrayList<String>(),
                      var favorites : MutableMap<String, Boolean> = HashMap()) {
    data class Comment(var uid:String? = null,
                       var userId:String? = null,
                       var comment : String? = null,
                       var timestamp:Long?= null)
}