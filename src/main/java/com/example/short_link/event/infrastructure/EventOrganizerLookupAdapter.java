package com.example.short_link.event.infrastructure;

import com.example.short_link.event.application.EventOrganizerLookup;
import com.example.short_link.user.application.read.UserSummaryReader;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class EventOrganizerLookupAdapter implements EventOrganizerLookup {

  private final UserSummaryReader users;

  @Override
  public Optional<Organizer> find(Long userId) {
    return users.find(userId).map(user -> new Organizer(user.username(), user.avatarUrl()));
  }
}
