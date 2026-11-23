package com.newtube.mobile.ui.playback;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.common.MobileActivity;

public final class FirebaseVideoPlayerActivity extends MobileActivity {
    public static final String EXTRA_TITLE = "firebase_video_title";
    public static final String EXTRA_DESCRIPTION = "firebase_video_description";
    public static final String EXTRA_VIDEO_URL = "firebase_video_url";

    private ExoPlayer mPlayer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_firebase_video_player);
        findViewById(R.id.firebase_player_back).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.firebase_player_title)).setText(getIntent().getStringExtra(EXTRA_TITLE));
        TextView description = findViewById(R.id.firebase_player_description);
        String text = getIntent().getStringExtra(EXTRA_DESCRIPTION);
        description.setText(text);
        description.setVisibility(text == null || text.trim().isEmpty() ? View.GONE : View.VISIBLE);

        String videoUrl = getIntent().getStringExtra(EXTRA_VIDEO_URL);
        if (videoUrl == null || !videoUrl.startsWith("https://")) {
            Toast.makeText(this, R.string.firebase_player_invalid_url, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        PlayerView playerView = findViewById(R.id.firebase_player_view);
        mPlayer = new ExoPlayer.Builder(this).build();
        mPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                Toast.makeText(FirebaseVideoPlayerActivity.this,
                        getString(R.string.firebase_player_failed, error.getErrorCodeName()),
                        Toast.LENGTH_LONG).show();
            }
        });
        playerView.setPlayer(mPlayer);
        mPlayer.setMediaItem(MediaItem.fromUri(videoUrl));
        mPlayer.prepare();
        mPlayer.play();
    }

    @Override
    protected void onStop() {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
        super.onStop();
    }
}
