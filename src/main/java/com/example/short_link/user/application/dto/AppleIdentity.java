package com.example.short_link.user.application.dto;

/**
 * The two claims we trust out of a verified Apple identity token. {@code subject} is Apple's stable
 * per-team user id; {@code email} may be a private-relay address and is absent when the user denied
 * the scope (possible only for returning users — Apple always sends it on first consent).
 */
public record AppleIdentity(String subject, String email) {}
