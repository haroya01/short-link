package com.example.short_link.profile.contact;

import com.example.short_link.profile.exception.InvalidUsernameException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * CONTACT_CARD block payload — the digital-business-card vertical's core. Stored as JSON in {@code
 * profile_block.content}. Only {@code name} is required; the rest fall through to "not shown" on
 * the rendered card. vCard serialization for the .vcf download happens on the front end, where the
 * browser can offer a save dialog without a round-trip.
 *
 * <p>{@code logoUrl} is an http(s) URL pointing to an image uploaded via the profile-images S3
 * pipeline; renders as a small brand mark on both the front and back of the holographic card.
 * Stored in the same JSON content column — no schema change needed.
 *
 * <p>{@code palette} is a whitelisted holographic foil preset id (e.g. {@code amethyst}, {@code
 * rose-gold}). The frontend maps each id to a set of 6 HSL color stops driving the card's
 * iridescent gradient. We validate the id against a closed set so a typo or hostile value can't
 * produce a broken render or feed arbitrary CSS into the page.
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
   * Logo focal point — percentage 0..100 on each axis, matching the CSS {@code object-position}
   * value the frontend applies when cropping the rectangular uploaded logo into the card's square
   * logo slot. Default 50/50 = visual center, which is what existing records (predating focal
   * points) end up with on the next normalize pass.
   */
  private static final int FOCAL_DEFAULT = 50;

  private static final int FOCAL_MIN = 0;
  private static final int FOCAL_MAX = 100;

  /**
   * Allowed palette ids. Keep in sync with the frontend palette map; adding a new palette is a
   * coordinated change across both repos. Null / blank means "use the default palette" so existing
   * CONTACT_CARD blocks (which predate this field) keep rendering with the original amethyst look.
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
      throw new InvalidUsernameException("contact card: config required");
    }
    ContactCard parsed;
    try {
      parsed = MAPPER.readValue(raw.trim(), ContactCard.class);
    } catch (JsonProcessingException ex) {
      throw new InvalidUsernameException("contact card: malformed json");
    }
    String name = trimTo(parsed.name, NAME_MAX);
    if (name == null || name.isEmpty()) {
      throw new InvalidUsernameException("contact card: name required");
    }
    String email = trimTo(parsed.email, EMAIL_MAX);
    if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
      throw new InvalidUsernameException("contact card: email malformed");
    }
    String website = trimTo(parsed.website, WEBSITE_MAX);
    if (website != null) validateHttpUrl(website, "website");
    String logoUrl = trimTo(parsed.logoUrl, LOGO_URL_MAX);
    if (logoUrl != null) validateHttpUrl(logoUrl, "logoUrl");
    String palette = trimTo(parsed.palette, 32);
    if (palette != null && !ALLOWED_PALETTES.contains(palette)) {
      throw new InvalidUsernameException(
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
      throw new InvalidUsernameException("contact card: serialization failed");
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
        throw new InvalidUsernameException("contact card: " + field + " must be http(s)");
      }
      if (uri.getHost() == null || uri.getHost().isBlank()) {
        throw new InvalidUsernameException("contact card: " + field + " missing host");
      }
    } catch (IllegalArgumentException ex) {
      throw new InvalidUsernameException("contact card: " + field + " malformed");
    }
  }
}
