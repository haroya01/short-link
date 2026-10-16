package com.example.short_link.post.application.read;

import com.example.short_link.post.application.access.PostUnlistedUrls;
import com.example.short_link.post.application.write.PostOwnership;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostRevisionRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostQueryService {

  private final PostRepository postRepository;
  private final PostBlockRepository postBlockRepository;
  private final PostRevisionRepository postRevisionRepository;
  private final PostOwnership postOwnership;
  private final PostUnlistedUrls unlistedUrls;

  public PostView findOwnPost(Long userId, Long postId) {
    PostEntity post =
        postRepository
            .findById(postId)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, postId));
    if (!post.isOwnedBy(userId)) {
      throw new PostException(PostErrorCode.PERMISSION_DENIED).with("postId", postId);
    }
    return PostView.from(post, unlistedUrls.of(post));
  }

  public List<PostView> listMyPosts(Long userId) {
    Function<PostEntity, String> urls = unlistedUrls.forAuthors();
    return postRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(post -> PostView.from(post, urls.apply(post)))
        .toList();
  }

  public List<PostBlockView> listBlocks(Long userId, Long postId) {
    postOwnership.requireOwned(userId, postId);
    return blocksOf(postId);
  }

  public PostBodyView readBody(Long userId, Long postId) {
    PostEntity post = postOwnership.requireOwned(userId, postId);
    return new PostBodyView(post.getContentVersion(), blocksOf(postId));
  }

  private List<PostBlockView> blocksOf(Long postId) {
    return postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(postId).stream()
        .map(PostBlockView::from)
        .toList();
  }

  public List<PostRevisionView> listRevisions(Long userId, Long postId) {
    postOwnership.requireOwned(userId, postId);
    return postRevisionRepository.findAllByPostIdOrderByVersionNumberDesc(postId).stream()
        .map(PostRevisionView::from)
        .toList();
  }
}
