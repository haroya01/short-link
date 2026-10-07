package com.example.short_link.notification.domain.policy;

// What the recipient's settings and their relationship to the sender say about one notice, read in
// one statement: the per-type preference, the policy (null when never set), and the sender facts
// the
// policy's categories ask about.
public record NotificationSender(
    boolean enabled,
    NotificationPolicy policy,
    boolean permitted,
    boolean following,
    boolean follower,
    boolean newAccount,
    boolean privateMention) {}
