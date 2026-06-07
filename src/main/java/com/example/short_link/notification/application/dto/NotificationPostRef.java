package com.example.short_link.notification.application.dto;

/**
 * The post a notification points at, snapshotted into the row's JSON payload at write time. Held as
 * a snapshot (not a live lookup) so a like/comment notification still reads sensibly after the post
 * is edited; the slug/title are point-in-time. Null for notifications with no post (e.g. FOLLOW).
 */
public record NotificationPostRef(Long postId, String slug, String title) {}
