package com.example.short_link.home.application.read;

// "<note id>.<post id>", the newest note and post the reader already has in following, latest and
// trending, handed back as since; 0 where the list had none.
record SinceMarker(long note, long post) {

  static final SinceMarker NONE = new SinceMarker(0, 0);

  static SinceMarker parse(String raw) {
    if (raw == null) {
      return null;
    }
    String[] parts = raw.split("\\.");
    try {
      return parts.length == 2
          ? new SinceMarker(Long.parseLong(parts[0]), Long.parseLong(parts[1]))
          : null;
    } catch (NumberFormatException malformed) {
      return null;
    }
  }

  @Override
  public String toString() {
    return note + "." + post;
  }
}
