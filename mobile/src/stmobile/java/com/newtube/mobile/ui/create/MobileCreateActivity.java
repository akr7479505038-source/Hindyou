package com.newtube.mobile.ui.create;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.ClipData;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Typeface;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.common.MobileActivity;

public class MobileCreateActivity extends MobileActivity {
    private static final int VIDEO = 0;
    private static final int SHORT = 1;
    private static final int LIVE = 2;
    private static final int POST = 3;

    private ActivityResultLauncher<Intent> mMediaPicker;
    private ActivityResultLauncher<Intent> mSoundPicker;
    private ActivityResultLauncher<Intent> mCamera;
    private Uri mMediaUri;
    private Uri mSoundUri;
    private int mMode = VIDEO;
    private boolean mFrontCamera;
    private String mTimer = "Off";
    private String mSpeed = "1x";

    private TextView mPrompt;
    private TextView mSelected;
    private ImageView mPreview;
    private EditText mCaption;
    private MaterialButton mPublish;
    private MaterialButton mAddSound;
    private MaterialButton mRecord;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mobile_create);

        mMediaPicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> onMediaPicked(result, false));
        mSoundPicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> onMediaPicked(result, true));
        mCamera = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                this::onCameraResult);

        mPrompt = findViewById(R.id.mobile_create_prompt);
        mSelected = findViewById(R.id.mobile_create_selected);
        mPreview = findViewById(R.id.mobile_create_preview);
        mCaption = findViewById(R.id.mobile_create_caption);
        mPublish = findViewById(R.id.mobile_create_publish);
        mAddSound = findViewById(R.id.mobile_create_add_sound);
        mRecord = findViewById(R.id.mobile_create_record);

        findViewById(R.id.mobile_create_close).setOnClickListener(v -> finish());
        findViewById(R.id.mobile_create_mode_video).setOnClickListener(v -> setMode(VIDEO));
        findViewById(R.id.mobile_create_mode_short).setOnClickListener(v -> setMode(SHORT));
        findViewById(R.id.mobile_create_mode_live).setOnClickListener(v -> setMode(LIVE));
        findViewById(R.id.mobile_create_mode_post).setOnClickListener(v -> setMode(POST));
        findViewById(R.id.mobile_create_add).setOnClickListener(v -> pickMedia());
        mAddSound.setOnClickListener(v -> pickSound());
        findViewById(R.id.mobile_create_flip).setOnClickListener(v -> flipCamera());
        findViewById(R.id.mobile_create_timer).setOnClickListener(v -> chooseTimer());
        findViewById(R.id.mobile_create_speed).setOnClickListener(v -> chooseSpeed());
        mRecord.setOnClickListener(v -> startCamera());
        mPublish.setOnClickListener(v -> continueCreate());
        setMode(VIDEO);
    }

    private void setMode(int mode) {
        if (mMode != mode) {
            mMediaUri = null;
            mSoundUri = null;
            mAddSound.setText(R.string.mobile_create_add_sound);
            mMode = mode;
            showSelectedMedia(null);
        }

        updateModeButton(R.id.mobile_create_mode_video, mode == VIDEO);
        updateModeButton(R.id.mobile_create_mode_short, mode == SHORT);
        updateModeButton(R.id.mobile_create_mode_live, mode == LIVE);
        updateModeButton(R.id.mobile_create_mode_post, mode == POST);
        findViewById(R.id.mobile_create_caption).setVisibility(mode == POST ? View.VISIBLE : View.GONE);
        findViewById(R.id.mobile_create_media_controls).setVisibility(mode == LIVE ? View.GONE : View.VISIBLE);
        findViewById(R.id.mobile_create_add_sound).setVisibility(
                mode == VIDEO || mode == SHORT ? View.VISIBLE : View.GONE);
        findViewById(R.id.mobile_create_camera_controls).setVisibility(
                mode == VIDEO || mode == SHORT ? View.VISIBLE : View.GONE);
        mRecord.setVisibility(mode == VIDEO || mode == SHORT ? View.VISIBLE : View.GONE);
        mPrompt.setText(mode == LIVE ? R.string.mobile_create_live_info
                : mode == POST ? R.string.mobile_create_select_post_prompt
                : R.string.mobile_create_select_prompt);
        updatePublishEnabled();
    }

    private void updateModeButton(int viewId, boolean selected) {
        MaterialButton button = findViewById(viewId);
        button.setSelected(selected);
        button.setTextColor(getColor(selected ? R.color.mobile_color_on_surface
                : R.color.mobile_color_on_surface_secondary));
        button.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void pickMedia() {
        if (mMode == LIVE) {
            openLiveStudio();
            return;
        }
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mMode == POST ? "image/*" : "video/*");
        mMediaPicker.launch(pick);
    }

    private void pickSound() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("audio/*");
        mSoundPicker.launch(pick);
    }

    private void onMediaPicked(ActivityResult result, boolean sound) {
        if (result.getResultCode() != RESULT_OK || result.getData() == null
                || result.getData().getData() == null) {
            return;
        }
        Uri uri = result.getData().getData();
        if (sound) {
            mSoundUri = uri;
            mAddSound.setText(getString(R.string.mobile_create_selected_sound, displayName(uri)));
        } else {
            mMediaUri = uri;
            showSelectedMedia(uri);
        }
        updatePublishEnabled();
    }

    private void onCameraResult(ActivityResult result) {
        if (result.getResultCode() == RESULT_OK && result.getData() != null
                && result.getData().getData() != null) {
            mMediaUri = result.getData().getData();
            showSelectedMedia(mMediaUri);
            updatePublishEnabled();
        }
    }

    private void showSelectedMedia(@Nullable Uri uri) {
        if (uri == null) {
            mSelected.setVisibility(View.GONE);
            mPreview.setVisibility(View.GONE);
            mPrompt.setVisibility(View.VISIBLE);
            return;
        }
        mSelected.setText(getString(R.string.mobile_create_selected_media, displayName(uri)));
        mSelected.setVisibility(View.VISIBLE);
        mPrompt.setVisibility(View.GONE);
        mPreview.setVisibility(View.VISIBLE);
        com.bumptech.glide.Glide.with(this).load(uri).centerCrop()
                .placeholder(R.drawable.ic_mobile_empty).into(mPreview);
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.isEmpty()) {
                        return name;
                    }
                }
            }
        }
        return uri.getLastPathSegment() != null ? uri.getLastPathSegment() : uri.toString();
    }

    private void flipCamera() {
        mFrontCamera = !mFrontCamera;
        Toast.makeText(this, mFrontCamera ? R.string.mobile_create_camera_front
                : R.string.mobile_create_camera_back, Toast.LENGTH_SHORT).show();
    }

    private void chooseTimer() {
        String[] choices = {"Off", "3s", "10s"};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.mobile_create_timer)
                .setItems(choices, (dialog, which) -> {
                    mTimer = choices[which];
                    ((MaterialButton) findViewById(R.id.mobile_create_timer))
                            .setText(getString(R.string.mobile_create_timer_value, mTimer));
                })
                .show();
    }

    private void chooseSpeed() {
        String[] choices = {"0.3x", "0.5x", "1x", "2x", "3x"};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.mobile_create_speed)
                .setItems(choices, (dialog, which) -> {
                    mSpeed = choices[which];
                    ((MaterialButton) findViewById(R.id.mobile_create_speed))
                            .setText(getString(R.string.mobile_create_speed_value, mSpeed));
                })
                .show();
    }

    private void startCamera() {
        Intent capture = new Intent(mMode == POST
                ? MediaStore.ACTION_IMAGE_CAPTURE : MediaStore.ACTION_VIDEO_CAPTURE);
        capture.putExtra("android.intent.extras.CAMERA_FACING", mFrontCamera ? 1 : 0);
        if (mMode == SHORT) {
            capture.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 60);
        }
        try {
            mCamera.launch(capture);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.mobile_create_no_camera, Toast.LENGTH_SHORT).show();
        }
    }

    private void continueCreate() {
        if (mMode == LIVE) {
            openLiveStudio();
            return;
        }
        if (mMediaUri == null) {
            pickMedia();
            return;
        }

        Intent share = new Intent(Intent.ACTION_SEND)
                .setType(mMode == POST ? "image/*" : "video/*")
                .putExtra(Intent.EXTRA_STREAM, mMediaUri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        share.setClipData(ClipData.newUri(getContentResolver(), "media", mMediaUri));
        String caption = mCaption.getText().toString().trim();
        if (!caption.isEmpty()) {
            share.putExtra(Intent.EXTRA_TEXT, caption);
        }
        startActivity(Intent.createChooser(share, getString(R.string.mobile_create_publish)));
    }

    private void openLiveStudio() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://studio.youtube.com/")));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.mobile_no_browser, Toast.LENGTH_SHORT).show();
        }
    }

    private void updatePublishEnabled() {
        mPublish.setEnabled(mMode == LIVE || mMediaUri != null);
    }
}
