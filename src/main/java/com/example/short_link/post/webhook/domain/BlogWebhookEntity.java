package com.example.short_link.post.webhook.domain;

import com.example.short_link.common.event.BlogInteractionType;
import com.example.short_link.common.jpa.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 개별 글이 아닌 작성자의 모든 글에 적용되는 알림 훅이다. 반복 실패 시 자동 비활성화한다. */
@Entity
@Table(name = "blog_webhook")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlogWebhookEntity extends BaseCreatedEntity {

  private static final int AUTO_DISABLE_FAILURE_THRESHOLD = 5;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = 2048)
  private String url;

  @Column(nullable = false, length = 255)
  private String secret;

  @Column(length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private BlogWebhookFormat format = BlogWebhookFormat.GENERIC;

  /** CSV of {@link BlogInteractionType} names this hook fires on (e.g. "LIKE,COMMENT,FOLLOW"). */
  @Column(name = "events", nullable = false, length = 255)
  private String events;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(name = "last_called_at")
  private Instant lastCalledAt;

  @Column(name = "last_status_code")
  private Integer lastStatusCode;

  @Column(name = "last_error", length = 500)
  private String lastError;

  @Column(name = "consecutive_failures", nullable = false)
  private int consecutiveFailures = 0;

  @Column(name = "auto_disabled_reason", length = 200)
  private String autoDisabledReason;

  public BlogWebhookEntity(
      Long userId,
      String url,
      String secret,
      String name,
      BlogWebhookFormat format,
      Set<BlogInteractionType> events) {
    this.userId = userId;
    this.url = url;
    this.secret = secret;
    this.name = name;
    this.format = format;
    this.events = encode(events);
  }

  public EnumSet<BlogInteractionType> events() {
    EnumSet<BlogInteractionType> set = EnumSet.noneOf(BlogInteractionType.class);
    if (events == null || events.isBlank()) {
      return set;
    }
    for (String token : events.split(",")) {
      String name = token.trim();
      if (name.isEmpty()) {
        continue;
      }
      try {
        set.add(BlogInteractionType.valueOf(name));
      } catch (IllegalArgumentException ignored) {
        // Drop unknown tokens so an old/renamed event name never breaks delivery for the rest.
      }
    }
    return set;
  }

  public boolean firesOn(BlogInteractionType type) {
    return enabled && events().contains(type);
  }

  /** GENERIC hooks carry an HMAC signature; chat formats don't support one. */
  public boolean signed() {
    return format == BlogWebhookFormat.GENERIC;
  }

  public void update(String name, Set<BlogInteractionType> events, Boolean enabled) {
    String updatedEvents = events != null && !events.isEmpty() ? encode(events) : this.events;
    if (name != null) {
      String trimmed = name.trim();
      this.name = trimmed.isEmpty() ? null : trimmed;
    }
    this.events = updatedEvents;
    if (enabled != null) {
      if (enabled) {
        enable();
      } else {
        this.enabled = false;
      }
    }
  }

  public void enable() {
    this.enabled = true;
    this.consecutiveFailures = 0;
    this.autoDisabledReason = null;
  }

  public void recordSuccess(int status) {
    this.lastCalledAt = Instant.now();
    this.lastStatusCode = status;
    this.lastError = null;
    this.consecutiveFailures = 0;
  }

  public void recordFailure(Integer status, String error) {
    this.lastCalledAt = Instant.now();
    this.lastStatusCode = status;
    this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
    this.consecutiveFailures += 1;
    if (this.consecutiveFailures >= AUTO_DISABLE_FAILURE_THRESHOLD && this.enabled) {
      this.enabled = false;
      this.autoDisabledReason =
          "auto-disabled after "
              + AUTO_DISABLE_FAILURE_THRESHOLD
              + " consecutive failures: "
              + (this.lastError == null
                  ? "(no detail)"
                  : this.lastError.substring(0, Math.min(this.lastError.length(), 150)));
    }
  }

  private static String encode(Set<BlogInteractionType> events) {
    if (events == null || events.isEmpty()) {
      return Arrays.stream(BlogInteractionType.values())
          .map(Enum::name)
          .collect(Collectors.joining(","));
    }
    if (events.stream().anyMatch(java.util.Objects::isNull)) {
      throw new IllegalArgumentException("events must not contain null");
    }
    return events.stream().map(Enum::name).collect(Collectors.joining(","));
  }
}
