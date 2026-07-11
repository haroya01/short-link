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
    // Stamp the edit on the (managed) post — a body replace otherwise never touches the posts row,
    // so without this a content-only edit wouldn't move last_edited_at. Hibernate dirty-checks the
    // managed entity and flushes the stamp at commit (no explicit save needed).
    PostEntity post = postOwnership.requireOwned(cmd.userId(), cmd.postId());
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
    // One multi-row INSERT instead of saveAll's per-row INSERTs (IDENTITY ids can't be batched),
    // then re-read so callers still receive the persisted blocks with their generated ids.
    postBlockRepository.insertAll(entities);
    List<PostBlockEntity> persisted =
        postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(cmd.postId());
    // 방금 심은 블록을 그대로 넘겨 파생 검색 컬럼을 갱신 — 재조회 없이 본문까지 인덱싱된다.
    searchTextUpdater.refresh(post, persisted);
    return persisted;
  }
}
