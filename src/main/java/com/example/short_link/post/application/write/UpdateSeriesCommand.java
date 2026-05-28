package com.example.short_link.post.application.write;

/** PATCH 의미. title / slug null = 변경 안 함. */
public record UpdateSeriesCommand(Long userId, Long seriesId, String title, String slug) {

  public UpdateSeriesCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (seriesId == null) throw new IllegalArgumentException("seriesId required");
    if (title != null && (title.isBlank() || title.length() > 200)) {
      throw new IllegalArgumentException("title blank or >200");
    }
    if (slug != null) {
      if (slug.length() < 2 || slug.length() > 200) {
        throw new IllegalArgumentException("slug length 2~200");
      }
      if (!CreateSeriesCommand.SLUG_PATTERN.matcher(slug).matches()) {
        throw new IllegalArgumentException("slug invalid format");
      }
    }
  }
}
