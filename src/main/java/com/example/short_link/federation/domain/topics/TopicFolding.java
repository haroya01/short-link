package com.example.short_link.federation.domain.topics;

import java.lang.Character.UnicodeScript;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class TopicFolding {

  public static final int MAX_RELATED = 3;
  static final int MIN_LATIN_LETTERS = 4;
  static final int MIN_CHARACTERS = 2;

  private TopicFolding() {}

  public static List<Topic> fold(List<Topic> topics) {
    List<Topic> tags = topics.stream().filter(Topic::isTag).sorted(TopicRanking.ORDER).toList();
    Map<String, List<Topic>> under = new LinkedHashMap<>();
    List<Topic> folded = new ArrayList<>();
    for (Topic link : topics.stream().filter(topic -> !topic.isTag()).toList()) {
      Optional<Topic> tag = tags.stream().filter(named -> carries(link, named)).findFirst();
      if (tag.isPresent()) {
        under.computeIfAbsent(tag.get().id(), id -> new ArrayList<>()).add(link);
      } else {
        folded.add(link);
      }
    }
    for (Topic tag : tags) {
      folded.add(withRelated(tag, under.getOrDefault(tag.id(), List.of())));
    }
    return folded;
  }

  private static Topic withRelated(Topic tag, List<Topic> links) {
    List<Topic> ranked = links.stream().sorted(TopicRanking.ORDER).toList();
    int largest =
        ranked.isEmpty() ? tag.people() : Math.max(tag.people(), ranked.getFirst().people());
    return tag.with(ranked.stream().limit(MAX_RELATED).map(Topic::article).toList(), largest);
  }

  static boolean carries(Topic link, Topic tag) {
    if (link.language() != null
        && tag.language() != null
        && !link.language().equals(tag.language())) {
      return false;
    }
    String name = tag.tag().toLowerCase(Locale.ROOT);
    String title = String.valueOf(link.article().title()).toLowerCase(Locale.ROOT);
    if (!latin(name)) {
      return name.codePointCount(0, name.length()) >= MIN_CHARACTERS && title.contains(name);
    }
    return name.codePoints().filter(Character::isLetter).count() >= MIN_LATIN_LETTERS
        && Pattern.compile(
                "(?<![\\p{IsLatin}\\p{N}])" + Pattern.quote(name) + "(?![\\p{IsLatin}\\p{N}])")
            .matcher(title)
            .find();
  }

  private static boolean latin(String name) {
    return name.codePoints()
        .filter(Character::isLetter)
        .allMatch(letter -> UnicodeScript.of(letter) == UnicodeScript.LATIN);
  }
}
