package com.example.short_link.event.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.mail.MailSender;
import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.link.presentation.LinkJourneyHttpSupport;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Event storage, questions, capacity, cancellation and organizer reads all use Docker MySQL. */
class EventLifecycleHttpQueryContractTest extends LinkJourneyHttpSupport {
  @MockitoBean private MailSender outgoingMail;
  @MockitoBean private ObjectStorage remoteStorage;

  @Test
  void organizerPublishesAndAttendeeRegistersCancelsAndRegistersAgain() throws Exception {
    var created =
        request("event-create", owner, "POST", "/api/v1/events", eventBody("Meetup"), 201);
    long id = created.path("id").asLong();
    String slug = created.path("slug").asText();
    String path = "/api/v1/events/" + id;
    String publicPath = "/api/v1/public/events/" + slug;
    assertThat(number("SELECT user_id FROM event WHERE id = ?", id)).isEqualTo(owner.id());
    assertThat(number("SELECT COUNT(*) FROM event_question WHERE event_id = ?", id)).isEqualTo(1);
    assertThat(number("SELECT primary_link_id FROM event WHERE id = ?", id)).isPositive();
    assertThat(request("event-list", owner, "GET", "/api/v1/events", null, 200).size())
        .isEqualTo(1);
    assertThat(request("event-detail", owner, "GET", path, null, 200).path("slug").asText())
        .isEqualTo(slug);
    request("event-detail-foreign-denied", stranger, "GET", path, null, 403);
    var updated =
        request("event-update", owner, "PATCH", path, eventBody("Readable code meetup"), 200);
    assertThat(text("SELECT title FROM event WHERE id = ?", id)).isEqualTo("Readable code meetup");
    long questionId = updated.path("questions").get(0).path("id").asLong();
    var alias =
        request(
            "event-alias-link", owner, "POST", path + "/links", Map.of("label", "Newsletter"), 201);
    assertThat(number("SELECT COUNT(*) FROM event_link WHERE event_id = ?", id)).isEqualTo(2);
    assertThat(number("SELECT COUNT(*) FROM link WHERE id = ?", alias.path("linkId").asLong()))
        .isEqualTo(1);
    assertThat(request("event-public-detail", null, "GET", publicPath, null, 200).toString())
        .contains("Readable code meetup");

    Map<String, Object> registration =
        Map.of(
            "name",
            "Attendee",
            "contact",
            "attendee@example.test",
            "answers",
            Map.of(Long.toString(questionId), "Readable services"));
    var registered =
        request("event-register", null, "POST", publicPath + "/registrations", registration, 201);
    long registrationId = registered.path("registrationId").asLong();
    String cancelToken = registered.path("cancelToken").asText();
    assertThat(number("SELECT registration_count FROM event WHERE id = ?", id)).isEqualTo(1);
    assertThat(text("SELECT answers_json FROM event_registration WHERE id = ?", registrationId))
        .contains("Readable services");
    assertThat(
            text("SELECT cancel_token_hash FROM event_registration WHERE id = ?", registrationId))
        .hasSize(64)
        .isNotEqualTo(cancelToken);
    verify(outgoingMail).send(anyString(), anyString(), anyString());
    request(
        "event-register-duplicate", null, "POST", publicPath + "/registrations", registration, 409);
    request(
        "event-register-capacity-full",
        null,
        "POST",
        publicPath + "/registrations",
        Map.of(
            "name",
            "Second attendee",
            "contact",
            "second@example.test",
            "answers",
            Map.of(Long.toString(questionId), "Testing")),
        409);
    assertThat(number("SELECT COUNT(*) FROM event_registration WHERE event_id = ?", id))
        .isEqualTo(1);
    assertThat(request("event-attendees", owner, "GET", path + "/attendees", null, 200).toString())
        .contains("attendee@example.test", "Readable services");
    assertThat(
            new String(
                raw("event-attendees-csv", owner, "GET", path + "/attendees.csv", null, null, 200)
                    .body(),
                StandardCharsets.UTF_8))
        .contains("attendee@example.test");
    assertThat(
            request("event-analytics", owner, "GET", path + "/analytics", null, 200)
                .path("totalRegistrations")
                .asLong())
        .isEqualTo(1);

    request(
        "event-registration-cancel",
        null,
        "POST",
        "/api/v1/public/events/registrations/cancel",
        Map.of("token", cancelToken),
        204);
    assertThat(text("SELECT status FROM event_registration WHERE id = ?", registrationId))
        .isEqualTo("CANCELED");
    assertThat(number("SELECT registration_count FROM event WHERE id = ?", id)).isZero();
    request(
        "event-registration-cancel-repeat",
        null,
        "POST",
        "/api/v1/public/events/registrations/cancel",
        Map.of("token", cancelToken),
        204);
    assertThat(number("SELECT registration_count FROM event WHERE id = ?", id)).isZero();
    var again =
        request(
            "event-register-after-cancel",
            null,
            "POST",
            publicPath + "/registrations",
            registration,
            201);
    assertThat(again.path("registrationId").asLong()).isEqualTo(registrationId);
    assertThat(again.path("cancelToken").asText()).isNotEqualTo(cancelToken);
    assertThat(number("SELECT registration_count FROM event WHERE id = ?", id)).isEqualTo(1);

    request("event-close", owner, "POST", path + "/status", Map.of("action", "CLOSE"), 200);
    assertThat(text("SELECT status FROM event WHERE id = ?", id)).isEqualTo("CLOSED");
    request(
        "event-register-closed", null, "POST", publicPath + "/registrations", registration, 409);
    request("event-reopen", owner, "POST", path + "/status", Map.of("action", "REOPEN"), 200);
    assertThat(text("SELECT status FROM event WHERE id = ?", id)).isEqualTo("OPEN");
    request("event-cancel", owner, "POST", path + "/status", Map.of("action", "CANCEL"), 200);
    assertThat(text("SELECT status FROM event WHERE id = ?", id)).isEqualTo("CANCELED");
  }

  @Test
  void organizerCommitsAStoredCoverWhileAnotherUserCannotAttachIt() throws Exception {
    // Object storage is a boundary fixture; ownership and the committed event row remain real.
    when(remoteStorage.isConfigured()).thenReturn(true);
    when(remoteStorage.presignPut(anyString(), anyString(), any()))
        .thenAnswer(call -> "https://storage.example.test/" + call.getArgument(0));
    when(remoteStorage.objectSize(anyString())).thenReturn(Optional.of(2048L));
    long id =
        request(
                "event-create-cover",
                owner,
                "POST",
                "/api/v1/events",
                eventBody("Cover meetup"),
                201)
            .path("id")
            .asLong();
    String path = "/api/v1/events/" + id;
    var presigned =
        request(
            "event-cover-presign",
            owner,
            "POST",
            path + "/cover/presign",
            Map.of("contentType", "image/png"),
            200);
    String key = presigned.path("key").asText();
    assertThat(key).startsWith("event-covers/" + owner.id() + "/" + id + "/");
    request(
        "event-cover-foreign-denied",
        stranger,
        "POST",
        path + "/cover/commit",
        Map.of("key", key),
        403);
    assertThat(text("SELECT cover_image_key FROM event WHERE id = ?", id)).isNull();
    request("event-cover-commit", owner, "POST", path + "/cover/commit", Map.of("key", key), 200);
    assertThat(text("SELECT cover_image_key FROM event WHERE id = ?", id)).isEqualTo(key);
    assertThat(
            request("event-cover-read", owner, "GET", path, null, 200)
                .path("coverImageUrl")
                .asText())
        .endsWith(key);
    verify(remoteStorage).applyImmutableCacheControl(key);
  }

  private Map<String, Object> eventBody(String title) {
    return Map.of(
        "title",
        title,
        "startsAt",
        Instant.now().plus(7, ChronoUnit.DAYS).toString(),
        "timezone",
        "Asia/Seoul",
        "capacity",
        1,
        "contactField",
        "EMAIL",
        "questions",
        List.of(Map.of("type", "SHORT_TEXT", "label", "Topic", "required", true)));
  }
}
