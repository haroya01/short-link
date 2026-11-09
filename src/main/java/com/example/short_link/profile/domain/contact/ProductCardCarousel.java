package com.example.short_link.profile.domain.contact;

import static com.example.short_link.profile.domain.contact.BlockConfig.trimTo;

import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Prices are free-form display strings; transactions happen at the CTA URL. Legacy image input
// becomes a one-element images list with centered focal points; writes emit only images.
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

  // Unknown badges become null so a frontend deployed ahead of the backend does not reject writes.
  private static final Set<String> BADGE_IDS = Set.of("NEW", "BEST", "LIMITED", "SOLD_OUT");

  // Unknown layouts fall back to carousel for compatibility with older records and clients.
  private static final Set<String> LAYOUT_IDS = Set.of("carousel", "grid");

  private static final int FOCAL_DEFAULT = 50;
  private static final int FOCAL_MIN = 0;
  private static final int FOCAL_MAX = 100;

  private ProductCardCarousel() {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Payload(String title, String layout, List<Item> items) {}

  // Accepts legacy image on input and ignores unknown fields for frontend/backend rollout
  // compatibility.
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

  public static List<String> buyUrls(String normalized) {
    List<Item> items = readPayload(normalized).items();
    return items == null
        ? List.of()
        : items.stream().map(Item::ctaUrl).filter(url -> url != null && !url.isBlank()).toList();
  }

  public static String normalize(String raw) {
    Payload parsed = readPayload(raw);
    PayloadOut normalized = normalizePayload(parsed);
    return writePayload(normalized);
  }

  private static Payload readPayload(String raw) {
    return BlockConfig.read(raw, Payload.class, "product card");
  }

  private static String writePayload(PayloadOut payload) {
    return BlockConfig.write(payload, "product card");
  }

  private static PayloadOut normalizePayload(Payload parsed) {
    requireItems(parsed);
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

  private static void requireItems(Payload parsed) {
    if (parsed == null || parsed.items == null || parsed.items.isEmpty()) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "product card: at least 1 item required");
    }
    if (parsed.items.size() > MAX_ITEMS) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "product card: max " + MAX_ITEMS + " items");
    }
  }

  private static ItemOut normalizeItem(Item item) {
    String name = trimTo(item.name, NAME_MAX);
    if (name == null || name.isEmpty()) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "product card: each item needs a name");
    }
    List<ImageEntry> images = normalizeImages(readCompatibleImages(item));
    String ctaUrl = trimTo(item.ctaUrl, CTA_URL_MAX);
    if (ctaUrl != null) BlockConfig.requireHttpUrl(ctaUrl, "product card: cta url");
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

  // 비어 있지 않은 images가 우선이다. 없거나 빈 배열이면 구형 image를 읽는다. 선택한 배열의 모든 항목이 나중에 제외되더라도 구형 이미지로 되돌아가지 않는다.
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
      BlockConfig.requireHttpUrl(url, "product card: image url");
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
}
