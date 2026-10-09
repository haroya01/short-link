package com.example.short_link.link.redirect.application.read;

import com.example.short_link.common.eventlink.EventLinkPreview;
import com.example.short_link.common.eventlink.EventLinkPreviewPort;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.redirect.application.LinkPreviewData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LinkPreviewQueryService {
  private static final String KURL_DESCRIPTION = "Shortened with kurl. Click to continue.";

  private final EventLinkPreviewPort eventLinkPreview;

  public LinkPreviewData find(LinkEntity link, String shortUrl, long clickCount) {
    // 이벤트 귀속 링크는 이벤트 메타데이터가 OG 오버라이드와 스크랩보다 우선한다.
    Long linkId = link.getId();
    EventLinkPreview event =
        linkId == null ? null : eventLinkPreview.findByLinkId(linkId).orElse(null);
    String title =
        event != null
            ? event.title()
            : nonBlankOr(link.getEffectiveOgTitle(), link.getOriginalUrl());
    String description =
        event != null
            ? event.description()
            : nonBlankOr(link.getEffectiveOgDescription(), KURL_DESCRIPTION);
    // Use a generated card only when neither the event nor destination provides an image.
    String destinationImage =
        event != null && event.coverImageUrl() != null && !event.coverImageUrl().isBlank()
            ? event.coverImageUrl()
            : link.getEffectiveOgImage();
    boolean useGenerated = destinationImage == null || destinationImage.isBlank();
    String image = useGenerated ? generatedCard(shortUrl, clickCount) : destinationImage;
    String original = link.getOriginalUrl();

    return new LinkPreviewData(title, description, shortUrl, image, original, useGenerated);
  }

  // Only what the owner wrote and kurl draws: nothing scraped from or pointing at the destination.
  public LinkPreviewData findWithoutDestination(LinkEntity link, String shortUrl, long clickCount) {
    return new LinkPreviewData(
        nonBlankOr(link.getOgTitleOverride(), shortUrl),
        KURL_DESCRIPTION,
        shortUrl,
        generatedCard(shortUrl, clickCount),
        null,
        true);
  }

  private static String generatedCard(String shortUrl, long clickCount) {
    return shortUrl + "/og.png?c=" + Math.max(0L, clickCount);
  }

  private static String nonBlankOr(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
