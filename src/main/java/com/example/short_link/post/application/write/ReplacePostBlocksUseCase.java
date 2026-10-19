package com.example.short_link.post.application.write;

import com.example.short_link.post.application.read.PostBlockView;
import com.example.short_link.post.application.read.PostBodyView;
import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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

  // 지우기·넣기·다시 읽기를 고쳐 쓰기보다 먼저 한다. 블록이 먼저 바뀌어 있으면 그 쿼리들이 세션을 미리
  // 비워 글 행을 한 번 더 UPDATE한다.
  private List<PostBlockEntity> rewrite(
      Long postId,
      List<ReplacePostBlocksCommand.BlockInput> wanted,
      List<PostBlockEntity> current) {
    int kept = Math.min(current.size(), wanted.size());
    if (current.size() > kept) {
      postBlockRepository.deleteAllById(
          current.subList(kept, current.size()).stream().map(PostBlockEntity::getId).toList());
    }
    List<PostBlockEntity> persisted = new ArrayList<>(current.subList(0, kept));
    if (wanted.size() > kept) {
      List<PostBlockEntity> added = new ArrayList<>(wanted.size() - kept);
      for (int i = kept; i < wanted.size(); i++) {
        ReplacePostBlocksCommand.BlockInput input = wanted.get(i);
        added.add(new PostBlockEntity(postId, input.type(), input.content(), i));
      }
      // IDENTITY는 JDBC 배치가 안 되므로 다중 행 INSERT 후 생성 ID를 다시 읽는다.
      postBlockRepository.insertAll(added);
      Set<Long> existing =
          persisted.stream().map(PostBlockEntity::getId).collect(Collectors.toSet());
      postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(postId).stream()
          .filter(block -> !existing.contains(block.getId()))
          .forEach(persisted::add);
    }
    for (int i = 0; i < kept; i++) {
      ReplacePostBlocksCommand.BlockInput input = wanted.get(i);
      persisted.get(i).rewrite(input.type(), input.content(), i);
    }
    return persisted;
  }

  // 독자가 본 내용은 모두 버전으로 남긴다. 초안 자동 저장은 기록하지 않는다.
  private void recordPublished(PostEntity post, List<PostBlockEntity> blocks) {
    if (post.isPublished()) {
      revisions.capture(post, blocks);
    }
  }
}
