package com.example.short_link.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.federation.CustomEmoji;
import com.example.short_link.notification.domain.NotificationActor;
import com.example.short_link.notification.domain.repository.NotificationActorReader;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationActorReaderIntegrationTest {

  private static final String ZWSP = String.valueOf((char) 0x200B);

  @Autowired private NotificationActorReader reader;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void aMemberComesWithTheNameTheyChoseAndTheirUploadedAvatar() {
    UserEntity user = new UserEntity("nar-alice@x.com", "google", "g-nar-alice");
    user.claimUsername("nar-alice");
    user.updateDisplayName("앨리스");
    user.updateAvatar("https://cdn.kurl.example/avatars/1/a.jpg", "avatars/1/a.jpg");
    long id = users.save(user).getId();
    UserEntity plain = new UserEntity("nar-bob@x.com", "google", "g-nar-bob");
    plain.claimUsername("nar-bob");
    long plainId = users.save(plain).getId();

    assertThat(reader.resolve(Set.of(id, plainId)))
        .containsEntry(
            id,
            new NotificationActor(
                id,
                "nar-alice",
                "https://cdn.kurl.example/avatars/1/a.jpg",
                null,
                null,
                "앨리스",
                List.of()))
        .containsEntry(plainId, new NotificationActor(plainId, "nar-bob", null));
  }

  @Test
  void anAccountElsewhereComesWithItsNameAndTheEmojisItsServerDrawsInIt() {
    String uri = "https://nar.elsewhere.example/users/ged";
    jdbc.update(
        "INSERT INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox, username,"
            + " domain, display_name, avatar_url, emojis, indexable, fetched_at, created_at,"
            + " updated_at, outbox_checked_at) VALUES (?, ?, 'pem', ?, 'ged',"
            + " 'nar.elsewhere.example', ?, 'https://files.nar.elsewhere.example/ged.png', ?,"
            + " TRUE, NOW(6), NOW(6), NOW(6), NOW(6))",
        uri,
        uri + "#main-key",
        uri + "/inbox",
        "Ged " + ZWSP + ":verified:" + ZWSP,
        "[{\"shortcode\":\"verified\",\"url\":\"https://files.nar.elsewhere.example/v.png\"}]");
    long id =
        jdbc.queryForObject(
            "SELECT id FROM federation_remote_actor WHERE actor_uri = ?", Long.class, uri);

    assertThat(reader.resolveRemote(Set.of(id)))
        .containsEntry(
            id,
            new NotificationActor(
                null,
                "ged@nar.elsewhere.example",
                "https://files.nar.elsewhere.example/ged.png",
                uri,
                id,
                "Ged " + ZWSP + ":verified:" + ZWSP,
                List.of(new CustomEmoji("verified", "https://files.nar.elsewhere.example/v.png"))));
  }
}
