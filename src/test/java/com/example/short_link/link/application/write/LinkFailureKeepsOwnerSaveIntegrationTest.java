package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.short_link.cta.application.write.CreateCtaCommand;
import com.example.short_link.cta.application.write.CreateCtaUseCase;
import com.example.short_link.cta.application.write.UpdateCtaCommand;
import com.example.short_link.cta.application.write.UpdateCtaUseCase;
import com.example.short_link.cta.domain.CtaEntity;
import com.example.short_link.cta.domain.CtaPurpose;
import com.example.short_link.cta.domain.CtaStyle;
import com.example.short_link.cta.domain.repository.CtaRepository;
import com.example.short_link.event.application.write.CreateEventCommand;
import com.example.short_link.event.application.write.CreateEventUseCase;
import com.example.short_link.event.domain.ContactField;
import com.example.short_link.link.safety.application.UrlSafetyChecker;
import com.example.short_link.room.application.write.RoomCommandService;
import com.example.short_link.room.application.write.RoomInviteService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// Not @Transactional: the owner's transaction has to commit for the test to see what it kept.
@SpringBootTest(properties = "short-link.link-quota.authenticated=0")
@ActiveProfiles("test")
class LinkFailureKeepsOwnerSaveIntegrationTest {

  @MockitoBean private UrlSafetyChecker safety;
  @Autowired private CreateCtaUseCase createCta;
  @Autowired private UpdateCtaUseCase updateCta;
  @Autowired private CtaRepository ctas;
  @Autowired private CreateEventUseCase createEvent;
  @Autowired private RoomCommandService rooms;
  @Autowired private RoomInviteService roomInvites;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  private Long userId;
  private String suffix;

  @BeforeEach
  void anOwnerWithNoLinksLeft() {
    suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    UserEntity user = new UserEntity("lq" + suffix + "@example.com", "google", "lq" + suffix);
    user.claimUsername("lq" + suffix);
    userId = users.save(user).getId();
    when(safety.isSafe(anyString())).thenReturn(true);
  }

  @AfterEach
  void deleteTheOwnersRows() {
    jdbc.update("DELETE FROM room_invite WHERE creator_id = ?", userId);
    jdbc.update("DELETE FROM room_member WHERE user_id = ?", userId);
    jdbc.update("DELETE FROM room WHERE owner_id = ?", userId);
    jdbc.update("DELETE FROM cta WHERE user_id = ?", userId);
    jdbc.update("UPDATE event SET primary_link_id = NULL WHERE user_id = ?", userId);
    jdbc.update(
        "DELETE l FROM event_link l JOIN event e ON e.id = l.event_id WHERE e.user_id = ?", userId);
    jdbc.update("DELETE FROM event WHERE user_id = ?", userId);
    jdbc.update("DELETE FROM link WHERE user_id = ?", userId);
    jdbc.update("DELETE FROM users WHERE id = ?", userId);
  }

  @Test
  void aCtaIsSavedUntrackedWhenItsLinkIsOverTheQuota() {
    String booking = "https://example.com/book-" + suffix;

    createCta.execute(
        new CreateCtaCommand(userId, "예약", booking, CtaStyle.PRIMARY, CtaPurpose.BOOKING));

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM cta WHERE user_id = ? AND url = ? AND tracked_short_code IS NULL",
                Long.class,
                userId,
                booking))
        .isEqualTo(1);
  }

  @Test
  void aCtaKeepsItsNewUrlWhenTheNewLinkIsOverTheQuota() {
    Long ctaId =
        ctas.save(
                new CtaEntity(
                    userId, "예약", "https://example.com/old", CtaStyle.PRIMARY, CtaPurpose.BOOKING))
            .getId();
    String moved = "https://example.com/moved-" + suffix;

    updateCta.execute(new UpdateCtaCommand(userId, ctaId, null, moved, null, null));

    assertThat(jdbc.queryForObject("SELECT url FROM cta WHERE id = ?", String.class, ctaId))
        .isEqualTo(moved);
  }

  @Test
  void anEventIsSavedWithoutItsLinkWhenTheLinkIsOverTheQuota() {
    createEvent.execute(
        new CreateEventCommand(
            userId,
            "스터디",
            null,
            Instant.now().plus(7, ChronoUnit.DAYS),
            null,
            "Asia/Seoul",
            null,
            null,
            null,
            null,
            null,
            ContactField.EMAIL,
            List.of()));

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM event WHERE user_id = ? AND primary_link_id IS NULL",
                Long.class,
                userId))
        .isEqualTo(1);
  }

  @Test
  void aRoomInviteIsSavedWithoutItsLinkWhenTheLinkIsOverTheQuota() {
    Long roomId = rooms.create(userId, "스터디방").id();

    roomInvites.create(userId, roomId, null, null, false);

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM room_invite WHERE room_id = ? AND short_code IS NULL",
                Long.class,
                roomId))
        .isEqualTo(1);
  }
}
