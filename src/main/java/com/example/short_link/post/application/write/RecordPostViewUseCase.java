package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostViewEventEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostViewEventRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public post 단건 view 카운터. visitor 가 publishing page 열 때 frontend 가 fire. 익명, dedup 없음 (v0 minimum)
 * — 정확한 unique visitor 추적은 L3 tracking JS 별도 트랙. PUBLISHED 글만 집계 (DRAFT / UNPUBLISHED 는 noop).
 *
 * <p>집계는 두 갈래다: posts.view_count 누적 카운터(카드에 보이는 총 조회수)를 올리고, 동시에 post_view_event 에 타임스탬프 한 줄을 남긴다.
 * 후자가 "trending" 을 누적 조회수가 아니라 최근 윈도우 조회수로 계산할 수 있게 하는 근거다.
 */
@Service
public class RecordPostViewUseCase {

  private final UserRepository userRepository;
  private final PostRepository postRepository;
  private final PostViewEventRepository postViewEventRepository;
  private final Clock clock;

  @Autowired
  public RecordPostViewUseCase(
      UserRepository userRepository,
      PostRepository postRepository,
      PostViewEventRepository postViewEventRepository) {
    this(userRepository, postRepository, postViewEventRepository, Clock.systemUTC());
  }

  RecordPostViewUseCase(
      UserRepository userRepository,
      PostRepository postRepository,
      PostViewEventRepository postViewEventRepository,
      Clock clock) {
    this.userRepository = userRepository;
    this.postRepository = postRepository;
    this.postViewEventRepository = postViewEventRepository;
    this.clock = clock;
  }

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
    postViewEventRepository.save(new PostViewEventEntity(post.getId(), clock.instant()));
  }
}
