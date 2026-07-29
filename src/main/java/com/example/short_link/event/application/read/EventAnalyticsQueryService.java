package com.example.short_link.event.application.read;

import com.example.short_link.event.application.read.EventAnalyticsView.Bucket;
import com.example.short_link.event.application.read.EventAnalyticsView.DailyBucket;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.EventRegistrationEntity;
import com.example.short_link.event.domain.RegistrationStatus;
import com.example.short_link.event.domain.repository.EventRegistrationRepository;
import com.example.short_link.event.domain.repository.EventRepository;
import com.example.short_link.event.exception.EventErrorCode;
import com.example.short_link.event.exception.EventException;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventAnalyticsQueryService {

  private static final String DIRECT = "direct";

  private final EventRepository eventRepository;
  private final EventRegistrationRepository registrationRepository;
  private final EventQueryService eventQueryService;
  private final ClickEventRepository clickEventRepository;

  @Transactional(readOnly = true)
  public EventAnalyticsView analyze(Long userId, Long eventId) {
    EventEntity event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND, eventId));
    if (!event.isOwnedBy(userId)) {
      throw new EventException(EventErrorCode.EVENT_PERMISSION_DENIED);
    }

    Map<Long, String> channelByLinkId = eventQueryService.channelLabels(eventId);
    List<ClickEventEntity> humanClicks =
        channelByLinkId.isEmpty()
            ? List.of()
            : clickEventRepository
                .findAllByLinkIdInOrderByClickedAtAsc(channelByLinkId.keySet())
                .stream()
                .filter(click -> !click.isBot())
                .toList();
    List<EventRegistrationEntity> confirmed =
        registrationRepository.findAllByEventIdOrderByCreatedAtAsc(eventId).stream()
            .filter(r -> r.getStatus() == RegistrationStatus.CONFIRMED)
            .toList();

    ZoneId zone = zoneOf(event.getTimezone());
    return new EventAnalyticsView(
        humanClicks.size(),
        confirmed.size(),
        buckets(humanClicks, click -> channelByLinkId.getOrDefault(click.getLinkId(), "?")),
        buckets(humanClicks, click -> orDirect(click.getClientApp())),
        buckets(confirmed, r -> registrationChannel(r, channelByLinkId)),
        confirmed.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    r -> r.getCreatedAt().atZone(zone).toLocalDate().toString(),
                    LinkedHashMap::new,
                    java.util.stream.Collectors.counting()))
            .entrySet()
            .stream()
            .map(entry -> new DailyBucket(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(DailyBucket::date))
            .toList());
  }

  private static <T> List<Bucket> buckets(List<T> items, Function<T, String> keyOf) {
    Map<String, Long> counts =
        items.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    keyOf, LinkedHashMap::new, java.util.stream.Collectors.counting()));
    return counts.entrySet().stream()
        .map(entry -> new Bucket(entry.getKey(), entry.getValue()))
        .sorted(Comparator.comparingLong(Bucket::count).reversed())
        .toList();
  }

  private static String registrationChannel(
      EventRegistrationEntity registration, Map<Long, String> channelByLinkId) {
    if (registration.getLinkId() != null) {
      String label = channelByLinkId.get(registration.getLinkId());
      if (label != null) return label;
    }
    if (registration.getClientApp() != null) return registration.getClientApp();
    if (registration.getReferrerHost() != null) return registration.getReferrerHost();
    return DIRECT;
  }

  private static String orDirect(String value) {
    return value == null || value.isBlank() ? DIRECT : value;
  }

  private static ZoneId zoneOf(String timezone) {
    try {
      return ZoneId.of(timezone);
    } catch (Exception e) {
      return ZoneId.of("Asia/Seoul");
    }
  }
}
