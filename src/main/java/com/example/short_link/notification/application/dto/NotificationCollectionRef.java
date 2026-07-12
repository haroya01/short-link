package com.example.short_link.notification.application.dto;

/**
 * The collection a graph notification (CONNECTED / PATH_GREW) points at, snapshotted into the row's
 * JSON payload at write time. {@code collectionId} is the deep-link target — both kinds open the
 * collection/path, not a post. {@code collectionName} is the point-in-time title so the bell reads
 * sensibly after a later rename. {@code postId} is the post that occasioned the connection (the
 * connected post itself, or the post a connected highlight sits on) — carried for context/preview
 * and null for a connected note, which has no post.
 */
public record NotificationCollectionRef(Long collectionId, String collectionName, Long postId) {}
