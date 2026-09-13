package com.example.short_link.user.application.dto;

/**
 * {@code subject} is Apple's stable per-team user id. {@code email} may be a private-relay address
 * or absent for returning users.
 */
public record AppleIdentity(String subject, String email) {}
