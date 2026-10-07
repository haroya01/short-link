package com.example.short_link.common.event;

// A member stopped approving followers by hand; everyone still waiting becomes a follower.
public record AccountUnlockedEvent(Long userId) {}
