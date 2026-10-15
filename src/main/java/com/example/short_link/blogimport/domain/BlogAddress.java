package com.example.short_link.blogimport.domain;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

// A blog on a supported platform. A brunch address written with the author's @handle does not name
// the feed: its feedUrl is null until the profile page gives the internal @@id (withBrunchFeed).
public record BlogAddress(ImportPlatform platform, String id, String blogUrl, String feedUrl) {

  private static final Pattern NAVER_ID = Pattern.compile("[a-z0-9_-]{1,40}");
  private static final Pattern TISTORY_ID = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");
  private static final Pattern VELOG_ID = Pattern.compile("[a-z0-9_-]{1,40}");
  private static final Pattern BRUNCH_HANDLE = Pattern.compile("[A-Za-z0-9_.-]{1,40}");
  private static final Pattern BRUNCH_INTERNAL = Pattern.compile("[A-Za-z0-9]{1,20}");

  public boolean feedKnown() {
    return feedUrl != null;
  }

  public String blogKey() {
    return platform.name().toLowerCase(Locale.ROOT) + ":" + id;
  }

  public BlogAddress withBrunchFeed(String internalId) {
    if (platform != ImportPlatform.BRUNCH || !BRUNCH_INTERNAL.matcher(internalId).matches()) {
      throw new IllegalArgumentException("not a brunch internal id: " + internalId);
    }
    return new BlogAddress(
        ImportPlatform.BRUNCH, "@@" + internalId, blogUrl, brunchFeed(internalId));
  }

  public static Optional<BlogAddress> parse(String raw) {
    if (raw == null || raw.isBlank() || raw.length() > 512) {
      return Optional.empty();
    }
    String text = raw.strip();
    if (!text.contains("://")) {
      text = "https://" + text;
    }
    URI uri;
    try {
      uri = URI.create(text);
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (uri.getHost() == null || !(scheme.equals("https") || scheme.equals("http"))) {
      return Optional.empty();
    }
    String host = uri.getHost().toLowerCase(Locale.ROOT);
    List<String> path = segments(uri.getPath());
    if (host.equals("blog.naver.com") || host.equals("m.blog.naver.com")) {
      return naver(path, uri.getRawQuery());
    }
    if (host.equals("rss.blog.naver.com")) {
      return path.size() == 1 && path.get(0).endsWith(".xml")
          ? naverId(path.get(0).substring(0, path.get(0).length() - 4))
          : Optional.empty();
    }
    if (host.endsWith(".blog.me")) {
      return naverId(host.substring(0, host.length() - ".blog.me".length()));
    }
    if (host.endsWith(".tistory.com")) {
      return tistory(host.substring(0, host.length() - ".tistory.com".length()));
    }
    if (host.equals("velog.io") || host.equals("www.velog.io") || host.equals("v2.velog.io")) {
      return velog(path);
    }
    if (host.equals("brunch.co.kr")
        || host.equals("www.brunch.co.kr")
        || host.equals("m.brunch.co.kr")) {
      return brunch(path);
    }
    return Optional.empty();
  }

  private static Optional<BlogAddress> naver(List<String> path, String rawQuery) {
    if (path.isEmpty()) {
      return Optional.empty();
    }
    String first = path.get(0);
    if (first.toLowerCase(Locale.ROOT).startsWith("post")
        && first.toLowerCase(Locale.ROOT).contains(".n")) {
      return queryParam(rawQuery, "blogId").flatMap(BlogAddress::naverId);
    }
    return naverId(first);
  }

  private static Optional<BlogAddress> naverId(String raw) {
    String id = raw.toLowerCase(Locale.ROOT);
    if (!NAVER_ID.matcher(id).matches()) {
      return Optional.empty();
    }
    return Optional.of(
        new BlogAddress(
            ImportPlatform.NAVER,
            id,
            "https://blog.naver.com/" + id,
            "https://rss.blog.naver.com/" + id + ".xml"));
  }

  private static Optional<BlogAddress> tistory(String sub) {
    if (sub.contains(".") || sub.equals("www") || !TISTORY_ID.matcher(sub).matches()) {
      return Optional.empty();
    }
    String base = "https://" + sub + ".tistory.com";
    return Optional.of(new BlogAddress(ImportPlatform.TISTORY, sub, base, base + "/rss"));
  }

  private static Optional<BlogAddress> velog(List<String> path) {
    String handle = null;
    if (!path.isEmpty() && path.get(0).startsWith("@")) {
      handle = path.get(0).substring(1);
    } else if (path.size() >= 2 && path.get(0).equals("rss")) {
      handle = path.get(1).startsWith("@") ? path.get(1).substring(1) : path.get(1);
    }
    if (handle == null) {
      return Optional.empty();
    }
    String id = handle.toLowerCase(Locale.ROOT);
    if (!VELOG_ID.matcher(id).matches()) {
      return Optional.empty();
    }
    return Optional.of(
        new BlogAddress(
            ImportPlatform.VELOG, id, "https://velog.io/@" + id, "https://v2.velog.io/rss/@" + id));
  }

  private static Optional<BlogAddress> brunch(List<String> path) {
    List<String> rest =
        !path.isEmpty() && path.get(0).equals("rss") ? path.subList(1, path.size()) : path;
    if (rest.isEmpty()) {
      return Optional.empty();
    }
    String first = rest.get(0);
    if (first.startsWith("@@")) {
      String internal = first.substring(2);
      if (!BRUNCH_INTERNAL.matcher(internal).matches()) {
        return Optional.empty();
      }
      return Optional.of(
          new BlogAddress(
              ImportPlatform.BRUNCH,
              "@@" + internal,
              "https://brunch.co.kr/@@" + internal,
              brunchFeed(internal)));
    }
    if (first.startsWith("@") && path == rest) {
      String handle = first.substring(1);
      if (!BRUNCH_HANDLE.matcher(handle).matches()) {
        return Optional.empty();
      }
      return Optional.of(
          new BlogAddress(
              ImportPlatform.BRUNCH, "@" + handle, "https://brunch.co.kr/@" + handle, null));
    }
    return Optional.empty();
  }

  private static String brunchFeed(String internal) {
    return "https://brunch.co.kr/rss/@@" + internal;
  }

  private static List<String> segments(String path) {
    if (path == null) {
      return List.of();
    }
    return Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).toList();
  }

  private static Optional<String> queryParam(String rawQuery, String name) {
    if (rawQuery == null) {
      return Optional.empty();
    }
    for (String pair : rawQuery.split("&")) {
      int eq = pair.indexOf('=');
      if (eq > 0 && pair.substring(0, eq).equalsIgnoreCase(name)) {
        return Optional.of(URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
      }
    }
    return Optional.empty();
  }
}
