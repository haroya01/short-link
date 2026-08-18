package com.example.short_link.link.redirect.application;

import com.example.short_link.common.eventlink.EventLinkPreview;
import com.example.short_link.common.eventlink.EventLinkPreviewPort;
import com.example.short_link.link.domain.LinkEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Renders an HTML page that embeds Open Graph + Twitter Card metadata for a short link, so social
 * crawlers (KakaoTalk, Slack, Twitter, etc.) display a rich preview when the short URL is shared.
 * Falls back to the original URL via meta-refresh and an explicit anchor — important for clients
 * that ignore JS but still follow refresh.
 */
@Component
@RequiredArgsConstructor
public class LinkPreviewRenderer {

  private final EventLinkPreviewPort eventLinkPreview;

  public String render(LinkEntity link, String shortUrl, long clickCount) {
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

    StringBuilder sb = new StringBuilder(2048);
    sb.append("<!doctype html>\n");
    sb.append("<html lang=\"en\"><head>\n");
    sb.append("<meta charset=\"utf-8\">\n");
    sb.append("<title>").append(escape(title)).append("</title>\n");
    sb.append("<meta name=\"description\" content=\"").append(escape(description)).append("\">\n");

    sb.append("<meta property=\"og:type\" content=\"website\">\n");
    sb.append("<meta property=\"og:title\" content=\"").append(escape(title)).append("\">\n");
    sb.append("<meta property=\"og:description\" content=\"")
        .append(escape(description))
        .append("\">\n");
    sb.append("<meta property=\"og:url\" content=\"").append(escape(shortUrl)).append("\">\n");
    if (image != null && !image.isBlank()) {
      sb.append("<meta property=\"og:image\" content=\"").append(escape(image)).append("\">\n");
      if (useGenerated) {
        sb.append("<meta property=\"og:image:width\" content=\"1200\">\n");
        sb.append("<meta property=\"og:image:height\" content=\"630\">\n");
      }
    }

    sb.append("<meta name=\"twitter:card\" content=\"")
        .append(image != null && !image.isBlank() ? "summary_large_image" : "summary")
        .append("\">\n");
    sb.append("<meta name=\"twitter:title\" content=\"").append(escape(title)).append("\">\n");
    sb.append("<meta name=\"twitter:description\" content=\"")
        .append(escape(description))
        .append("\">\n");
    if (image != null && !image.isBlank()) {
      sb.append("<meta name=\"twitter:image\" content=\"").append(escape(image)).append("\">\n");
    }

    sb.append("<link rel=\"canonical\" href=\"").append(escape(original)).append("\">\n");
    sb.append("<meta http-equiv=\"refresh\" content=\"0;url=")
        .append(escape(original))
        .append("\">\n");
    sb.append("</head>\n<body>\n");
    sb.append("<p>Redirecting to <a href=\"")
        .append(escape(original))
        .append("\">")
        .append(escape(original))
        .append("</a>&hellip;</p>\n");
    sb.append("</body></html>\n");
    return sb.toString();
  }

  private static String nonBlankOr(String value, String fallback) {
    if (value == null || value.isBlank()) return fallback;
    return value;
  }

  private static String escape(String s) {
    if (s == null) return "";
    StringBuilder out = new StringBuilder(s.length() + 16);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '&' -> out.append("&amp;");
        case '<' -> out.append("&lt;");
        case '>' -> out.append("&gt;");
        case '"' -> out.append("&quot;");
        case '\'' -> out.append("&#39;");
        default -> out.append(c);
      }
    }
    return out.toString();
  }
}
