package com.example.short_link.note.application.media;

import com.example.short_link.common.net.PublicImageFetcher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class RemoteMediaKeys {

  static final String FOLDER = "note-images/remote/";

  private RemoteMediaKeys() {}

  public static String type(String contentType) {
    if (contentType == null) {
      return null;
    }
    String type = contentType.strip().toLowerCase(Locale.ROOT);
    return type.equals(PublicImageFetcher.JPEG) || type.equals(PublicImageFetcher.PNG)
        ? type
        : null;
  }

  public static boolean copyable(String url, String contentType) {
    return url != null && url.regionMatches(true, 0, "https://", 0, 8) && type(contentType) != null;
  }

  public static String keyOf(String url, String type) {
    return FOLDER + sha256(url) + (PublicImageFetcher.PNG.equals(type) ? ".png" : ".jpg");
  }

  private static String sha256(String url) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException missing) {
      throw new IllegalStateException(missing);
    }
  }
}
