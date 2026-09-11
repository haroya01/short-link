package com.example.short_link.link.destination.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.repository.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 국가·기기·OS 조건과 가중치가 있는 목적지 변형이다. 국가는 ISO 3166 alpha-2 대문자 코드다. 선택 가능한 변형이 없으면 링크의 원본 URL을 사용한다. */
@Entity
@Table(name = "link_destination")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkDestinationEntity extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "link_id", nullable = false)
  private Long linkId;

  public LinkId linkId() {
    return linkId == null ? null : new LinkId(linkId);
  }

  @Column(nullable = false, length = 2048)
  private String url;

  @Column(nullable = false)
  private int weight = 1;

  @Column(length = 40)
  private String label;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(name = "country_code", length = 2)
  private String countryCode;

  /** mobile/tablet/desktop. 국가·OS 조건과 함께 적용하며 null은 제한 없음이다. */
  @Column(name = "device_class", length = 16)
  private String deviceClass;

  /** ios/android/windows/macos/linux. null은 제한 없음이다. */
  @Column(length = 16)
  private String os;

  public LinkDestinationEntity(
      LinkId linkId, String url, int weight, String label, String countryCode) {
    this(linkId, url, weight, label, countryCode, null, null);
  }

  public LinkDestinationEntity(
      LinkId linkId,
      String url,
      int weight,
      String label,
      String countryCode,
      String deviceClass,
      String os) {
    this.linkId = linkId == null ? null : linkId.value();
    this.url = url;
    this.weight = Math.max(1, weight);
    this.label = label;
    this.enabled = true;
    this.countryCode = normalizeCountry(countryCode);
    this.deviceClass = normalizeDeviceClass(deviceClass);
    this.os = normalizeOs(os);
  }

  public void update(
      String url, Integer weight, String label, Boolean enabled, String countryCode) {
    update(url, weight, label, enabled, countryCode, null, null);
  }

  public void update(
      String url,
      Integer weight,
      String label,
      Boolean enabled,
      String countryCode,
      String deviceClass,
      String os) {
    if (url != null) this.url = url;
    if (weight != null) this.weight = Math.max(1, weight);
    if (label != null) this.label = label;
    if (enabled != null) this.enabled = enabled;
    if (countryCode != null) this.countryCode = normalizeCountry(countryCode);
    if (deviceClass != null) this.deviceClass = normalizeDeviceClass(deviceClass);
    if (os != null) this.os = normalizeOs(os);
  }

  private static String normalizeCountry(String input) {
    if (input == null) return null;
    String trimmed = input.trim();
    if (trimmed.isEmpty()) return null;
    if (trimmed.length() != 2) {
      throw new IllegalArgumentException("countryCode must be ISO-3166 alpha-2 (2 chars)");
    }
    return trimmed.toUpperCase(Locale.ROOT);
  }

  private static String normalizeDeviceClass(String input) {
    if (input == null) return null;
    String v = input.trim().toLowerCase(Locale.ROOT);
    if (v.isEmpty()) return null;
    return switch (v) {
      case "mobile", "tablet", "desktop" -> v;
      default ->
          throw new IllegalArgumentException("deviceClass must be mobile, tablet, or desktop");
    };
  }

  private static String normalizeOs(String input) {
    if (input == null) return null;
    String v = input.trim().toLowerCase(Locale.ROOT);
    if (v.isEmpty()) return null;
    return switch (v) {
      case "ios", "android", "windows", "macos", "linux" -> v;
      default ->
          throw new IllegalArgumentException("os must be ios, android, windows, macos, or linux");
    };
  }
}
