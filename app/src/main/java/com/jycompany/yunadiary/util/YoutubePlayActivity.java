package com.jycompany.yunadiary.util;

import android.os.Bundle;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.youtube.player.YouTubeBaseActivity;
import com.google.android.youtube.player.YouTubeInitializationResult;
import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayerView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.jycompany.yunadiary.R;

public class YoutubePlayActivity extends YouTubeBaseActivity {
    YouTubePlayerView playerView;
    YouTubePlayer player;

    private static String API_KEY = "";    //일단 임시 API 키. 이건 나중에 파이어베이스 DB에 저장하고 불러와서 쓸 것임
    private static String videoId = "";      //이 역시 임시 비디오 ID, 나중엔 액티비티 호출시 intent에 넣어오면 받아 쓸 것임
    private static String TAG = "YoutubePlayActivity_TAG";
    private static FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_youtube_play);

        //intent의 bundle?에서 API_KEY와 videoId 추출하는 코드 여기에 넣을 것!!
        firestore = FirebaseFirestore.getInstance();
        firestore.collection("authentic").document("client").get().addOnCompleteListener(
                new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()){
                            API_KEY = task.getResult().get("youtubePlayApiKey").toString();
                            videoId = getIntent().getStringExtra("youtubeKey");

                            initPlayerAndPlay();        //API_KEY와 videoId 모두 준비되었을 때 실행
                        }
                    }
                }
        );
    }

    public void initPlayerAndPlay(){
        playerView = findViewById(R.id.youtubePlayerView);
        playerView.initialize(API_KEY, new YouTubePlayer.OnInitializedListener() {
            @Override
            public void onInitializationSuccess(YouTubePlayer.Provider provider, YouTubePlayer youTubePlayer, boolean wasRestored) {      //플레이어가 초기화 성공 시
                player = youTubePlayer;
                player.setPlayerStateChangeListener(new YouTubePlayer.PlayerStateChangeListener() {
                    @Override
                    public void onLoading() {}

                    @Override
                    public void onLoaded(String s) {
                        player.play();
                    }

                    @Override
                    public void onAdStarted() {}

                    @Override
                    public void onVideoStarted() {}

                    @Override
                    public void onVideoEnded() {}

                    @Override
                    public void onError(YouTubePlayer.ErrorReason errorReason) {
                    }
                });
                if(!wasRestored){
                    if(player!= null){
                        player.cueVideo(videoId);
                    }
                }
            }

            @Override
            public void onInitializationFailure(YouTubePlayer.Provider provider, YouTubeInitializationResult youTubeInitializationResult) {     //유튭 플레이어 초기화 실패 시
            }
        });
    }
}