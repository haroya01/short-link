package com.example.short_link.post.application.read;

import static com.example.short_link.common.analytics.VisitStatsAssembler.DAILY_WINDOW;
import static com.example.short_link.common.analytics.VisitStatsAssembler.TOP_SIZE;
import static com.example.short_link.common.analytics.VisitStatsAssembler.overPeriods;

import com.example.short_link.common.analytics.VisitStats;
import com.example.short_link.common.analytics.VisitStatsAssembler;
import com.example.short_link.common.security.UserAccessLookup;
import com.example.short_link.common.time.OffsetPeriod;
import com.example.short_link.common.time.ReportZones;
import com.example.short_link.post.application.write.SeriesOwnership;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostReadStatsReader;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostReadStatsService {

  private final PostReadStatsReader reader;
  private final PostRepository postRepository;
  private final SeriesOwnership seriesOwnership;
  private final UserAccessLookup users;
  private final Clock clock;

  @Transactional(readOnly = true)
  public VisitStats forPost(Long userId, Long postId) {
    PostEntity post =
        postRepository
            .findById(postId)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, postId));
    if (!post.isOwnedBy(userId)) {
      throw new PostException(PostErrorCode.PERMISSION_DENIED).with("postId", postId);
    }
    return compute(List.of(postId), userId);
  }

  @Transactional(readOnly = true)
  public VisitStats forSeries(Long userId, Long seriesId) {
    seriesOwnership.requireOwned(userId, seriesId);
    List<Long> postIds =
        postRepository.findAllBySeriesIdOrderBySeriesOrderAsc(seriesId).stream()
            .map(PostEntity::getId)
            .toList();
    return compute(postIds, userId);
  }

  private VisitStats compute(Collection<Long> postIds, Long ownerUserId) {
    ZoneId zone = ReportZones.zoneOf(users.timezone(ownerUserId).orElse(null));
    if (postIds.isEmpty()) {
      // An empty series must short-circuit before SQL IN ().
      return VisitStats.empty(zone.getId());
    }
    Instant now = clock.instant();
    Instant until = now.truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);

    VisitStatsAssembler.Totals totals =
        new VisitStatsAssembler.Totals(
            reader.countViews(postIds),
            reader.countHuman(postIds),
            reader.countBot(postIds),
            reader.countUnique(postIds),
            reader.firstViewedAt(postIds),
            reader.lastViewedAt(postIds));
    List<OffsetPeriod> recent = OffsetPeriod.between(zone, now.minus(DAILY_WINDOW), until);
    List<OffsetPeriod> lifetime = OffsetPeriod.between(zone, totals.firstVisitOr(now), until);
    VisitStatsAssembler.Rows rows =
        new VisitStatsAssembler.Rows(
            overPeriods(recent, p -> reader.daily(postIds, p.from(), p.until(), p.offset())),
            overPeriods(lifetime, p -> reader.hourly(postIds, p.from(), p.until(), p.offset())),
            overPeriods(lifetime, p -> reader.heatmap(postIds, p.from(), p.until(), p.offset())),
            reader.topCountries(postIds, TOP_SIZE),
            reader.topDevices(postIds, TOP_SIZE),
            reader.topBrowsers(postIds, TOP_SIZE),
            reader.topReferrerHosts(postIds, TOP_SIZE),
            reader.topSourceChannels(postIds, TOP_SIZE),
            reader.topUtmCampaigns(postIds, TOP_SIZE),
            reader.topUtmSources(postIds, TOP_SIZE));
    return VisitStatsAssembler.assemble(zone, totals, rows);
  }
}
