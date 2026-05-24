package com.example.short_link.profile.contact;

import com.example.short_link.profile.exception.InvalidUsernameException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Set;

/**
 * PLACE block payload — a single business / venue / 매장 promo card. Visitors see the storefront
 * photo (or a map fallback), name, address, hours, and a "길찾기" / phone / share / copy action row.
 * We deliberately scope this to one location per block — multi-location chains can stack PLACE
 * blocks or wait for a v3 chain-grouping feature.
 *
 * <p>Google Places handles the address / lat / lng / placeId resolution on the frontend; the
 * backend just persists the resolved fields and runs sanity validation (URL whitelisting,
 * coordinate ranges, category whitelist). No outbound HTTP calls — the API key lives on the client
 * (HTTP-referrer-restricted) and the Place Details / Static Map URLs are built from the stored
 * fields at render time.
 *
 * <p>Categories are a fixed whitelist matching the frontend icon set (cafe / bakery / restaurant /
 * retail / studio / gallery / popup / space). Anything else is collapsed to {@code null} so a
 * misspelled or future-added category from a forward-rolled-out frontend doesn't 400 the block.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Place(
    String name,
    String address,
    Double lat,
    Double lng,
    String placeId,
    String phone,
    String coverUrl,
    String category,
    String hoursText) {

  private static final int NAME_MAX = 80;
  private static final int ADDRESS_MAX = 200;
  private static final int PHONE_MAX = 30;
  private static final int COVER_URL_MAX = 512;
  private static final int CATEGORY_MAX = 30;
  private static final int HOURS_TEXT_MAX = 200;
  private static final int PLACE_ID_MAX = 255;

  /**
   * Whitelist of accepted category slugs. Anything outside this set is dropped on normalize so a
   * frontend rolled out with a new category before the backend doesn't error the entire save — the
   * block just renders with no category chip until both sides agree.
   */
  private static final Set<String> CATEGORIES =
      Set.of("cafe", "bakery", "restaurant", "retail", "studio", "gallery", "popup", "space");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static String normalize(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new InvalidUsernameException("place: config required");
    }
    Place parsed;
    try {
      parsed = MAPPER.readValue(raw.trim(), Place.class);
    } catch (JsonProcessingException ex) {
      throw new InvalidUsernameException("place: malformed json");
    }

    String name = trimTo(parsed.name, NAME_MAX);
    if (name == null) {
      throw new InvalidUsernameException("place: name required");
    }
    String address = trimTo(parsed.address, ADDRESS_MAX);
    if (address == null) {
      throw new InvalidUsernameException("place: address required");
    }
    if (parsed.lat == null || parsed.lng == null) {
      throw new InvalidUsernameException("place: lat/lng required");
    }
    if (parsed.lat < -90 || parsed.lat > 90) {
      throw new InvalidUsernameException("place: lat out of range");
    }
    if (parsed.lng < -180 || parsed.lng > 180) {
      throw new InvalidUsernameException("place: lng out of range");
    }

    String placeId = trimTo(parsed.placeId, PLACE_ID_MAX);
    String phone = trimTo(parsed.phone, PHONE_MAX);
    String coverUrl = trimTo(parsed.coverUrl, COVER_URL_MAX);
    if (coverUrl != null) {
      validateHttpUrl(coverUrl);
    }

    String categoryIn = trimTo(parsed.category, CATEGORY_MAX);
    String category = categoryIn != null && CATEGORIES.contains(categoryIn) ? categoryIn : null;

    String hoursText = trimTo(parsed.hoursText, HOURS_TEXT_MAX);

    Place out =
        new Place(
            name, address, parsed.lat, parsed.lng, placeId, phone, coverUrl, category, hoursText);
    try {
      return MAPPER.writeValueAsString(out);
    } catch (JsonProcessingException ex) {
      throw new InvalidUsernameException("place: serialization failed");
    }
  }

  private static String trimTo(String s, int max) {
    if (s == null) return null;
    String t = s.trim();
    if (t.isEmpty()) return null;
    return t.length() <= max ? t : t.substring(0, max);
  }

  private static void validateHttpUrl(String url) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException ex) {
      throw new InvalidUsernameException("place: cover url malformed");
    }
    String scheme = uri.getScheme();
    if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
      throw new InvalidUsernameException("place: cover url must be http(s)");
    }
    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw new InvalidUsernameException("place: cover url missing host");
    }
  }
}
