package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public post 단건 view 카운터. visitor 가 publishing page 열 때 frontend 가 fire. 익명, dedup 없음 (v0 minimum)
 * — 정확한 unique visitor 추적은 L3 tracking JS 별도 트랙. PUBLISHED 글만 increment (DRAFT / UNPUBLISHED 는
 * noop).
 */
@Service
@RequiredArgsConstructor
public class RecordPostViewUseCase {

  private final UserRepository userRepository;
  private final PostRepository postRepository;

  @Transactional
  public void execute(RecordPostViewCommand cmd) {
    String normalized = cmd.username().trim().toLowerCase();
    UserEntity author =
        userRepository.findByUsername(normalized).filter(u -> !u.isDeleted()).orElse(null);
    if (author == null) return;
    PostEntity post = postRepository.findByUserIdAndSlug(author.getId(), cmd.slug()).orElse(null);
    if (post == null || !post.isPublished()) return;
    post.incrementViewCount();
    postRepository.save(post);
  }
}
