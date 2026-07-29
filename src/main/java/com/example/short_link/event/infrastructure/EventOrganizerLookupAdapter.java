package com.example.short_link.event.infrastructure;

import com.example.short_link.event.application.EventOrganizerLookup;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class EventOrganizerLookupAdapter implements EventOrganizerLookup {

  private final UserRepository userRepository;

  @Override
  public Optional<Organizer> find(Long userId) {
    return userRepository
        .findById(userId)
        .map(user -> new Organizer(user.getUsername(), user.getAvatarUrl()));
  }
}
