package com.example.short_link.post.application.read;

public record PostScoped<T>(T body, boolean restricted) {}
