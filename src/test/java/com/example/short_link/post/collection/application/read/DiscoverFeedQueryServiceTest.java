package com.example.short_link.post.collection.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.post.collection.domain.ConnectionBlockType;
import com.example.short_link.post.collection.domain.DiscoverConnectionRow;
import com.example.short_link.post.collection.domain.repository.CollectionConnectionRepository;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.note.domain.NoteEntity;
import com.example.short_link.post.note.domain.repository.NoteRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DiscoverFeedQueryServiceTest {

  @Mock private CollectionConnectionRepository connectionRepository;
  @Mock private FollowRepository followRepository;
  @Mock private PostRepository postRepository;
  @Mock private PostHighlightRepository highlightRepository;
  @Mock private NoteRepository noteRepository;
  @Mock private UserRepository userRepository;

  private DiscoverFeedQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new DiscoverFeedQueryService(
            connectionRepository,
            followRepository,
            postRepository,
            highlightRepository,
            noteRepository,
            userRepository);
  }

  private DiscoverConnectionRow row(
      long connId, ConnectionBlockType type, long refId, long ownerId) {
    return new DiscoverConnectionRow(
        connId, type, refId, "왜", Instant.parse("2026-06-12T00:00:00Z"), 50L, "느린 사고", ownerId);
  }

  private PostEntity post(long id, long authorId) {
    PostEntity p = new PostEntity(authorId, "slug-" + id, "Title " + id, "ko");
    p.publish();
    ReflectionTestUtils.setField(p, "id", id);
    return p;
  }

  private UserEntity user(long id, String username) {
    UserEntity u = new UserEntity("u" + id + "@x.com", "google", "g-" + id);
    u.claimUsername(username);
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  @Test
  void emptyWhenViewerFollowsNoOne() {
    when(followRepository.findFollowingIds(1L)).thenReturn(List.of());

    DiscoverFeedView feed = service.feed(1L, 0, 20);

    assertThat(feed.items()).isEmpty();
    assertThat(feed.hasNext()).isFalse();
    verifyNoInteractions(connectionRepository);
  }

  @Test
  void resolvesMixedBlocksWithCurators() {
    when(followRepository.findFollowingIds(1L)).thenReturn(List.of(2L, 3L));
    when(connectionRepository.findPublicConnectionsByOwners(anyCollection(), anyInt(), anyInt()))
        .thenReturn(
            List.of(
                row(100L, ConnectionBlockType.POST, 5L, 2L),
                row(101L, ConnectionBlockType.HIGHLIGHT, 9L, 3L),
                row(102L, ConnectionBlockType.NOTE, 7L, 2L)));

    PostHighlightEntity hl =
        new PostHighlightEntity(6L, 4L, 0, 0, 0, 3, "좋은 추상은 더 지울 게 없을 때", null);
    ReflectionTestUtils.setField(hl, "id", 9L);
    when(highlightRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(hl));
    NoteEntity note = new NoteEntity(2L, "더 나은 질문을 기다리는 일");
    ReflectionTestUtils.setField(note, "id", 7L);
    when(noteRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(note));
    when(postRepository.findAllByIdIn(anyCollection()))
        .thenReturn(List.of(post(5L, 4L), post(6L, 4L)));
    when(userRepository.findAllByIdIn(anyCollection()))
        .thenReturn(List.of(user(2L, "minji"), user(3L, "sori"), user(4L, "author")));

    DiscoverFeedView feed = service.feed(1L, 0, 20);

    assertThat(feed.items()).hasSize(3);
    assertThat(feed.hasNext()).isFalse(); // 3 < 20
    DiscoverConnectionView postItem = feed.items().get(0);
    assertThat(postItem.curator().username()).isEqualTo("minji");
    assertThat(postItem.blockType()).isEqualTo("POST");
    assertThat(postItem.title()).isEqualTo("Title 5");
    assertThat(postItem.username()).isEqualTo("author");
    assertThat(postItem.collectionTitle()).isEqualTo("느린 사고");
    DiscoverConnectionView hlItem = feed.items().get(1);
    assertThat(hlItem.curator().username()).isEqualTo("sori");
    assertThat(hlItem.blockType()).isEqualTo("HIGHLIGHT");
    assertThat(hlItem.quote()).isEqualTo("좋은 추상은 더 지울 게 없을 때");
    assertThat(hlItem.title()).isEqualTo("Title 6");
    DiscoverConnectionView noteItem = feed.items().get(2);
    assertThat(noteItem.blockType()).isEqualTo("NOTE");
    assertThat(noteItem.body()).isEqualTo("더 나은 질문을 기다리는 일");
  }

  @Test
  void skipsRowWhenCuratorMissingAndFlagsHasNext() {
    when(followRepository.findFollowingIds(1L)).thenReturn(List.of(2L));
    when(connectionRepository.findPublicConnectionsByOwners(anyCollection(), anyInt(), anyInt()))
        .thenReturn(
            List.of(
                row(100L, ConnectionBlockType.NOTE, 7L, 2L),
                row(101L, ConnectionBlockType.NOTE, 8L, 999L))); // curator 999 absent
    NoteEntity n7 = new NoteEntity(2L, "보이는 노트");
    ReflectionTestUtils.setField(n7, "id", 7L);
    NoteEntity n8 = new NoteEntity(999L, "주인 없는 노트");
    ReflectionTestUtils.setField(n8, "id", 8L);
    when(noteRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(n7, n8));
    when(postRepository.findAllByIdIn(anyCollection())).thenReturn(List.of());
    when(highlightRepository.findAllByIdIn(anyCollection())).thenReturn(List.of());
    when(userRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(user(2L, "minji")));

    DiscoverFeedView feed = service.feed(1L, 0, 2);

    assertThat(feed.items()).hasSize(1); // 999-owned row skipped
    assertThat(feed.items().get(0).body()).isEqualTo("보이는 노트");
    assertThat(feed.hasNext()).isTrue(); // 2 rows == requested size 2
  }
}
