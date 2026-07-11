package com.example.short_link.post.collection.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.collection.domain.CollectionConnectionEntity;
import com.example.short_link.post.collection.domain.CollectionEntity;
import com.example.short_link.post.collection.domain.CollectionKind;
import com.example.short_link.post.collection.domain.CollectionVisibility;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import com.example.short_link.post.collection.domain.repository.CollectionConnectionRepository;
import com.example.short_link.post.collection.domain.repository.CollectionRepository;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.note.domain.NoteEntity;
import com.example.short_link.post.note.domain.repository.NoteRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 발견 피드 — 실제 MySQL 로 findRecentPublicConnections 쿼리를 검증한다(비로그인 첫 표면). 팔로우와 무관하게 전역의 *공개* 컬렉션 연결만
 * 흐르고, PRIVATE 은 빠지며, 대상이 사라진 연결은 조용히 건너뛴다. 공유 DB 오염 대비 이 테스트가 만든 고유 제목/본문으로만 단언한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PublicConnectionFeedIntegrationTest {

  @Autowired private DiscoverFeedQueryService service;
  @Autowired private CollectionRepository collectionRepository;
  @Autowired private CollectionConnectionRepository connectionRepository;
  @Autowired private PostRepository postRepository;
  @Autowired private NoteRepository noteRepository;
  @Autowired private UserRepository userRepository;

  private Long user(String username, String seed) {
    UserEntity u = new UserEntity(seed + "@x.com", "google", "g-" + seed);
    u.claimUsername(username);
    return userRepository.save(u).getId();
  }

  private Long post(Long authorId, String slug) {
    PostEntity p = new PostEntity(authorId, slug, "Title " + slug, "ko");
    p.publish();
    return postRepository.save(p).getId();
  }

  private Long note(Long userId, String body) {
    return noteRepository.save(new NoteEntity(userId, body)).getId();
  }

  private Long collection(Long ownerId, String title, CollectionVisibility vis) {
    return collectionRepository
        .save(new CollectionEntity(ownerId, title, null, vis, CollectionKind.COLLECTION))
        .getId();
  }

  private void connect(Long collectionId, ConnectionBlockType type, Long refId, int pos) {
    connectionRepository.save(new CollectionConnectionEntity(collectionId, type, refId, null, pos));
  }

  @Test
  void publicFeed_flowsGlobalPublicConnections_excludingPrivate_ignoringFollowGraph() {
    Long alice = user("alice-pubfeed", "apf");
    Long bob = user("bob-pubfeed", "bpf"); // 아무도 팔로우하지 않는 큐레이터 — 그래도 흐른다.

    Long p1 = post(alice, "pf-uniq-one"); // 제목 "Title pf-uniq-one" 로 이 테스트 고유.
    Long n1 = note(bob, "공개 노트 pf-uniq");
    Long pSecret = post(alice, "pf-uniq-secret");

    // 공개: alice C-pub{p1}, bob C-pub{n1} — 둘 다 흐른다(팔로우 무관).
    Long ac = collection(alice, "A-pub", CollectionVisibility.PUBLIC);
    connect(ac, ConnectionBlockType.POST, p1, 0);
    Long bc = collection(bob, "B-pub", CollectionVisibility.PUBLIC);
    connect(bc, ConnectionBlockType.NOTE, n1, 0);
    // 비공개: 절대 새지 않아야.
    Long priv = collection(alice, "A-priv", CollectionVisibility.PRIVATE);
    connect(priv, ConnectionBlockType.POST, pSecret, 0);
    // 대상이 사라진 공개 연결 — resolve 가 조용히 건너뛴다(예외 없이).
    connect(ac, ConnectionBlockType.POST, 999_000_101L, 1);

    List<DiscoverConnectionView> items = service.publicFeed(0, 200).items();

    // 이 테스트 고유 콘텐츠로만 추린다(공유 DB 오염 무해).
    DiscoverConnectionView postItem =
        items.stream().filter(i -> "Title pf-uniq-one".equals(i.title())).findFirst().orElseThrow();
    // 공개 글은 큐레이터(alice)와 함께 흐른다 — 팔로우 그래프와 무관.
    assertThat(postItem.blockType()).isEqualTo("POST");
    assertThat(postItem.curator().username()).isEqualTo("alice-pubfeed");

    DiscoverConnectionView noteItem =
        items.stream().filter(i -> "공개 노트 pf-uniq".equals(i.body())).findFirst().orElseThrow();
    assertThat(noteItem.blockType()).isEqualTo("NOTE");
    assertThat(noteItem.curator().username()).isEqualTo("bob-pubfeed");

    // 비공개 글은 절대 흐르지 않는다.
    assertThat(items).noneMatch(i -> "Title pf-uniq-secret".equals(i.title()));
  }

  @Test
  void publicFeed_paginates_hasNextWhenPageFull() {
    Long carol = user("carol-page", "cpg");
    Long c = collection(carol, "C-page", CollectionVisibility.PUBLIC);
    connect(c, ConnectionBlockType.POST, post(carol, "pg-1"), 0);
    connect(c, ConnectionBlockType.POST, post(carol, "pg-2"), 1);
    connect(c, ConnectionBlockType.POST, post(carol, "pg-3"), 2);

    // 공유 DB 라 다른 행이 섞일 수 있으니 size 1 페이지가 "가득 참"만 검증(hasNext=true).
    DiscoverFeedView first = service.publicFeed(0, 1);
    assertThat(first.items()).hasSize(1);
    assertThat(first.page()).isEqualTo(0);
    assertThat(first.size()).isEqualTo(1);
    assertThat(first.hasNext()).isTrue(); // rows.size() == size 1
  }
}
