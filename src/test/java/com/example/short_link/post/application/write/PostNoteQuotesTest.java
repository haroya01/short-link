package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.NotesEmbeddedEvent;
import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostBlockType;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostNoteQuoteRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PostNoteQuotesTest {

  @Mock private PostNoteQuoteRepository repository;
  @Mock private ApplicationEventPublisher events;

  private PostNoteQuotes quotes;

  @BeforeEach
  void setUp() {
    quotes =
        new PostNoteQuotes(
            repository,
            events,
            JsonMapper.builder().build(),
            "https://api.kurl.me",
            "https://app.kurl.me",
            "https://blog.kurl.me");
  }

  private static PostBlockEntity embed(String content) {
    return new PostBlockEntity(42L, PostBlockType.EMBED, content, 0);
  }

  private static PostEntity post(boolean published) {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    ReflectionTestUtils.setField(post, "id", 42L);
    if (published) {
      post.publish();
    }
    return post;
  }

  @Test
  void readsNoteCardsFromEveryNoteAddressThisServerGives() {
    Set<Long> ids =
        quotes.noteIds(
            List.of(
                embed("https://blog.kurl.me/@alice/notes/11"),
                embed("https://app.kurl.me/ko/p/alice/notes/12/"),
                embed("https://api.kurl.me/ap/notes/13"),
                embed("https://BLOG.kurl.me/blog/remote/9/notes/14?ref=share"),
                embed("{\"url\":\"https://blog.kurl.me/@bob/notes/15\"}")));

    assertThat(ids).containsExactly(11L, 12L, 13L, 14L, 15L);
  }

  @Test
  void leavesOtherLinksAndInlineMentionsAlone() {
    Set<Long> ids =
        quotes.noteIds(
            List.of(
                embed("https://mastodon.social/@alice/notes/11"),
                embed("https://blog.kurl.me/@alice/posts/hello"),
                embed("https://blog.kurl.me/@alice/notes/abc"),
                embed("{\"url\":3}"),
                embed("{broken"),
                embed("not a url at all"),
                embed(null),
                new PostBlockEntity(
                    42L, PostBlockType.PARAGRAPH, "https://blog.kurl.me/@alice/notes/16", 1)));

    assertThat(ids).isEmpty();
  }

  @Test
  void countsANoteOnceAndStopsAtFifty() {
    List<PostBlockEntity> blocks = new ArrayList<>();
    blocks.add(embed("https://blog.kurl.me/@alice/notes/1"));
    for (int i = 1; i <= 60; i++) {
      blocks.add(embed("https://blog.kurl.me/@alice/notes/" + i));
    }

    assertThat(quotes.noteIds(blocks)).hasSize(50).first().isEqualTo(1L);
  }

  @Test
  void aPublicPostReplacesWhatItQuotesAndTellsOnlyTheNewlyQuotedNotes() {
    PostEntity post = post(true);
    when(repository.noteIds(42L)).thenReturn(Set.of(11L));

    quotes.index(
        post,
        List.of(
            embed("https://blog.kurl.me/@alice/notes/11"),
            embed("https://blog.kurl.me/@bob/notes/12")));

    verify(repository).replace(42L, Set.of(11L, 12L));
    verify(events).publishEvent(new NotesEmbeddedEvent(7L, 42L, "my-post", "My Post", Set.of(12L)));
  }

  @Test
  void anEditThatQuotesNothingNewStaysQuiet() {
    PostEntity post = post(true);
    when(repository.noteIds(42L)).thenReturn(Set.of(11L));

    quotes.index(post, List.of(embed("https://blog.kurl.me/@alice/notes/11")));

    verify(repository).replace(42L, Set.of(11L));
    verifyNoInteractions(events);
  }

  @Test
  void aPostWithoutNoteCardsSkipsTheLookup() {
    quotes.index(post(true), List.of());

    verify(repository, never()).noteIds(anyLong());
    verify(repository).replace(42L, Set.of());
    verifyNoInteractions(events);
  }

  @Test
  void anUnpublishedPostStillKeepsItsQuotesCurrentForARepublish() {
    PostEntity post = post(true);
    post.unpublish();

    quotes.index(post, List.of(embed("https://blog.kurl.me/@alice/notes/11")));

    verify(repository).replace(42L, Set.of(11L));
    verify(repository, never()).noteIds(anyLong());
    verifyNoInteractions(events);
  }

  @Test
  void aDraftIsLeftAloneSoAutosavesCostNothing() {
    quotes.index(post(false), List.of(embed("https://blog.kurl.me/@alice/notes/11")));

    verifyNoInteractions(repository);
  }

  @Test
  void aFirstPublishOnlyAddsBecauseNothingWasKeptBefore() {
    PostEntity post = post(true);

    quotes.indexFirstPublish(post, List.of(embed("https://blog.kurl.me/@alice/notes/11")));

    verify(repository).add(42L, Set.of(11L));
    verify(repository, never()).replace(anyLong(), any());
    verify(events).publishEvent(new NotesEmbeddedEvent(7L, 42L, "my-post", "My Post", Set.of(11L)));
  }

  @Test
  void ignoresHostsItCannotRead() {
    PostNoteQuotes odd =
        new PostNoteQuotes(
            repository,
            events,
            JsonMapper.builder().build(),
            "not a url",
            "mailto:x",
            "https://blog.kurl.me");

    assertThat(odd.noteIds(List.of(embed("https://blog.kurl.me/@alice/notes/11"))))
        .containsExactly(11L);
  }
}
