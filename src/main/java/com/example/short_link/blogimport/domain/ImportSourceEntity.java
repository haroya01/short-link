package com.example.short_link.blogimport.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "blog_import_source")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImportSourceEntity extends BaseTimeEntity {

  public static final int MAX_TITLE_LENGTH = 200;
  public static final Duration FETCH_INTERVAL = Duration.ofMinutes(30);
  public static final Duration MAX_BACKOFF = Duration.ofHours(24);

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ImportPlatform platform;

  @Column(name = "blog_key", nullable = false, length = 191)
  private String blogKey;

  // Holds blogKey while this member's ownership stands; unique, so a blog has one verified owner.
  @Column(name = "claim_key", length = 191)
  private String claimKey;

  @Column(name = "blog_url", nullable = false, length = 512)
  private String blogUrl;

  @Column(name = "feed_url", nullable = false, length = 512)
  private String feedUrl;

  @Column(name = "blog_title", nullable = false, length = MAX_TITLE_LENGTH)
  private String blogTitle;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private ImportSourceStatus status;

  @Column(name = "verification_code", length = 16)
  private String verificationCode;

  @Column(name = "verified_at")
  private Instant verifiedAt;

  @Column(name = "full_content", nullable = false)
  private boolean fullContent;

  @Column(name = "announce_new_posts", nullable = false)
  private boolean announceNewPosts = true;

  @Column(name = "show_in_discover", nullable = false)
  private boolean showInDiscover = true;

  @Column(name = "last_synced_at")
  private Instant lastSyncedAt;

  @Column(name = "last_fetched_at")
  private Instant lastFetchedAt;

  @Column(name = "next_fetch_at")
  private Instant nextFetchAt;

  @Column(name = "fetch_failures", nullable = false)
  private int fetchFailures;

  @Column(name = "feed_etag", length = 255)
  private String feedEtag;

  @Column(name = "feed_last_modified", length = 64)
  private String feedLastModified;

  @Column(name = "last_error", length = 64)
  private String lastError;

  @Column(name = "progress_total")
  private Integer progressTotal;

  @Column(name = "progress_done")
  private Integer progressDone;

  @Column(name = "progress_failed")
  private Integer progressFailed;

  public ImportSourceEntity(
      Long userId, BlogAddress address, String blogTitle, boolean fullContent, String code) {
    this.userId = userId;
    this.platform = address.platform();
    this.blogKey = address.blogKey();
    this.blogUrl = address.blogUrl();
    this.feedUrl = address.feedUrl();
    this.blogTitle = clip(blogTitle);
    this.fullContent = fullContent;
    this.status = ImportSourceStatus.PENDING_VERIFICATION;
    this.verificationCode = code;
  }

  public boolean isOwnedBy(Long userId) {
    return this.userId.equals(userId);
  }

  public BlogAddress address() {
    return new BlogAddress(platform, blogKey.substring(blogKey.indexOf(':') + 1), blogUrl, feedUrl);
  }

  public boolean isVerified() {
    return status.verified();
  }

  public boolean isDisconnected() {
    return status == ImportSourceStatus.DISCONNECTED;
  }

  public boolean awaitsVerification() {
    return status == ImportSourceStatus.PENDING_VERIFICATION;
  }

  public void reconnect(String code, String blogTitle, boolean fullContent) {
    this.status = ImportSourceStatus.PENDING_VERIFICATION;
    this.verificationCode = code;
    this.claimKey = null;
    this.blogTitle = clip(blogTitle);
    this.fullContent = fullContent;
    this.lastError = null;
  }

  public void verify(Instant at, int pending) {
    this.status = ImportSourceStatus.IMPORTING;
    this.claimKey = blogKey;
    this.verificationCode = null;
    this.verifiedAt = at;
    this.lastError = null;
    fetched(at);
    startRun(pending);
  }

  public FeedValidators validators() {
    return new FeedValidators(feedEtag, feedLastModified);
  }

  public void rememberValidators(FeedValidators validators) {
    FeedValidators kept = validators == null ? FeedValidators.NONE : validators;
    this.feedEtag = kept.etag();
    this.feedLastModified = kept.lastModified();
  }

  public void fetched(Instant at) {
    this.lastFetchedAt = at;
    this.nextFetchAt = at.plus(FETCH_INTERVAL);
    this.fetchFailures = 0;
  }

  public void fetchFailed(Instant at) {
    this.lastFetchedAt = at;
    this.fetchFailures++;
    Duration wait = FETCH_INTERVAL.multipliedBy(1L << Math.min(fetchFailures, 6));
    this.nextFetchAt = at.plus(wait.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : wait);
  }

  public void refresh(String blogTitle, boolean fullContent) {
    this.blogTitle = clip(blogTitle);
    this.fullContent = fullContent;
  }

  public void startRun(int pending) {
    this.progressTotal = pending;
    this.progressDone = 0;
    this.progressFailed = 0;
  }

  public void recordProgress(int done, int failed) {
    this.progressDone = done;
    this.progressFailed = failed;
  }

  public void finishRun(Instant at) {
    if (!isVerified()) {
      return;
    }
    this.status = ImportSourceStatus.SYNCING;
    this.lastSyncedAt = at;
    this.lastError = null;
  }

  public void failRun(String errorCode) {
    if (!isVerified()) {
      return;
    }
    this.status = ImportSourceStatus.ERROR;
    this.lastError = errorCode;
  }

  public void disconnect() {
    this.status = ImportSourceStatus.DISCONNECTED;
    this.claimKey = null;
    this.verificationCode = null;
  }

  public void updateSettings(Boolean announceNewPosts, Boolean showInDiscover) {
    if (announceNewPosts != null) {
      this.announceNewPosts = announceNewPosts;
    }
    if (showInDiscover != null) {
      this.showInDiscover = showInDiscover;
    }
  }

  public boolean announces(Instant publishedAt) {
    return announceNewPosts
        && verifiedAt != null
        && publishedAt != null
        && publishedAt.isAfter(verifiedAt);
  }

  private static String clip(String title) {
    String value = title == null ? "" : title.strip();
    return value.length() > MAX_TITLE_LENGTH ? value.substring(0, MAX_TITLE_LENGTH) : value;
  }
}
