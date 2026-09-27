package com.example.short_link.event.application;

import java.util.Optional;

public interface EventOrganizerLookup {

  record Organizer(String username, String avatarUrl) {}

  Optional<Organizer> find(Long userId);
}
