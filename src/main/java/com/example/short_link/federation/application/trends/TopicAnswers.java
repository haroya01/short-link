package com.example.short_link.federation.application.trends;

import static com.example.short_link.federation.application.ActivityStreams.text;

import com.example.short_link.common.note.NoteTopicReader;
import com.example.short_link.federation.domain.topics.TopicArticle;
import com.example.short_link.federation.domain.topics.TopicLanguage;
import com.example.short_link.federation.domain.topics.TopicSighting;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

final class TopicAnswers {

  static final int MAX_TAG = 100;
  static final int COUNTED_DAYS = 2;

  private TopicAnswers() {}

  static List<TopicSighting> tags(JsonNode rows, String host) {
    List<TopicSighting> sightings = new ArrayList<>();
    for (int place = 0; place < rows.size(); place++) {
      JsonNode row = rows.get(place);
      String name = tagName(text(row.get("name")));
      if (name != null) {
        sightings.add(TopicSighting.tag(host, place, name, people(row.path("history"))));
      }
    }
    return sightings;
  }

  static List<TopicSighting> links(JsonNode rows, String host) {
    List<TopicSighting> sightings = new ArrayList<>();
    for (int place = 0; place < rows.size(); place++) {
      JsonNode row = rows.get(place);
      TopicArticle article =
          article(
              text(row.get("url")),
              text(row.get("title")),
              text(row.get("provider_name")),
              text(row.get("image")));
      if (article != null) {
        sightings.add(
            TopicSighting.link(
                host,
                place,
                article,
                language(text(row.get("language")), article.title()),
                people(row.path("history"))));
      }
    }
    return sightings;
  }

  static List<TopicSighting> ownTags(List<NoteTopicReader.Tag> tags, String host) {
    List<TopicSighting> sightings = new ArrayList<>();
    for (int place = 0; place < tags.size(); place++) {
      String name = tagName(tags.get(place).name());
      if (name != null) {
        sightings.add(TopicSighting.tag(host, place, name, (int) tags.get(place).accounts()));
      }
    }
    return sightings;
  }

  static List<TopicSighting> ownLinks(List<NoteTopicReader.Link> links, String host) {
    List<TopicSighting> sightings = new ArrayList<>();
    for (int place = 0; place < links.size(); place++) {
      NoteTopicReader.Link link = links.get(place);
      TopicArticle article = article(link.url(), link.title(), null, link.image());
      if (article != null) {
        sightings.add(
            TopicSighting.link(
                host, place, article, language(null, article.title()), (int) link.accounts()));
      }
    }
    return sightings;
  }

  static String tagName(String name) {
    String bare = name == null ? "" : name.strip();
    bare = bare.startsWith("#") ? bare.substring(1) : bare;
    return bare.isEmpty() || bare.codePointCount(0, bare.length()) > MAX_TAG ? null : bare;
  }

  private static TopicArticle article(String url, String title, String source, String image) {
    String address = TrendingStatusReader.https(url);
    String name = TrendingStatusReader.cut(source, TrendingStatusReader.MAX_NAME);
    String headline =
        TopicArticle.withoutSource(
            TrendingStatusReader.cut(title, TrendingStatusReader.MAX_CARD_TITLE), name);
    return address == null || headline == null
        ? null
        : new TopicArticle(address, headline, name, TrendingStatusReader.https(image));
  }

  private static String language(String stated, String title) {
    String primary = TopicLanguage.primary(stated);
    return primary != null ? primary : TopicLanguage.ofScript(title);
  }

  static int people(JsonNode history) {
    int people = 0;
    for (int day = 0; day < Math.min(COUNTED_DAYS, history.size()); day++) {
      people += count(history.get(day).path("accounts"));
    }
    return people;
  }

  private static int count(JsonNode accounts) {
    try {
      return Math.max(Integer.parseInt(accounts.asString("0").strip()), 0);
    } catch (NumberFormatException malformed) {
      return 0;
    }
  }
}
