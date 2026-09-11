package com.example.short_link.post.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Discovery branches, synchronized interests and short notes are exercised after real publication.
 */
class PersonalDiscoveryHttpQueryContractTest extends ContentHttpJourneySupport {

  @Test
  void readerSearchesPublishedWritingChoosesInterestsAndSharesAShortNote() throws Exception {
    long korean =
        createPost(
            "discovery-korean-draft-create", "korean-discovery", "Readable architecture examples");
    step(
        "discovery-korean-metadata",
        "PATCH",
        postPath(korean),
        author,
        Map.of("tags", List.of("architecture"), "languageTag", "ko"),
        200);
    step(
        "discovery-korean-body",
        "PUT",
        postPath(korean) + "/markdown",
        author,
        Map.of("markdown", "Readable architecture makes a database journey explicit."),
        200);
    publish("discovery-korean-publish", korean);
    long english =
        createPost(
            "discovery-english-draft-create", "english-discovery", "Readable programming examples");
    step(
        "discovery-english-metadata",
        "PATCH",
        postPath(english),
        author,
        Map.of("tags", List.of("programming"), "languageTag", "en"),
        200);
    publish("discovery-english-publish", english);
    browseAndSearch(korean, english);
    chooseInterestsAndRead(korean);
    publishAndReactToNote();
    verifyContracts();
  }

  private void browseAndSearch(long korean, long english) throws Exception {
    assertThat(get("discovery-recent-feed", "/api/v1/public/posts", null).path("items").size())
        .isEqualTo(2);
    assertThat(
            get("discovery-trending-feed", "/api/v1/public/posts?sort=trending", null)
                .path("items")
                .size())
        .isEqualTo(2);
    assertThat(
            get("discovery-language-filter", "/api/v1/public/posts?lang=en", null)
                .path("items")
                .get(0)
                .path("id")
                .asLong())
        .isEqualTo(english);
    assertThat(
            get("discovery-tag-feed", "/api/v1/public/posts?tag=architecture", null)
                .path("items")
                .get(0)
                .path("id")
                .asLong())
        .isEqualTo(korean);
    assertThat(
            get("discovery-search-relevance", "/api/v1/public/posts?q=Readable", null)
                .path("items")
                .size())
        .isEqualTo(2);
    assertThat(
            get(
                    "discovery-search-recent-language",
                    "/api/v1/public/posts?q=Readable&sort=recent&lang=ko",
                    null)
                .path("items")
                .get(0)
                .path("id")
                .asLong())
        .isEqualTo(korean);
    assertThat(
            get("discovery-search-trending", "/api/v1/public/posts?q=Readable&sort=trending", null)
                .path("items")
                .size())
        .isEqualTo(2);
    assertThat(get("discovery-popular-tags", "/api/v1/public/tags", null).toString())
        .contains("architecture", "programming");
    assertThat(get("discovery-suggested-authors", "/api/v1/public/authors", null).toString())
        .contains(author.username());
    // Sections may omit tags with fewer than the configured minimum posts; SQL and shape remain
    // verified.
    assertThat(
            get("discovery-trending-tag-sections", "/api/v1/public/feed/trending-by-tag", null)
                .isArray())
        .isTrue();
    assertThat(
            get("discovery-for-you-cold-start", "/api/v1/feed/for-you", reader)
                .path("items")
                .size())
        .isEqualTo(2);
  }

  private void chooseInterestsAndRead(long korean) throws Exception {
    assertThat(
            get("reader-tag-preferences-initial", "/api/v1/users/me/tag-prefs", reader)
                .path("followed")
                .size())
        .isZero();
    step(
        "reader-tag-follow",
        "PUT",
        "/api/v1/users/me/tag-prefs/followed/architecture",
        reader,
        null,
        200);
    assertThat(
            jdbc.queryForObject(
                "SELECT kind FROM user_tag_pref WHERE user_id = ? AND tag = ?",
                String.class,
                reader.id(),
                "architecture"))
        .isEqualTo("FOLLOW");
    assertThat(
            get("discovery-for-you-followed-interest", "/api/v1/feed/for-you", reader)
                .path("items")
                .get(0)
                .path("id")
                .asLong())
        .isEqualTo(korean);
    step("discovery-read-recommended-post", "POST", postPath(korean) + "/read", reader, null, 204);
    assertThat(count("post_read", "post_id = ? AND user_id = ?", korean, reader.id())).isEqualTo(1);
    assertThat(
            get("discovery-for-you-excludes-read", "/api/v1/feed/for-you", reader)
                .path("items")
                .size())
        .isZero();
    step(
        "reader-tag-hide-replaces-follow",
        "PUT",
        "/api/v1/users/me/tag-prefs/hidden/architecture",
        reader,
        null,
        200);
    assertThat(count("user_tag_pref", "user_id = ? AND tag = ?", reader.id(), "architecture"))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT kind FROM user_tag_pref WHERE user_id = ? AND tag = ?",
                String.class,
                reader.id(),
                "architecture"))
        .isEqualTo("HIDE");
    step(
        "reader-tag-unhide",
        "DELETE",
        "/api/v1/users/me/tag-prefs/hidden/architecture",
        reader,
        null,
        200);
    assertThat(count("user_tag_pref", "user_id = ?", reader.id())).isZero();
    step(
        "reader-tag-follow-again",
        "PUT",
        "/api/v1/users/me/tag-prefs/followed/architecture",
        reader,
        null,
        200);
    step(
        "reader-tag-unfollow",
        "DELETE",
        "/api/v1/users/me/tag-prefs/followed/architecture",
        reader,
        null,
        200);
    assertThat(count("user_tag_pref", "user_id = ?", reader.id())).isZero();
    assertThat(
            get("reader-feed-preferences-default", "/api/v1/users/me/feed-prefs", reader)
                .path("defaultTab")
                .asText())
        .isNotBlank();
    step(
        "reader-feed-preference-select",
        "PUT",
        "/api/v1/users/me/feed-prefs/default-tab/following",
        reader,
        null,
        200);
    assertThat(
            jdbc.queryForObject(
                "SELECT default_tab FROM user_feed_pref WHERE user_id = ?",
                String.class,
                reader.id()))
        .isEqualTo("following");
    assertThat(
            get("reader-feed-preference-reload", "/api/v1/users/me/feed-prefs", reader)
                .path("defaultTab")
                .asText())
        .isEqualTo("following");
    assertThat(count("user_feed_pref", "user_id = ?", outsider.id())).isZero();
  }

  private void publishAndReactToNote() throws Exception {
    long note =
        step(
                "reader-short-note-create",
                "POST",
                "/api/v1/notes",
                reader,
                Map.of("body", "  Read the use case before the adapters.  "),
                201)
            .path("id")
            .asLong();
    assertThat(jdbc.queryForObject("SELECT body FROM note WHERE id = ?", String.class, note))
        .isEqualTo("Read the use case before the adapters.");
    assertThat(get("reader-public-note-feed", "/api/v1/public/notes", null).toString())
        .contains("Read the use case before the adapters.");
    step("author-note-like", "PUT", "/api/v1/notes/" + note + "/like", author, null, 200);
    step(
        "author-note-like-idempotent", "PUT", "/api/v1/notes/" + note + "/like", author, null, 200);
    assertThat(count("note_like", "note_id = ? AND user_id = ?", note, author.id())).isEqualTo(1);
    assertThat(
            get("author-note-liked-ids", "/api/v1/notes/like-status?ids=" + note, author)
                .toString())
        .contains(Long.toString(note));
    step("author-note-unlike", "DELETE", "/api/v1/notes/" + note + "/like", author, null, 200);
    assertThat(count("note_like", "note_id = ?", note)).isZero();
    step("outsider-note-delete-denied", "DELETE", "/api/v1/notes/" + note, outsider, null, 403);
    step("reader-short-note-delete", "DELETE", "/api/v1/notes/" + note, reader, null, 204);
    assertThat(count("note", "id = ?", note)).isZero();
    assertThat(get("reader-deleted-note-absent", "/api/v1/public/notes", null).toString())
        .doesNotContain("Read the use case before the adapters.");
  }
}
