package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class QuotingPostsQueryServiceTest {

  @Mock private PostRepository postRepository;
  @Mock private UserRepository userRepository;
  @Mock private SeriesRepository seriesRepository;

  private QuotingPostsQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new QuotingPostsQueryService(
            postRepository, new PostFeedItemAssembler(userRepository, seriesRepository));
  }

  private static PostEntity published(long id) {
    PostEntity p = new PostEntity(100L, "p" + id, "Title " + id, "ko");
    p.publish();
    ReflectionTestUtils.setField(p, "id", id);
    return p;
  }

  private static UserEntity alice() {
    UserEntity u = new UserEntity("a@x.com", "google", "g-100");
    u.claimUsername("alice");
    ReflectionTestUtils.setField(u, "id", 100L);
    return u;
  }

  @Test
  void listsTheFirstPageAndSaysThereIsMore() {
    List<PostEntity> rows = new ArrayList<>();
    for (long id = 1; id <= QuotingPostsQueryService.SIZE + 1; id++) {
      rows.add(published(id));
    }
    when(postRepository.findPublishedQuotingNote(5L, null, 0, QuotingPostsQueryService.SIZE + 1))
        .thenReturn(rows);
    when(userRepository.findAllByIdIn(List.of(100L))).thenReturn(List.of(alice()));

    PublicFeedView view = service.ofNote(5L, null, 0);

    assertThat(view.items()).hasSize(QuotingPostsQueryService.SIZE);
    assertThat(view.items().getFirst().author().username()).isEqualTo("alice");
    assertThat(view.hasNext()).isTrue();
    assertThat(view.page()).isZero();
  }

  @Test
  void aLaterPageEndsWhenFewerComeBackAndANegativePageIsTheFirst() {
    when(postRepository.findPublishedQuotingNote(5L, null, 0, QuotingPostsQueryService.SIZE + 1))
        .thenReturn(List.of(published(1L)));
    when(userRepository.findAllByIdIn(List.of(100L))).thenReturn(List.of(alice()));

    PublicFeedView view = service.ofNote(5L, null, -3);

    assertThat(view.items()).extracting(PublicFeedItem::id).containsExactly(1L);
    assertThat(view.hasNext()).isFalse();
    assertThat(view.page()).isZero();
  }
}
