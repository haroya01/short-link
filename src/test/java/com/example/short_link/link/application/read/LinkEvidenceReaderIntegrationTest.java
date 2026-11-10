package com.example.short_link.link.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.dto.LinkEvidence;
import com.example.short_link.link.application.dto.LinkEvidence.Destination;
import com.example.short_link.link.application.dto.LinkEvidence.DestinationKind;
import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class LinkEvidenceReaderIntegrationTest {

  private static final Instant FETCHED = Instant.parse("2026-11-09T01:02:03Z");

  @Autowired private LinkEvidenceReader reader;
  @Autowired private LinkRepository links;
  @Autowired private LinkDestinationRepository destinations;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  private final String run = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  private Long ownerId;

  @AfterEach
  void deleteCommittedRows() {
    jdbc.update("DELETE FROM link WHERE short_code LIKE ?", "ev" + run + "%");
    if (ownerId != null) {
      jdbc.update("DELETE FROM users WHERE id = ?", ownerId);
    }
  }

  @Test
  void aLinkShowsWhatItsPageSaysWhereItCanGoAndHowItsOwnersOtherLinksFared() {
    ownerId = users.save(new UserEntity("ev" + run + "@example.com", "google", "ev" + run)).getId();
    LinkEntity suspect = save("ev" + run + "a", "https://Bad.Example/pay?token=t1", ownerId);
    save("ev" + run + "b", "https://fine.example", ownerId);
    LinkEntity disabled = save("ev" + run + "c", "https://gone.example", ownerId);
    jdbc.update(
        "INSERT INTO link_moderation (link_id, reason, disabled_by, disabled_at, created_at,"
            + " updated_at) VALUES (?, 'ADMIN', NULL, NOW(6), NOW(6), NOW(6))",
        disabled.getId());
    jdbc.update(
        "UPDATE link SET og_title = 'Claim your prize', og_description = 'Enter your card',"
            + " og_image = 'https://cdn.example/og.png', og_fetch_status = 'OK',"
            + " og_fetched_at = ?, expired_redirect_url = 'https://after.example/x' WHERE id = ?",
        LocalDateTime.ofInstant(FETCHED, ZoneOffset.UTC),
        suspect.getId());
    destinations.save(
        new LinkDestinationEntity(
            suspect.linkId(), "https://ios.example/a", 3, "B", "KR", "mobile", "ios"));
    LinkDestinationEntity off =
        destinations.save(
            new LinkDestinationEntity(suspect.linkId(), "https://off.example", 1, null, null));
    off.update(null, null, null, false, null);
    destinations.save(off);

    LinkEvidence evidence = reader.find(new ShortCode("ev" + run + "a")).orElseThrow();

    assertThat(evidence.linkId()).isEqualTo(suspect.getId());
    assertThat(evidence.ownerId()).isEqualTo(ownerId);
    assertThat(evidence.introduction())
        .isEqualTo(
            new LinkEvidence.Introduction(
                "Claim your prize",
                "Enter your card",
                "OK",
                FETCHED,
                "https://cdn.example/og.png"));
    assertThat(evidence.destinations())
        .containsExactly(
            new Destination("bad.example", DestinationKind.ORIGINAL, null, null, null, null),
            new Destination("ios.example", DestinationKind.BRANCH, 3, "KR", "mobile", "ios"),
            new Destination("after.example", DestinationKind.AFTER_EXPIRY, null, null, null, null));
    assertThat(evidence.ownerLinks()).isEqualTo(new LinkEvidence.OwnerLinks(3, 1));
    assertThat(reader.image(new ShortCode("ev" + run + "a")))
        .contains(new LinkEvidence.Image(suspect.getId(), "https://cdn.example/og.png"));
  }

  @Test
  void anAnonymousLinkHasNoOwnerToCountAndAnUnfetchedPageSaysNothing() {
    LinkEntity anonymous = save("ev" + run + "d", "https://anon.example/x", null);

    LinkEvidence evidence = reader.find(new ShortCode("ev" + run + "d")).orElseThrow();

    assertThat(evidence.ownerId()).isNull();
    assertThat(evidence.ownerLinks()).isNull();
    assertThat(evidence.introduction())
        .isEqualTo(new LinkEvidence.Introduction(null, null, "PENDING", null, null));
    assertThat(evidence.destinations())
        .containsExactly(
            new Destination("anon.example", DestinationKind.ORIGINAL, null, null, null, null));
    assertThat(reader.image(new ShortCode("ev" + run + "d")))
        .contains(new LinkEvidence.Image(anonymous.getId(), null));
  }

  @Test
  void anUnknownCodeHasNoEvidence() {
    assertThat(reader.find(new ShortCode("ev" + run + "z"))).isEmpty();
    assertThat(reader.image(new ShortCode("ev" + run + "z"))).isEmpty();
  }

  private LinkEntity save(String code, String url, Long owner) {
    return links.save(new LinkEntity(url, new ShortCode(code), owner, null));
  }
}
