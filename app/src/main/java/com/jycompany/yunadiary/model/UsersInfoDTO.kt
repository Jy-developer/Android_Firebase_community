package com.jycompany.yunadiary.model

data class UsersInfoDTO(
    var uid : String? = null,               //uid
    var name : String? = null,              //이름
    var relation : String? = null,          //관계
    var wordWannaSay : String? = null       //하고 싶은 말
)