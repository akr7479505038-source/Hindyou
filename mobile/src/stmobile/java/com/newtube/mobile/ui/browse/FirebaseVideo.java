package com.newtube.mobile.ui.browse;

/** Realtime Database record for a public HindYou upload. */
public final class FirebaseVideo {
    public String id;
    public String title;
    public String description;
    public String videoUrl;
    public String thumbnailUrl;
    public String ownerId;
    public String ownerName;
    public String ownerPhoto;
    public long createdAt;

    public FirebaseVideo() {
    }
}
