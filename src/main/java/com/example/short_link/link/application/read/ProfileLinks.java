package com.example.short_link.link.application.read;

import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.domain.repository.LinkRepository.ProfileLinkRow;
import com.example.short_link.link.stats.application.read.ClickTotalsReader;
import com.example.short_link.link.stats.domain.projection.ClickProjections.LinkClickCount;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileLinks {

  private final LinkRepository links;
  private final ClickTotalsReader clicks;
  private final ShortLinkUrlBuilder urls;

  public List<ProfileLinkCard> publicCards(Long userId) {
    List<ProfileLinkRow> rows = links.findPublicProfileLinks(userId);
    Map<Long, Long> counts = clickCounts(rows);
    return rows.stream().map(row -> card(row, counts)).toList();
  }

  public List<ShortCode> viewLimitReached(Collection<ShortCode> shortCodes) {
    return links.findShortCodesWithViewLimitReached(shortCodes);
  }

  private Map<Long, Long> clickCounts(List<ProfileLinkRow> rows) {
    Map<Long, Long> counts = new HashMap<>();
    if (!rows.isEmpty()) {
      List<Long> ids = rows.stream().map(row -> row.getLink().getId()).toList();
      for (LinkClickCount row : clicks.countsByLinkIds(ids)) {
        counts.put(row.getLinkId(), row.getCount());
      }
    }
    return counts;
  }

  private ProfileLinkCard card(ProfileLinkRow row, Map<Long, Long> counts) {
    LinkEntity link = row.getLink();
    return new ProfileLinkCard(
        link.getShortCode(),
        urls.build(link.getShortCode()),
        link.getOriginalUrl(),
        new ProfileLinkCard.Preview(
            link.getEffectiveOgTitle(), link.getEffectiveOgImage(), link.getOgTitleOverride()),
        Boolean.TRUE.equals(row.getPasswordRequired()),
        link.isProfileHighlighted(),
        link.getProfileOrder(),
        counts.getOrDefault(link.getId(), 0L),
        new ProfileLinkCard.Window(
            row.getOpensAt(), link.getExpiresAt(), link.getMaxViews() != null));
  }
}
