package com.newtube.mobile.ui.create;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.common.MobileActivity;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MobileCreateActivity extends MobileActivity {
    private static final ExecutorService UPLOAD_EXECUTOR = Executors.newSingleThreadExecutor();

    private final Handler mMain = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<String[]> mVideoPicker;
    private ActivityResultLauncher<String[]> mThumbnailPicker;
    private Uri mVideoUri;
    private Uri mThumbnailUri;
    private EditText mTitle;
    private EditText mDescription;
    private TextView mVideoName;
    private TextView mThumbnailName;
    private TextView mStatus;
    private MaterialButton mPublish;
    private View mProgress;
    private boolean mUploading;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mobile_create);

        mVideoPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                uri -> setSelectedMedia(uri, true));
        mThumbnailPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                uri -> setSelectedMedia(uri, false));

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(this, R.string.firebase_sign_in_required, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        mTitle = findViewById(R.id.firebase_upload_title);
        mDescription = findViewById(R.id.firebase_upload_description);
        mVideoName = findViewById(R.id.firebase_upload_video_name);
        mThumbnailName = findViewById(R.id.firebase_upload_thumbnail_name);
        mStatus = findViewById(R.id.firebase_upload_status);
        mPublish = findViewById(R.id.firebase_upload_publish);
        mProgress = findViewById(R.id.firebase_upload_progress);

        findViewById(R.id.firebase_upload_back).setOnClickListener(v -> finish());
        findViewById(R.id.firebase_pick_video).setOnClickListener(v -> mVideoPicker.launch(new String[] {"video/*"}));
        findViewById(R.id.firebase_pick_thumbnail).setOnClickListener(v -> mThumbnailPicker.launch(new String[] {"image/*"}));
        mPublish.setOnClickListener(v -> upload());
        mTitle.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePublishEnabled();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
        updatePublishEnabled();
    }

    private void setSelectedMedia(@Nullable Uri uri, boolean video) {
        if (uri == null) {
            return;
        }
        if (video) {
            mVideoUri = uri;
            mVideoName.setText(getString(R.string.firebase_selected_video, displayName(uri)));
        } else {
            mThumbnailUri = uri;
            mThumbnailName.setText(getString(R.string.firebase_selected_thumbnail, displayName(uri)));
        }
        updatePublishEnabled();
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getString(0);
            }
        }
        String path = uri.getLastPathSegment();
        return path != null ? path : getString(R.string.firebase_selected_file);
    }

    private void updatePublishEnabled() {
        if (mPublish != null) {
            mPublish.setEnabled(!mUploading && mVideoUri != null && mThumbnailUri != null
                    && mTitle.getText().toString().trim().length() > 0);
        }
    }

    private void upload() {
        FirebaseUser owner = FirebaseAuth.getInstance().getCurrentUser();
        String title = mTitle.getText().toString().trim();
        if (owner == null) {
            showFailure(getString(R.string.firebase_sign_in_required));
            return;
        }
        if (mVideoUri == null || mThumbnailUri == null) {
            showFailure(getString(R.string.firebase_media_required));
            return;
        }
        if (title.isEmpty()) {
            showFailure(getString(R.string.firebase_title_required));
            return;
        }

        DatabaseReference videos;
        try {
            videos = FirebaseDatabase.getInstance().getReference("videos");
        } catch (IllegalStateException e) {
            showFailure(getString(R.string.firebase_setup_failed, e.getMessage()));
            return;
        }

        String description = mDescription.getText().toString().trim();
        String ownerId = owner.getUid();
        String ownerName = owner.getDisplayName() != null ? owner.getDisplayName()
                : owner.getEmail() != null ? owner.getEmail() : "";
        String ownerPhoto = owner.getPhotoUrl() != null ? owner.getPhotoUrl().toString() : "";
        Uri videoUri = mVideoUri;
        Uri thumbnailUri = mThumbnailUri;
        String videoName = displayName(videoUri);
        String thumbnailName = displayName(thumbnailUri);

        mUploading = true;
        mProgress.setVisibility(View.VISIBLE);
        mStatus.setText(R.string.firebase_uploading);
        updatePublishEnabled();

        UPLOAD_EXECUTOR.execute(() -> {
            try {
                String videoUrl = CloudinaryUploader.upload(getContentResolver(), videoUri, videoName, true);
                String thumbnailUrl = CloudinaryUploader.upload(getContentResolver(), thumbnailUri, thumbnailName, false);
                DatabaseReference record = videos.push();
                String id = record.getKey();
                if (id == null) {
                    throw new IOException("Could not allocate a video id");
                }
                Map<String, Object> values = new HashMap<>();
                values.put("id", id);
                values.put("title", title);
                values.put("description", description);
                values.put("videoUrl", videoUrl);
                values.put("thumbnailUrl", thumbnailUrl);
                values.put("ownerId", ownerId);
                values.put("ownerName", ownerName);
                values.put("ownerPhoto", ownerPhoto);
                values.put("createdAt", ServerValue.TIMESTAMP);

                record.setValue(values)
                        .addOnSuccessListener(unused -> mMain.post(() -> {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }
                            Toast.makeText(this, R.string.firebase_upload_complete, Toast.LENGTH_LONG).show();
                            finish();
                        }))
                        .addOnFailureListener(error -> mMain.post(() ->
                                finishUploadWithError(getString(R.string.firebase_save_failed,
                                        error.getLocalizedMessage()))));
            } catch (IOException e) {
                mMain.post(() -> finishUploadWithError(getString(R.string.firebase_upload_failed,
                        e.getLocalizedMessage())));
            }
        });
    }

    private void finishUploadWithError(String message) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        mUploading = false;
        mProgress.setVisibility(View.GONE);
        mStatus.setText(message);
        updatePublishEnabled();
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showFailure(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
