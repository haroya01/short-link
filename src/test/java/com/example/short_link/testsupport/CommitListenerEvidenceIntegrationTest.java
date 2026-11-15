package com.example.short_link.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.og.application.LinkOgFetchListener;
import com.example.short_link.link.og.application.LinkOgFetchService;
import com.example.short_link.link.og.application.dto.LinkOgFetchRequested;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class CommitListenerEvidenceIntegrationTest {

  private static final String LISTENER = "LinkOgFetchListener.onLinkCreated";
  private static final Path EVIDENCE = CommitListenerEvidenceConfiguration.DIRECTORY;
  private static final LinkOgFetchRequested REQUESTED =
      new LinkOgFetchRequested(ShortCode.of("probe01"), "https://example.com/");

  @Autowired private ApplicationEventPublisher events;
  @Autowired private TransactionTemplate transactions;
  @MockitoBean private LinkOgFetchService fetchService;

  @Test
  void aRolledBackPublicationIsNotADelivery() {
    int before = CommitListenerEvidenceConfiguration.deliveries(LISTENER);

    transactions.executeWithoutResult(
        status -> {
          events.publishEvent(REQUESTED);
          status.setRollbackOnly();
        });

    assertThat(CommitListenerEvidenceConfiguration.deliveries(LISTENER)).isEqualTo(before);
  }

  @Test
  void aCommittedPublicationIsADelivery() {
    int before = CommitListenerEvidenceConfiguration.deliveries(LISTENER);

    transactions.executeWithoutResult(status -> events.publishEvent(REQUESTED));

    assertThat(CommitListenerEvidenceConfiguration.deliveries(LISTENER)).isEqualTo(before + 1);
    assertThat(EVIDENCE.resolve("delivered").resolve(LISTENER + ".json")).exists();
  }

  @Test
  void aPublicationOutsideATransactionIsADropNamedAfterItsPublisher() throws Exception {
    int deliveries = CommitListenerEvidenceConfiguration.deliveries(LISTENER);
    int drops = CommitListenerEvidenceConfiguration.drops(LISTENER);

    events.publishEvent(REQUESTED);

    assertThat(CommitListenerEvidenceConfiguration.deliveries(LISTENER)).isEqualTo(deliveries);
    assertThat(CommitListenerEvidenceConfiguration.drops(LISTENER)).isEqualTo(drops + 1);
    Path dropped =
        EVIDENCE
            .resolve("dropped")
            .resolve(
                LISTENER
                    + "__CommitListenerEvidenceIntegrationTest"
                    + ".aPublicationOutsideATransactionIsADropNamedAfterItsPublisher.json");
    assertThat(
            JsonMapper.builder().build().readTree(dropped.toFile()).get("production").asBoolean())
        .isFalse();
  }

  @Test
  void onlyApplicationCodeCountsAsAProductionPublisher() {
    assertThat(
            CommitListenerEvidenceConfiguration.fromMainClasses(
                LinkOgFetchListener.class.getName()))
        .isTrue();
    assertThat(
            CommitListenerEvidenceConfiguration.fromMainClasses(
                CommitListenerEvidenceIntegrationTest.class.getName()))
        .isFalse();
  }
}
