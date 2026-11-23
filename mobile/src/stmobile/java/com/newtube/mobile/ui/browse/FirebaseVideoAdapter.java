package com.newtube.mobile.ui.browse;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

final class FirebaseVideoAdapter extends RecyclerView.Adapter<FirebaseVideoAdapter.Holder> {
    interface OnVideoClickListener {
        void onVideoClick(FirebaseVideo video);
    }

    private final List<FirebaseVideo> mVideos = new ArrayList<>();
    private final OnVideoClickListener mListener;

    FirebaseVideoAdapter(OnVideoClickListener listener) {
        mListener = listener;
    }

    void submit(List<FirebaseVideo> videos) {
        mVideos.clear();
        mVideos.addAll(videos);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_firebase_video, parent, false), mListener);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(mVideos.get(position));
    }

    @Override
    public int getItemCount() {
        return mVideos.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        private final ImageView mThumbnail;
        private final TextView mTitle;
        private final TextView mOwner;
        private final TextView mDescription;

        Holder(View itemView, OnVideoClickListener listener) {
            super(itemView);
            mThumbnail = itemView.findViewById(R.id.firebase_video_thumbnail);
            mTitle = itemView.findViewById(R.id.firebase_video_title);
            mOwner = itemView.findViewById(R.id.firebase_video_owner);
            mDescription = itemView.findViewById(R.id.firebase_video_description);
            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onVideoClick((FirebaseVideo) v.getTag());
                }
            });
        }

        void bind(FirebaseVideo video) {
            itemView.setTag(video);
            mTitle.setText(video.title);
            mOwner.setText(video.ownerName);
            mDescription.setText(video.description);
            mDescription.setVisibility(video.description == null || video.description.trim().isEmpty()
                    ? View.GONE : View.VISIBLE);
            Glide.with(mThumbnail).load(video.thumbnailUrl)
                    .placeholder(R.drawable.ic_mobile_empty)
                    .centerCrop()
                    .into(mThumbnail);
        }
    }
}
