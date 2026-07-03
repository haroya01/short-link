package com.example.short_link.post.collection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.application.write.CreateHighlightUseCase;
import com.example.short_link.post.application.write.DeletePostCommand;
import com.example.short_link.post.application.write.DeletePostUseCase;
import com.example.short_link.post.collection.application.read.CollectionQueryService;
import com.example.short_link.post.collection.domain.CollectionConnectionEntity;
import com.example.short_link.post.collection.domain.CollectionEntity;
import com.example.short_link.post.collection.domain.CollectionKind;
import com.example.short_link.post.collection.domain.CollectionVisibility;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import com.example.short_link.post.collection.domain.repository.CollectionConnectionRepository;
import com.example.short_link.post.collection.domain.repository.CollectionRepository;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.note.application.write.NoteCommandService;
import com.example.short_link.post.note.domain.NoteEntity;
import com.example.short_link.post.note.domain.repository.NoteRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대상 블록(글·하이라이트·노트)이 하드 삭제되면 그를 가리키던 collection_connection 행도 같은 트랜잭션에서 사라져야 한다 — ref_id 는 FK 없는 다형
 * 참조라 DB 가 대신 지워주지 못한다. 죽은 행이 남으면 countByCollectionId 가 상세 화면이 렌더하는 수보다 많아지는 불일치가 생긴다(실제 MySQL 로
 * 검증).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CollectionConnectionCleanupIntegrationTest {

  @Autowired private CreateHighlightUseCase createHighlight;
  @Autowired private NoteCommandService noteCommand;
  @Autowired private DeletePostUseCase deletePost;
  @Autowired private CollectionQueryService collectionQuery;
  @Autowired private CollectionConnectionRepository connectionRepository;
  @Autowired private CollectionRepository collectionRepository;
  @Autowired private PostRepository postRepository;
  @Autowired private PostHighlightRepository highlightRepository;
  @Autowired private NoteRepository noteRepository;
  @Autowired private UserRepository userRepository;

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
    assertThat(collectionQuery.connectionCount(collectionId)).isEqualTo(3);

    createHighlight.delete(alice, highlightId);

    assertThat(
            connectionRepository.findAllByBlockTypeAndRefId(
                ConnectionBlockType.HIGHLIGHT, highlightId))
        .isEmpty();
    assertThat(collectionQuery.connectionCount(collectionId)).isEqualTo(2);

    noteCommand.delete(alice, noteId);

    assertThat(connectionRepository.findAllByBlockTypeAndRefId(ConnectionBlockType.NOTE, noteId))
        .isEmpty();
    assertThat(collectionQuery.connectionCount(collectionId)).isEqualTo(1);
    // 손대지 않은 글 연결은 그대로.
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
    assertThat(collectionQuery.connectionCount(collectionId)).isEqualTo(2);

    deletePost.execute(new DeletePostCommand(alice, postId));

    assertThat(connectionRepository.findAllByBlockTypeAndRefId(ConnectionBlockType.POST, postId))
        .isEmpty();
    assertThat(
            connectionRepository.findAllByBlockTypeAndRefId(
                ConnectionBlockType.HIGHLIGHT, highlightId))
        .isEmpty();
    assertThat(collectionQuery.connectionCount(collectionId)).isZero();
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
    return noteRepository.save(new NoteEntity(userId, body)).getId();
  }

  private Long collection(Long ownerId, String title) {
    return collectionRepository
        .save(
            new CollectionEntity(
                ownerId, title, null, CollectionVisibility.PUBLIC, CollectionKind.COLLECTION))
        .getId();
  }

  private void connect(Long collectionId, ConnectionBlockType type, Long refId, int pos) {
    connectionRepository.save(new CollectionConnectionEntity(collectionId, type, refId, null, pos));
  }
}
