package com.example.short_link.profile.domain.contact;

import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Prices are free-form display strings; transactions happen at the CTA URL. Legacy {@code image}
 * input becomes a one-element {@code images} list with centered focal points; writes emit only
 * {@code images}.
 */
public final class ProductCardCarousel {

  public static final int MAX_ITEMS = 8;

  public static final int MAX_IMAGES_PER_ITEM = 5;

  private static final int TITLE_MAX = 60;
  private static final int NAME_MAX = 60;
  private static final int IMAGE_MAX = 512;
  private static final int PRICE_MAX = 30;
  private static final int DESC_MAX = 200;
  private static final int CTA_LABEL_MAX = 30;
  private static final int CTA_URL_MAX = 512;

  /**
   * Unknown badges become null so a frontend deployed ahead of the backend does not reject writes.
   */
  private static final Set<String> BADGE_IDS = Set.of("NEW", "BEST", "LIMITED", "SOLD_OUT");

  /** Unknown layouts fall back to carousel for compatibility with older records and clients. */
  private static final Set<String> LAYOUT_IDS = Set.of("carousel", "grid");

  private static final int FOCAL_DEFAULT = 50;
  private static final int FOCAL_MIN = 0;
  private static final int FOCAL_MAX = 100;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ProductCardCarousel() {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Payload(String title, String layout, List<Item> items) {}

  /**
   * Accepts legacy {@code image} on input and ignores unknown fields for frontend/backend rollout
   * compatibility.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Item(
      String name,
      List<ImageEntry> images,
      String image,
      String price,
      String originalPrice,
      String badge,
      String description,
      String ctaLabel,
      String ctaUrl) {}

  /**
   * Focal coordinates are percentages from 0 to 100 matching CSS {@code object-position}; missing
   * values use the center (50/50).
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ImageEntry(String url, Integer focalX, Integer focalY) {}

  private record ItemOut(
      String name,
      List<ImageEntry> images,
      String price,
      String originalPrice,
      String badge,
      String description,
      String ctaLabel,
      String ctaUrl) {}

  private record PayloadOut(String title, String layout, List<ItemOut> items) {}

  public static String normalize(String raw) {
    Payload parsed = readPayload(raw);
    PayloadOut normalized = normalizePayload(parsed);
    return writePayload(normalized);
  }

  private static Payload readPayload(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "product card: config required");
    }
    try {
      return MAPPER.readValue(raw.trim(), Payload.class);
    } catch (JsonProcessingException ex) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "product card: malformed json");
    }
  }

  private static String writePayload(PayloadOut payload) {
    try {
      return MAPPER.writeValueAsString(payload);
    } catch (JsonProcessingException ex) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "product card: serialization failed");
    }
  }

  private static PayloadOut normalizePayload(Payload parsed) {
    if (parsed == null || parsed.items == null || parsed.items.isEmpty()) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "product card: at least 1 item required");
    }
    if (parsed.items.size() > MAX_ITEMS) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "product card: max " + MAX_ITEMS + " items");
    }
    String title = trimTo(parsed.title, TITLE_MAX);
    String layout = normalizeLayout(parsed.layout);
    List<ItemOut> out = new ArrayList<>(parsed.items.size());
    for (Item item : parsed.items) {
      if (item == null) continue;
      out.add(normalizeItem(item));
    }
    if (out.isEmpty()) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "product card: at least 1 item required");
    }
    return new PayloadOut(title, layout, out);
  }

  private static ItemOut normalizeItem(Item item) {
    String name = trimTo(item.name, NAME_MAX);
    if (name == null || name.isEmpty()) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "product card: each item needs a name");
    }
    List<ImageEntry> images = normalizeImages(readCompatibleImages(item));
    String ctaUrl = trimTo(item.ctaUrl, CTA_URL_MAX);
    if (ctaUrl != null) validateHttpUrl(ctaUrl, "cta");
    return new ItemOut(
        name,
        images,
        trimTo(item.price, PRICE_MAX),
        trimTo(item.originalPrice, PRICE_MAX),
        normalizeBadge(item.badge),
        trimTo(item.description, DESC_MAX),
        trimTo(item.ctaLabel, CTA_LABEL_MAX),
        ctaUrl);
  }

  /**
   * 비어 있지 않은 {@code images}가 우선이다. 없거나 빈 배열이면 구형 {@code image}를 읽는다. 선택한 배열의 모든 항목이 나중에 제외되더라도 구형
   * 이미지로 되돌아가지 않는다.
   */
  private static List<ImageEntry> readCompatibleImages(Item item) {
    if (item.images != null && !item.images.isEmpty()) return item.images;
    if (item.image == null) return List.of();
    return List.of(new ImageEntry(item.image, FOCAL_DEFAULT, FOCAL_DEFAULT));
  }

  private static List<ImageEntry> normalizeImages(List<ImageEntry> images) {
    if (images.size() > MAX_IMAGES_PER_ITEM) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME,
          "product card: max " + MAX_IMAGES_PER_ITEM + " images per item");
    }
    List<ImageEntry> out = new ArrayList<>();
    for (ImageEntry entry : images) {
      if (entry == null) continue;
      String url = trimTo(entry.url, IMAGE_MAX);
      if (url == null) continue;
      validateHttpUrl(url, "image");
      out.add(new ImageEntry(url, clampFocal(entry.focalX), clampFocal(entry.focalY)));
    }
    return out;
  }

  private static String normalizeBadge(String raw) {
    if (raw == null) return null;
    String t = raw.trim();
    if (t.isEmpty()) return null;
    return BADGE_IDS.contains(t) ? t : null;
  }

  private static String normalizeLayout(String raw) {
    if (raw == null) return "carousel";
    String t = raw.trim();
    if (t.isEmpty()) return "carousel";
    return LAYOUT_IDS.contains(t) ? t : "carousel";
  }

  private static Integer clampFocal(Integer raw) {
    if (raw == null) return FOCAL_DEFAULT;
    int v = raw;
    if (v < FOCAL_MIN) return FOCAL_MIN;
    if (v > FOCAL_MAX) return FOCAL_MAX;
    return v;
  }

  private static String trimTo(String s, int max) {
    if (s == null) return null;
    String t = s.trim();
    if (t.isEmpty()) return null;
    return t.length() <= max ? t : t.substring(0, max);
  }

  private static void validateHttpUrl(String url, String field) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException ex) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "product card: " + field + " url malformed");
    }
    String scheme = uri.getScheme();
    if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "product card: " + field + " url must be http(s)");
    }
    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "product card: " + field + " url missing host");
    }
  }
}
