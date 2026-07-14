package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostBlockType;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostSearchTextRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ReplacePostBlocksUseCaseTest {

  @Mock private PostOwnership postOwnership;
  @Mock private PostBlockRepository postBlockRepository;
  @Mock private PostSearchTextRepository postSearchTextRepository;

  private ReplacePostBlocksUseCase useCase;

  @BeforeEach
  void setUp() {
    PostSearchTextUpdater searchTextUpdater =
        new PostSearchTextUpdater(
            postBlockRepository, postSearchTextRepository, JsonMapper.builder().build());
    useCase = new ReplacePostBlocksUseCase(postOwnership, postBlockRepository, searchTextUpdater);
  }

  @Test
  void replacesWithNewBlocks() {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);
    // insertAll returns no ids, so the use case reads the blocks back for the response — the
    // re-read is the source of the returned list.
    List<PostBlockEntity> persisted =
        List.of(
            new PostBlockEntity(42L, PostBlockType.PARAGRAPH, "Hello", 0),
            new PostBlockEntity(42L, PostBlockType.IMAGE, "{\"url\":\"x\"}", 1),
            new PostBlockEntity(42L, PostBlockType.DIVIDER, null, 2));
    when(postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(42L)).thenReturn(persisted);

    List<PostBlockEntity> result =
        useCase.execute(
            new ReplacePostBlocksCommand(
                7L,
                42L,
                List.of(
                    new ReplacePostBlocksCommand.BlockInput(PostBlockType.PARAGRAPH, "Hello"),
                    new ReplacePostBlocksCommand.BlockInput(PostBlockType.IMAGE, "{\"url\":\"x\"}"),
                    new ReplacePostBlocksCommand.BlockInput(PostBlockType.DIVIDER, null))));

    verify(postBlockRepository).deleteAllByPostId(42L);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<PostBlockEntity>> inserted = ArgumentCaptor.forClass(List.class);
    verify(postBlockRepository).insertAll(inserted.capture());
    List<PostBlockEntity> built = inserted.getValue();
    assertThat(built).hasSize(3);
    assertThat(built.get(0).getType()).isEqualTo(PostBlockType.PARAGRAPH);
    assertThat(built.get(0).getBlockOrder()).isZero();
    assertThat(built.get(1).getType()).isEqualTo(PostBlockType.IMAGE);
    assertThat(built.get(1).getBlockOrder()).isEqualTo(1);
    assertThat(built.get(2).getType()).isEqualTo(PostBlockType.DIVIDER);
    assertThat(built.get(2).getBlockOrder()).isEqualTo(2);

    assertThat(result).isSameAs(persisted);

    // 파생 검색 평문이 제목 + 본문(문단 텍스트)까지 담겨 곁 테이블에 upsert 되는지 — 본문이 인덱싱되는 게 이 업그레이드의 핵심.
    ArgumentCaptor<String> searchText = ArgumentCaptor.forClass(String.class);
    verify(postSearchTextRepository).upsert(any(), searchText.capture());
    assertThat(searchText.getValue()).contains("My Post").contains("Hello");
  }

  @Test
  void replaceWithEmptyDeletesAll() {
    PostEntity post = new PostEntity(7L, "my-post", "My Post", "ko");
    when(postOwnership.requireOwned(7L, 42L)).thenReturn(post);

    List<PostBlockEntity> result =
        useCase.execute(new ReplacePostBlocksCommand(7L, 42L, List.of()));

    verify(postBlockRepository).deleteAllByPostId(42L);
    assertThat(result).isEmpty();
    // 본문을 비워도 검색 평문은 제목으로 다시 채워져 upsert 된다(예전 본문 잔재 없음).
    verify(postSearchTextRepository).upsert(any(), eq("My Post"));
  }

  @Test
  void rejectsBlocksOverMax() {
    List<ReplacePostBlocksCommand.BlockInput> tooMany =
        IntStream.range(0, 501)
            .mapToObj(
                i -> new ReplacePostBlocksCommand.BlockInput(PostBlockType.PARAGRAPH, "block " + i))
            .toList();

    // 본문 한도는 사용자가 볼 사유라 PostException(BODY_LIMIT) — 익명 IllegalArgument 가 아니다.
    assertThatThrownBy(() -> new ReplacePostBlocksCommand(7L, 42L, tooMany))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.BODY_LIMIT);
  }

  @Test
  void rejectsForeignOwner() {
    when(postOwnership.requireOwned(7L, 42L))
        .thenThrow(new PostException(PostErrorCode.PERMISSION_DENIED));

    assertThatThrownBy(() -> useCase.execute(new ReplacePostBlocksCommand(7L, 42L, List.of())))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.PERMISSION_DENIED);
  }
}
