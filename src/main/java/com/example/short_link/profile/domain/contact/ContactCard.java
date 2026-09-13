package com.example.short_link.profile.domain.contact;

import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Only name is required; absent optional fields are hidden. vCard serialization occurs on the
 * frontend. Palette IDs must stay allow-listed to prevent arbitrary CSS values reaching rendering.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContactCard(
    String name,
    String title,
    String company,
    String email,
    String phone,
    String address,
    String website,
    String logoUrl,
    Integer logoFocalX,
    Integer logoFocalY,
    String palette) {

  private static final int NAME_MAX = 60;
  private static final int TITLE_MAX = 80;
  private static final int COMPANY_MAX = 80;
  private static final int EMAIL_MAX = 254;
  private static final int PHONE_MAX = 30;
  private static final int ADDRESS_MAX = 200;
  private static final int WEBSITE_MAX = 256;
  private static final int LOGO_URL_MAX = 512;

  /**
   * Focal points are percentages from 0 to 100 matching CSS {@code object-position}; missing values
   * default to the center (50/50).
   */
  private static final int FOCAL_DEFAULT = 50;

  private static final int FOCAL_MIN = 0;
  private static final int FOCAL_MAX = 100;

  /**
   * Keep IDs aligned with the frontend palette map. Null or blank selects the default amethyst
   * palette for compatibility.
   */
  private static final Set<String> ALLOWED_PALETTES =
      Set.of(
          "amethyst",
          "rose-gold",
          "emerald",
          "sapphire",
          "sunset",
          "midnight",
          "champagne",
          "aurora");

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static String normalize(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "contact card: config required");
    }
    ContactCard parsed;
    try {
      parsed = MAPPER.readValue(raw.trim(), ContactCard.class);
    } catch (JsonProcessingException ex) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "contact card: malformed json");
    }
    String name = trimTo(parsed.name, NAME_MAX);
    if (name == null || name.isEmpty()) {
      throw new ProfileException(ProfileErrorCode.INVALID_USERNAME, "contact card: name required");
    }
    String email = trimTo(parsed.email, EMAIL_MAX);
    if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "contact card: email malformed");
    }
    String website = trimTo(parsed.website, WEBSITE_MAX);
    if (website != null) validateHttpUrl(website, "website");
    String logoUrl = trimTo(parsed.logoUrl, LOGO_URL_MAX);
    if (logoUrl != null) validateHttpUrl(logoUrl, "logoUrl");
    String palette = trimTo(parsed.palette, 32);
    if (palette != null && !ALLOWED_PALETTES.contains(palette)) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME,
          "contact card: palette must be one of " + ALLOWED_PALETTES);
    }
    ContactCard out =
        new ContactCard(
            name,
            trimTo(parsed.title, TITLE_MAX),
            trimTo(parsed.company, COMPANY_MAX),
            email,
            trimTo(parsed.phone, PHONE_MAX),
            trimTo(parsed.address, ADDRESS_MAX),
            website,
            logoUrl,
            clampFocal(parsed.logoFocalX),
            clampFocal(parsed.logoFocalY),
            palette);
    try {
      return MAPPER.writeValueAsString(out);
    } catch (JsonProcessingException ex) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "contact card: serialization failed");
    }
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
    try {
      URI uri = URI.create(url);
      String scheme = uri.getScheme();
      if (scheme == null
          || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
        throw new ProfileException(
            ProfileErrorCode.INVALID_USERNAME, "contact card: " + field + " must be http(s)");
      }
      if (uri.getHost() == null || uri.getHost().isBlank()) {
        throw new ProfileException(
            ProfileErrorCode.INVALID_USERNAME, "contact card: " + field + " missing host");
      }
    } catch (IllegalArgumentException ex) {
      throw new ProfileException(
          ProfileErrorCode.INVALID_USERNAME, "contact card: " + field + " malformed");
    }
  }
}
