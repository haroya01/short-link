package com.example.short_link.blogimport.application;

import com.example.short_link.blogimport.application.feed.FeedDocument;
import com.example.short_link.blogimport.application.feed.FeedEntry;
import com.example.short_link.blogimport.application.feed.HtmlBlockConverter;
import com.example.short_link.blogimport.application.feed.RssFeedParser;
import com.example.short_link.blogimport.domain.BlogAddress;
import com.example.short_link.blogimport.domain.ImportPlatform;
import com.example.short_link.blogimport.exception.BlogImportErrorCode;
import com.example.short_link.blogimport.exception.BlogImportException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BlogFeeds {

  private static final Pattern BRUNCH_FEED =
      Pattern.compile("https?://brunch\\.co\\.kr/rss/@@([A-Za-z0-9]{1,20})");
  private static final int MAX_URL_LENGTH = 2048;

  private final BlogFeedFetcher fetcher;
  private final HtmlBlockConverter html;

  public BlogAddress parse(String url) {
    return BlogAddress.parse(url)
        .orElseThrow(
            () -> new BlogImportException(BlogImportErrorCode.IMPORT_UNSUPPORTED_SOURCE, url));
  }

  public BlogFeed read(BlogAddress address) {
    BlogAddress resolved = address.feedKnown() ? address : brunchFeed(address);
    FeedDocument doc =
        RssFeedParser.parse(fetcher.fetch(resolved.feedUrl()))
            .orElseThrow(
                () ->
                    new BlogImportException(
                        BlogImportErrorCode.IMPORT_FEED_INVALID, resolved.feedUrl()));
    if (doc.isEmpty()) {
      throw new BlogImportException(BlogImportErrorCode.IMPORT_FEED_NOT_FOUND, resolved.blogUrl());
    }
    List<BlogFeed.Post> posts = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (FeedEntry entry : doc.entries()) {
      String url = canonical(resolved.platform(), entry);
      if (url == null || !seen.add(url)) {
        continue;
      }
      posts.add(
          new BlogFeed.Post(url, entry.title(), entry.publishedAt(), entry.html(), entry.tags()));
    }
    String title = doc.title().isBlank() ? resolved.blogUrl() : doc.title();
    return new BlogFeed(
        resolved,
        title,
        doc.description(),
        language(doc.language()),
        fullContent(resolved, posts),
        posts);
  }

  private BlogAddress brunchFeed(BlogAddress address) {
    Matcher feed = BRUNCH_FEED.matcher(fetcher.fetch(address.blogUrl()));
    if (!feed.find()) {
      throw new BlogImportException(BlogImportErrorCode.IMPORT_FEED_NOT_FOUND, address.blogUrl());
    }
    return address.withBrunchFeed(feed.group(1));
  }

  private boolean fullContent(BlogAddress address, List<BlogFeed.Post> posts) {
    if (address.platform().excerptOnlyFeed()) {
      return false;
    }
    if (address.platform() == ImportPlatform.VELOG) {
      return true;
    }
    return posts.stream().anyMatch(post -> html.hasMarkup(post.html()));
  }

  static String canonical(ImportPlatform platform, FeedEntry entry) {
    String candidate =
        platform == ImportPlatform.NAVER && isHttp(entry.guid())
            ? entry.guid()
            : isHttp(entry.link()) ? entry.link() : isHttp(entry.guid()) ? entry.guid() : null;
    if (candidate == null) {
      return null;
    }
    URI uri;
    try {
      uri = URI.create(candidate.strip());
    } catch (IllegalArgumentException e) {
      return null;
    }
    if (uri.getRawAuthority() == null) {
      return null;
    }
    String url =
        uri.getScheme().toLowerCase(Locale.ROOT)
            + "://"
            + uri.getRawAuthority()
            + (uri.getRawPath() == null ? "" : uri.getRawPath());
    return url.length() > MAX_URL_LENGTH ? null : url;
  }

  private static boolean isHttp(String value) {
    if (value == null) {
      return false;
    }
    String lower = value.strip().toLowerCase(Locale.ROOT);
    return lower.startsWith("https://") || lower.startsWith("http://");
  }

  private static String language(String raw) {
    String value = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
    if (value.startsWith("ja")) {
      return "ja";
    }
    if (value.startsWith("en")) {
      return "en";
    }
    return "ko";
  }
}
