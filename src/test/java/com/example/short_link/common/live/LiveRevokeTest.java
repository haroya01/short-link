package com.example.short_link.common.live;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LiveRevokeTest {

  private static final LiveScope ROOM_NOTE = new LiveScope(7L, 9L);
  private static final LiveScope PUBLIC_NOTE = new LiveScope(7L, null);

  @Test
  void leavingARoomEndsThatPersonsStreamsOnTheRoomAndOnWhatLivesInIt() {
    LiveRevoke left = new LiveRevoke.LeftRoom(9L, 2L);

    assertThat(left.ends(2L, LiveTopic.room(9L), new LiveScope(null, 9L))).isTrue();
    assertThat(left.ends(2L, LiveTopic.note(30L), ROOM_NOTE)).isTrue();
    assertThat(left.ends(2L, LiveTopic.note(31L), PUBLIC_NOTE)).isFalse();
    assertThat(left.ends(3L, LiveTopic.room(9L), new LiveScope(null, 9L))).isFalse();
    assertThat(left.ends(2L, LiveTopic.room(8L), new LiveScope(null, 8L))).isFalse();
  }

  @Test
  void aBlockEitherWayEndsStreamsOnTheOtherPersonsWriting() {
    LiveRevoke blocked = new LiveRevoke.Blocked(2L, 7L);

    assertThat(blocked.ends(2L, LiveTopic.note(30L), PUBLIC_NOTE)).isTrue();
    assertThat(blocked.ends(7L, LiveTopic.post(5L), new LiveScope(2L, null))).isTrue();
    assertThat(blocked.ends(3L, LiveTopic.note(30L), PUBLIC_NOTE)).isFalse();
    assertThat(blocked.ends(2L, LiveTopic.room(9L), new LiveScope(null, 9L))).isFalse();
  }

  @Test
  void aGoneAccountEndsItsOwnStreamsAndThoseOnItsWriting() {
    LiveRevoke gone = new LiveRevoke.AccountGone(7L);

    assertThat(gone.ends(7L, LiveTopic.room(9L), new LiveScope(null, 9L))).isTrue();
    assertThat(gone.ends(2L, LiveTopic.note(30L), PUBLIC_NOTE)).isTrue();
    assertThat(gone.ends(2L, LiveTopic.room(9L), new LiveScope(null, 9L))).isFalse();
  }

  @Test
  void aClosedOrRecheckedTopicEndsEveryoneOnItAndNoOneElse() {
    for (LiveRevoke revoke :
        new LiveRevoke[] {
          new LiveRevoke.Closed(LiveTopic.post(5L)), new LiveRevoke.Recheck(LiveTopic.post(5L))
        }) {
      assertThat(revoke.ends(2L, LiveTopic.post(5L), PUBLIC_NOTE)).isTrue();
      assertThat(revoke.ends(2L, LiveTopic.note(5L), PUBLIC_NOTE)).isFalse();
    }
  }
}
