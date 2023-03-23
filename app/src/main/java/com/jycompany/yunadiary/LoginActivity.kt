package com.jycompany.yunadiary

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import com.facebook.*
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.jycompany.yunadiary.model.AuthenticDTO
import kotlinx.android.synthetic.main.activity_login.*
import java.io.IOException
import java.util.*

class LoginActivity : AppCompatActivity() {
    val TAG = "LoginActivity_tag"
    var auth: FirebaseAuth? = null
    var googleSignInClient : GoogleSignInClient? = null
    var callbackManager : CallbackManager? = null

    val GOOGLE_LOGIN_CODE = 9001

    val MARKET_URL = "https://play.google.com/store/apps/details?id=com.jycompany.yunadiary"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()       //Firebase 로그인 통합 관리하는 Object
        //구글 로그인 옵션
        var gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)  //구글 로그인 클래스를 만듬
        callbackManager = CallbackManager.Factory.create()

        google_sign_in_button.setOnClickListener { googleLogin() }
        facebook_login_button.setOnClickListener { facebookLogin() }
        email_login_button.setOnClickListener { emailLogin() }
    }

    override fun onStart() {
        super.onStart()
        moveMainPage(auth?.currentUser)         //자동 로그인 설정
    }

    override fun onResume() {
        restoreState()
        super.onResume()
    }

    override fun onPause() {
        saveState()
        super.onPause()
    }

    fun moveMainPage(user : FirebaseUser?){     //유저가 로그인했다면, MainActivity를 실행하고 LoginActivity를 종료한다.
        if(user != null){       //유저가 로그인 했다면
            Toast.makeText(this, getString(R.string.signin_complete), Toast.LENGTH_SHORT).show()
            FirebaseFirestore.getInstance()
                .collection("usersInfo")
                .document(user.uid).get()
                .addOnSuccessListener { document ->
                    getMarketVersion().execute()    //버젼체크 백그라운드 asyncTask 실행
                }.addOnFailureListener { exception ->
                    startActivity(Intent(this, InfoActivity::class.java))
                    finish()
                }
        }
    }

    fun googleLogin(){
        progress_bar.visibility = View.VISIBLE
        var signInIntent = googleSignInClient?.signInIntent
        startActivityForResult(signInIntent, GOOGLE_LOGIN_CODE)
    }

    fun facebookLogin(){
        progress_bar.visibility = View.VISIBLE
        LoginManager.getInstance().logInWithReadPermissions(this, Arrays.asList("public_profile", "email"))
        LoginManager.getInstance().registerCallback(callbackManager, object : FacebookCallback<LoginResult>{
            override fun onSuccess(loginResult: LoginResult) {
                handleFacebookAccessToken(loginResult.accessToken)
            }
            override fun onCancel() {
                progress_bar.visibility = View.GONE
            }
            override fun onError(error: FacebookException?) {
                progress_bar.visibility = View.GONE
            }
        })
    }

    fun handleFacebookAccessToken(token : AccessToken){         //Facebook 토큰을 Firebase로 넘겨주는 코드
        val credential = FacebookAuthProvider.getCredential(token.token)
        auth?.signInWithCredential(credential)
                ?.addOnCompleteListener { task ->
            progress_bar.visibility = View.GONE
            if(task.isSuccessful){      //다음 페이지 이동
                resetState()
                moveMainPage(auth?.currentUser)
            }
        }
    }

    fun createAndLoginEmail(){      //이메일 회원 가입 및 로그인 메소드
        auth?.createUserWithEmailAndPassword(email_edittext.text.toString(), password_edittext.text.toString())
            ?.addOnCompleteListener { task ->
                progress_bar.visibility = View.GONE
                if(task.isSuccessful){          //계정이 없었는데 새로 생성해서 만들고 그것이 성공한 경우
                    Toast.makeText(this, getString(R.string.signup_complete), Toast.LENGTH_SHORT).show()
                    moveMainPage(auth?.currentUser)
                }else if(task.exception?.message.isNullOrEmpty()){
                    Toast.makeText(this, task.exception!!.message, Toast.LENGTH_SHORT).show()
                }else{                      //이미 계정이 존재하는 경우
                    signinEmail()
                }
            }
    }

    fun emailLogin(){
        if(email_edittext.text.toString().isNullOrEmpty() || password_edittext.text.toString().isNullOrEmpty()){
            Toast.makeText(this, getString(R.string.signout_fail_null), Toast.LENGTH_SHORT).show()
        }else{
            progress_bar.visibility = View.VISIBLE
            saveState()             //id pass 를 sharedPreference 로 저장
            createAndLoginEmail()
        }
    }

    protected fun restoreState(){
        var pref = getSharedPreferences("pref", Activity.MODE_PRIVATE)
        if(pref != null && pref.contains("id") && pref.contains("pass")) {
            email_edittext.setText(pref.getString("id", ""))
            password_edittext.setText(pref.getString("pass", ""))
        }
    }

    protected fun saveState(){          //id와 pass를 파일로 저장
        var pref = getSharedPreferences("pref", Activity.MODE_PRIVATE)
        var editor = pref?.edit()
        editor!!.putString("id", email_edittext.text.toString())
        editor!!.putString("pass", password_edittext.text.toString())
        editor.commit()
    }

    protected fun resetState(){
        var pref = getSharedPreferences("pref", Activity.MODE_PRIVATE)
        var editor = pref?.edit()
        editor!!.putString("id", "")
        editor!!.putString("pass", "")
        editor.commit()
    }

    fun signinEmail(){
        auth?.signInWithEmailAndPassword(email_edittext.text.toString(), password_edittext.text.toString())
            ?.addOnCompleteListener { task ->
                progress_bar.visibility = View.GONE
                if(task.isSuccessful){      //로그인 성공 및 다음 페이지 호출
                    moveMainPage(auth?.currentUser)
                }else{          //로그인 실패 시
                    Toast.makeText(this, task.exception!!.message, Toast.LENGTH_SHORT).show()
                }
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        callbackManager?.onActivityResult(requestCode, resultCode, data)    //Facebook SDK로 값 넘겨주기

        if(requestCode == GOOGLE_LOGIN_CODE){
            var result = Auth.GoogleSignInApi.getSignInResultFromIntent(data)
            if(result!!.isSuccess){
                var account = result.signInAccount
                firebaseAuthWithGoogle(account!!)
                //기존 이메일에서 구글인증 하여 가입시 기존 이메일 정보 삭제
                resetState()
            }else{
                progress_bar.visibility = View.GONE
            }
        }
    }

    fun firebaseAuthWithGoogle(account : GoogleSignInAccount){
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        auth?.signInWithCredential(credential)?.addOnCompleteListener { task ->
            progress_bar.visibility = View.GONE
            if(task.isSuccessful){
                moveMainPage(auth?.currentUser)
            }
        }
    }

    inner class getMarketVersion : AsyncTask<Any, Any, String>() {
        var marketVersion : String? = null
        var verSion : String? = null
        var loginActivity = this@LoginActivity
        var firestore = FirebaseFirestore.getInstance()

        override fun onPreExecute() {
            loginActivity.progress_bar.visibility = View.VISIBLE
            super.onPreExecute()
        }

        override fun doInBackground(vararg params: Any?): String? {
            try{
//                var doc : Document = Jsoup.connect(MARKET_URL).get()      //기존 사용하던 구글플레이스토어 마켓 버젼 숫자 가져오기
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
            loginActivity.progress_bar.visibility = View.GONE
            try{
                verSion = getDeviceAppVersion(applicationContext!!)
            }catch (e : PackageManager.NameNotFoundException){
                e.printStackTrace()
            }
            marketVersion = result

            if(verSion!!.toFloat() < marketVersion!!.toFloat()){        //디바이스의 앱버젼(verSion)이 마켓버젼(marketVersion)보다 낮을 때
                val mDialog : AlertDialog.Builder = AlertDialog.Builder(this@LoginActivity!!)
                mDialog.setMessage("마켓에 신버전이 올라와 있어요~" +
                        "\n신버전은 새 기능이 추가되었거나 앱 안정성이 높습니다." +
                        "\n업데이트 후 사용을 추천드립니다." +
                        "\n(마켓버젼이 예전 버전인 "+verSion+"으로 표시되는 경우가 있" +
                        "습니다. 마켓을 껐다가 다시 확인하시면 신버전("+marketVersion+")이 제대로 나옵니다.)" +
                        "\n\n현재 버젼 : "+verSion+"\n신버전 : "+marketVersion)
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.update_btn_yes), DialogInterface.OnClickListener { dialog, which ->
                        var marketLaunch : Intent? = Intent(Intent.ACTION_VIEW)
                        marketLaunch?.setData(Uri.parse(MARKET_URL))
                        startActivity(marketLaunch)
//                            loginActivity?.finish()     //이 시점에 어느 액티비티가 켜져 있는지 모르겠음; 일단 무효화
                    })
                    .setNegativeButton(getString(R.string.terminate_app), DialogInterface.OnClickListener { dialog, which ->
                        finish()
                    })
                var alert = mDialog.create()
                alert.setTitle(getString(R.string.update_inform))
                alert.show()

                val titleId = resources.getIdentifier("alertTitle", "id", applicationContext.packageName)
                var textViewTitle : TextView? = null
                if(titleId > 0){
                    textViewTitle = alert.findViewById<View>(titleId) as TextView
                }
                val textViewMessage = alert.window?.findViewById<View>(android.R.id.message) as TextView
                val buttonYes = alert.window?.findViewById<View>(android.R.id.button1) as Button
                val buttonNo = alert.window?.findViewById<View>(android.R.id.button2) as Button
                val font = ResourcesCompat.getFont(applicationContext, R.font.hanmaum_myungjo)
                textViewTitle?.setTypeface(font)
                textViewMessage.setTypeface(font)
                buttonYes.setTypeface(font)
                buttonNo.setTypeface(font)
            }else{          //디바이스의 앱버젼(verSion)과 마켓버젼(marketVersion)이 같거나 심지어 디바이스 앱버젼이 더 최신이면
//                Toast.makeText(applicationContext, getString(R.string.update_already_new)+verSion, Toast.LENGTH_LONG).show()
                this@LoginActivity.startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                finish()
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
}