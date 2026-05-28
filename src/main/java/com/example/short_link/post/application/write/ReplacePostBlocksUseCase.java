package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostBlockEntity;
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

  @Transactional
  public List<PostBlockEntity> execute(ReplacePostBlocksCommand cmd) {
    postOwnership.requireOwned(cmd.userId(), cmd.postId());
    postBlockRepository.deleteAllByPostId(cmd.postId());
    if (cmd.blocks().isEmpty()) {
      return List.of();
    }
    List<PostBlockEntity> entities = new ArrayList<>(cmd.blocks().size());
    int order = 0;
    for (ReplacePostBlocksCommand.BlockInput input : cmd.blocks()) {
      entities.add(new PostBlockEntity(cmd.postId(), input.type(), input.content(), order++));
    }
    return postBlockRepository.saveAll(entities);
  }
}
