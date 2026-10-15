package com.example.short_link.blogimport.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.short_link.blogimport.application.BlogFeedFetcher;
import com.example.short_link.blogimport.application.write.BlogImportRunner;
import com.example.short_link.blogimport.domain.FeedValidators;
import com.example.short_link.testsupport.OperationalHttpJourneySupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

class BlogImportHttpQueryContractTest extends OperationalHttpJourneySupport {

  private static final String FEED = "https://v2.velog.io/rss/@velopert";
  private static final String SOURCES = "/api/v1/imports/sources";

  @MockitoBean private BlogFeedFetcher feeds;
  @Autowired private BlogImportRunner runner;

  private void serve(String body) {
    when(feeds.fetch(eq(FEED), any()))
        .thenReturn(new BlogFeedFetcher.Fetched(body, FeedValidators.NONE, false));
  }

  @Test
  void aWriterBringsTheirVelogInAndKeepsItInSync() throws Exception {
    Actor owner = actor("blog-owner", false);
    Actor other = actor("blog-other", false);
    String velog = Files.readString(Path.of("src/test/resources/blog-import/velog.xml"));
    when(feeds.robots(anyString())).thenReturn(Optional.empty());
    serve(velog);

    JsonNode preview =
        step(
            "blog-import-preview",
            "POST",
            "/api/v1/imports/preview",
            null,
            Map.of("url", "https://velog.io/@velopert"),
            200);
    assertThat(preview.path("platform").asString()).isEqualTo("VELOG");
    assertThat(preview.path("blogUrl").asString()).isEqualTo("https://velog.io/@velopert");
    assertThat(preview.path("fullContent").asBoolean()).isTrue();
    assertThat(preview.path("items")).hasSize(3);
    assertThat(preview.path("items").get(0).path("url").asString())
        .isEqualTo("https://velog.io/@velopert/2022.log");

    JsonNode created =
        step(
            "blog-import-connect",
            "POST",
            SOURCES,
            owner,
            Map.of("url", "velog.io/@velopert"),
            201);
    long id = created.path("id").asLong();
    String code = created.path("verificationCode").asString();
    assertThat(created.path("status").asString()).isEqualTo("PENDING_VERIFICATION");
    assertThat(code).matches("kurl-[2-9A-HJKMNP-Z]{6}");
    assertThat(created.path("progress").isNull()).isTrue();

    step(
        "blog-import-verify-code-missing",
        "POST",
        SOURCES + "/" + id + "/verify",
        owner,
        null,
        422);

    serve(velog.replace("<description>CEO @", "<description>" + code + " CEO @"));
    JsonNode verified =
        step("blog-import-verify", "POST", SOURCES + "/" + id + "/verify", owner, null, 200);
    assertThat(verified.path("status").asString()).isEqualTo("IMPORTING");
    assertThat(verified.path("verificationCode").isNull()).isTrue();
    assertThat(verified.path("progress").path("total").asInt()).isEqualTo(3);
    assertThat(count("posts", "import_source_id = ? AND status = 'PUBLISHED'", id)).isEqualTo(3);
    assertThat(count("note", "user_id = ?", owner.id())).isZero();

    step(
        "blog-import-connect-taken",
        "POST",
        SOURCES,
        other,
        Map.of("url", "https://velog.io/@velopert/2022.log"),
        409);

    JsonNode listed = step("blog-import-list", "GET", SOURCES, owner, null, 200).path("items");
    assertThat(listed).hasSize(1);
    assertThat(listed.get(0).path("status").asString()).isEqualTo("SYNCING");
    assertThat(listed.get(0).path("importedCount").asInt()).isEqualTo(3);
    assertThat(listed.get(0).path("progress").path("done").asInt()).isEqualTo(3);
    step("blog-import-get", "GET", SOURCES + "/" + id, owner, null, 200);

    String slug =
        jdbc.queryForObject(
            "SELECT slug FROM posts WHERE source_url = ?",
            String.class,
            "https://velog.io/@velopert/veltrends-dev-review");
    JsonNode detail =
        step(
            "blog-import-public-post",
            "GET",
            "/api/v1/public/profiles/" + owner.username() + "/posts/" + slug,
            null,
            null,
            200);
    JsonNode source = detail.path("post").path("source");
    assertThat(source.path("platform").asString()).isEqualTo("VELOG");
    assertThat(source.path("originalUrl").asString())
        .isEqualTo("https://velog.io/@velopert/veltrends-dev-review");
    assertThat(source.path("shortUrl").asString()).isNotBlank();
    assertThat(source.path("excerptOnly").asBoolean()).isFalse();
    List<String> types = new ArrayList<>();
    detail.path("blocks").forEach(block -> types.add(block.path("type").asString()));
    assertThat(types).contains("CODE", "H2", "IMAGE");

    JsonNode hidden =
        step(
            "blog-import-hide-from-discover",
            "PATCH",
            SOURCES + "/" + id,
            owner,
            Map.of("showInDiscover", false),
            200);
    assertThat(hidden.path("showInDiscover").asBoolean()).isFalse();
    assertThat(count("posts", "import_source_id = ? AND hidden_from_discover = TRUE", id))
        .isEqualTo(3);

    String newPost =
        "<item><title><![CDATA[새 글]]></title>"
            + "<link>https://velog.io/@velopert/new-post</link>"
            + "<guid>https://velog.io/@velopert/new-post</guid>"
            + "<pubDate>"
            + DateTimeFormatter.RFC_1123_DATE_TIME
                .withLocale(Locale.ENGLISH)
                .format(Instant.now().plusSeconds(2).atOffset(ZoneOffset.UTC))
            + "</pubDate>"
            + "<description><![CDATA[<p>@Transactional 을 붙인 메서드가 하나 더 생겼다.</p>"
            + "<pre><code class=\"language-java\">class A {}</code></pre>]]></description>"
            + "</item>";
    serve(velog.replaceFirst("<item>", newPost + "<item>"));
    deliverBackgroundWork("blog-import-sync-new-post", () -> runner.run(id));
    assertThat(count("posts", "import_source_id = ?", id)).isEqualTo(4);
    String note =
        jdbc.queryForObject("SELECT body FROM note WHERE user_id = ?", String.class, owner.id());
    assertThat(note).startsWith("새 글\n").contains("＠Transactional").doesNotContain("@Trans");

    long deleted =
        jdbc.queryForObject(
            "SELECT id FROM posts WHERE source_url = ?",
            Long.class,
            "https://velog.io/@velopert/2022.log");
    step(
        "blog-import-owner-deletes-a-post", "DELETE", "/api/v1/posts/" + deleted, owner, null, 204);
    deliverBackgroundWork("blog-import-sync-nothing-new", () -> runner.run(id));
    assertThat(count("posts", "import_source_id = ?", id)).isEqualTo(3);
    assertThat(count("note", "user_id = ?", owner.id())).isEqualTo(1);

    step(
        "blog-import-disconnect-keep",
        "DELETE",
        SOURCES + "/" + id + "?keepPosts=true",
        owner,
        null,
        204);
    assertThat(count("posts", "import_source_id = ?", id)).isEqualTo(3);

    JsonNode reconnected =
        step(
            "blog-import-reconnect",
            "POST",
            SOURCES,
            owner,
            Map.of("url", "velog.io/@velopert"),
            201);
    assertThat(reconnected.path("id").asLong()).isEqualTo(id);
    assertThat(reconnected.path("status").asString()).isEqualTo("PENDING_VERIFICATION");

    step(
        "blog-import-delete-with-posts",
        "DELETE",
        SOURCES + "/" + id + "?keepPosts=false",
        owner,
        null,
        204);
    assertThat(count("posts", "source_url LIKE 'https://velog.io/@velopert/%'")).isZero();
    assertThat(count("blog_import_source", "id = ?", id)).isZero();
    assertThat(count("link", "user_id = ?", owner.id())).isEqualTo(4);
  }
}
