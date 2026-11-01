package com.example.short_link.note.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.common.federation.CustomEmoji;
import com.example.short_link.common.note.NoteVisibility;
import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.federation.domain.RemoteActorDocument;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.note.application.write.NoteComposer;
import com.example.short_link.note.application.write.NoteDraft;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RemoteEmojiIntegrationTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final CustomEmoji NEOGHOST =
      new CustomEmoji("neoghost", "https://files.em.example/emoji/neoghost.png");
  private static final CustomEmoji VERIFIED =
      new CustomEmoji("verified", "https://files.em.example/emoji/verified.png");

  @Autowired private MockMvc mvc;
  @Autowired private RemoteNotes remoteNotes;
  @Autowired private RemoteActorRepository actors;
  @Autowired private NoteComposer composer;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;
  @PersistenceContext private EntityManager em;

  private final String run = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  private long actor;

  @BeforeEach
  void anAccountElsewhereWithAnEmojiInItsName() {
    String uri = "https://em.example/users/" + run;
    actor =
        actors
            .saveAndFlush(
                new RemoteActorEntity(
                    new RemoteActorDocument(
                        uri,
                        uri + "#main-key",
                        "pem",
                        uri + "/inbox",
                        null,
                        "ged" + run,
                        "em.example",
                        "https://em.example/@ged" + run,
                        "Ged :verified:",
                        null,
                        false,
                        false,
                        List.of(VERIFIED)),
                    Instant.now()))
            .getId();
  }

  @Test
  void aNoteFromElsewhereShowsItsEmojisAndItsAuthorsAsTheServerSentThem() throws Exception {
    long note = receive("boo", "\"Wait, it has pockets ...\" :neoghost:", null, List.of(NEOGHOST));
    reread();

    assertThat(
            jdbc.queryForObject(
                "SELECT JSON_LENGTH(remote_emojis) FROM note WHERE id = ?", Integer.class, note))
        .isOne();
    assertThat(
            jdbc.queryForObject(
                "SELECT JSON_LENGTH(emojis) FROM federation_remote_actor WHERE id = ?",
                Integer.class,
                actor))
        .isOne();
    JsonNode shown = shown(note);
    assertThat(emojis(shown.get("emojis"))).containsExactly(NEOGHOST);
    assertThat(emojis(shown.path("author").get("emojis"))).containsExactly(VERIFIED);
    assertThat(shown.path("author").path("displayName").asString()).isEqualTo("Ged :verified:");
  }

  @Test
  void anUpdateReplacesTheEmojisWithoutMakingTheNoteEdited() throws Exception {
    long note = receive("changing", "boo :neoghost:", null, List.of(NEOGHOST));
    reread();
    CustomEmoji redrawn = new CustomEmoji("neoghost", "https://files.em.example/emoji/v2.png");

    assertThat(
            remoteNotes.revise(
                actor,
                note,
                new RemoteNotes.Revision(
                    "boo :neoghost:",
                    null,
                    false,
                    null,
                    List.of(),
                    null,
                    RemoteNotes.Shape.NOTE,
                    null,
                    List.of(redrawn))))
        .isTrue();
    reread();

    JsonNode shown = shown(note);
    assertThat(emojis(shown.get("emojis"))).containsExactly(redrawn);
    assertThat(shown.get("editedAt").isNull()).isTrue();

    remoteNotes.revise(
        actor,
        note,
        new RemoteNotes.Revision(
            "boo, plainly",
            null,
            false,
            null,
            List.of(),
            null,
            RemoteNotes.Shape.NOTE,
            null,
            List.of()));
    reread();

    assertThat(
            jdbc.queryForObject(
                "SELECT remote_emojis IS NULL FROM note WHERE id = ?", Boolean.class, note))
        .isTrue();
    assertThat(shown(note).get("emojis").isEmpty()).isTrue();
  }

  @Test
  void aQuotedNoteFromElsewhereCarriesItsOwnEmojis() throws Exception {
    long quoted = receive("quoted", "boo :neoghost:", null, List.of(NEOGHOST));
    long quoting = receive("quoting", "look at this", quoted, List.of());
    reread();

    JsonNode shown = shown(quoting);
    assertThat(shown.get("emojis").isEmpty()).isTrue();
    assertThat(emojis(shown.path("quotedNote").get("emojis"))).containsExactly(NEOGHOST);
    assertThat(emojis(shown.path("quotedNote").path("author").get("emojis")))
        .containsExactly(VERIFIED);
  }

  @Test
  void aNoteKeptBeforeEmojisAndAMembersNoteHaveNone() throws Exception {
    long older = receive("older", "boo :neoghost:", null, List.of(NEOGHOST));
    jdbc.update("UPDATE note SET remote_emojis = NULL WHERE id = ?", older);
    jdbc.update("UPDATE federation_remote_actor SET emojis = NULL WHERE id = ?", actor);
    UserEntity member = new UserEntity(run + "@x.com", "google", "g-" + run);
    member.claimUsername("em" + run);
    long writer = users.save(member).getId();
    NoteView written =
        composer.create(writer, new NoteDraft("hi :neoghost:", List.of(), null, null));
    reread();

    assertThat(written.emojis()).isEmpty();
    assertThat(written.author().emojis()).isEmpty();
    JsonNode kept = shown(older);
    assertThat(kept.get("emojis").isArray()).isTrue();
    assertThat(kept.get("emojis").isEmpty()).isTrue();
    assertThat(kept.path("author").get("emojis").isEmpty()).isTrue();
    JsonNode local = shown(written.id());
    assertThat(local.get("emojis").isEmpty()).isTrue();
    assertThat(local.path("author").get("emojis").isEmpty()).isTrue();
  }

  private long receive(String name, String body, Long quotedNoteId, List<CustomEmoji> emojis) {
    return remoteNotes
        .receive(
            new RemoteNotes.Received(
                actor,
                "https://em.example/users/" + run + "/statuses/" + name,
                null,
                body,
                null,
                false,
                NoteVisibility.PUBLIC,
                null,
                null,
                null,
                List.of(),
                List.of(),
                null,
                RemoteNotes.Shape.NOTE,
                false,
                quotedNoteId,
                emojis))
        .orElseThrow();
  }

  private void reread() {
    em.flush();
    em.clear();
  }

  private JsonNode shown(long noteId) throws Exception {
    String body =
        mvc.perform(get("/api/v1/public/notes/" + noteId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(body).get("note");
  }

  private static List<CustomEmoji> emojis(JsonNode array) {
    assertThat(array).isNotNull();
    assertThat(array.isArray()).isTrue();
    List<CustomEmoji> emojis = new ArrayList<>();
    for (JsonNode item : array) {
      emojis.add(new CustomEmoji(item.get("shortcode").asString(), item.get("url").asString()));
    }
    return emojis;
  }
}
