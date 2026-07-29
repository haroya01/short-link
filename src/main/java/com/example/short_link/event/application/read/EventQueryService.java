package com.example.short_link.event.application.read;

import com.example.short_link.event.application.image.EventCoverImageService;
import com.example.short_link.event.application.read.EventView.EventLinkView;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.EventLinkEntity;
import com.example.short_link.event.domain.EventRegistrationEntity;
import com.example.short_link.event.domain.repository.EventLinkRepository;
import com.example.short_link.event.domain.repository.EventQuestionRepository;
import com.example.short_link.event.domain.repository.EventRegistrationRepository;
import com.example.short_link.event.domain.repository.EventRepository;
import com.example.short_link.event.exception.EventErrorCode;
import com.example.short_link.event.exception.EventException;
import com.example.short_link.link.domain.repository.LinkRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventQueryService {

  private final EventRepository eventRepository;
  private final EventQuestionRepository questionRepository;
  private final EventRegistrationRepository registrationRepository;
  private final EventLinkRepository eventLinkRepository;
  private final LinkRepository linkRepository;
  private final EventCoverImageService coverImageService;

  @Transactional(readOnly = true)
  public List<EventView> listMyEvents(Long userId) {
    return eventRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(
            event ->
                EventView.from(
                    event,
                    List.of(),
                    List.of(),
                    coverImageService.urlFor(event.getCoverImageKey())))
        .toList();
  }

  @Transactional(readOnly = true)
  public EventView findOwnEvent(Long userId, Long eventId) {
    EventEntity event = requireOwned(userId, eventId);
    List<EventQuestionView> questions =
        questionRepository.findAllByEventIdOrderByPosition(eventId).stream()
            .map(EventQuestionView::from)
            .toList();
    return EventView.from(
        event, questions, linkViews(eventId), coverImageService.urlFor(event.getCoverImageKey()));
  }

  @Transactional(readOnly = true)
  public List<AttendeeView> listAttendees(Long userId, Long eventId) {
    requireOwned(userId, eventId);
    Map<Long, String> channelByLinkId = channelLabels(eventId);
    return registrationRepository.findAllByEventIdOrderByCreatedAtAsc(eventId).stream()
        .map(r -> AttendeeView.from(r, channelLabel(r, channelByLinkId)))
        .toList();
  }

  @Transactional(readOnly = true)
  public String exportAttendeesCsv(Long userId, Long eventId) {
    List<AttendeeView> attendees = listAttendees(userId, eventId);
    List<EventQuestionView> questions =
        questionRepository.findAllByEventIdOrderByPosition(eventId).stream()
            .map(EventQuestionView::from)
            .toList();
    StringBuilder csv = new StringBuilder("name,contact,status,channel,registered_at");
    for (EventQuestionView question : questions) {
      csv.append(',').append(escapeCsv(question.label()));
    }
    csv.append('\n');
    for (AttendeeView attendee : attendees) {
      csv.append(escapeCsv(attendee.name()))
          .append(',')
          .append(escapeCsv(attendee.contact()))
          .append(',')
          .append(attendee.status())
          .append(',')
          .append(escapeCsv(attendee.channel() == null ? "" : attendee.channel()))
          .append(',')
          .append(attendee.createdAt());
      for (EventQuestionView question : questions) {
        csv.append(',')
            .append(escapeCsv(attendee.answers().getOrDefault(String.valueOf(question.id()), "")));
      }
      csv.append('\n');
    }
    return csv.toString();
  }

  Map<Long, String> channelLabels(Long eventId) {
    return eventLinkRepository.findAllByEventId(eventId).stream()
        .collect(
            Collectors.toMap(
                EventLinkEntity::getLinkId,
                EventLinkEntity::getLabel,
                (a, b) -> a,
                LinkedHashMap::new));
  }

  private List<EventLinkView> linkViews(Long eventId) {
    return eventLinkRepository.findAllByEventId(eventId).stream()
        .map(
            link ->
                new EventLinkView(
                    link.getLinkId(),
                    linkRepository
                        .findById(link.getLinkId())
                        .map(entity -> entity.getShortCode().value())
                        .orElse(null),
                    link.getLabel()))
        .toList();
  }

  private static String channelLabel(
      EventRegistrationEntity registration, Map<Long, String> channelByLinkId) {
    if (registration.getLinkId() != null) {
      String label = channelByLinkId.get(registration.getLinkId());
      if (label != null) return label;
    }
    if (registration.getClientApp() != null) return registration.getClientApp();
    if (registration.getReferrerHost() != null) return registration.getReferrerHost();
    return null;
  }

  private EventEntity requireOwned(Long userId, Long eventId) {
    EventEntity event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND, eventId));
    if (!event.isOwnedBy(userId)) {
      throw new EventException(EventErrorCode.EVENT_PERMISSION_DENIED);
    }
    return event;
  }

  private static String escapeCsv(String value) {
    if (value == null) return "";
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return '"' + value.replace("\"", "\"\"") + '"';
    }
    return value;
  }
}
