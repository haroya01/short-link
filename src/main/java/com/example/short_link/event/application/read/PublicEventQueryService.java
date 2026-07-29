package com.example.short_link.event.application.read;

import com.example.short_link.event.application.image.EventCoverImageService;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.repository.EventQuestionRepository;
import com.example.short_link.event.domain.repository.EventRepository;
import com.example.short_link.event.exception.EventErrorCode;
import com.example.short_link.event.exception.EventException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublicEventQueryService {

  private final EventRepository eventRepository;
  private final EventQuestionRepository questionRepository;
  private final EventCoverImageService coverImageService;

  @Transactional(readOnly = true)
  public PublicEventView findBySlug(String slug) {
    EventEntity event =
        eventRepository
            .findBySlug(slug)
            .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND, slug));
    List<EventQuestionView> questions =
        questionRepository.findAllByEventIdOrderByPosition(event.getId()).stream()
            .map(EventQuestionView::from)
            .toList();
    return PublicEventView.from(
        event, questions, Instant.now(), coverImageService.urlFor(event.getCoverImageKey()));
  }
}
