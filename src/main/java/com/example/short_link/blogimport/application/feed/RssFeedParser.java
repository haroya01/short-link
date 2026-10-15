package com.example.short_link.blogimport.application.feed;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

// RSS 2.0 only. The XML parser never resolves DTDs or external entities.
public final class RssFeedParser {

  public static final int MAX_ENTRIES = 50;

  private static final DateTimeFormatter RFC_1123 =
      DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH);

  private RssFeedParser() {}

  public static Optional<FeedDocument> parse(String xml) {
    if (xml == null || xml.isBlank()) {
      return Optional.empty();
    }
    Document doc = Jsoup.parse(xml, "", Parser.xmlParser());
    Element rss = doc.selectFirst("rss");
    Element channel = rss == null ? null : rss.selectFirst("> channel");
    if (channel == null) {
      return Optional.empty();
    }
    List<FeedEntry> entries = new ArrayList<>();
    for (Element item : channel.select("> item")) {
      if (entries.size() >= MAX_ENTRIES) {
        break;
      }
      entries.add(entry(item));
    }
    return Optional.of(
        new FeedDocument(
            text(channel, "title"),
            text(channel, "link"),
            text(channel, "description"),
            text(channel, "language"),
            entries));
  }

  private static FeedEntry entry(Element item) {
    String content = raw(item, "content|encoded");
    return new FeedEntry(
        text(item, "title"),
        text(item, "link"),
        text(item, "guid"),
        date(text(item, "pubDate")),
        content.isBlank() ? raw(item, "description") : content,
        tags(item));
  }

  private static List<String> tags(Element item) {
    List<String> tags = new ArrayList<>();
    for (Element tag : item.select("> tag")) {
      Arrays.stream(tag.wholeText().split(","))
          .map(String::strip)
          .filter(t -> !t.isEmpty())
          .forEach(tags::add);
    }
    return tags;
  }

  private static String text(Element parent, String child) {
    Element element = parent.selectFirst("> " + child);
    return element == null ? "" : Parser.unescapeEntities(element.text(), false).strip();
  }

  private static String raw(Element parent, String child) {
    Element element = parent.selectFirst("> " + child);
    return element == null ? "" : element.wholeText();
  }

  static Instant date(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return ZonedDateTime.parse(value.strip(), RFC_1123).toInstant();
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
