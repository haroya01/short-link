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
    // 이벤트 귀속 링크는 이벤트가 미리보기의 진실원 — OG 오버라이드/스크랩보다 먼저 본다.
    // 단톡·디스코드에서 "Shortened with kurl" 대신 이벤트명·일시·장소가 뜬다.
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
    // Fall back to a kurl-generated card when the destination doesn't have its own OG image. This
    // keeps real content links (YouTube, blog posts) showing their real preview while turning
    // plain redirects into a self-marketing surface that announces the click count.
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
