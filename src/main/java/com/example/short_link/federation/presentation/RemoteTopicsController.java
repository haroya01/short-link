package com.example.short_link.federation.presentation;

import com.example.short_link.federation.application.trends.RemoteTopicFeed;
import com.example.short_link.federation.application.trends.TopicAsk;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/federation/topics")
@RequiredArgsConstructor
public class RemoteTopicsController {

  private final RemoteTopicFeed feed;

  @GetMapping
  public RemoteTopicFeed.TopicList topics(
      @AuthenticationPrincipal Long viewerId,
      @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String languages,
      @RequestParam(defaultValue = "20") int limit) {
    return feed.topics(viewerId, languages, limit);
  }

  @GetMapping("/posts")
  public RemoteTopicFeed.TopicPosts posts(
      @AuthenticationPrincipal Long viewerId,
      @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String languages,
      @RequestParam(required = false) String tag,
      @RequestParam(required = false) String url,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor) {
    return feed.posts(viewerId, languages, TopicAsk.of(tag, url), cursor, limit);
  }
}
