package com.example.short_link.portability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.DomainBlocks;
import com.example.short_link.federation.application.RemoteAccountView;
import com.example.short_link.federation.application.RemoteFollowing;
import com.example.short_link.note.application.write.NoteCommandService;
import com.example.short_link.note.application.write.NoteFeedSettingsService;
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
  @Mock private DomainBlocks domainBlocks;
  @Mock private NoteCommandService notes;
  @Mock private NoteFeedSettingsService feedSettings;
  @Mock private NoteListService lists;
  @Mock private ImportLookup lookup;

  private ImportRowApplier applier() {
    return new ImportRowApplier(
        follows,
        blocks,
        mutes,
        remoteFollowing,
        domainBlocks,
        notes,
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
  void aBellThatCannotRingYetDoesNotFailTheFollow() {
    doThrow(new IllegalStateException("requested"))
        .when(follows)
        .setNoteNotifications(7L, "mio", true);

    assertThat(applier().apply(7L, ImportKind.FOLLOWING, List.of("mio", "true", "true"))).isTrue();
    verify(feedSettings, never())
        .setRepostsHidden(anyLong(), any(), org.mockito.ArgumentMatchers.anyBoolean());
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
            false);
    when(remoteFollowing.lookup(7L, "Alice@mastodon.social")).thenReturn(alice);

    assertThat(applier().apply(7L, ImportKind.FOLLOWING, List.of("Alice@Mastodon.Social", "true")))
        .isTrue();
    verify(remoteFollowing).follow(7L, 42L);
  }

  @Test
  void blocksMutesAndListsTakeMembersHereOnly() {
    ImportRowApplier applier = applier();

    assertThat(applier.apply(7L, ImportKind.BLOCKS, List.of("troll@kurl.me"))).isTrue();
    verify(blocks).block(7L, "troll");
    assertThat(applier.apply(7L, ImportKind.BLOCKS, List.of("troll@elsewhere.example"))).isFalse();
    assertThat(applier.apply(7L, ImportKind.MUTES, List.of("loud@kurl.me", "false"))).isTrue();
    verify(mutes).mute(7L, "loud", false, null);
    assertThat(applier.apply(7L, ImportKind.MUTES, List.of("quiet"))).isTrue();
    verify(mutes).mute(7L, "quiet", true, null);
    assertThat(applier.apply(7L, ImportKind.LISTS, List.of("friends", "a@elsewhere.example")))
        .isFalse();
    verifyNoInteractions(lists);
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
    verify(notes).setBookmark(7L, 5L, true);
    assertThat(
            applier.apply(
                7L, ImportKind.BOOKMARKS, List.of("https://mastodon.social/users/a/statuses/1")))
        .isTrue();
    verify(notes).setBookmark(7L, 9L, true);
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
