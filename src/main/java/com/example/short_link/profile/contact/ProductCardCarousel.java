package com.example.short_link.profile.contact;

import com.example.short_link.profile.application.InvalidUsernameException;
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
 * <p>Each item carries minimal commerce metadata: a name (the only required field), an optional
 * image, a free-form price string ("45,000원" / "$25" / "from ₩20k"), a short description, and an
 * optional CTA button (label + URL — typically a KakaoTalk channel / naver booking / Stripe pay
 * link). We intentionally don't model price as a number or accept payment — kurl is the
 * presentation surface; the actual transaction lives at the CTA URL.
 */
public final class ProductCardCarousel {

  /** Cap chosen so the rendered carousel stays comfortable to swipe on mobile. */
  public static final int MAX_ITEMS = 8;

  private static final int TITLE_MAX = 60;
  private static final int NAME_MAX = 60;
  private static final int IMAGE_MAX = 512;
  private static final int PRICE_MAX = 30;
  private static final int DESC_MAX = 200;
  private static final int CTA_LABEL_MAX = 30;
  private static final int CTA_URL_MAX = 512;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ProductCardCarousel() {}

  public record Payload(String title, List<Item> items) {}

  public record Item(
      String name,
      String image,
      String price,
      String description,
      String ctaLabel,
      String ctaUrl) {}

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
    List<Item> out = new ArrayList<>(parsed.items.size());
    for (Item item : parsed.items) {
      if (item == null) continue;
      String name = trimTo(item.name, NAME_MAX);
      if (name == null || name.isEmpty()) {
        throw new InvalidUsernameException("product card: each item needs a name");
      }
      String image = trimTo(item.image, IMAGE_MAX);
      if (image != null) validateHttpUrl(image, "image");
      String ctaUrl = trimTo(item.ctaUrl, CTA_URL_MAX);
      if (ctaUrl != null) validateHttpUrl(ctaUrl, "cta");
      out.add(
          new Item(
              name,
              image,
              trimTo(item.price, PRICE_MAX),
              trimTo(item.description, DESC_MAX),
              trimTo(item.ctaLabel, CTA_LABEL_MAX),
              ctaUrl));
    }
    if (out.isEmpty()) {
      throw new InvalidUsernameException("product card: at least 1 item required");
    }
    try {
      return MAPPER.writeValueAsString(new Payload(title, out));
    } catch (JsonProcessingException ex) {
      throw new InvalidUsernameException("product card: serialization failed");
    }
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
