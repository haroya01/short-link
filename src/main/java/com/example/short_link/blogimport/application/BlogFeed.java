package com.example.short_link.blogimport.application;

import com.example.short_link.blogimport.domain.BlogAddress;
import com.example.short_link.blogimport.domain.FeedValidators;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

public record BlogFeed(
    BlogAddress address,
    String title,
    String description,
    String languageTag,
    boolean fullContent,
    List<Post> posts,
    FeedValidators validators) {

  public BlogFeed {
    posts = List.copyOf(posts);
    validators = validators == null ? FeedValidators.NONE : validators;
  }

  public BlogFeed(
      BlogAddress address,
      String title,
      String description,
      String languageTag,
      boolean fullContent,
      List<Post> posts) {
    this(address, title, description, languageTag, fullContent, posts, FeedValidators.NONE);
  }

  public boolean describes(String code) {
    return code != null
        && description.toUpperCase(Locale.ROOT).contains(code.toUpperCase(Locale.ROOT));
  }

  public record Post(
      String url, String title, Instant publishedAt, String html, List<String> tags) {

    public Post {
      tags = List.copyOf(tags);
    }
  }
}
