package com.example.short_link.post.application.access;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.user.application.read.UserSummary;
import com.example.short_link.user.application.read.UserSummaryReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

@Component
public class PostUnlistedUrls {

  private final String blogBaseUrl;
  private final UserSummaryReader users;

  public PostUnlistedUrls(
      @Value("${short-link.blog-base-url}") String blogBaseUrl, UserSummaryReader users) {
    this.blogBaseUrl =
        blogBaseUrl.endsWith("/")
            ? blogBaseUrl.substring(0, blogBaseUrl.length() - 1)
            : blogBaseUrl;
    this.users = users;
  }

  public String of(PostEntity post) {
    return forAuthors().apply(post);
  }

  public Function<PostEntity, String> forAuthors() {
    Map<Long, String> usernames = new HashMap<>();
    return post ->
        unlisted(post)
            ? of(post, usernames.computeIfAbsent(post.getUserId(), this::username))
            : null;
  }

  private String username(Long userId) {
    return users.find(userId).map(UserSummary::username).orElse(null);
  }

  public String of(PostEntity post, String username) {
    if (!unlisted(post) || username == null) return null;
    return blogBaseUrl
        + "/@"
        + UriUtils.encodePathSegment(username, StandardCharsets.UTF_8)
        + "/"
        + UriUtils.encodePathSegment(post.getSlug(), StandardCharsets.UTF_8)
        + "?k="
        + post.getUnlistedKey();
  }

  private static boolean unlisted(PostEntity post) {
    return post.getAudience().keyed() && post.getUnlistedKey() != null;
  }
}
