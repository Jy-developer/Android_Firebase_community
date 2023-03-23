package com.jycompany.yunadiary.model

data class TokenDTO(var access_token : String? = null,
                    var expires_in : String? = null,
                    var refresh_token : String? = null,
                    var scope : String? = null,
                    var token_type : String? = null,
                    var id_token : String? = null
)