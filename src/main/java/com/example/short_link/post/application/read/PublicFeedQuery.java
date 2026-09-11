package com.example.short_link.post.application.read;

/**
 * A public feed selects one source: search text, a tag, or all published posts. Language values
 * retain the repository's shared filter normalization.
 */
public record PublicFeedQuery(Selection selection, int page, int size) {

  private static final int MAX_SIZE = 50;

  public PublicFeedQuery {
    page = Math.max(page, 0);
    size = Math.min(Math.max(size, 1), MAX_SIZE);
  }

  public static PublicFeedQuery from(
      String searchText, String tag, String sort, String language, int page, int size) {
    return new PublicFeedQuery(select(searchText, tag, sort, language), page, size);
  }

  private static Selection select(String searchText, String tag, String sort, String language) {
    if (hasText(searchText)) {
      return new Search(searchText.trim(), SearchOrder.from(sort), language);
    }
    if (hasText(tag)) {
      return new Tagged(tag.trim());
    }
    return new Browse(BrowseOrder.from(sort), language);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  public sealed interface Selection permits Search, Tagged, Browse {}

  /** Search takes priority over a supplied tag. */
  public record Search(String text, SearchOrder order, String language) implements Selection {}

  /** A tag feed spans every language and always lists newest posts first. */
  public record Tagged(String tag) implements Selection {}

  public record Browse(BrowseOrder order, String language) implements Selection {}

  public enum BrowseOrder {
    RECENT,
    TRENDING;

    static BrowseOrder from(String value) {
      if ("trending".equalsIgnoreCase(value)) return TRENDING;
      return RECENT;
    }
  }

  public enum SearchOrder {
    RELEVANCE,
    RECENT,
    TRENDING;

    static SearchOrder from(String value) {
      if ("trending".equalsIgnoreCase(value)) return TRENDING;
      if ("recent".equalsIgnoreCase(value)) return RECENT;
      return RELEVANCE;
    }
  }
}
