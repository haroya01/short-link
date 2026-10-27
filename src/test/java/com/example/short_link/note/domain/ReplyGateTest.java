package com.example.short_link.note.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReplyGateTest {

  private static final ReplyGate.Member WRITER = new ReplyGate.Member(9L, "owner");
  private static final ReplyGate.Member NAMED = new ReplyGate.Member(7L, "mina");
  private static final ReplyGate.Member STRANGER = new ReplyGate.Member(8L, "bob");
  private static final ReplyGate.Remote NAMED_ELSEWHERE = new ReplyGate.Remote("Alice@M.example");
  private static final ReplyGate.Remote STRANGER_ELSEWHERE = new ReplyGate.Remote("eve@x.example");

  private static NoteEntity root(NoteReplyPolicy policy) {
    NoteEntity root = new NoteEntity(9L, "@mina @alice@m.example 둘만", null, null);
    ReflectionTestUtils.setField(root, "id", 1L);
    root.limitReplies(policy);
    return root;
  }

  private static final BooleanSupplier FOLLOWS = () -> true;
  private static final BooleanSupplier DOES_NOT_FOLLOW = () -> false;

  @Test
  void underMentionedOnlyTheWriterAndThoseTheFirstNoteNamesMayAnswer() {
    NoteEntity root = root(NoteReplyPolicy.MENTIONED);

    assertThat(ReplyGate.admits(root, WRITER, DOES_NOT_FOLLOW)).isTrue();
    assertThat(ReplyGate.admits(root, NAMED, DOES_NOT_FOLLOW)).isTrue();
    assertThat(ReplyGate.admits(root, NAMED_ELSEWHERE, DOES_NOT_FOLLOW)).isTrue();
    assertThat(ReplyGate.admits(root, STRANGER, FOLLOWS)).isFalse();
    assertThat(ReplyGate.admits(root, STRANGER_ELSEWHERE, FOLLOWS)).isFalse();
  }

  @Test
  void underFollowingAnyoneTheWriterFollowsMayAnswerToo() {
    NoteEntity root = root(NoteReplyPolicy.FOLLOWING);

    assertThat(ReplyGate.admits(root, STRANGER, FOLLOWS)).isTrue();
    assertThat(ReplyGate.admits(root, STRANGER, DOES_NOT_FOLLOW)).isFalse();
    assertThat(ReplyGate.admits(root, STRANGER_ELSEWHERE, FOLLOWS)).isTrue();
    assertThat(ReplyGate.admits(root, STRANGER_ELSEWHERE, DOES_NOT_FOLLOW)).isFalse();
    assertThat(ReplyGate.admits(root, NAMED, DOES_NOT_FOLLOW)).isTrue();
  }

  @Test
  void aThreadWhoseFirstNoteIsGoneOrCameFromElsewhereTakesAnyAnswer() {
    NoteEntity elsewhere = root(NoteReplyPolicy.MENTIONED);
    ReflectionTestUtils.setField(elsewhere, "remoteActorId", 42L);

    assertThat(ReplyGate.admits(null, STRANGER, DOES_NOT_FOLLOW)).isTrue();
    assertThat(ReplyGate.admits(elsewhere, STRANGER, DOES_NOT_FOLLOW)).isTrue();
    assertThat(ReplyGate.admits(elsewhere, STRANGER_ELSEWHERE, DOES_NOT_FOLLOW)).isTrue();
  }

  @Test
  void whoTheWriterFollowsIsAskedOnlyWhenNothingElseDecides() {
    AtomicInteger asked = new AtomicInteger();
    BooleanSupplier counted =
        () -> {
          asked.incrementAndGet();
          return false;
        };

    ReplyGate.admits(root(NoteReplyPolicy.FOLLOWING), WRITER, counted);
    ReplyGate.admits(root(NoteReplyPolicy.FOLLOWING), NAMED, counted);
    ReplyGate.admits(root(NoteReplyPolicy.MENTIONED), STRANGER, counted);
    assertThat(asked).hasValue(0);

    ReplyGate.admits(root(NoteReplyPolicy.FOLLOWING), STRANGER, counted);
    assertThat(asked).hasValue(1);
  }

  @Test
  void aMemberWithoutAHandleIsNamedByNoNote() {
    assertThat(
            ReplyGate.admits(
                root(NoteReplyPolicy.MENTIONED), new ReplyGate.Member(7L, null), DOES_NOT_FOLLOW))
        .isFalse();
    assertThat(
            ReplyGate.admits(
                root(NoteReplyPolicy.MENTIONED), new ReplyGate.Remote(null), DOES_NOT_FOLLOW))
        .isFalse();
  }
}
