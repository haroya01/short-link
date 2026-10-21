package com.example.short_link.post.application.access;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
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
  private final UserRepository users;

  public PostUnlistedUrls(
      @Value("${short-link.blog-base-url}") String blogBaseUrl, UserRepository users) {
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
    return users.findById(userId).map(UserEntity::getUsername).orElse(null);
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
