package com.example.short_link.analytics.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.analytics.domain.BehaviorEventEntity;
import com.example.short_link.analytics.domain.repository.BehaviorEventRepository;
import com.example.short_link.link.application.dto.UserAgentInfo;
import com.example.short_link.link.classifier.application.AsnResolver;
import com.example.short_link.link.classifier.application.BotHeuristic;
import com.example.short_link.link.classifier.application.UserAgentClassifier;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordBehaviorEventsUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
  private static final String SESSION = "a1b2c3d4-e5f6-7890";

  @Mock private BehaviorEventRepository repository;
  @Mock private UserAgentClassifier userAgentClassifier;
  @Mock private AsnResolver asnResolver;
  @Mock private BotHeuristic botHeuristic;

  private RecordBehaviorEventsUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new RecordBehaviorEventsUseCase(
            repository,
            userAgentClassifier,
            asnResolver,
            botHeuristic,
            new SimpleMeterRegistry(),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private void stubHuman() {
    when(userAgentClassifier.classify(any()))
        .thenReturn(new UserAgentInfo("mobile", "iOS", "Safari", false, null));
    when(botHeuristic.isSuspectBurst(any())).thenReturn(false);
    when(asnResolver.resolve(any())).thenReturn(new AsnResolver.AsnInfo(0, "ISP", false));
  }

  private BehaviorContext ctx() {
    return new BehaviorContext("Mozilla/5.0", "1.2.3.4", false);
  }

  @Test
  void persistsWhitelistedEventsWithEnrichment() {
    stubHuman();

    int accepted =
        useCase.execute(
            SESSION,
            List.of(
                new BehaviorEventCommand("read_progress", 7L, null, null, 75, null),
                new BehaviorEventCommand("second_action", 7L, "connection", "42", null, null)),
            ctx());

    assertThat(accepted).isEqualTo(2);
    ArgumentCaptor<List<BehaviorEventEntity>> rows = ArgumentCaptor.captor();
    verify(repository).saveAll(rows.capture());
    BehaviorEventEntity progress = rows.getValue().get(0);
    assertThat(progress.getEventName()).isEqualTo("read_progress");
    assertThat(progress.getSessionId()).isEqualTo(SESSION);
    assertThat(progress.getDepthPct()).isEqualTo(75);
    assertThat(progress.getOccurredAt()).isEqualTo(NOW);
    assertThat(progress.getDeviceClass()).isEqualTo("mobile");
    assertThat(progress.isBot()).isFalse();
    assertThat(progress.getVisitorHash()).hasSize(64);
    BehaviorEventEntity action = rows.getValue().get(1);
    assertThat(action.getTargetType()).isEqualTo("connection");
    assertThat(action.getTargetId()).isEqualTo("42");
  }

  @Test
  void dropsUnknownNamesTargetsAndMilestones() {
    stubHuman();

    int accepted =
        useCase.execute(
            SESSION,
            List.of(
                new BehaviorEventCommand("keylog", 7L, null, null, null, null),
                new BehaviorEventCommand("second_action", 7L, "iframe", "x", null, null),
                new BehaviorEventCommand("read_progress", 7L, null, null, 33, null),
                new BehaviorEventCommand("read_progress", 7L, null, null, null, null)),
            ctx());

    assertThat(accepted).isZero();
    verify(repository, never()).saveAll(anyList());
  }

  @Test
  void capsBatchSize() {
    stubHuman();

    List<BehaviorEventCommand> oversized =
        IntStream.range(0, 40)
            .mapToObj(i -> new BehaviorEventCommand("read_progress", 7L, null, null, 25, null))
            .toList();

    assertThat(useCase.execute(SESSION, oversized, ctx()))
        .isEqualTo(RecordBehaviorEventsUseCase.MAX_BATCH);
  }

  @Test
  void flagsBotFromHeuristicsAndKeepsRow() {
    when(userAgentClassifier.classify(any()))
        .thenReturn(new UserAgentInfo("desktop", "Linux", "Chrome", false, null));
    when(botHeuristic.isSuspectBurst(any())).thenReturn(true);

    useCase.execute(
        SESSION,
        List.of(new BehaviorEventCommand("cta_click", null, null, "signup", null, null)),
        ctx());

    ArgumentCaptor<List<BehaviorEventEntity>> rows = ArgumentCaptor.captor();
    verify(repository).saveAll(rows.capture());
    assertThat(rows.getValue().get(0).isBot()).isTrue();
    assertThat(rows.getValue().get(0).getBotName()).isEqualTo(BotHeuristic.SUSPECT_LABEL);
  }

  @Test
  void gpcSkipsVisitorHashAndBadSessionIdIsDroppedNotFatal() {
    stubHuman();

    useCase.execute(
        "<script>",
        List.of(new BehaviorEventCommand("read_progress", 7L, null, null, 100, 12_000L)),
        new BehaviorContext("Mozilla/5.0", "1.2.3.4", true));

    ArgumentCaptor<List<BehaviorEventEntity>> rows = ArgumentCaptor.captor();
    verify(repository).saveAll(rows.capture());
    assertThat(rows.getValue().get(0).getVisitorHash()).isNull();
    assertThat(rows.getValue().get(0).getSessionId()).isNull();
    assertThat(rows.getValue().get(0).getDwellMs()).isEqualTo(12_000L);
  }

  @Test
  void emptyBatchIsNoop() {
    assertThat(useCase.execute(SESSION, Collections.emptyList(), ctx())).isZero();
    verify(repository, never()).saveAll(anyList());
  }
}
