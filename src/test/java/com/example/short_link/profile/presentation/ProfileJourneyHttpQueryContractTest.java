package com.example.short_link.profile.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.testsupport.AccountHttpJourneySupport;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Real profile state and read models; only external object/oEmbed provider boundaries are replaced.
 */
@TestPropertySource(properties = "short-link.avatar.public-base-url=https://cdn.example.test")
class ProfileJourneyHttpQueryContractTest extends AccountHttpJourneySupport {
  @Autowired private LinkRepository links;
  @MockitoBean private ObjectStorage storage;

  @MockitoBean(name = "oembedRestClient")
  private RestClient oembedClient;

  private static final String MY_PROFILE = "/api/v1/users/me/profile";

  @BeforeEach
  void configureObjectProviderBoundary() {
    when(storage.isConfigured()).thenReturn(true);
    when(storage.objectSize(anyString())).thenReturn(Optional.of(1024L));
    when(storage.presignPut(anyString(), anyString(), any()))
        .thenAnswer(call -> "https://upload.example.test/" + call.getArgument(0));
  }

  @Test
  void authorEditsProfilePublishesOrderedBlocksAndRemovesOne() throws Exception {
    assertThat(
            body(call("profile-owner-read", "GET", MY_PROFILE, null, token, 200))
                .path("username")
                .asText())
        .isEqualTo(owner.getUsername());
    call(
        "profile-owner-update",
        "PUT",
        MY_PROFILE,
        Map.of("bio", "A profile edited through HTTP", "theme", "ocean", "hideFollowerCount", true),
        token,
        200);
    assertThat(jdbc.queryForObject("select bio from users where id=?", String.class, owner.getId()))
        .isEqualTo("A profile edited through HTTP");
    String blocks = MY_PROFILE + "/blocks";
    long first =
        body(call(
                "profile-block-create",
                "POST",
                blocks,
                Map.of("type", "TEXT", "content", "First section"),
                token,
                200))
            .path("id")
            .asLong();
    long second =
        body(call(
                "profile-second-block-create",
                "POST",
                blocks,
                Map.of("type", "TEXT", "content", "Second section"),
                token,
                200))
            .path("id")
            .asLong();
    var updatedBlock =
        body(
            call(
                "profile-block-update",
                "PATCH",
                blocks + "/" + first,
                Map.of("content", "Edited section"),
                token,
                200));
    String committedContent =
        jdbc.queryForObject("select content from profile_block where id=?", String.class, first);
    var committedText = json.readTree(committedContent);
    assertThat(committedText.path("body").asText()).isEqualTo("Edited section");
    assertThat(committedText.path("layout").asText()).isEqualTo("inline");
    assertThat(committedText.path("accent").isNull()).isTrue();
    assertThat(committedText.path("icon").isNull()).isTrue();
    assertThat(updatedBlock.path("content").asText()).isEqualTo(committedContent);
    call(
        "profile-block-cross-user-update-rejected",
        "PATCH",
        blocks + "/" + first,
        Map.of("content", "intrusion"),
        strangerToken,
        404);
    assertThat(
            jdbc.queryForObject(
                "select content from profile_block where id=?", String.class, first))
        .isEqualTo(committedContent);
    call(
        "profile-block-order",
        "PUT",
        MY_PROFILE + "/order",
        Map.of(
            "items",
            List.of(
                Map.of("kind", "BLOCK", "id", String.valueOf(second)),
                Map.of("kind", "BLOCK", "id", String.valueOf(first)))),
        token,
        200);
    assertThat(
            jdbc.queryForList(
                "select id from profile_block where user_id=? order by profile_order",
                Long.class,
                owner.getId()))
        .containsExactly(second, first);
    String publicPath = "/api/v1/public/profiles/" + owner.getUsername();
    var publicProfile = body(call("profile-public-read", "GET", publicPath, null, null, 200));
    assertThat(publicProfile.path("bio").asText()).isEqualTo("A profile edited through HTTP");
    assertThat(publicProfile.path("entries").get(0).path("id").asLong()).isEqualTo(second);
    var handles =
        call(
            "profile-public-directory",
            "GET",
            "/api/v1/public/profiles?page=0&size=100",
            null,
            null,
            200);
    assertThat(handles.body()).contains(owner.getUsername());
    call(
        "profile-block-cross-user-delete-rejected",
        "DELETE",
        blocks + "/" + first,
        null,
        strangerToken,
        404);
    call("profile-block-delete", "DELETE", blocks + "/" + first, null, token, 200);
    assertThat(count("select count(*) from profile_block where id=?", first)).isZero();
    var afterDelete =
        body(call("profile-public-read-after-delete", "GET", publicPath, null, null, 200));
    assertThat(afterDelete.path("entries").size()).isEqualTo(1);
  }

  @Test
  void ownerShowsAndHighlightsLinkAndStrangerCannotChangeIt() throws Exception {
    LinkEntity link =
        transactions.execute(
            status ->
                links.save(
                    new LinkEntity(
                        "https://example.com/profile", "pr" + owner.getId(), owner.getId(), null)));
    String path = "/api/v1/links/" + link.getShortCode().value() + "/profile";
    call("profile-link-show", "PUT", path, Map.of("show", true), token, 200);
    assertThat(
            count(
                "select count(*) from link_profile_binding where link_id=? and profile_order is not null",
                link.getId()))
        .isEqualTo(1);
    call(
        "profile-link-highlight",
        "PUT",
        path + "/highlight",
        Map.of("highlighted", true),
        token,
        200);
    assertThat(
            count(
                "select count(*) from link_profile_binding where link_id=? and profile_highlighted=true",
                link.getId()))
        .isEqualTo(1);
    call(
        "profile-link-cross-user-highlight-rejected",
        "PUT",
        path + "/highlight",
        Map.of("highlighted", false),
        strangerToken,
        404);
    call(
        "profile-link-cross-user-hide-rejected",
        "PUT",
        path,
        Map.of("show", false),
        strangerToken,
        404);
    var visible =
        call(
            "profile-public-with-link",
            "GET",
            "/api/v1/public/profiles/" + owner.getUsername(),
            null,
            null,
            200);
    assertThat(visible.body()).contains(link.getShortCode().value(), "https://example.com/profile");
    call("profile-link-hide", "PUT", path, Map.of("show", false), token, 200);
    assertThat(
            count(
                "select count(*) from link_profile_binding where link_id=? and profile_order is null",
                link.getId()))
        .isEqualTo(1);
  }

  @Test
  void visitorSubmitsLeadAndOwnerExportsOptsOutAndDeletesIt() throws Exception {
    long block =
        body(call(
                "profile-email-form-create",
                "POST",
                MY_PROFILE + "/blocks",
                Map.of("type", "EMAIL_FORM", "content", "{\"title\":\"Newsletter\"}"),
                token,
                200))
            .path("id")
            .asLong();
    Map<String, Object> lead = Map.of("blockId", block, "email", "Reader@example.com");
    call("profile-email-lead-submit", "POST", "/api/v1/public/email-leads", lead, null, 200);
    call(
        "profile-email-lead-submit-duplicate",
        "POST",
        "/api/v1/public/email-leads",
        lead,
        null,
        200);
    assertThat(
            count(
                "select count(*) from email_lead where block_id=? and email='reader@example.com'",
                block))
        .isEqualTo(1);
    String path = "/api/v1/users/me/email-leads";
    var list = body(call("profile-email-lead-list", "GET", path, null, token, 200));
    long id = list.path("items").get(0).path("id").asLong();
    assertThat(list.path("items").get(0).path("email").asText()).isEqualTo("reader@example.com");
    assertThat(
            body(call("profile-email-lead-other-owner-list", "GET", path, null, strangerToken, 200))
                .path("items")
                .size())
        .isZero();
    var csv = call("profile-email-lead-export", "GET", path + "/export.csv", null, token, 200);
    assertThat(csv.body()).contains("email,block_id", "reader@example.com");
    call(
        "profile-email-lead-cross-user-optout-rejected",
        "PATCH",
        path + "/" + id,
        Map.of("optedOut", true),
        strangerToken,
        404);
    call(
        "profile-email-lead-optout",
        "PATCH",
        path + "/" + id,
        Map.of("optedOut", true),
        token,
        200);
    assertThat(count("select count(*) from email_lead where id=? and opted_out=true", id))
        .isEqualTo(1);
    assertThat(
            call("profile-email-lead-export-active", "GET", path + "/export.csv", null, token, 200)
                .body())
        .doesNotContain("reader@example.com");
    call(
        "profile-email-lead-cross-user-delete-rejected",
        "DELETE",
        path + "/" + id,
        null,
        strangerToken,
        404);
    call("profile-email-lead-delete", "DELETE", path + "/" + id, null, token, 200);
    assertThat(count("select count(*) from email_lead where id=?", id)).isZero();
  }

  @Test
  void actualVisitAppearsInOwnerStatsAndOnlyBecomesPublicAfterOptIn() throws Exception {
    String publicPath = "/api/v1/public/profiles/" + owner.getUsername();
    callWithHeaders(
        "profile-visit-record",
        "POST",
        publicPath + "/visit?src=blog&utm_source=newsletter",
        null,
        Map.of(
            "User-Agent",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36",
            "Sec-GPC",
            "1"),
        204);
    assertThat(
            count(
                "select count(*) from profile_visit_event where profile_user_id=? and utm_source='newsletter' and visitor_hash is null",
                owner.getId()))
        .isEqualTo(1);
    long human =
        count(
            "select count(*) from profile_visit_event where profile_user_id=? and is_bot=false",
            owner.getId());
    assertThat(
            body(call("profile-owner-stats", "GET", MY_PROFILE + "/stats", null, token, 200))
                .path("totalVisits")
                .asLong())
        .isEqualTo(1);
    assertThat(
            body(call(
                    "profile-owner-stats-summary",
                    "GET",
                    MY_PROFILE + "/stats/summary",
                    null,
                    token,
                    200))
                .path("allTime")
                .asLong())
        .isEqualTo(human);
    assertThat(
            body(call(
                    "profile-stats-visibility-read",
                    "GET",
                    MY_PROFILE + "/stats/visibility",
                    null,
                    token,
                    200))
                .path("isPublic")
                .asBoolean())
        .isFalse();
    call("profile-public-stats-private-rejected", "GET", publicPath + "/stats", null, null, 404);
    call(
        "profile-stats-visibility-enable",
        "PATCH",
        MY_PROFILE + "/stats/visibility",
        Map.of("isPublic", true),
        token,
        200);
    assertThat(
            count("select count(*) from users where id=? and is_stats_public=true", owner.getId()))
        .isEqualTo(1);
    assertThat(
            body(call("profile-public-stats-read", "GET", publicPath + "/stats", null, null, 200))
                .path("totalVisits")
                .asLong())
        .isEqualTo(1);
  }

  @Test
  void profileImageProviderCommitCanBeSavedIntoARealProfileBlock() throws Exception {
    String path = MY_PROFILE + "/images";
    String key =
        body(call(
                "profile-image-presign",
                "POST",
                path + "/presigned-url",
                Map.of("contentType", "image/png"),
                token,
                200))
            .path("key")
            .asText();
    assertThat(key).startsWith("profile-images/" + owner.getId() + "/");
    call(
        "profile-image-cross-user-commit-rejected",
        "PUT",
        path,
        Map.of("key", key),
        strangerToken,
        400);
    String imageUrl =
        body(call("profile-image-commit", "PUT", path, Map.of("key", key), token, 200))
            .path("imageUrl")
            .asText();
    long block =
        body(call(
                "profile-image-block-create",
                "POST",
                MY_PROFILE + "/blocks",
                Map.of("type", "IMAGE", "content", imageUrl),
                token,
                200))
            .path("id")
            .asLong();
    assertThat(
            jdbc.queryForObject(
                "select content from profile_block where id=?", String.class, block))
        .isEqualTo(imageUrl);
  }

  @Test
  void oembedFetchesProviderMetadataAndReusesRealRedisCache() throws Exception {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer provider = MockRestServiceServer.bindTo(builder).build();
    provider
        .expect(anything())
        .andRespond(
            withSuccess(
                "{\"type\":\"video\",\"title\":\"Fixture video\",\"author_name\":\"Fixture author\"}",
                MediaType.APPLICATION_JSON));
    RestClient client = builder.build();
    when(oembedClient.get()).thenAnswer(invocation -> client.get());
    String path =
        "/api/v1/public/oembed?url="
            + URLEncoder.encode(
                "https://www.youtube.com/watch?v=" + owner.getUsername(), StandardCharsets.UTF_8);
    var first = body(call("profile-oembed-fetch", "GET", path, null, null, 200));
    assertThat(first.path("title").asText()).isEqualTo("Fixture video");
    var second = body(call("profile-oembed-cached", "GET", path, null, null, 200));
    assertThat(second).isEqualTo(first);
    provider.verify();
    call(
        "profile-oembed-unsupported-rejected",
        "GET",
        "/api/v1/public/oembed?url=https%3A%2F%2Fexample.com",
        null,
        null,
        422);
  }
}
