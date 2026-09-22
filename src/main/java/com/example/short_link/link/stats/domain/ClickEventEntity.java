package com.example.short_link.link.stats.domain;

import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.repository.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "click_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClickEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "link_id", nullable = false)
  private Long linkId;

  public LinkId linkId() {
    return linkId == null ? null : new LinkId(linkId);
  }

  @Column(name = "clicked_at", nullable = false, updatable = false)
  // MySQL TIMESTAMP uses the connection time zone; forcing a UTC calendar would shift the epoch.
  // The column holds whole seconds and MySQL rounds fractions, so a click at x.5s or later would be
  // stored in the next second: after a read in that same second, or in the next hour or day.
  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  private Instant clickedAt;

  @Column(columnDefinition = "TEXT")
  private String referrer;

  @Column(name = "referrer_host", length = 255)
  private String referrerHost;

  @Column(name = "user_agent", columnDefinition = "TEXT")
  private String userAgent;

  @Column(name = "client_ip", length = 45)
  private String clientIp;

  @Column(name = "utm_source", length = 255)
  private String utmSource;

  @Column(name = "utm_medium", length = 255)
  private String utmMedium;

  @Column(name = "utm_campaign", length = 255)
  private String utmCampaign;

  @Column(name = "utm_term", length = 255)
  private String utmTerm;

  @Column(name = "utm_content", length = 255)
  private String utmContent;

  @Column(name = "device_class", length = 32)
  private String deviceClass;

  @Column(name = "os_name", length = 64)
  private String osName;

  @Column(name = "browser_name", length = 64)
  private String browserName;

  @Column(name = "is_bot", nullable = false)
  private boolean bot;

  @Column(name = "bot_name", length = 64)
  private String botName;

  @Column(name = "country_code", length = 2)
  private String countryCode;

  @Column(name = "region_name", length = 128)
  private String regionName;

  @Column(name = "city_name", length = 128)
  private String cityName;

  @Column(length = 8)
  private String language;

  @Column(name = "visitor_hash", length = 64)
  private String visitorHash;

  /** {@code ?src=} attributes traffic when the referrer header is missing. */
  @Column(name = "source_channel", length = 40)
  private String sourceChannel;

  /** Which A/B variant ({@link LinkDestinationEntity}) served this click — null if no variants. */
  @Column(name = "destination_id")
  private Long destinationId;

  /** Set from {@code ?post=} for the embedding blog post; null for other traffic. */
  @Column(name = "post_id")
  private Long postId;

  /** Autonomous System Number for the visitor's IP — null when ASN db is unavailable. */
  @Column(name = "asn")
  private Integer asn;

  @Column(name = "asn_org", length = 200)
  private String asnOrg;

  /**
   * In-app browser derived from the UA; null for ordinary browsers and independent of {@link #bot}.
   */
  @Column(name = "client_app", length = 32)
  private String clientApp;

  /**
   * {@code Sec-Fetch-Site}: {@code none} for direct navigation, {@code cross-site} for followed
   * links, null when absent.
   */
  @Column(name = "fetch_site", length = 16)
  private String fetchSite;

  @Builder
  public ClickEventEntity(
      LinkId linkId,
      Instant clickedAt,
      String referrer,
      String referrerHost,
      String userAgent,
      String clientIp,
      String utmSource,
      String utmMedium,
      String utmCampaign,
      String utmTerm,
      String utmContent,
      String deviceClass,
      String osName,
      String browserName,
      boolean bot,
      String botName,
      String countryCode,
      String regionName,
      String cityName,
      String language,
      String visitorHash,
      String sourceChannel,
      Long destinationId,
      Long postId,
      Integer asn,
      String asnOrg,
      String clientApp,
      String fetchSite) {
    this.linkId = linkId == null ? null : linkId.value();
    this.clickedAt = clickedAt == null ? null : clickedAt.truncatedTo(ChronoUnit.SECONDS);
    this.referrer = referrer;
    this.referrerHost = referrerHost;
    this.userAgent = userAgent;
    this.clientIp = clientIp;
    this.utmSource = utmSource;
    this.utmMedium = utmMedium;
    this.utmCampaign = utmCampaign;
    this.utmTerm = utmTerm;
    this.utmContent = utmContent;
    this.deviceClass = deviceClass;
    this.osName = osName;
    this.browserName = browserName;
    this.bot = bot;
    this.botName = botName;
    this.countryCode = countryCode;
    this.regionName = regionName;
    this.cityName = cityName;
    this.language = language;
    this.visitorHash = visitorHash;
    this.sourceChannel = sourceChannel;
    this.destinationId = destinationId;
    this.postId = postId;
    this.asn = asn;
    this.asnOrg = asnOrg;
    this.clientApp = clientApp;
    this.fetchSite = fetchSite;
  }

  @PrePersist
  void prePersist() {
    if (this.clickedAt == null) {
      this.clickedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }
  }
}
