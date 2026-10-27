package com.example.short_link.post.application.write;

import static com.example.short_link.common.notification.BlogNotificationKind.COMMENT;
import static com.example.short_link.common.notification.BlogNotificationKind.REPLY;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.notification.BlogNotificationKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CommentNotificationPlanTest {

  private static final Long OWNER = 1L;
  private static final Long ACTOR = 2L;
  private static final Long PARENT = 3L;
  private static final Long BOB = 5L;

  private static final class FakeLookups implements CommentNotificationPlan.Lookups {
    final Map<String, Long> handles = new HashMap<>();
    final Set<Long> outsiders = new HashSet<>();
    final Set<String> mutes = new HashSet<>();
    final List<String> asked = new ArrayList<>();

    FakeLookups user(String handle, Long id) {
      handles.put(handle, id);
      return this;
    }

    FakeLookups outsider(Long id) {
      outsiders.add(id);
      return this;
    }

    FakeLookups muting(Long id, BlogNotificationKind kind) {
      mutes.add(id + ":" + kind);
      return this;
    }

    @Override
    public boolean reads(Long userId) {
      asked.add("reads " + userId);
      return !outsiders.contains(userId);
    }

    @Override
    public boolean muted(Long userId, BlogNotificationKind kind) {
      asked.add("muted " + userId + " " + kind);
      return mutes.contains(userId + ":" + kind);
    }

    @Override
    public Long activeUserId(String handle) {
      asked.add("user " + handle);
      return handles.get(handle);
    }
  }

  @Test
  void theOwnerHearsACommentByAnyoneElse() {
    FakeLookups lookups = new FakeLookups();

    CommentNotificationPlan plan = CommentNotificationPlan.forComment(OWNER, ACTOR, null, lookups);

    assertThat(plan.ownerHearsComment()).isTrue();
    assertThat(plan.authorHearsReply()).isFalse();
    assertThat(lookups.asked).containsExactly("muted 1 COMMENT");
  }

  @Test
  void theOwnersOwnCommentAsksNothing() {
    FakeLookups lookups = new FakeLookups();

    CommentNotificationPlan plan = CommentNotificationPlan.forComment(OWNER, OWNER, null, lookups);

    assertThat(plan.ownerHearsComment()).isFalse();
    assertThat(plan.authorHearsReply()).isFalse();
    assertThat(lookups.asked).isEmpty();
  }

  @Test
  void aReplyToTheOwnerIsAReplyAndNotAlsoAComment() {
    FakeLookups lookups = new FakeLookups().muting(OWNER, REPLY);

    CommentNotificationPlan plan = CommentNotificationPlan.forComment(OWNER, ACTOR, OWNER, lookups);

    assertThat(plan.ownerHearsComment()).isFalse();
    assertThat(plan.authorHearsReply()).isTrue();
  }

  @Test
  void theParentAuthorHearsAReplyOnlyWhileTheyCanReadThePost() {
    assertThat(
            CommentNotificationPlan.forComment(OWNER, ACTOR, PARENT, new FakeLookups())
                .authorHearsReply())
        .isTrue();
    assertThat(
            CommentNotificationPlan.forComment(
                    OWNER, ACTOR, PARENT, new FakeLookups().outsider(PARENT))
                .authorHearsReply())
        .isFalse();
    assertThat(
            CommentNotificationPlan.forComment(OWNER, PARENT, PARENT, new FakeLookups())
                .authorHearsReply())
        .isFalse();
  }

  @Test
  void mentionsSkipTheActorUnknownHandlesOutsidersAndRepeats() {
    FakeLookups lookups =
        new FakeLookups().user("me", ACTOR).user("bob", BOB).user("out", 9L).outsider(9L);

    CommentNotificationPlan plan = CommentNotificationPlan.forComment(OWNER, ACTOR, null, lookups);

    assertThat(plan.mentioned(List.of("me", "ghost", "out", "bob", "bob"))).containsExactly(BOB);
  }

  @Test
  void someoneAlreadyToldOfTheCommentOrReplyIsNotMentionedToo() {
    FakeLookups lookups = new FakeLookups().user("owner", OWNER).user("parent", PARENT);

    CommentNotificationPlan plan =
        CommentNotificationPlan.forComment(OWNER, ACTOR, PARENT, lookups);

    assertThat(plan.mentioned(List.of("owner", "parent"))).isEmpty();
  }

  @Test
  void mutingCommentsOrRepliesStillLetsAnExplicitMentionThrough() {
    FakeLookups lookups =
        new FakeLookups()
            .user("owner", OWNER)
            .user("parent", PARENT)
            .muting(OWNER, COMMENT)
            .muting(PARENT, REPLY);

    CommentNotificationPlan plan =
        CommentNotificationPlan.forComment(OWNER, ACTOR, PARENT, lookups);

    assertThat(plan.ownerHearsComment()).isTrue();
    assertThat(plan.authorHearsReply()).isTrue();
    assertThat(plan.mentioned(List.of("owner", "parent"))).containsExactly(OWNER, PARENT);
  }

  @Test
  void askingForMentionsTwiceGivesTheSameRecipients() {
    FakeLookups lookups = new FakeLookups().user("bob", BOB);

    CommentNotificationPlan plan = CommentNotificationPlan.forComment(OWNER, ACTOR, null, lookups);

    assertThat(plan.mentioned(List.of("bob"))).containsExactly(BOB);
    assertThat(plan.mentioned(List.of("bob"))).containsExactly(BOB);
  }

  @Test
  void asksTheLookupsTheDecisionsNeedInOrder() {
    FakeLookups lookups = new FakeLookups().user("me", ACTOR).user("owner", OWNER).user("bob", BOB);

    CommentNotificationPlan plan =
        CommentNotificationPlan.forComment(OWNER, ACTOR, PARENT, lookups);
    plan.mentioned(List.of("ghost", "me", "owner", "bob"));

    assertThat(lookups.asked)
        .containsExactly(
            "muted 1 COMMENT",
            "reads 3",
            "muted 3 REPLY",
            "user ghost",
            "user me",
            "user owner",
            "reads 1",
            "user bob",
            "reads 5");
  }

  @Test
  void theHighlightAuthorHearsAReplyByAnyoneElseWhoCanReadThePost() {
    FakeLookups lookups = new FakeLookups();

    CommentNotificationPlan plan =
        CommentNotificationPlan.forHighlightReply(PARENT, ACTOR, lookups);

    assertThat(plan.ownerHearsComment()).isFalse();
    assertThat(plan.authorHearsReply()).isTrue();
    assertThat(lookups.asked).containsExactly("reads 3", "muted 3 REPLY");
    assertThat(
            CommentNotificationPlan.forHighlightReply(PARENT, PARENT, new FakeLookups())
                .authorHearsReply())
        .isFalse();
    assertThat(
            CommentNotificationPlan.forHighlightReply(
                    PARENT, ACTOR, new FakeLookups().outsider(PARENT))
                .authorHearsReply())
        .isFalse();
  }

  @Test
  void aHighlightAuthorWhoMutedRepliesIsStillMentioned() {
    FakeLookups lookups = new FakeLookups().user("author", PARENT).muting(PARENT, REPLY);

    CommentNotificationPlan plan =
        CommentNotificationPlan.forHighlightReply(PARENT, ACTOR, lookups);

    assertThat(plan.mentioned(List.of("author"))).containsExactly(PARENT);
    assertThat(
            CommentNotificationPlan.forHighlightReply(
                    PARENT, ACTOR, new FakeLookups().user("author", PARENT))
                .mentioned(List.of("author")))
        .isEmpty();
  }
}
