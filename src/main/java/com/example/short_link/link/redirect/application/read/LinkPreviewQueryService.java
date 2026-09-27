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
            : nonBlankOr(
                link.getEffectiveOgDescription(), "Shortened with kurl. Click to continue.");
    // Use a generated card only when neither the event nor destination provides an image.
    String destinationImage =
        event != null && event.coverImageUrl() != null && !event.coverImageUrl().isBlank()
            ? event.coverImageUrl()
            : link.getEffectiveOgImage();
    boolean useGenerated = destinationImage == null || destinationImage.isBlank();
    String image =
        useGenerated ? shortUrl + "/og.png?c=" + Math.max(0L, clickCount) : destinationImage;
    String original = link.getOriginalUrl();

    return new LinkPreviewData(title, description, shortUrl, image, original, useGenerated);
  }

  private static String nonBlankOr(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
