package com.example.short_link.profile.domain.contact;

import com.example.short_link.profile.exception.InvalidUsernameException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * PRODUCT_CARD block payload — the vertical-agnostic "row of selling cards" used by bakery menus,
 * real-estate listings, salon services, course catalogs, etc. Stored as JSON in {@code
 * profile_block.content}; frontend renders as a horizontal scroll-snap carousel with one card per
 * item.
 *
 * <p>Each item carries minimal commerce metadata: a name (the only required field), a list of
 * images each with its own focal point (cropping uses {@code object-cover} on the public card, so
 * the focal point lets the seller move the visible center off the geometric center — e.g. push a
 * cake's top edge up so the icing isn't trimmed), a free-form price string ("45,000원" / "$25"), a
 * short description, and an optional CTA button (label + URL — typically a KakaoTalk channel /
 * naver booking / Stripe pay link). We intentionally don't model price as a number or accept
 * payment — kurl is the presentation surface; the actual transaction lives at the CTA URL.
 *
 * <p>Backward compat: the prior schema had a single {@code image: String} per item. Old payloads
 * are accepted on read — the legacy field is wrapped into a 1-element {@code images} list with the
 * default focal point. New writes always emit the {@code images} list, never the legacy field.
 */
public final class ProductCardCarousel {

  /** Cap chosen so the rendered carousel stays comfortable to swipe on mobile. */
  public static final int MAX_ITEMS = 8;

  /**
   * Per-item image cap. Five images covers the gallery patterns we've seen (hero + 4 supporting
   * shots) while keeping the public card's thumbnail strip a single tap-row, not a second carousel.
   */
  public static final int MAX_IMAGES_PER_ITEM = 5;

  private static final int TITLE_MAX = 60;
  private static final int NAME_MAX = 60;
  private static final int IMAGE_MAX = 512;
  private static final int PRICE_MAX = 30;
  private static final int DESC_MAX = 200;
  private static final int CTA_LABEL_MAX = 30;
  private static final int CTA_URL_MAX = 512;

  /**
   * Whitelisted badge ids. Frontend translates each id to a locale-specific label and renders a
   * fixed chip color so the visual language stays consistent (NEW → blue, BEST → amber, LIMITED →
   * red, SOLD_OUT → grayscale overlay). An unknown value is dropped to null rather than rejected so
   * a frontend ahead of a backend deploy doesn't 400 every write.
   */
  private static final java.util.Set<String> BADGE_IDS =
      java.util.Set.of("NEW", "BEST", "LIMITED", "SOLD_OUT");

  /**
   * Whitelisted layout ids. Controls how the block renders on the public profile — {@code carousel}
   * (default, horizontal swipe) or {@code grid} (2-column vertical, denser browsing). Sellers with
   * 5-8 items often benefit from {@code grid} because all items are visible without swiping.
   * Unknown values silently fall back to carousel so old clients work.
   */
  private static final java.util.Set<String> LAYOUT_IDS = java.util.Set.of("carousel", "grid");

  private static final int FOCAL_DEFAULT = 50;
  private static final int FOCAL_MIN = 0;
  private static final int FOCAL_MAX = 100;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ProductCardCarousel() {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Payload(String title, String layout, List<Item> items) {}

  /**
   * Legacy {@code image} field accepted on input but dropped on output — Jackson's {@link
   * JsonIgnoreProperties} would normally trip on unknown fields. We ignore other unknowns too so a
   * forward-compatible field added by the frontend ahead of a backend deploy doesn't 400 every
   * write.
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
   * Focal point is stored as a percentage 0..100 on each axis, matching the CSS {@code
   * object-position} value the frontend applies. Default 50/50 = visual center, the same crop
   * behavior as before focal points existed.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ImageEntry(String url, Integer focalX, Integer focalY) {}

  /** Normalized output record — what we serialize back. Has no legacy {@code image} field. */
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
    if (raw == null || raw.isBlank()) {
      throw new InvalidUsernameException("product card: config required");
    }
    Payload parsed;
    try {
      parsed = MAPPER.readValue(raw.trim(), Payload.class);
    } catch (JsonProcessingException ex) {
      throw new InvalidUsernameException("product card: malformed json");
    }
    if (parsed == null || parsed.items == null || parsed.items.isEmpty()) {
      throw new InvalidUsernameException("product card: at least 1 item required");
    }
    if (parsed.items.size() > MAX_ITEMS) {
      throw new InvalidUsernameException("product card: max " + MAX_ITEMS + " items");
    }
    String title = trimTo(parsed.title, TITLE_MAX);
    String layout = normalizeLayout(parsed.layout);
    List<ItemOut> out = new ArrayList<>(parsed.items.size());
    for (Item item : parsed.items) {
      if (item == null) continue;
      String name = trimTo(item.name, NAME_MAX);
      if (name == null || name.isEmpty()) {
        throw new InvalidUsernameException("product card: each item needs a name");
      }
      List<ImageEntry> images = normalizeImages(item.images, item.image);
      String ctaUrl = trimTo(item.ctaUrl, CTA_URL_MAX);
      if (ctaUrl != null) validateHttpUrl(ctaUrl, "cta");
      out.add(
          new ItemOut(
              name,
              images,
              trimTo(item.price, PRICE_MAX),
              trimTo(item.originalPrice, PRICE_MAX),
              normalizeBadge(item.badge),
              trimTo(item.description, DESC_MAX),
              trimTo(item.ctaLabel, CTA_LABEL_MAX),
              ctaUrl));
    }
    if (out.isEmpty()) {
      throw new InvalidUsernameException("product card: at least 1 item required");
    }
    try {
      return MAPPER.writeValueAsString(new PayloadOut(title, layout, out));
    } catch (JsonProcessingException ex) {
      throw new InvalidUsernameException("product card: serialization failed");
    }
  }

  /**
   * Resolves the final image list for a single item. Order of precedence: explicit {@code images}
   * array wins; if absent, the legacy single {@code image} string is wrapped into a one-element
   * list with default focal point. Either way each entry is URL-validated and the cap is enforced.
   */
  private static List<ImageEntry> normalizeImages(List<ImageEntry> images, String legacyImage) {
    List<ImageEntry> out = new ArrayList<>();
    if (images != null && !images.isEmpty()) {
      if (images.size() > MAX_IMAGES_PER_ITEM) {
        throw new InvalidUsernameException(
            "product card: max " + MAX_IMAGES_PER_ITEM + " images per item");
      }
      for (ImageEntry entry : images) {
        if (entry == null) continue;
        String url = trimTo(entry.url, IMAGE_MAX);
        if (url == null) continue;
        validateHttpUrl(url, "image");
        out.add(new ImageEntry(url, clampFocal(entry.focalX), clampFocal(entry.focalY)));
      }
    } else if (legacyImage != null) {
      String url = trimTo(legacyImage, IMAGE_MAX);
      if (url != null) {
        validateHttpUrl(url, "image");
        out.add(new ImageEntry(url, FOCAL_DEFAULT, FOCAL_DEFAULT));
      }
    }
    return out;
  }

  /**
   * Coerces an incoming badge string to a whitelisted id or null. Unknown values silently drop to
   * null — see {@link #BADGE_IDS} for why we don't reject them.
   */
  private static String normalizeBadge(String raw) {
    if (raw == null) return null;
    String t = raw.trim();
    if (t.isEmpty()) return null;
    return BADGE_IDS.contains(t) ? t : null;
  }

  /**
   * Coerces an incoming layout string to {@code "carousel"} (default) or {@code "grid"}. Null,
   * blank, or unknown values fall back to {@code "carousel"} — keeps records that predate this
   * field rendering exactly the same.
   */
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
      throw new InvalidUsernameException("product card: " + field + " url malformed");
    }
    String scheme = uri.getScheme();
    if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
      throw new InvalidUsernameException("product card: " + field + " url must be http(s)");
    }
    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw new InvalidUsernameException("product card: " + field + " url missing host");
    }
  }
}
