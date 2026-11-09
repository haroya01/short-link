package com.example.short_link.profile.domain.contact;

import static com.example.short_link.profile.domain.contact.BlockConfig.trimTo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Set;

// Places are resolved on the frontend; the backend validates and stores resolved fields without
// outbound requests.
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

  // Unknown categories become null so a frontend category added before backend deployment does not
  // reject the whole block.
  private static final Set<String> CATEGORIES =
      Set.of("cafe", "bakery", "restaurant", "retail", "studio", "gallery", "popup", "space");

  public static String normalize(String raw) {
    Place parsed = BlockConfig.read(raw, Place.class, "place");
    String name = required(parsed.name, NAME_MAX, "place: name required");
    String address = required(parsed.address, ADDRESS_MAX, "place: address required");
    requireCoordinates(parsed.lat, parsed.lng);
    String placeId = trimTo(parsed.placeId, PLACE_ID_MAX);
    String phone = trimTo(parsed.phone, PHONE_MAX);
    String coverUrl = trimTo(parsed.coverUrl, COVER_URL_MAX);
    if (coverUrl != null) {
      BlockConfig.requireHttpUrl(coverUrl, "place: cover url");
    }
    Place out =
        new Place(
            name,
            address,
            parsed.lat,
            parsed.lng,
            placeId,
            phone,
            coverUrl,
            category(parsed.category),
            trimTo(parsed.hoursText, HOURS_TEXT_MAX));
    return BlockConfig.write(out, "place");
  }

  private static String required(String value, int max, String missing) {
    String kept = trimTo(value, max);
    if (kept == null) {
      throw BlockConfig.invalid(missing);
    }
    return kept;
  }

  private static void requireCoordinates(Double lat, Double lng) {
    if (lat == null || lng == null) {
      throw BlockConfig.invalid("place: lat/lng required");
    }
    if (outside(lat, 90)) {
      throw BlockConfig.invalid("place: lat out of range");
    }
    if (outside(lng, 180)) {
      throw BlockConfig.invalid("place: lng out of range");
    }
  }

  private static boolean outside(double value, double bound) {
    return value < -bound || value > bound;
  }

  private static String category(String raw) {
    String category = trimTo(raw, CATEGORY_MAX);
    return category != null && CATEGORIES.contains(category) ? category : null;
  }
}
