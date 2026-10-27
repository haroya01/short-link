package com.example.short_link.portability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.DomainBlocks;
import com.example.short_link.federation.application.RemoteAccountView;
import com.example.short_link.federation.application.RemoteBlocks;
import com.example.short_link.federation.application.RemoteFollowing;
import com.example.short_link.federation.application.RemoteMutes;
import com.example.short_link.note.application.write.NoteFeedSettingsService;
import com.example.short_link.note.application.write.NoteInteractions;
import com.example.short_link.note.application.write.NoteListService;
import com.example.short_link.portability.domain.ImportKind;
import com.example.short_link.user.application.write.BlockUseCase;
import com.example.short_link.user.application.write.FollowUseCase;
import com.example.short_link.user.application.write.MuteUseCase;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImportRowApplierTest {

  @Mock private FollowUseCase follows;
  @Mock private BlockUseCase blocks;
  @Mock private MuteUseCase mutes;
  @Mock private RemoteFollowing remoteFollowing;
  @Mock private RemoteBlocks remoteBlocks;
  @Mock private RemoteMutes remoteMutes;
  @Mock private DomainBlocks domainBlocks;
  @Mock private NoteInteractions interactions;
  @Mock private NoteFeedSettingsService feedSettings;
  @Mock private NoteListService lists;
  @Mock private ImportLookup lookup;

  private ImportRowApplier applier() {
    return new ImportRowApplier(
        follows,
        blocks,
        mutes,
        remoteFollowing,
        remoteBlocks,
        remoteMutes,
        domainBlocks,
        interactions,
        feedSettings,
        lists,
        lookup,
        "https://kurl.me/");
  }

  @Test
  void aMemberHereIsFollowedWithTheirRepostsAndBellAsTheFileSays() {
    assertThat(
            applier()
                .apply(7L, ImportKind.FOLLOWING, List.of("@sori@kurl.me", "false", "true", "")))
        .isTrue();

    verify(follows).follow(7L, "sori", null);
    verify(feedSettings).setRepostsHidden(7L, "sori", true);
    verify(follows).setNoteNotifications(7L, "sori", true);
  }

  @Test
  void aBellTheFileLeftOffIsTurnedOffAndAMissingOneKeepsTheFollowsDefault() {
    applier().apply(7L, ImportKind.FOLLOWING, List.of("sori", "true", "FALSE"));
    applier().apply(7L, ImportKind.FOLLOWING, List.of("mio", "true"));

    verify(follows).setNoteNotifications(7L, "sori", false);
    verify(follows, never()).setNoteNotifications(eq(7L), eq("mio"), anyBoolean());
  }

  @Test
  void aBellThatCannotRingYetDoesNotFailTheFollow() {
    doThrow(new IllegalStateException("requested"))
        .when(follows)
        .setNoteNotifications(7L, "mio", true);

    assertThat(applier().apply(7L, ImportKind.FOLLOWING, List.of("mio", "true", "true"))).isTrue();
    verify(feedSettings, never())
        .setRepostsHidden(anyLong(), any(), org.mockito.ArgumentMatchers.anyBoolean());
  }

  private static RemoteAccountView alice() {
    return new RemoteAccountView(
        42L,
        "alice@mastodon.social",
        "alice",
        "mastodon.social",
        null,
        null,
        "u",
        false,
        false,
        false,
        false,
        false);
  }

  @Test
  void anAccountElsewhereIsLookedUpThenFollowed() {
    RemoteAccountView alice =
        new RemoteAccountView(
            42L,
            "alice@mastodon.social",
            "alice",
            "mastodon.social",
            null,
            null,
            "u",
            false,
            false,
            false,
            false,
            false);
    when(remoteFollowing.lookup(7L, "Alice@mastodon.social")).thenReturn(alice);

    assertThat(applier().apply(7L, ImportKind.FOLLOWING, List.of("Alice@Mastodon.Social", "true")))
        .isTrue();
    verify(remoteFollowing).followFromImport(7L, 42L);
  }

  @Test
  void blocksAndMutesTakeMembersHereAndListsTakeOnlyThem() {
    ImportRowApplier applier = applier();

    assertThat(applier.apply(7L, ImportKind.BLOCKS, List.of("troll@kurl.me"))).isTrue();
    verify(blocks).block(7L, "troll");
    assertThat(applier.apply(7L, ImportKind.BLOCKS, List.of("@"))).isFalse();
    assertThat(applier.apply(7L, ImportKind.MUTES, List.of("loud@kurl.me", "false"))).isTrue();
    verify(mutes).mute(7L, "loud", false, null);
    assertThat(applier.apply(7L, ImportKind.MUTES, List.of("quiet"))).isTrue();
    verify(mutes).mute(7L, "quiet", true, null);
    assertThat(applier.apply(7L, ImportKind.MUTES, List.of(""))).isFalse();
    assertThat(applier.apply(7L, ImportKind.LISTS, List.of("friends", "a@elsewhere.example")))
        .isFalse();
    verifyNoInteractions(lists, remoteBlocks, remoteMutes);
  }

  @Test
  void anAccountElsewhereIsLookedUpThenBlockedOrMuted() {
    when(remoteFollowing.lookup(7L, "troll@mastodon.social")).thenReturn(alice());
    when(remoteFollowing.lookup(7L, "loud@mastodon.social")).thenReturn(alice());
    ImportRowApplier applier = applier();

    assertThat(applier.apply(7L, ImportKind.BLOCKS, List.of("troll@Mastodon.Social"))).isTrue();
    verify(remoteBlocks).block(7L, 42L);
    assertThat(applier.apply(7L, ImportKind.MUTES, List.of("loud@mastodon.social", "false")))
        .isTrue();
    verify(remoteMutes).mute(7L, 42L, false, null);
    verifyNoInteractions(blocks, mutes);
  }

  @Test
  void aListIsReusedByTitleOrCreated() {
    when(lists.mine(7L)).thenReturn(List.of(new NoteListService.ListView(3L, "friends", 1)));
    when(lists.create(7L, "work")).thenReturn(new NoteListService.ListView(4L, "work", 0));
    ImportRowApplier applier = applier();

    assertThat(applier.apply(7L, ImportKind.LISTS, List.of("friends", "sori@kurl.me"))).isTrue();
    assertThat(applier.apply(7L, ImportKind.LISTS, List.of("work", "mio"))).isTrue();
    verify(lists).add(7L, 3L, "sori");
    verify(lists).add(7L, 4L, "mio");
    assertThat(applier.apply(7L, ImportKind.LISTS, List.of("", "mio"))).isFalse();
  }

  @Test
  void serversAndBookmarksAreAppliedByTheirAddress() {
    when(lookup.noteIdByUri("https://mastodon.social/users/a/statuses/1"))
        .thenReturn(Optional.of(9L));
    when(lookup.noteIdByUri("https://gone.example/1")).thenReturn(Optional.empty());
    ImportRowApplier applier = applier();

    assertThat(applier.apply(7L, ImportKind.DOMAIN_BLOCKS, List.of("spam.example"))).isTrue();
    verify(domainBlocks).block(7L, "spam.example");
    assertThat(applier.apply(7L, ImportKind.BOOKMARKS, List.of("https://kurl.me/ap/notes/5")))
        .isTrue();
    verify(interactions).setBookmark(7L, 5L, true);
    assertThat(
            applier.apply(
                7L, ImportKind.BOOKMARKS, List.of("https://mastodon.social/users/a/statuses/1")))
        .isTrue();
    verify(interactions).setBookmark(7L, 9L, true);
    assertThat(applier.apply(7L, ImportKind.BOOKMARKS, List.of("https://gone.example/1")))
        .isFalse();
    assertThat(applier.apply(7L, ImportKind.BOOKMARKS, List.of("https://kurl.me/ap/notes/x")))
        .isFalse();
  }

  @Test
  void aLineThatFailsIsCountedAndNothingElseBreaks() {
    doThrow(new IllegalArgumentException("no such user")).when(follows).follow(7L, "ghost", null);

    assertThat(applier().apply(7L, ImportKind.FOLLOWING, List.of("ghost@kurl.me"))).isFalse();
    assertThat(applier().apply(7L, ImportKind.FOLLOWING, List.of("@"))).isFalse();
  }

  @Test
  void anAddressIsANameAndAnOptionalServer() {
    assertThat(ImportRowApplier.address("@a@B.example"))
        .isEqualTo(new ImportRowApplier.Address("a", "b.example"));
    assertThat(ImportRowApplier.address("a")).isEqualTo(new ImportRowApplier.Address("a", null));
    assertThat(ImportRowApplier.address(" ")).isNull();
    assertThat(ImportRowApplier.address("a@")).isNull();
    assertThat(ImportRowApplier.address(null)).isNull();
  }
}
