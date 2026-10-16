package com.example.short_link.federation.application.trends;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.RemoteNoteParser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class TrendingStatusReaderTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String SERVER = "mastodon.example";

  private final TrendingStatusReader reader =
      new TrendingStatusReader(
          new RemoteNoteParser(
              new FederationUrls(
                  new FederationProperties("https://kurl.me", "https://blog.kurl.me"))),
          JSON);

  static ObjectNode status(String uri, String acct) {
    String host = uri.substring("https://".length(), uri.indexOf('/', "https://".length()));
    String name = acct.contains("@") ? acct.substring(0, acct.indexOf('@')) : acct;
    return (ObjectNode)
        JSON.readTree(
            """
            {"id":"1","uri":"%1$s","url":"https://%2$s/@%3$s/1","created_at":"2026-10-16T13:48:01.441Z",
             "visibility":"public","in_reply_to_id":null,"reblog":null,"sensitive":false,
             "spoiler_text":"","language":"en","content":"<p>hello</p>",
             "replies_count":1,"reblogs_count":2,"favourites_count":3,
             "account":{"acct":"%4$s","display_name":"  Name  ","discoverable":true,
              "avatar":"https://%2$s/avatar.png","url":"https://%2$s/@%3$s",
              "uri":"https://%2$s/users/%3$s"},
             "media_attachments":[],"card":null,"poll":null}"""
                .formatted(uri, host, name, acct));
  }

  private Optional<TrendingStatusReader.Read> read(ObjectNode status) {
    return reader.read(status, SERVER);
  }

  @Test
  void aPostReadsAsAKeptRemoteNoteWouldWithItsAuthorCountsAndCard() {
    ObjectNode status = status("https://mastodon.example/users/alice/statuses/1", "alice");
    status.put(
        "content",
        "<p><span class=\"h-card\"><a href=\"https://other.example/@bob\" class=\"u-url"
            + " mention\">@<span>bob</span></a></span> look &amp; see</p><p>next</p>");
    status.put("spoiler_text", "spoilers");
    status.put("sensitive", true);
    status.put("language", "ja");
    status.set(
        "media_attachments",
        JSON.readTree(
            """
            [{"type":"image","url":"https://files.example/a.png","description":"a cat",
              "meta":{"original":{"width":800,"height":600}}},
             {"type":"gifv","url":"https://files.example/b.mp4","description":null},
             {"type":"unknown","url":null,"remote_url":"https://elsewhere.example/c"},
             {"type":"image","url":"http://files.example/d.png"}]"""));
    status.set(
        "card",
        JSON.readTree(
            """
            {"url":"https://news.example/story","title":" A\\n story ","description":"",
             "image":"https://files.example/card.jpg","type":"link"}"""));

    TrendingStatusReader.Read read = read(status).orElseThrow();

    assertThat(read.actorUri()).isEqualTo("https://mastodon.example/users/alice");
    assertThat(read.domain()).isEqualTo("mastodon.example");
    TrendingNote note = read.note();
    assertThat(note.uri()).isEqualTo("https://mastodon.example/users/alice/statuses/1");
    assertThat(note.body()).isEqualTo("@bob@other.example look & see\n\nnext");
    assertThat(note.contentWarning()).isEqualTo("spoilers");
    assertThat(note.sensitive()).isTrue();
    assertThat(note.language()).isEqualTo("ja");
    assertThat(note.createdAt()).isEqualTo(Instant.parse("2026-10-16T13:48:01.441Z"));
    assertThat(note.editedAt()).isNull();
    assertThat(note.author())
        .isEqualTo(
            new TrendingNote.Author(
                null,
                "alice@mastodon.example",
                "https://mastodon.example/avatar.png",
                "Name",
                null,
                "https://mastodon.example/@alice"));
    assertThat(note.media())
        .containsExactly(
            new RemoteNotes.Media("https://files.example/a.png", "a cat", "image/jpeg", 800, 600),
            new RemoteNotes.Media("https://files.example/b.mp4", null, "video/mp4"));
    assertThat(note.likeCount()).isEqualTo(3);
    assertThat(note.repostCount()).isEqualTo(2);
    assertThat(note.replyCount()).isEqualTo(1);
    assertThat(note.poll()).isNull();
    assertThat(note.linkPreview())
        .isEqualTo(
            new TrendingNote.LinkPreview(
                "https://news.example/story", "A story", null, "https://files.example/card.jpg"));
    assertThat(note.remote())
        .isEqualTo(
            new TrendingNote.Remote("NOTE", null, "https://mastodon.example/@alice/1", null));
    assertThat(note.url()).isEqualTo("https://mastodon.example/@alice/1");
    assertThat(note.servers()).containsExactly(SERVER);
    assertThat(note.sourceHost()).isEqualTo(SERVER);
  }

  @Test
  void anAccountElsewhereKeepsItsServerAndAPageOnAnotherHostIsNotItsAddress() {
    ObjectNode status = status("https://misskey.example/notes/9", "Bob_1@Misskey.Example");
    status.put("url", "https://mirror.example/notes/9");
    status.put("language", (String) null);
    status.put("edited_at", "2026-10-16T14:00:00.000Z");
    ((ObjectNode) status.get("account")).put("avatar", "http://misskey.example/a.png");

    TrendingNote note = read(status).orElseThrow().note();

    assertThat(note.author().username()).isEqualTo("Bob_1@misskey.example");
    assertThat(note.author().avatarUrl()).isNull();
    assertThat(note.language()).isNull();
    assertThat(note.contentWarning()).isNull();
    assertThat(note.remote().url()).isNull();
    assertThat(note.url()).isEqualTo("https://misskey.example/notes/9");
    assertThat(note.editedAt()).isEqualTo(Instant.parse("2026-10-16T14:00:00Z"));
  }

  @Test
  void aPollComesAsARemoteNotesPollDoesAndAnOverlongOneIsLeftOut() {
    ObjectNode status = status("https://mastodon.example/users/alice/statuses/2", "alice");
    status.set(
        "poll",
        JSON.readTree(
            """
            {"expires_at":"2026-10-17T00:00:00.000Z","expired":true,"multiple":true,
             "votes_count":7,"voters_count":5,
             "options":[{"title":"tea","votes_count":4},{"title":"coffee","votes_count":3}]}"""));

    TrendingNote note = read(status).orElseThrow().note();

    assertThat(note.remote().kind()).isEqualTo("QUESTION");
    assertThat(note.poll())
        .isEqualTo(
            new TrendingNote.Poll(
                Instant.parse("2026-10-17T00:00:00Z"),
                true,
                true,
                7,
                5,
                List.of(
                    new TrendingNote.PollOption("tea", 4),
                    new TrendingNote.PollOption("coffee", 3)),
                null,
                null));

    status.set(
        "poll",
        JSON.readTree(
            """
            {"expires_at":null,"expired":true,"multiple":false,"voters_count":null,
             "options":[{"title":"a","votes_count":1},{"title":"b","votes_count":2}]}"""));
    TrendingNote.Poll closed = read(status).orElseThrow().note().poll();
    assertThat(closed.expired()).isTrue();
    assertThat(closed.multiple()).isFalse();
    assertThat(closed.votersCount()).isEqualTo(3);

    status.set(
        "poll",
        JSON.readTree(
            """
            {"expired":false,"multiple":false,"options":[{"title":"1"},{"title":"2"},
             {"title":"3"},{"title":"4"},{"title":"5"}]}"""));
    TrendingNote crowded = read(status).orElseThrow().note();
    assertThat(crowded.remote().kind()).isEqualTo("QUESTION");
    assertThat(crowded.poll()).isNull();
  }

  @Test
  void onlyAPublicPostOfItsOwnByAnAccountThatAgreedToBeFoundIsTaken() {
    String uri = "https://mastodon.example/users/alice/statuses/3";
    assertThat(read(status(uri, "alice"))).isPresent();

    ObjectNode unlisted = status(uri, "alice");
    unlisted.put("visibility", "unlisted");
    ObjectNode reply = status(uri, "alice");
    reply.put("in_reply_to_id", "9");
    ObjectNode boost = status(uri, "alice");
    boost.set("reblog", status(uri, "alice"));
    ObjectNode hidden = status(uri, "alice");
    ((ObjectNode) hidden.get("account")).put("discoverable", false);
    ObjectNode unindexed = status(uri, "alice");
    ((ObjectNode) unindexed.get("account")).put("noindex", true);
    ObjectNode foreign = status(uri, "alice");
    ((ObjectNode) foreign.get("account")).put("uri", "https://other.example/users/alice");
    ObjectNode nameless = status(uri, "alice");
    ((ObjectNode) nameless.get("account")).put("acct", "a lice");
    ObjectNode undated = status(uri, "alice");
    undated.put("created_at", "yesterday");
    ObjectNode overlong = status("https://mastodon.example/" + "x".repeat(500), "alice");
    ObjectNode anonymous = status(uri, "alice");
    ((ObjectNode) anonymous.get("account")).remove("uri");

    for (ObjectNode left :
        List.of(
            unlisted, reply, boost, hidden, unindexed, foreign, nameless, undated, overlong,
            anonymous)) {
      assertThat(read(left)).isEmpty();
    }
  }
}
