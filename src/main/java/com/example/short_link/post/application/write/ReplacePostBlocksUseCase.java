package com.example.short_link.post.application.write;

import com.example.short_link.post.application.read.PostBlockView;
import com.example.short_link.post.application.read.PostBodyView;
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
  private final PostEditGuard editGuard;
  private final PostBlockRepository postBlockRepository;
  private final PostSearchTextUpdater searchTextUpdater;
  private final PostNoteQuotes noteQuotes;
  private final PostRevisionCapture revisions;

  @Transactional
  public PostBodyView execute(ReplacePostBlocksCommand cmd) {
    // 본문만 교체해도 last_edited_at이 바뀌도록 부모 글에 편집 시각을 기록한다.
    PostEntity post = postOwnership.requireOwnedForUpdate(cmd.userId(), cmd.postId());
    editGuard.check(post, cmd.baseVersion(), cmd.overwrite());
    post.markEdited();
    List<PostBlockEntity> persisted =
        rewrite(
            cmd.postId(),
            cmd.blocks(),
            postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(cmd.postId()));
    // 검색 평문은 공개 글만 읽고 발행할 때 다시 만들므로 초안 자동 저장에서는 쓰지 않는다.
    if (post.isPublished()) {
      searchTextUpdater.refresh(post, persisted);
    }
    noteQuotes.index(post, persisted);
    recordPublished(post, persisted);
    return new PostBodyView(
        post.getContentVersion(), persisted.stream().map(PostBlockView::from).toList());
  }

  private List<PostBlockEntity> rewrite(
      Long postId,
      List<ReplacePostBlocksCommand.BlockInput> wanted,
      List<PostBlockEntity> current) {
    int kept = Math.min(current.size(), wanted.size());
    for (int i = 0; i < kept; i++) {
      ReplacePostBlocksCommand.BlockInput input = wanted.get(i);
      current.get(i).rewrite(input.type(), input.content(), i);
    }
    if (current.size() > kept) {
      postBlockRepository.deleteAllById(
          current.subList(kept, current.size()).stream().map(PostBlockEntity::getId).toList());
      return new ArrayList<>(current.subList(0, kept));
    }
    if (wanted.size() == kept) {
      return current;
    }
    List<PostBlockEntity> added = new ArrayList<>(wanted.size() - kept);
    for (int i = kept; i < wanted.size(); i++) {
      ReplacePostBlocksCommand.BlockInput input = wanted.get(i);
      added.add(new PostBlockEntity(postId, input.type(), input.content(), i));
    }
    // IDENTITY는 JDBC 배치가 안 되므로 다중 행 INSERT 후 생성 ID를 다시 읽는다.
    postBlockRepository.insertAll(added);
    return postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(postId);
  }

  // 독자가 본 내용은 모두 버전으로 남긴다. 초안 자동 저장은 기록하지 않는다.
  private void recordPublished(PostEntity post, List<PostBlockEntity> blocks) {
    if (post.isPublished()) {
      revisions.capture(post, blocks);
    }
  }
}
