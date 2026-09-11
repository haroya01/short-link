package com.example.short_link.link.redirect.presentation.helper;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.redirect.application.LinkPreviewData;
import com.example.short_link.link.redirect.application.read.LinkPreviewQueryService;
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

  private final LinkPreviewQueryService previews;

  public String render(LinkEntity link, String shortUrl, long clickCount) {
    LinkPreviewData preview = previews.find(link, shortUrl, clickCount);
    String title = preview.title();
    String description = preview.description();
    String image = preview.image();
    String original = preview.originalUrl();
    boolean useGenerated = preview.generatedImage();

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
