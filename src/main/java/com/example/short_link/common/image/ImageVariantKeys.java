package com.example.short_link.common.image;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Clients derive these keys from the original's path on their own, so the rule is an external
// contract shared with web and iOS: <dir>/<name>.w480.<vext> and <dir>/<name>.w1080.<vext>.
public final class ImageVariantKeys {

  public static final int SMALL = 480;
  public static final int LARGE = 1080;

  private static final String KEY = "(?:note-images|post-images)/[A-Za-z0-9_/-]+\\.(?i:jpe?g|png)";
  private static final Pattern ELIGIBLE = Pattern.compile(KEY);
  private static final Pattern IN_TEXT =
      Pattern.compile("(?<![A-Za-z0-9_-])" + KEY + "(?![A-Za-z0-9.])");

  private ImageVariantKeys() {}

  public static boolean eligible(String key) {
    return key != null && ELIGIBLE.matcher(key).matches();
  }

  public static boolean png(String key) {
    return extension(key).equals("png");
  }

  public static String contentType(String key) {
    return png(key) ? "image/png" : "image/jpeg";
  }

  public static String variantKey(String key, int width) {
    if (!eligible(key)) {
      throw new IllegalArgumentException("not an image with variants: " + key);
    }
    int dot = key.lastIndexOf('.');
    return key.substring(0, dot) + ".w" + width + "." + (png(key) ? "png" : "jpg");
  }

  public static List<String> variantsOf(String key) {
    return eligible(key) ? List.of(variantKey(key, LARGE), variantKey(key, SMALL)) : List.of();
  }

  public static List<String> keysIn(String text) {
    if (text == null || text.isEmpty()) {
      return List.of();
    }
    Set<String> keys = new LinkedHashSet<>();
    Matcher matcher = IN_TEXT.matcher(text);
    while (matcher.find()) {
      keys.add(matcher.group());
    }
    return new ArrayList<>(keys);
  }

  private static String extension(String key) {
    int dot = key.lastIndexOf('.');
    return key.substring(dot + 1).toLowerCase(Locale.ROOT);
  }
}
