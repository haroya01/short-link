package com.example.short_link.post.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A curator connects real posts, quotations and notes, publishes a path and controls its
 * visibility.
 */
class CuratedReadingPathHttpQueryContractTest extends ContentHttpJourneySupport {

  @Test
  void curatorBuildsAPublicReadingPathAndReadersFollowItsConnections() throws Exception {
    long postId = createPost("curation-source-post-create", "curation-source", "Readable systems");
    step(
        "curation-source-body-write",
        "PUT",
        postPath(postId) + "/markdown",
        author,
        Map.of("markdown", "Readable systems make every responsibility visible."),
        200);
    publish("curation-source-post-publish", postId);
    long highlight =
        step(
                "curation-source-highlight-create",
                "POST",
                postPath(postId) + "/highlights",
                reader,
                Map.of(
                    "blockOrder",
                    0,
                    "startOffset",
                    0,
                    "endOffset",
                    8,
                    "quote",
                    "Readable",
                    "note",
                    "An essential idea"),
                201)
            .path("id")
            .asLong();
    long note =
        step(
                "curation-note-create",
                "POST",
                "/api/v1/notes",
                reader,
                Map.of("body", "Read the example before the abstraction."),
                201)
            .path("id")
            .asLong();
    assertThat(jdbc.queryForObject("SELECT body FROM note WHERE id = ?", String.class, note))
        .isEqualTo("Read the example before the abstraction.");
    long collection =
        createCollection(
            "curation-private-path-create", reader, "A path through readable code", "PRIVATE");
    connect(
        "curation-connect-post",
        collection,
        "POST",
        postId,
        "Start with the concrete system",
        reader);
    connect(
        "curation-connect-highlight",
        collection,
        "HIGHLIGHT",
        highlight,
        "Extract its principle",
        reader);
    connect("curation-connect-note", collection, "NOTE", note, "Apply the principle", reader);
    assertThat(count("collection_connection", "collection_id = ?", collection)).isEqualTo(3);
    step("reader-private-path-hidden", "GET", collectionPath(collection), outsider, null, 404);
    publishAndOrder(collection, postId);
    discoverConnections(collection, postId, highlight);
    shareTasteWithAnotherCurator(collection, postId);
    removeTargetsAndConnections(collection, postId, highlight, note);
    verifyContracts();
  }

  private void publishAndOrder(long collection, long postId) throws Exception {
    step(
        "curation-publish-path",
        "PUT",
        collectionPath(collection),
        reader,
        Map.of(
            "title",
            "A public path through readable code",
            "description",
            "Examples followed by rules",
            "visibility",
            "PUBLIC"),
        200);
    assertThat(
            jdbc.queryForObject(
                "SELECT visibility FROM collection WHERE id = ?", String.class, collection))
        .isEqualTo("PUBLIC");
    assertThat(get("curation-my-collections", "/api/v1/users/me/collections", reader).toString())
        .contains("A public path through readable code");
    assertThat(
            get(
                    "curation-my-collections-connected-status",
                    "/api/v1/users/me/collections?blockType=POST&refId=" + postId,
                    reader)
                .toString())
        .contains("connectionId");
    List<Long> original =
        jdbc.queryForList(
            "SELECT id FROM collection_connection WHERE collection_id = ? ORDER BY position",
            Long.class,
            collection);
    List<Long> reversed = original.reversed();
    step(
        "curation-reorder-connections",
        "PUT",
        collectionPath(collection) + "/connections/order",
        reader,
        Map.of("connectionIds", reversed),
        204);
    assertThat(
            jdbc.queryForList(
                "SELECT id FROM collection_connection WHERE collection_id = ? ORDER BY position",
                Long.class,
                collection))
        .containsExactlyElementsOf(reversed);
    assertThat(
            get("reader-public-path-detail", collectionPath(collection), outsider)
                .path("connections")
                .size())
        .isEqualTo(3);
    step(
        "outsider-path-edit-denied",
        "PUT",
        collectionPath(collection),
        outsider,
        Map.of("title", "Stolen path", "visibility", "PUBLIC"),
        403);
    step(
        "curation-partial-reorder-denied",
        "PUT",
        collectionPath(collection) + "/connections/order",
        reader,
        Map.of("connectionIds", List.of(original.getFirst())),
        400);
    assertThat(count("collection_connection", "collection_id = ?", collection)).isEqualTo(3);
    connect(
        "curation-reconnect-updates-explanation",
        collection,
        "POST",
        postId,
        "Begin with the real database path",
        reader);
    assertThat(
            count(
                "collection_connection",
                "collection_id = ? AND block_type = ? AND ref_id = ?",
                collection,
                "POST",
                postId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT why FROM collection_connection WHERE collection_id = ? AND block_type = 'POST' AND ref_id = ?",
                String.class,
                collection,
                postId))
        .isEqualTo("Begin with the real database path");
  }

  private void discoverConnections(long collection, long postId, long highlight) throws Exception {
    assertThat(
            get(
                    "reader-public-curator-collections",
                    "/api/v1/public/profiles/" + reader.username() + "/collections",
                    null)
                .toString())
        .contains("A public path through readable code");
    assertThat(
            get(
                    "reader-post-containing-paths",
                    "/api/v1/public/posts/" + postId + "/collections",
                    null)
                .get(0)
                .path("id")
                .asLong())
        .isEqualTo(collection);
    assertThat(
            get(
                    "reader-post-containing-paths-batch",
                    "/api/v1/public/posts/collections?ids=" + postId,
                    null)
                .toString())
        .contains("A public path through readable code");
    assertThat(
            get(
                    "reader-highlight-containing-paths",
                    "/api/v1/public/highlights/" + highlight + "/collections",
                    null)
                .get(0)
                .path("id")
                .asLong())
        .isEqualTo(collection);
    assertThat(
            get("reader-public-connection-feed", "/api/v1/public/feed/connections", null)
                .path("items")
                .size())
        .isEqualTo(3);
    assertThat(
            get("reader-personal-connection-global-fallback", "/api/v1/feed/connections", outsider)
                .path("source")
                .asText())
        .isEqualTo("global");
    step(
        "reader-follow-curator",
        "PUT",
        "/api/v1/users/" + reader.username() + "/follow",
        outsider,
        null,
        200);
    assertThat(
            get("reader-personal-connection-following", "/api/v1/feed/connections", outsider)
                .path("source")
                .asText())
        .isEqualTo("following");
    assertThat(
            get(
                    "reader-personal-connection-forced-global",
                    "/api/v1/feed/connections?scope=global",
                    outsider)
                .path("source")
                .asText())
        .isEqualTo("global");
    assertThat(
            get(
                    "reader-related-post-connections",
                    "/api/v1/public/graph/blocks/post/" + postId + "/related",
                    null)
                .toString())
        .contains("Read the example before the abstraction.");
    assertThat(
            get(
                    "reader-related-highlight-connections",
                    "/api/v1/public/graph/blocks/highlight/" + highlight + "/related",
                    null)
                .toString())
        .contains("Readable systems");
  }

  private void shareTasteWithAnotherCurator(long collection, long postId) throws Exception {
    long second =
        createCollection(
            "curation-second-curator-collection", outsider, "Related reading", "PUBLIC");
    connect(
        "curation-second-curator-connect-shared-post",
        second,
        "POST",
        postId,
        "A shared interest",
        outsider);
    assertThat(
            get(
                    "reader-kindred-curators",
                    "/api/v1/public/profiles/" + reader.username() + "/kindred",
                    null)
                .toString())
        .contains(outsider.username());
    step(
        "curation-unlist-path",
        "PUT",
        collectionPath(collection),
        reader,
        Map.of("title", "A public path through readable code", "visibility", "UNLISTED"),
        200);
    assertThat(
            get("reader-unlisted-path-direct-link", collectionPath(collection), outsider)
                .path("id")
                .asLong())
        .isEqualTo(collection);
    assertThat(
            get(
                    "reader-unlisted-path-absent-from-profile",
                    "/api/v1/public/profiles/" + reader.username() + "/collections",
                    null)
                .size())
        .isZero();
    step(
        "curation-second-collection-delete", "DELETE", collectionPath(second), outsider, null, 204);
    assertThat(count("collection_connection", "collection_id = ?", second)).isZero();
  }

  private void removeTargetsAndConnections(long collection, long postId, long highlight, long note)
      throws Exception {
    long postConnection =
        jdbc.queryForObject(
            "SELECT id FROM collection_connection WHERE collection_id = ? AND block_type = 'POST'",
            Long.class,
            collection);
    step(
        "curation-disconnect-post",
        "DELETE",
        collectionPath(collection) + "/connections/" + postConnection,
        reader,
        null,
        204);
    assertThat(count("collection_connection", "id = ?", postConnection)).isZero();
    step(
        "curation-delete-highlight-purges-connection",
        "DELETE",
        "/api/v1/highlights/" + highlight,
        reader,
        null,
        204);
    assertThat(count("collection_connection", "block_type = 'HIGHLIGHT' AND ref_id = ?", highlight))
        .isZero();
    step(
        "curation-delete-note-purges-connection",
        "DELETE",
        "/api/v1/notes/" + note,
        reader,
        null,
        204);
    assertThat(count("collection_connection", "block_type = 'NOTE' AND ref_id = ?", note)).isZero();
    assertThat(
            get("curation-path-after-target-deletion", collectionPath(collection), reader)
                .path("connections")
                .size())
        .isZero();
    step("curation-collection-delete", "DELETE", collectionPath(collection), reader, null, 204);
    assertThat(count("collection", "id = ?", collection)).isZero();
    step("curation-source-post-delete", "DELETE", postPath(postId), author, null, 204);
  }

  private long createCollection(String id, Actor actor, String title, String visibility)
      throws Exception {
    long collection =
        step(
                id,
                "POST",
                "/api/v1/collections",
                actor,
                Map.of("title", title, "visibility", visibility, "kind", "PATH"),
                201)
            .path("id")
            .asLong();
    assertThat(
            jdbc.queryForObject(
                "SELECT title FROM collection WHERE id = ?", String.class, collection))
        .isEqualTo(title);
    return collection;
  }

  private void connect(String id, long collection, String type, long ref, String why, Actor actor)
      throws Exception {
    step(
        id,
        "POST",
        collectionPath(collection) + "/connections",
        actor,
        Map.of("blockType", type, "refId", ref, "why", why),
        201);
  }

  private static String collectionPath(long id) {
    return "/api/v1/collections/" + id;
  }
}
