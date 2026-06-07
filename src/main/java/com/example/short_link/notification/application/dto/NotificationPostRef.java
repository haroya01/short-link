package com.example.short_link.notification.application.dto;

/**
 * The post a notification points at, snapshotted into the row's JSON payload at write time. Held as
 * a snapshot (not a live lookup) so a like/comment notification still reads sensibly after the post
 * is edited; the slug/title are point-in-time. Null for notifications with no post (e.g. FOLLOW).
 *
 * <p>{@code authorUsername} is the post owner's handle, needed to build the post link when the
 * recipient is NOT the author — i.e. a REPLY (you commented on someone else's post) or a NEW_POST
 * (a followed author published). It is null for LIKE/COMMENT, where the recipient is the author and
 * the client already knows its own username.
 */
public record NotificationPostRef(Long postId, String slug, String title, String authorUsername) {}
