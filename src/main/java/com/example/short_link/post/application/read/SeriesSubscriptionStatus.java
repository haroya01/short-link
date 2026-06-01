package com.example.short_link.post.application.read;

/**
 * Subscription state for a series: whether the current viewer subscribes + the subscriber total.
 */
public record SeriesSubscriptionStatus(boolean subscribed, long subscriberCount) {}
