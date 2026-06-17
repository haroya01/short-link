package com.example.short_link.user.application.read;

/** One row of the "blocked users" management list — enough for the client to render + unblock. */
public record BlockedUserView(Long id, String username, String avatarUrl) {}
