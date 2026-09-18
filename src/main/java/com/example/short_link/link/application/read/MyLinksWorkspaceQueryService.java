package com.example.short_link.link.application.read;

import com.example.short_link.link.application.dto.MyLinksOverview;
import com.example.short_link.link.application.dto.MyLinksOverview.DayClick;
import com.example.short_link.link.application.dto.MyLinksResult;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyLinksWorkspaceQueryService {
  private final LinkRepository links;
  private final MyLinkReader reader;
  private final Clock clock;

  public MyLinksResult favorites(Long userId) {
    return page(links.findAllByUserIdAndFavoriteOrderIsNotNullOrderByFavoriteOrderAscIdAsc(userId));
  }

  public MyLinksResult byCodes(Long userId, List<ShortCode> codes) {
    if (codes.isEmpty()) return new MyLinksResult(List.of(), null, false);
    List<ShortCode> unique = List.copyOf(new LinkedHashSet<>(codes));
    Map<ShortCode, LinkEntity> found =
        links.findAllByShortCodeInAndUserId(unique, userId).stream()
            .collect(Collectors.toMap(LinkEntity::getShortCode, Function.identity()));
    return page(unique.stream().filter(found::containsKey).map(found::get).toList());
  }

  public MyLinksOverview overview(Long userId) {
    Instant now = clock.instant();
    ZoneId zone = reader.ownerZone(userId);
    LocalDate today = now.atZone(zone).toLocalDate();
    List<LinkEntity> all = links.findAllByUserIdOrderByCreatedAtDesc(userId);
    List<Long> ids = all.stream().map(LinkEntity::getId).toList();
    Map<Long, Long> counts = reader.clickCountsByLinkIds(ids);
    Map<Long, Long> humans = reader.humanClickCountsByLinkIds(ids);
    Map<Long, List<Long>> sparks = reader.dailySeries(ids, zone, now);
    long[] dayCounts = new long[7];
    sparks
        .values()
        .forEach(
            series -> {
              for (int i = 0; i < 7; i++) dayCounts[i] += series.get(i);
            });
    List<DayClick> days =
        IntStream.range(0, 7)
            .mapToObj(i -> new DayClick(today.minusDays(6 - i), dayCounts[i]))
            .toList();
    List<LinkEntity> top =
        all.stream()
            .sorted(
                Comparator.comparingLong((LinkEntity link) -> humans.getOrDefault(link.getId(), 0L))
                    .reversed()
                    .thenComparing(LinkEntity::getId, Comparator.reverseOrder()))
            .limit(5)
            .toList();
    return new MyLinksOverview(
        all.size(),
        counts.values().stream().mapToLong(Long::longValue).sum(),
        humans.values().stream().mapToLong(Long::longValue).sum(),
        days.stream().mapToLong(DayClick::count).sum(),
        dayCounts[6],
        all.stream().filter(link -> counts.getOrDefault(link.getId(), 0L) == 0).count(),
        all.stream()
            .filter(
                link ->
                    link.getExpiresAt() != null
                        && !link.getExpiresAt().isBefore(now)
                        && !link.getExpiresAt().isAfter(now.plus(3, ChronoUnit.DAYS)))
            .count(),
        zone.getId(),
        now,
        days,
        reader.assemble(top, counts, sparks, zone));
  }

  private MyLinksResult page(List<LinkEntity> items) {
    if (items.isEmpty()) return new MyLinksResult(List.of(), null, false);
    return new MyLinksResult(
        reader.assemble(
            items, reader.clickCountsByLinkIds(items.stream().map(LinkEntity::getId).toList())),
        null,
        false);
  }
}
