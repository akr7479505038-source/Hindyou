package com.newtube.mobile.ui.browse;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.common.MobileActivity;
import com.newtube.mobile.ui.playback.FirebaseVideoPlayerActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FirebaseVideosActivity extends MobileActivity {
    private FirebaseAuth mAuth;
    private DatabaseReference mVideosReference;
    private FirebaseVideoAdapter mAdapter;
    private TextView mAccount;
    private TextView mEmpty;
    private View mSignIn;
    private View mSignOut;
    private View mUpload;
    private ActivityResultLauncher<Intent> mGoogleSignInLauncher;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private ValueEventListener mVideosListener;
    private Query mVideosQuery;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_firebase_videos);

        mAccount = findViewById(R.id.firebase_account);
        mEmpty = findViewById(R.id.firebase_video_empty);
        mEmpty.setVisibility(View.GONE);
        mSignIn = findViewById(R.id.firebase_sign_in);
        mSignOut = findViewById(R.id.firebase_sign_out);
        mUpload = findViewById(R.id.firebase_upload);

        RecyclerView videos = findViewById(R.id.firebase_video_list);
        videos.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new FirebaseVideoAdapter(this::openVideo);
        videos.setAdapter(mAdapter);

        mGoogleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), this::onGoogleSignInResult);
        try {
            mAuth = FirebaseAuth.getInstance();
            mVideosReference = FirebaseDatabase.getInstance().getReference("videos");
        } catch (IllegalStateException e) {
            showFailure(getString(R.string.firebase_setup_failed, e.getMessage()));
            mSignIn.setEnabled(false);
            mUpload.setEnabled(false);
            return;
        }

        mAuthListener = auth -> updateAccount(auth.getCurrentUser());
        mAuth.addAuthStateListener(mAuthListener);
        mSignIn.setOnClickListener(v -> startGoogleSignIn());
        mSignOut.setOnClickListener(v -> signOut());
        mUpload.setOnClickListener(v -> {
            if (mAuth.getCurrentUser() == null) {
                showFailure(getString(R.string.firebase_sign_in_required));
                return;
            }
            startActivity(new Intent(this, com.newtube.mobile.ui.create.MobileCreateActivity.class));
        });
        attachVideosListener();
    }

    private void startGoogleSignIn() {
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInLauncher.launch(GoogleSignIn.getClient(this, options).getSignInIntent());
    }

    private void onGoogleSignInResult(ActivityResult result) {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) {
            return;
        }
        Task<com.google.android.gms.auth.api.signin.GoogleSignInAccount> accountTask =
                GoogleSignIn.getSignedInAccountFromIntent(result.getData());
        accountTask.addOnSuccessListener(account -> {
            String idToken = account.getIdToken();
            if (idToken == null || idToken.isEmpty()) {
                showFailure(getString(R.string.firebase_google_token_missing));
                return;
            }
            AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
            mAuth.signInWithCredential(credential)
                    .addOnFailureListener(error -> showFailure(getString(R.string.firebase_sign_in_failed,
                            error.getLocalizedMessage())));
        }).addOnFailureListener(error -> showFailure(getString(R.string.firebase_sign_in_failed,
                error.getLocalizedMessage())));
    }

    private void signOut() {
        mAuth.signOut();
        GoogleSignIn.getClient(this, new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()).signOut().addOnFailureListener(error ->
                showFailure(getString(R.string.firebase_sign_out_failed, error.getLocalizedMessage())));
    }

    private void updateAccount(FirebaseUser user) {
        boolean signedIn = user != null;
        mSignIn.setVisibility(signedIn ? View.GONE : View.VISIBLE);
        mSignOut.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        mUpload.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        mAccount.setText(signedIn
                ? getString(R.string.firebase_signed_in_as, user.getDisplayName() != null
                        ? user.getDisplayName() : user.getEmail())
                : getString(R.string.firebase_signed_out));
    }

    private void attachVideosListener() {
        mVideosQuery = mVideosReference.orderByChild("createdAt");
        mVideosListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<FirebaseVideo> videos = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    FirebaseVideo video = child.getValue(FirebaseVideo.class);
                    if (isDisplayable(video)) {
                        if (video.id == null || video.id.isEmpty()) {
                            video.id = child.getKey();
                        }
                        videos.add(video);
                    }
                }
                Collections.reverse(videos);
                mAdapter.submit(videos);
                mEmpty.setVisibility(videos.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showFailure(getString(R.string.firebase_load_failed, error.getMessage()));
                mEmpty.setVisibility(View.VISIBLE);
            }
        };
        mVideosQuery.addValueEventListener(mVideosListener);
    }

    private static boolean isDisplayable(@Nullable FirebaseVideo video) {
        return video != null
                && hasText(video.title)
                && isHttps(video.videoUrl)
                && isHttps(video.thumbnailUrl)
                && hasText(video.ownerId);
    }

    private static boolean isHttps(String value) {
        return value != null && value.startsWith("https://");
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void openVideo(FirebaseVideo video) {
        Intent intent = new Intent(this, com.newtube.mobile.ui.playback.FirebaseVideoPlayerActivity.class)
                .putExtra(FirebaseVideoPlayerActivity.EXTRA_TITLE, video.title)
                .putExtra(FirebaseVideoPlayerActivity.EXTRA_DESCRIPTION, video.description)
                .putExtra(FirebaseVideoPlayerActivity.EXTRA_VIDEO_URL, video.videoUrl);
        startActivity(intent);
    }

    private void showFailure(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        if (mAuth != null && mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
        if (mVideosQuery != null && mVideosListener != null) {
            mVideosQuery.removeEventListener(mVideosListener);
        }
        super.onDestroy();
    }
}
