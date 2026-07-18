package com.example.short_link.post.collection.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        connId,
        type,
        refId,
        "왜",
        Instant.parse("2026-06-12T00:00:00Z"),
        50L,
        "느린 사고",
        com.example.short_link.post.collection.domain.CollectionKind.COLLECTION,
        ownerId);
  }

  private PostEntity post(long id, long authorId) {
    PostEntity p = new PostEntity(authorId, "slug-" + id, "Title " + id, "ko");
    p.publish();
    ReflectionTestUtils.setField(p, "id", id);
    return p;
  }

  private PostEntity draftPost(long id, long authorId) {
    PostEntity p = new PostEntity(authorId, "slug-" + id, "Title " + id, "ko");
    ReflectionTestUtils.setField(p, "id", id); // status 는 DRAFT 그대로 — 미발행.
    return p;
  }

  private UserEntity user(long id, String username) {
    UserEntity u = new UserEntity("u" + id + "@x.com", "google", "g-" + id);
    u.claimUsername(username);
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  // 팔로우 0 = 콜드스타트 — 빈 화면 대신 전역 공개 피드로 폴백한다(source=global).
  @Test
  void fallsBackToGlobalWhenViewerFollowsNoOne() {
    when(followRepository.findFollowingIds(1L)).thenReturn(List.of());
    when(connectionRepository.findRecentPublicConnections(0, 20))
        .thenReturn(List.of(row(100L, ConnectionBlockType.NOTE, 7L, 3L)));
    NoteEntity note = new NoteEntity(3L, "전역에서 온 노트");
    ReflectionTestUtils.setField(note, "id", 7L);
    when(noteRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(note));
    when(userRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(user(3L, "sori")));

    DiscoverFeedView feed = service.feed(1L, 0, 20, false);

    assertThat(feed.source()).isEqualTo("global");
    assertThat(feed.items()).hasSize(1);
    assertThat(feed.items().get(0).body()).isEqualTo("전역에서 온 노트");
    verify(connectionRepository, never())
        .findPublicConnectionsByOwners(anyCollection(), anyInt(), anyInt());
  }

  // 팔로우는 있는데 그들의 공개 연결이 0 — 첫 페이지만 전역으로 폴백한다.
  @Test
  void fallsBackToGlobalOnFirstPageWhenFollowedCuratorsAreQuiet() {
    when(followRepository.findFollowingIds(1L)).thenReturn(List.of(2L));
    when(connectionRepository.findPublicConnectionsByOwners(anyCollection(), anyInt(), anyInt()))
        .thenReturn(List.of());
    when(connectionRepository.findRecentPublicConnections(0, 20))
        .thenReturn(List.of(row(100L, ConnectionBlockType.NOTE, 7L, 3L)));
    NoteEntity note = new NoteEntity(3L, "전역에서 온 노트");
    ReflectionTestUtils.setField(note, "id", 7L);
    when(noteRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(note));
    when(userRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(user(3L, "sori")));

    DiscoverFeedView feed = service.feed(1L, 0, 20, false);

    assertThat(feed.source()).isEqualTo("global");
    assertThat(feed.items()).hasSize(1);
  }

  // 1페이지 이후의 빈 결과는 개인화 피드의 정상 종료 — 전역을 섞으면 페이지가 오염되므로 폴백하지 않는다.
  @Test
  void doesNotFallBackBeyondFirstPage() {
    when(followRepository.findFollowingIds(1L)).thenReturn(List.of(2L));
    when(connectionRepository.findPublicConnectionsByOwners(anyCollection(), anyInt(), anyInt()))
        .thenReturn(List.of());

    DiscoverFeedView feed = service.feed(1L, 1, 20, false);

    assertThat(feed.source()).isEqualTo("following");
    assertThat(feed.items()).isEmpty();
    assertThat(feed.hasNext()).isFalse();
    verify(connectionRepository, never()).findRecentPublicConnections(anyInt(), anyInt());
  }

  // scope=global 고정 — 팔로우 그래프를 아예 묻지 않고 전역 페이지네이션을 이어간다.
  @Test
  void forceGlobalPinsGlobalFeedRegardlessOfFollowGraph() {
    when(connectionRepository.findRecentPublicConnections(2, 10)).thenReturn(List.of());

    DiscoverFeedView feed = service.feed(1L, 2, 10, true);

    assertThat(feed.source()).isEqualTo("global");
    assertThat(feed.items()).isEmpty();
    verifyNoInteractions(followRepository);
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

    DiscoverFeedView feed = service.feed(1L, 0, 20, false);

    assertThat(feed.items()).hasSize(3);
    assertThat(feed.hasNext()).isFalse(); // 3 < 20
    assertThat(feed.source()).isEqualTo("following"); // 개인화가 채워졌으니 폴백 없음
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

    DiscoverFeedView feed = service.feed(1L, 0, 2, false);

    assertThat(feed.items()).hasSize(1); // 999-owned row skipped
    assertThat(feed.items().get(0).body()).isEqualTo("보이는 노트");
    assertThat(feed.hasNext()).isTrue(); // 2 rows == requested size 2
  }

  @Test
  void publicFeedResolvesGloballyWithoutFollowGate() {
    when(connectionRepository.findRecentPublicConnections(0, 20))
        .thenReturn(
            List.of(
                row(100L, ConnectionBlockType.POST, 5L, 2L),
                row(101L, ConnectionBlockType.NOTE, 7L, 3L)));
    NoteEntity note = new NoteEntity(3L, "더 나은 질문을 기다리는 일");
    ReflectionTestUtils.setField(note, "id", 7L);
    when(noteRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(note));
    when(highlightRepository.findAllByIdIn(anyCollection())).thenReturn(List.of());
    when(postRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(post(5L, 4L)));
    when(userRepository.findAllByIdIn(anyCollection()))
        .thenReturn(List.of(user(2L, "minji"), user(3L, "sori"), user(4L, "author")));

    DiscoverFeedView feed = service.publicFeed(0, 20);

    // 팔로우와 무관하게 전역 공개 연결이 흐른다 — followRepository 는 건드리지 않는다.
    assertThat(feed.items()).hasSize(2);
    assertThat(feed.hasNext()).isFalse(); // 2 < 20
    assertThat(feed.source()).isEqualTo("global");
    assertThat(feed.items().get(0).blockType()).isEqualTo("POST");
    assertThat(feed.items().get(0).curator().username()).isEqualTo("minji");
    assertThat(feed.items().get(0).title()).isEqualTo("Title 5");
    assertThat(feed.items().get(1).blockType()).isEqualTo("NOTE");
    assertThat(feed.items().get(1).body()).isEqualTo("더 나은 질문을 기다리는 일");
    verifyNoInteractions(followRepository);
  }

  // 미발행 글(초안·비공개·관리자 차단)은 공개 발견 피드에서 빠진다 — 담김 연결이 남아 있어도 메타(제목·발췌·슬러그)가 새지 않게.
  @Test
  void publicFeedHidesUnpublishedPosts() {
    when(connectionRepository.findRecentPublicConnections(0, 20))
        .thenReturn(
            List.of(
                row(100L, ConnectionBlockType.POST, 5L, 2L),
                row(101L, ConnectionBlockType.POST, 6L, 2L)));
    when(highlightRepository.findAllByIdIn(anyCollection())).thenReturn(List.of());
    when(noteRepository.findAllByIdIn(anyCollection())).thenReturn(List.of());
    when(postRepository.findAllByIdIn(anyCollection()))
        .thenReturn(List.of(post(5L, 4L), draftPost(6L, 4L)));
    when(userRepository.findAllByIdIn(anyCollection()))
        .thenReturn(List.of(user(2L, "minji"), user(4L, "author")));

    DiscoverFeedView feed = service.publicFeed(0, 20);

    assertThat(feed.items()).hasSize(1); // 초안 글(6)은 빠지고 발행 글(5)만.
    assertThat(feed.items().get(0).title()).isEqualTo("Title 5");
  }

  @Test
  void publicFeedEmptyWhenNoPublicConnectionsAndFlagsHasNext() {
    when(connectionRepository.findRecentPublicConnections(3, 10)).thenReturn(List.of());

    DiscoverFeedView feed = service.publicFeed(3, 10);

    assertThat(feed.items()).isEmpty();
    assertThat(feed.page()).isEqualTo(3);
    assertThat(feed.size()).isEqualTo(10);
    assertThat(feed.hasNext()).isFalse(); // 0 rows != size 10
    assertThat(feed.source()).isEqualTo("global");
    verifyNoInteractions(followRepository);
  }
}
