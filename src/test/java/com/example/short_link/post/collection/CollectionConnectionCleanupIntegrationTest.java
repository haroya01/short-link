package com.example.short_link.post.collection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.note.application.write.NoteThreadControls;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.post.application.write.CreateHighlightUseCase;
import com.example.short_link.post.application.write.DeletePostCommand;
import com.example.short_link.post.application.write.DeletePostUseCase;
import com.example.short_link.post.collection.domain.CollectionConnectionEntity;
import com.example.short_link.post.collection.domain.CollectionEntity;
import com.example.short_link.post.collection.domain.CollectionVisibility;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import com.example.short_link.post.collection.domain.repository.CollectionConnectionRepository;
import com.example.short_link.post.collection.domain.repository.CollectionRepository;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.application.write.UserDeletionService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// 대상 블록(글·하이라이트·노트)이 하드 삭제되면 그를 가리키던 collection_connection 행도 같은 트랜잭션에서 사라져야 한다 — ref_id 는 FK 없는 다형
// 참조라 DB 가 대신 지워주지 못한다. 죽은 행이 남으면 countByCollectionId 가 상세 화면이 렌더하는 수보다 많아지는 불일치가 생긴다(실제 MySQL 로
// 검증).
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CollectionConnectionCleanupIntegrationTest {

  @Autowired private CreateHighlightUseCase createHighlight;
  @Autowired private NoteThreadControls threads;
  @Autowired private DeletePostUseCase deletePost;
  @Autowired private CollectionConnectionRepository connectionRepository;
  @Autowired private CollectionRepository collectionRepository;
  @Autowired private PostRepository postRepository;
  @Autowired private PostHighlightRepository highlightRepository;
  @Autowired private NoteRepository noteRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private UserDeletionService userDeletion;
  @PersistenceContext private EntityManager em;

  @Test
  void deletingHighlightAndNotePurgesTheirConnectionsAndCorrectsCount() {
    Long alice = user("orphan-a", "opa");
    Long postId = post(alice, "orphan-post-a");
    Long highlightId = highlight(postId, alice, "지울 게 없을 때가 완성");
    Long noteId = note(alice, "연결은 조용한 동사");

    Long collectionId = collection(alice, "orphan C-a");
    connect(collectionId, ConnectionBlockType.POST, postId, 0);
    connect(collectionId, ConnectionBlockType.HIGHLIGHT, highlightId, 1);
    connect(collectionId, ConnectionBlockType.NOTE, noteId, 2);
    assertThat(connectionRepository.countByCollectionId(collectionId)).isEqualTo(3);

    createHighlight.delete(alice, highlightId);

    assertThat(
            connectionRepository.findAllByBlockTypeAndRefId(
                ConnectionBlockType.HIGHLIGHT, highlightId))
        .isEmpty();
    assertThat(connectionRepository.countByCollectionId(collectionId)).isEqualTo(2);

    threads.delete(alice, noteId);

    assertThat(connectionRepository.findAllByBlockTypeAndRefId(ConnectionBlockType.NOTE, noteId))
        .isEmpty();
    assertThat(connectionRepository.countByCollectionId(collectionId)).isEqualTo(1);
    assertThat(connectionRepository.findAllByBlockTypeAndRefId(ConnectionBlockType.POST, postId))
        .hasSize(1);
  }

  @Test
  void deletingPostPurgesItsOwnConnectionAndItsHighlightConnections() {
    Long alice = user("orphan-b", "opb");
    Long postId = post(alice, "orphan-post-b");
    Long highlightId = highlight(postId, alice, "글과 하이라이트가 함께");

    Long collectionId = collection(alice, "orphan C-b");
    connect(collectionId, ConnectionBlockType.POST, postId, 0);
    connect(collectionId, ConnectionBlockType.HIGHLIGHT, highlightId, 1);
    assertThat(connectionRepository.countByCollectionId(collectionId)).isEqualTo(2);

    deletePost.execute(new DeletePostCommand(alice, postId));

    assertThat(connectionRepository.findAllByBlockTypeAndRefId(ConnectionBlockType.POST, postId))
        .isEmpty();
    assertThat(
            connectionRepository.findAllByBlockTypeAndRefId(
                ConnectionBlockType.HIGHLIGHT, highlightId))
        .isEmpty();
    assertThat(connectionRepository.countByCollectionId(collectionId)).isZero();
  }

  @Test
  void erasingAnAccountPurgesConnectionsToItsPostsInOtherCollections() {
    Long alice = user("orphan-c", "opc");
    Long bob = user("orphan-d", "opd");
    Long alicePost = post(alice, "orphan-post-c");
    Long bobPost = post(bob, "orphan-post-d");
    Long bobs = collection(bob, "orphan C-d");
    connect(bobs, ConnectionBlockType.POST, alicePost, 0);
    connect(bobs, ConnectionBlockType.POST, bobPost, 1);

    eraseAccount(alice);

    assertThat(connectionRepository.findAllByBlockTypeAndRefId(ConnectionBlockType.POST, alicePost))
        .isEmpty();
    assertThat(connectionRepository.countByCollectionId(bobs)).isEqualTo(1);
  }

  @Test
  void erasingAnAccountPurgesConnectionsToItsHighlightsAndThoseOnItsPosts() {
    Long alice = user("orphan-e", "ope");
    Long bob = user("orphan-f", "opf");
    Long alicePost = post(alice, "orphan-post-e");
    Long bobPost = post(bob, "orphan-post-f");
    Long aliceOnBob = highlight(bobPost, alice, "남의 글에 남긴 내 줄");
    Long bobOnAlice = highlight(alicePost, bob, "사라질 글에 남긴 남의 줄");
    Long bobOnBob = highlight(bobPost, bob, "남는 줄");
    Long bobs = collection(bob, "orphan C-f");
    connect(bobs, ConnectionBlockType.HIGHLIGHT, aliceOnBob, 0);
    connect(bobs, ConnectionBlockType.HIGHLIGHT, bobOnAlice, 1);
    connect(bobs, ConnectionBlockType.HIGHLIGHT, bobOnBob, 2);

    eraseAccount(alice);

    assertThat(
            connectionRepository.findAllByBlockTypeAndRefId(
                ConnectionBlockType.HIGHLIGHT, aliceOnBob))
        .isEmpty();
    assertThat(
            connectionRepository.findAllByBlockTypeAndRefId(
                ConnectionBlockType.HIGHLIGHT, bobOnAlice))
        .isEmpty();
    assertThat(connectionRepository.countByCollectionId(bobs)).isEqualTo(1);
  }

  @Test
  void erasingAnAccountPurgesConnectionsToItsNotesInOtherCollections() {
    Long alice = user("orphan-g", "opg");
    Long bob = user("orphan-h", "oph");
    Long aliceNote = note(alice, "지워질 노트");
    Long bobNote = note(bob, "남는 노트");
    Long bobs = collection(bob, "orphan C-h");
    connect(bobs, ConnectionBlockType.NOTE, aliceNote, 0);
    connect(bobs, ConnectionBlockType.NOTE, bobNote, 1);

    eraseAccount(alice);

    assertThat(connectionRepository.findAllByBlockTypeAndRefId(ConnectionBlockType.NOTE, aliceNote))
        .isEmpty();
    assertThat(connectionRepository.countByCollectionId(bobs)).isEqualTo(1);
  }

  private void eraseAccount(Long userId) {
    userDeletion.hardDelete(userId);
    em.flush();
    assertThat(userRepository.findById(userId)).isEmpty();
  }

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

  private Long highlight(Long postId, Long userId, String quote) {
    return highlightRepository
        .save(new PostHighlightEntity(postId, userId, 0, 0, 0, 3, quote, null))
        .getId();
  }

  private Long note(Long userId, String body) {
    return noteRepository.save(new NoteEntity(userId, body, null, null)).getId();
  }

  private Long collection(Long ownerId, String title) {
    return collectionRepository
        .save(new CollectionEntity(ownerId, title, null, CollectionVisibility.PUBLIC, false))
        .getId();
  }

  private void connect(Long collectionId, ConnectionBlockType type, Long refId, int pos) {
    connectionRepository.save(new CollectionConnectionEntity(collectionId, type, refId, null, pos));
  }
}
