package com.example.short_link.link.application.read;

import com.example.short_link.link.application.dto.MyLinksOverview;
import com.example.short_link.link.application.dto.MyLinksOverview.DayClick;
import com.example.short_link.link.application.dto.MyLinksResult;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.repository.ClickRangeReadRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
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
  private final ClickRangeReadRepository clickRanges;
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
    ZonedDateTime localNow = now.atZone(zone);
    LocalDate today = localNow.toLocalDate();
    Instant weekStart = today.minusDays(6).atStartOfDay(zone).toInstant();
    List<LinkEntity> all = links.findAllByUserIdOrderByCreatedAtDesc(userId);
    List<Long> ids = all.stream().map(LinkEntity::getId).toList();
    Map<Long, Long> counts = reader.clickCountsByLinkIds(ids);
    Map<Long, Long> humans = reader.humanClickCountsByLinkIds(ids);
    Map<Long, List<Long>> sparks = reader.dailySeries(ids, zone, now);
    long[] dayCounts = new long[7];
    Map<Long, Long> weekByLink = new HashMap<>();
    sparks.forEach(
        (id, series) -> {
          for (int i = 0; i < 7; i++) dayCounts[i] += series.get(i);
          weekByLink.put(id, series.stream().mapToLong(Long::longValue).sum());
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
    List<LinkEntity> weekTop =
        all.stream()
            .filter(link -> weekByLink.getOrDefault(link.getId(), 0L) > 0)
            .sorted(
                Comparator.comparingLong(
                        (LinkEntity link) -> weekByLink.getOrDefault(link.getId(), 0L))
                    .reversed()
                    .thenComparing(LinkEntity::getId, Comparator.reverseOrder()))
            .limit(5)
            .toList();
    long previousClicks7d =
        clickRanges.countHumanByUserIdAndRange(
            userId,
            today.minusDays(13).atStartOfDay(zone).toInstant(),
            localNow.minusWeeks(1).toInstant());
    MyLinksOverview.Peak peak =
        clickRanges
            .findHeatmapByUserIdAndRange(userId, weekStart, now, offset(zone, now), 1)
            .stream()
            .findFirst()
            .map(row -> new MyLinksOverview.Peak(row.getDow(), row.getHour(), row.getCount()))
            .orElse(null);
    return new MyLinksOverview(
        all.size(),
        counts.values().stream().mapToLong(Long::longValue).sum(),
        humans.values().stream().mapToLong(Long::longValue).sum(),
        days.stream().mapToLong(DayClick::count).sum(),
        previousClicks7d,
        dayCounts[6],
        all.stream().filter(link -> humans.getOrDefault(link.getId(), 0L) == 0).count(),
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
        peak,
        reader.assemble(top, counts, sparks, zone),
        reader.assemble(weekTop, counts, sparks, zone));
  }

  private static String offset(ZoneId zone, Instant at) {
    ZoneOffset offset = zone.getRules().getOffset(at);
    return offset.getId().equals("Z") ? "+00:00" : offset.getId();
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
