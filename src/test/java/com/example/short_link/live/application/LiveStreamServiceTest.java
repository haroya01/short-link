package com.example.short_link.live.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.live.LiveScope;
import com.example.short_link.common.live.LiveScopeReader;
import com.example.short_link.common.live.LiveTopic;
import com.example.short_link.common.security.ClickStreamTokenService;
import com.example.short_link.live.exception.LiveErrorCode;
import com.example.short_link.live.exception.LiveException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

class LiveStreamServiceTest {

  private static final LiveTopic THREAD = LiveTopic.note(10L);
  private static final LiveScope SCOPE = new LiveScope(7L, null);

  private final LiveScopeReader notes = mock(LiveScopeReader.class);
  private final ClickStreamTokenService tokens = mock(ClickStreamTokenService.class);
  private final LiveStreams streams = mock(LiveStreams.class);
  private LiveStreamService live;

  @BeforeEach
  void setUp() {
    when(notes.scope()).thenReturn(LiveTopic.Scope.NOTE);
    live =
        new LiveStreamService(
            List.of(notes),
            tokens,
            streams,
            new LiveProperties(null, 0, null, null, null, null, null, null));
  }

  @Test
  void aReaderWithRoomGetsATokenBoundToTheScope() {
    when(notes.open(10L, 2L, "1.2.3.4")).thenReturn(Optional.of(SCOPE));
    when(streams.admits(2L)).thenReturn(true);
    when(tokens.createStreamToken(2L, "live:note:10", Duration.ofMinutes(10))).thenReturn("signed");

    assertThat(live.issueToken(THREAD, 2L, "1.2.3.4")).isEqualTo("signed");
  }

  @Test
  void whatTheViewerCannotReadLooksTheSameAsWhatIsNotThere() {
    when(notes.open(10L, 2L, null)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> live.issueToken(THREAD, 2L, null))
        .isInstanceOf(LiveException.class)
        .extracting(e -> ((LiveException) e).errorCode())
        .isEqualTo(LiveErrorCode.LIVE_SCOPE_NOT_FOUND);
    verify(tokens, never()).createStreamToken(any(), any(), any());
  }

  @Test
  void aScopeNoSliceServesIsNotThere() {
    assertThat(live.open(LiveTopic.post(5L), 2L, null)).isEmpty();
  }

  @Test
  void aFullServerSendsTheViewerBackToRefreshingWithARetryTime() {
    when(notes.open(10L, 2L, null)).thenReturn(Optional.of(SCOPE));
    when(streams.admits(2L)).thenReturn(false);

    assertThatThrownBy(() -> live.issueToken(THREAD, 2L, null))
        .isInstanceOf(LiveException.class)
        .satisfies(
            e -> {
              LiveException error = (LiveException) e;
              assertThat(error.errorCode()).isEqualTo(LiveErrorCode.LIVE_STREAM_LIMIT);
              assertThat(error.status().value()).isEqualTo(429);
              assertThat(error.properties())
                  .containsEntry("retryAfterSeconds", LiveStreamService.RETRY_AFTER_SECONDS);
            });
  }

  @Test
  void onlyATokenForThisScopeNamesItsHolder() {
    when(tokens.parseStreamToken("signed", "live:note:10")).thenReturn(2L);
    when(tokens.parseStreamToken("other", "live:note:10"))
        .thenThrow(new IllegalArgumentException("scope mismatch"));

    assertThat(live.holder(THREAD, "signed")).contains(2L);
    assertThat(live.holder(THREAD, "other")).isEmpty();
    assertThat(live.holder(THREAD, " ")).isEmpty();
    assertThat(live.holder(THREAD, null)).isEmpty();
  }

  @Test
  void beginningAStreamReadsTheLatestItemOnceAndHandsItOn() {
    LiveSubscriber subscriber = new LiveSubscriber(2L, THREAD, SCOPE, new RecordingChannel());
    when(notes.latest(10L)).thenReturn(120L);
    when(tokens.createStreamToken(2L, "live:note:10", Duration.ofMinutes(10)))
        .thenReturn("renewed");

    live.begin(subscriber, 100L);

    verify(streams).start(subscriber, 120L, 100L, "renewed");
  }

  @Test
  void aStreamThatCannotBeginGivesBackTheSlotItJoinedWith() {
    LiveSubscriber subscriber = new LiveSubscriber(2L, THREAD, SCOPE, new RecordingChannel());
    when(notes.latest(10L)).thenThrow(new QueryTimeoutException("slow"));

    assertThatThrownBy(() -> live.begin(subscriber, 100L))
        .isInstanceOf(QueryTimeoutException.class);
    verify(streams).remove(subscriber);
    verify(streams, never()).start(any(), anyLong(), any(), any());
  }

  @Test
  void joiningAndLeavingGoThroughTheHub() {
    LiveSubscriber subscriber = new LiveSubscriber(2L, THREAD, SCOPE, new RecordingChannel());
    when(streams.register(subscriber)).thenReturn(true);

    assertThat(live.join(subscriber)).isTrue();
    live.leave(subscriber);

    verify(streams).remove(subscriber);
  }
}
