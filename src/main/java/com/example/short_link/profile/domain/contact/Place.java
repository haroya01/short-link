package com.example.short_link.profile.domain.contact;

import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Set;

/**
 * Places are resolved on the frontend; the backend validates and stores resolved fields without
 * outbound requests.
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
   * Unknown categories become null so a frontend category added before backend deployment does not
   * reject the whole block.
   */
  private static final Set<String> CATEGORIES =
      Set.of("cafe", "bakery", "restaurant", "retail", "studio", "gallery", "popup", "space");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static String normalize(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "place: config required");
    }
    Place parsed;
    try {
      parsed = MAPPER.readValue(raw.trim(), Place.class);
    } catch (JsonProcessingException ex) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "place: malformed json");
    }

    String name = trimTo(parsed.name, NAME_MAX);
    if (name == null) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "place: name required");
    }
    String address = trimTo(parsed.address, ADDRESS_MAX);
    if (address == null) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "place: address required");
    }
    if (parsed.lat == null || parsed.lng == null) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "place: lat/lng required");
    }
    if (parsed.lat < -90 || parsed.lat > 90) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "place: lat out of range");
    }
    if (parsed.lng < -180 || parsed.lng > 180) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "place: lng out of range");
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
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "place: serialization failed");
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
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "place: cover url malformed");
    }
    String scheme = uri.getScheme();
    if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "place: cover url must be http(s)");
    }
    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "place: cover url missing host");
    }
  }
}
