package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostViewEventEntity;
import com.example.short_link.post.domain.TagPrefKind;
import com.example.short_link.post.domain.UserTagPrefEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostViewEventRepository;
import com.example.short_link.post.domain.repository.UserTagPrefRepository;
import com.example.short_link.support.DiscoverableBodies;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ForYouFeedIntegrationTest {

  @Autowired private ForYouQueryService service;
  @Autowired private PostRepository postRepository;
  @Autowired private PostViewEventRepository postViewEventRepository;
  @Autowired private UserTagPrefRepository userTagPrefRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private EntityManager em;

  @Test
  void readersLanguageHiddenTagsAndHumanViewsShapeTheFeed() {
    long viewer = user("fyviewer", "ko");
    long koAuthor = user("fykoauthor", "ko");
    long jaAuthor = user("fyjaauthor", "ja");
    long other = user("fyother", "ko");
    long another = user("fyanother", "ko");
    userTagPrefRepository.save(new UserTagPrefEntity(viewer, "fy-java", TagPrefKind.FOLLOW));
    userTagPrefRepository.save(new UserTagPrefEntity(viewer, "fy-crypto", TagPrefKind.HIDE));
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    long korean = publish(koAuthor, "fy-ko", "ko", now.minus(Duration.ofHours(20)), "fy-java");
    long japanese = publish(jaAuthor, "fy-ja", "ja", now.minus(Duration.ofHours(1)), "fy-java");
    long hidden =
        publish(other, "fy-hidden", "ko", now.minus(Duration.ofHours(2)), "fy-java", "fy-crypto");
    Instant same = now.minus(Duration.ofHours(30));
    long readByPeople = publish(other, "fy-people", "ko", same, "fy-go");
    long crawled = publish(another, "fy-crawled", "ko", same, "fy-go");
    for (int i = 0; i < 3; i++) view(readByPeople, now.minus(Duration.ofHours(1)), false);
    for (int i = 0; i < 30; i++) view(crawled, now.minus(Duration.ofHours(1)), true);
    em.flush();
    em.clear();

    List<PublicFeedItem> items = service.feedForYou(viewer, 0, 50).items();
    List<Long> ids = items.stream().map(PublicFeedItem::id).toList();

    assertThat(ids).doesNotContain(hidden);
    assertThat(ids.indexOf(korean)).isLessThan(ids.indexOf(japanese));
    assertThat(ids.indexOf(readByPeople)).isLessThan(ids.indexOf(crawled));
    assertThat(items.get(ids.indexOf(korean)).followReason().tag()).isEqualTo("fy-java");
  }

  private long user(String handle, String locale) {
    UserEntity u = userRepository.save(new UserEntity(handle + "@x.com", "google", "g-" + handle));
    u.claimUsername(handle);
    u.updateLocale(locale);
    return userRepository.save(u).getId();
  }

  private long publish(long author, String slug, String lang, Instant at, String... tags) {
    PostEntity p = new PostEntity(author, slug, slug, lang);
    p.updateTags(List.of(tags));
    DiscoverableBodies.discoverable(p);
    ReflectionTestUtils.setField(p, "publishedAt", at);
    p.publish();
    return postRepository.save(p).getId();
  }

  private void view(long postId, Instant at, boolean bot) {
    postViewEventRepository.save(
        PostViewEventEntity.builder().postId(postId).viewedAt(at).bot(bot).build());
  }
}
