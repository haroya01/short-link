package com.example.short_link.profile.domain.contact;

import static com.example.short_link.profile.domain.contact.BlockConfig.trimTo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Set;
import java.util.regex.Pattern;

// Only name is required; absent optional fields are hidden. vCard serialization occurs on the
// frontend. Palette IDs must stay allow-listed to prevent arbitrary CSS values reaching rendering.
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

  private static final int FOCAL_DEFAULT = 50;

  private static final int FOCAL_MIN = 0;
  private static final int FOCAL_MAX = 100;

  // Keep IDs aligned with the frontend palette map. Null or blank selects the default amethyst
  // palette for compatibility.
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

  public static String normalize(String raw) {
    ContactCard parsed = BlockConfig.read(raw, ContactCard.class, "contact card");
    String name = trimTo(parsed.name, NAME_MAX);
    if (name == null || name.isEmpty()) {
      throw BlockConfig.invalid("contact card: name required");
    }
    String email = trimTo(parsed.email, EMAIL_MAX);
    if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
      throw BlockConfig.invalid("contact card: email malformed");
    }
    String website = httpUrl(parsed.website, WEBSITE_MAX, "website");
    String logoUrl = httpUrl(parsed.logoUrl, LOGO_URL_MAX, "logoUrl");
    String palette = palette(parsed.palette);
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
    return BlockConfig.write(out, "contact card");
  }

  private static String palette(String raw) {
    String palette = trimTo(raw, 32);
    if (palette != null && !ALLOWED_PALETTES.contains(palette)) {
      throw BlockConfig.invalid("contact card: palette must be one of " + ALLOWED_PALETTES);
    }
    return palette;
  }

  private static String httpUrl(String raw, int max, String field) {
    String url = trimTo(raw, max);
    if (url != null) {
      BlockConfig.requireHttpUrl(url, "contact card: " + field);
    }
    return url;
  }

  private static Integer clampFocal(Integer raw) {
    if (raw == null) return FOCAL_DEFAULT;
    int v = raw;
    if (v < FOCAL_MIN) return FOCAL_MIN;
    if (v > FOCAL_MAX) return FOCAL_MAX;
    return v;
  }
}
