package com.newtube.mobile.ui.create;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

final class CloudinaryUploader {
    private static final String CLOUD_NAME = "kkncxm3h";
    private static final String UPLOAD_PRESET = "ml_default";
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(10, java.util.concurrent.TimeUnit.MINUTES)
            .readTimeout(2, java.util.concurrent.TimeUnit.MINUTES)
            .build();

    private CloudinaryUploader() {
    }

    static String upload(@NonNull ContentResolver resolver, @NonNull Uri uri,
                         @NonNull String fileName, boolean video) throws IOException {
        String resourceType = video ? "video" : "image";
        String mimeType = resolver.getType(uri);
        RequestBody file = new ContentUriBody(resolver, uri, mimeType);
        RequestBody form = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upload_preset", UPLOAD_PRESET)
                .addFormDataPart("file", fileName, file)
                .build();
        Request request = new Request.Builder()
                .url("https://api.cloudinary.com/v1_1/" + CLOUD_NAME + "/" + resourceType + "/upload")
                .post(form)
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Cloudinary upload failed (HTTP " + response.code() + "): " + body);
            }
            try {
                String secureUrl = new JSONObject(body).optString("secure_url");
                if (!secureUrl.startsWith("https://")) {
                    throw new IOException("Cloudinary response did not contain a secure URL");
                }
                return secureUrl;
            } catch (JSONException e) {
                throw new IOException("Cloudinary returned an invalid response", e);
            }
        }
    }

    private static final class ContentUriBody extends RequestBody {
        private final ContentResolver mResolver;
        private final Uri mUri;
        private final MediaType mMediaType;
        private final long mLength;

        ContentUriBody(ContentResolver resolver, Uri uri, String mimeType) {
            mResolver = resolver;
            mUri = uri;
            mMediaType = mimeType != null ? MediaType.parse(mimeType) : null;
            mLength = contentLength(resolver, uri);
        }

        @Override
        public MediaType contentType() {
            return mMediaType;
        }

        @Override
        public long contentLength() {
            return mLength;
        }

        @Override
        public void writeTo(@NonNull BufferedSink sink) throws IOException {
            InputStream input = mResolver.openInputStream(mUri);
            if (input == null) {
                throw new IOException("Could not read the selected media");
            }
            try (InputStream stream = input) {
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    sink.write(buffer, 0, read);
                }
            }
        }

        private static long contentLength(ContentResolver resolver, Uri uri) {
            try (Cursor cursor = resolver.query(uri, new String[] {OpenableColumns.SIZE}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                    return cursor.getLong(0);
                }
            }
            return -1;
        }
    }
}
