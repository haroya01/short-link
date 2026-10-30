package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.short_link.campaign.application.CampaignBatchService;
import com.example.short_link.campaign.application.write.CampaignBatchBulkCommand;
import com.example.short_link.campaign.application.write.CampaignBatchCreateCommand;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignPostEndAction;
import com.example.short_link.campaign.domain.repository.CampaignRepository;
import com.example.short_link.cta.application.write.CreateCtaCommand;
import com.example.short_link.cta.application.write.CreateCtaUseCase;
import com.example.short_link.cta.application.write.UpdateCtaCommand;
import com.example.short_link.cta.application.write.UpdateCtaUseCase;
import com.example.short_link.cta.domain.CtaPurpose;
import com.example.short_link.cta.domain.CtaStyle;
import com.example.short_link.event.application.write.CreateEventCommand;
import com.example.short_link.event.application.write.CreateEventUseCase;
import com.example.short_link.event.domain.ContactField;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.link.safety.application.UrlSafetyChecker;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Not @Transactional: the test asks whether a transaction is open while a destination is checked.
@SpringBootTest
@ActiveProfiles("test")
class LinkChecksOutsideTransactionIntegrationTest {

  @MockitoBean private UrlSafetyChecker safety;
  @Autowired private CampaignRepository campaigns;
  @Autowired private CampaignBatchService batches;
  @Autowired private CreateCtaUseCase createCta;
  @Autowired private UpdateCtaUseCase updateCta;
  @Autowired private CreateEventUseCase createEvent;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  private final List<String> checked = new CopyOnWriteArrayList<>();
  private Long userId;
  private String suffix;

  @BeforeEach
  void anOwnerWhoseDestinationsAreWatched() {
    suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    UserEntity user = new UserEntity("lc" + suffix + "@example.com", "google", "lc" + suffix);
    user.claimUsername("lc" + suffix);
    userId = users.save(user).getId();
    when(safety.isSafe(anyString()))
        .thenAnswer(
            inv -> {
              checked.add(inv.getArgument(0) + " " + where());
              return true;
            });
  }

  private static String where() {
    return TransactionSynchronizationManager.isActualTransactionActive()
        ? "inside a transaction"
        : "outside";
  }

  @AfterEach
  void deleteTheOwnersRows() {
    jdbc.update(
        "DELETE b FROM campaign_batch b JOIN campaign c ON c.id = b.campaign_id WHERE c.owner_id = ?",
        userId);
    jdbc.update("DELETE FROM campaign WHERE owner_id = ?", userId);
    jdbc.update("DELETE FROM cta WHERE user_id = ?", userId);
    jdbc.update("UPDATE event SET primary_link_id = NULL WHERE user_id = ?", userId);
    jdbc.update(
        "DELETE l FROM event_link l JOIN event e ON e.id = l.event_id WHERE e.user_id = ?", userId);
    jdbc.update("DELETE FROM event WHERE user_id = ?", userId);
    jdbc.update("DELETE FROM link WHERE user_id = ?", userId);
    jdbc.update("DELETE FROM users WHERE id = ?", userId);
  }

  private Long campaign() {
    return campaigns
        .save(
            new CampaignEntity(
                userId,
                "전단",
                Instant.now(),
                Instant.now().plus(30, ChronoUnit.DAYS),
                "https://example.com/" + suffix,
                CampaignPostEndAction.KEEP,
                null,
                null))
        .getId();
  }

  private static CampaignBatchCreateCommand row(String name, String destination) {
    return new CampaignBatchCreateCommand(name, null, null, 1, destination, null);
  }

  @Test
  void aCampaignBatchChecksItsDestinationBeforeItsTransaction() {
    Long campaignId = campaign();
    String one = "https://example.com/one-" + suffix;
    String two = "https://example.com/two-" + suffix;
    String three = "https://example.com/three-" + suffix;

    batches.create(campaignId, userId, row("역 앞", one));
    batches.createBulk(
        campaignId,
        userId,
        new CampaignBatchBulkCommand(List.of(row("정문", two), row("후문", three))));

    assertThat(checked).containsExactly(one + " outside", two + " outside", three + " outside");
  }

  @Test
  void aCtaChecksItsTrackedDestinationBeforeItsTransaction() {
    String booking = "https://example.com/book-" + suffix;
    String moved = "https://example.com/book2-" + suffix;

    Long ctaId =
        createCta
            .execute(
                new CreateCtaCommand(userId, "예약", booking, CtaStyle.PRIMARY, CtaPurpose.BOOKING))
            .getId();
    updateCta.execute(new UpdateCtaCommand(userId, ctaId, null, moved, null, null));

    assertThat(checked).containsExactly(booking + " outside", moved + " outside");
  }

  @Test
  void anEventsOwnPageGetsItsLinkWithoutAskingSafeBrowsing() {
    EventEntity event =
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

    assertThat(checked).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_link WHERE event_id = ?", Long.class, event.getId()))
        .isEqualTo(1);
  }
}
