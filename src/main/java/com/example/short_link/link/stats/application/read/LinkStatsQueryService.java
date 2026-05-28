package com.example.short_link.link.stats.application.read;

import com.example.short_link.common.security.UserAccessLookup;
import com.example.short_link.link.access.application.LinkAccessGuard;
import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LinkStatsQueryService {

  private final LinkRepository linkRepository;
  private final UserAccessLookup users;
  private final LinkAccessGuard accessGuard;
  private final LinkStatsReportAssembler reportAssembler;

  public LinkStats stats(Long userId, ShortCode shortCode) {
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
    accessGuard.requireView(userId, link);
    Long zoneOwnerId = link.isOwnedBy(userId) ? userId : link.getUserId();
    return reportAssembler.assemble(link, ownerZone(zoneOwnerId));
  }

  public LinkStats publicStats(ShortCode shortCode) {
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
    if (!link.isStatsPublic()) {
      throw new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode);
    }
    return reportAssembler.assemble(link, ownerZone(link.getUserId()));
  }

  private ZoneId ownerZone(Long userId) {
    if (userId == null) return LinkStatsDateSupport.DEFAULT_ZONE;
    return users
        .timezone(userId)
        .map(LinkStatsDateSupport::safeZone)
        .orElse(LinkStatsDateSupport.DEFAULT_ZONE);
  }

  public static ZoneId safeZone(String tz) {
    return LinkStatsDateSupport.safeZone(tz);
  }

  public static String currentOffset(ZoneId zone) {
    return LinkStatsDateSupport.currentOffset(zone);
  }

  static String mapDayOfWeek(int mysqlDow) {
    return LinkStatsTimeBucketsReader.mapDayOfWeek(mysqlDow);
  }

  static Integer halfLife(List<LinkStats.DayClick> days) {
    return LinkStatsLifecycleReader.halfLife(days);
  }
}
