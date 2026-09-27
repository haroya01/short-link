package com.example.short_link.event.application.write;

import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.repository.EventRepository;
import com.example.short_link.event.exception.EventErrorCode;
import com.example.short_link.event.exception.EventException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChangeEventStatusUseCase {

  public enum Action {
    CLOSE,
    REOPEN,
    CANCEL
  }

  private final EventRepository eventRepository;

  @Transactional
  public EventEntity execute(Long userId, Long eventId, Action action) {
    EventEntity event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND, eventId));
    if (!event.isOwnedBy(userId)) {
      throw new EventException(EventErrorCode.EVENT_PERMISSION_DENIED);
    }
    switch (action) {
      case CLOSE -> event.close();
      case REOPEN -> event.reopen();
      case CANCEL -> event.cancel();
    }
    return event;
  }
}
