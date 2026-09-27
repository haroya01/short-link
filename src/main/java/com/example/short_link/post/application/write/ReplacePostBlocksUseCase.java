package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReplacePostBlocksUseCase {

  private final PostOwnership postOwnership;
  private final PostBlockRepository postBlockRepository;
  private final PostSearchTextUpdater searchTextUpdater;

  @Transactional
  public List<PostBlockEntity> execute(ReplacePostBlocksCommand cmd) {
    // 본문만 교체해도 last_edited_at이 바뀌도록 부모 글에 편집 시각을 기록한다.
    PostEntity post = postOwnership.requireOwnedForUpdate(cmd.userId(), cmd.postId());
    post.markEdited();
    postBlockRepository.deleteAllByPostId(cmd.postId());
    if (cmd.blocks().isEmpty()) {
      // 본문이 비었어도 검색 컬럼은 제목·요약·태그로 다시 채워야 한다(예전 본문 잔재가 남지 않게).
      searchTextUpdater.refresh(post, List.of());
      return List.of();
    }
    List<PostBlockEntity> entities = new ArrayList<>(cmd.blocks().size());
    int order = 0;
    for (ReplacePostBlocksCommand.BlockInput input : cmd.blocks()) {
      entities.add(new PostBlockEntity(cmd.postId(), input.type(), input.content(), order++));
    }
    // IDENTITY는 JDBC 배치가 안 되므로 다중 행 INSERT 후 생성 ID를 다시 읽는다.
    postBlockRepository.insertAll(entities);
    List<PostBlockEntity> persisted =
        postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(cmd.postId());
    searchTextUpdater.refresh(post, persisted);
    return persisted;
  }
}
