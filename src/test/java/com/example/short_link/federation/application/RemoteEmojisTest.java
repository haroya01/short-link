package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.federation.CustomEmoji;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

class RemoteEmojisTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static JsonNode json(String raw) {
    return JSON.readTree(raw);
  }

  private static String tag(String name, String url) {
    return """
        {"type":"Emoji","name":"%s","icon":{"type":"Image","mediaType":"image/png","url":"%s"}}"""
        .formatted(name, url);
  }

  @Test
  void anEmojiTagNamesItsShortcodeBetweenColonsAndItsPictureInItsIcon() {
    JsonNode tags =
        json(
            "["
                + tag(":neoghost:", "https://files.m.example/emoji/neoghost.png")
                + ",{\"type\":\"Mention\",\"href\":\"https://m.example/users/bob\",\"name\":\"@bob\"}"
                + ",{\"type\":\"Hashtag\",\"name\":\"#cats\"}"
                + ","
                + tag("blob_cat2", "https://files.m.example/emoji/blob.png")
                + ",{\"type\":\"Emoji\",\"name\":\":listed:\",\"icon\":[{\"url\":\"https://files.m.example/emoji/l.png\"}]}"
                + "]");

    assertThat(RemoteEmojis.fromTags(tags))
        .containsExactly(
            new CustomEmoji("neoghost", "https://files.m.example/emoji/neoghost.png"),
            new CustomEmoji("blob_cat2", "https://files.m.example/emoji/blob.png"),
            new CustomEmoji("listed", "https://files.m.example/emoji/l.png"));
    assertThat(RemoteEmojis.fromTags(json(tag(":alone:", "https://f.example/a.png"))))
        .containsExactly(new CustomEmoji("alone", "https://f.example/a.png"));
  }

  @Test
  void anEmojiWithAnOddNameOrAPictureOffHttpsIsDroppedAndTheRestKept() {
    JsonNode tags =
        json(
            "["
                + tag(":a:", "https://f.example/one-letter.png")
                + ","
                + tag(":has-dash:", "https://f.example/dash.png")
                + ","
                + tag(":" + "x".repeat(129) + ":", "https://f.example/long-name.png")
                + ","
                + tag(":plain:", "http://f.example/plain.png")
                + ","
                + tag(":relative:", "/emoji/relative.png")
                + ","
                + tag(":spaced:", "https://f.example/a b.png")
                + ","
                + tag(":huge:", "https://f.example/" + "p".repeat(512))
                + ",{\"type\":\"Emoji\",\"name\":\":noicon:\"}"
                + ",{\"type\":\"Emoji\",\"name\":\":urlobject:\",\"icon\":{\"url\":{\"href\":\"https://f.example/o.png\"}}}"
                + ",{\"type\":\"Emoji\",\"icon\":{\"url\":\"https://f.example/nameless.png\"}}"
                + ","
                + tag(":fine:", "https://f.example/fine.png")
                + "]");

    assertThat(RemoteEmojis.fromTags(tags))
        .containsExactly(new CustomEmoji("fine", "https://f.example/fine.png"));
    assertThat(RemoteEmojis.fromTags(null)).isEmpty();
    assertThat(RemoteEmojis.fromTags(json("\"Emoji\""))).isEmpty();
  }

  @Test
  void aShortcodeIsKeptOnceAndANoteKeepsAtMostFifty() {
    ArrayNode tags = JSON.createArrayNode();
    tags.add(json(tag(":twice:", "https://f.example/first.png")));
    tags.add(json(tag(":twice:", "https://f.example/second.png")));
    tags.add(json(tag(":bad-one:", "https://f.example/bad.png")));
    for (int i = 0; i < 60; i++) {
      tags.add(json(tag(":e" + i + ":", "https://f.example/" + i + ".png")));
    }

    var emojis = RemoteEmojis.fromTags(tags);

    assertThat(emojis).hasSize(RemoteEmojis.MAX);
    assertThat(emojis.getFirst())
        .isEqualTo(new CustomEmoji("twice", "https://f.example/first.png"));
    assertThat(emojis.getLast()).isEqualTo(new CustomEmoji("e48", "https://f.example/48.png"));
  }

  @Test
  void mastodonsApiGivesTheStillPictureWhenItHasOneAndTheOriginalOtherwise() {
    JsonNode emojis =
        json(
            """
            [{"shortcode":"spin","url":"https://f.example/spin.gif",
              "static_url":"https://f.example/spin-static.png","visible_in_picker":true},
             {"shortcode":"plain","url":"https://f.example/plain.png"},
             {"shortcode":"badstill","url":"https://f.example/b.png","static_url":"http://f.example/b.png"},
             {"shortcode":"nothing","url":"http://f.example/n.png","static_url":null},
             {"shortcode":"spin","url":"https://f.example/again.png"},
             {"shortcode":":colons:","url":"https://f.example/c.png"}]""");

    assertThat(RemoteEmojis.fromRest(emojis))
        .containsExactly(
            new CustomEmoji("spin", "https://f.example/spin-static.png"),
            new CustomEmoji("plain", "https://f.example/plain.png"),
            new CustomEmoji("badstill", "https://f.example/b.png"));
    assertThat(RemoteEmojis.fromRest(null)).isEmpty();
    assertThat(RemoteEmojis.fromRest(json("{}"))).isEmpty();
  }

  @Test
  void mastodonsApiKeepsAtMostFifty() {
    ArrayNode emojis = JSON.createArrayNode();
    for (int i = 0; i < 55; i++) {
      emojis.addObject().put("shortcode", "e" + i).put("url", "https://f.example/" + i + ".png");
    }

    assertThat(RemoteEmojis.fromRest(emojis)).hasSize(RemoteEmojis.MAX);
  }

  @Test
  void aPictureAddressIsKeptUpToTheLongestUriAndDroppedOneCharacterPastIt() {
    String prefix = "https://f.example/";
    String longest = prefix + "a".repeat(ActivityStreams.MAX_URI - prefix.length());
    String tooLong = longest + "a";

    assertThat(RemoteEmojis.fromTags(json("[" + tag(":fits:", longest) + "]")))
        .extracting(CustomEmoji::url)
        .containsExactly(longest);
    assertThat(RemoteEmojis.fromTags(json("[" + tag(":over:", tooLong) + "]"))).isEmpty();
  }
}
