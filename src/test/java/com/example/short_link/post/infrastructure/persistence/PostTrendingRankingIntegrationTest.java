package com.example.short_link.post.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.post.PostAudience;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.support.DiscoverableBodies;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostTrendingRankingIntegrationTest {

  private static final int VISITORS = 40;

  @Autowired private JpaPostRepository jpa;
  @Autowired private UserRepository users;
  @Autowired private EntityManager em;

  private String tag;
  private PostRepositoryAdapter posts;

  @BeforeEach
  void setUp() {
    tag = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    posts = new PostRepositoryAdapter(jpa, new TrendingPostRanking(jpa, Duration.ofHours(1)));
  }

  private Long user(String prefix) {
    String name = prefix + tag;
    UserEntity user = new UserEntity(name + "@example.com", "google", name);
    user.claimUsername(name);
    return users.save(user).getId();
  }

  private PostEntity post(Long authorId, String language, int visitors) {
    PostEntity post = new PostEntity(authorId, "p" + UUID.randomUUID(), "인기 글", language);
    post.publish();
    DiscoverableBodies.discoverable(post);
    PostEntity saved = jpa.saveAndFlush(post);
    for (int i = 0; i < visitors; i++) {
      update(
          "INSERT INTO post_view_event (post_id, viewed_at, is_bot, visitor_hash)"
              + " VALUES (?1, NOW(6), FALSE, ?2)",
          saved.getId(),
          tag + "-" + i);
    }
    return saved;
  }

  private void update(String sql, Object... values) {
    var query = em.createNativeQuery(sql);
    for (int i = 0; i < values.length; i++) {
      query.setParameter(i + 1, values[i]);
    }
    query.executeUpdate();
  }

  private List<Long> home(Long viewerId) {
    return posts.findPublishedTrendingAt(viewerId, 0, 10).stream().map(PostEntity::getId).toList();
  }

  @Test
  void aRankingAnyoneLoadedStillAnswersBlocksAndMutesPerReader() {
    Long author = user("a");
    Long blocker = user("b");
    Long blocked = user("d");
    Long muter = user("m");
    Long reader = user("r");
    Long post = post(author, "ko", VISITORS).getId();

    assertThat(home(null)).contains(post);
    Long later = post(author, "ko", VISITORS * 2).getId();
    update(
        "INSERT INTO user_block (blocker_id, blocked_id, created_at) VALUES (?1, ?2, NOW(6))",
        blocker,
        author);
    update(
        "INSERT INTO user_block (blocker_id, blocked_id, created_at) VALUES (?1, ?2, NOW(6))",
        author,
        blocked);
    update(
        "INSERT INTO user_mute (user_id, muted_user_id, hide_notifications, created_at)"
            + " VALUES (?1, ?2, FALSE, NOW(6))",
        muter,
        author);

    assertThat(home(reader)).contains(post).doesNotContain(later);
    assertThat(home(blocker)).doesNotContain(post);
    assertThat(home(blocked)).doesNotContain(post);
    assertThat(home(muter)).doesNotContain(post);
  }

  @Test
  void aPostThatLeftTheListingAfterRankingIsNotShown() {
    Long author = user("a");
    PostEntity unpublished = post(author, "ko", VISITORS);
    PostEntity moved = post(author, "ko", VISITORS);
    Long room = roomOf(author);

    assertThat(home(null)).contains(unpublished.getId(), moved.getId());
    unpublished.unpublish();
    moved.changeAudience(PostAudience.ROOM, room, null);
    jpa.saveAllAndFlush(List.of(unpublished, moved));

    assertThat(home(null)).doesNotContain(unpublished.getId(), moved.getId());
  }

  private Long roomOf(Long owner) {
    update(
        "INSERT INTO room (owner_id, name, created_at, updated_at) VALUES (?1, ?2, NOW(6), NOW(6))",
        owner,
        "room" + tag);
    Long room =
        ((Number)
                em.createNativeQuery("SELECT id FROM room WHERE name = ?1")
                    .setParameter(1, "room" + tag)
                    .getSingleResult())
            .longValue();
    update(
        "INSERT INTO room_member (room_id, user_id, joined_at) VALUES (?1, ?2, NOW(6))",
        room,
        owner);
    return room;
  }

  @Test
  void aLanguageIsRankedOnItsOwn() {
    Long author = user("a");
    Long korean = post(author, "ko", VISITORS).getId();
    Long japanese = post(author, "ja", VISITORS).getId();

    List<Long> ko =
        posts.findPublishedTrending(null, "ko", 0, 10).stream().map(PostEntity::getId).toList();
    List<Long> all =
        posts.findPublishedTrending(null, null, 0, 10).stream().map(PostEntity::getId).toList();

    assertThat(ko).contains(korean).doesNotContain(japanese);
    assertThat(all).contains(korean, japanese);
  }

  @Test
  void pagesPastTheSharedRankingContinueWithoutRepeatsOrGaps() {
    List<Long> authors = List.of(user("x"), user("y"), user("z"));
    Set<Long> mine = new HashSet<>();
    for (int i = 0; i < TrendingPostRanking.SIZE + 5; i++) {
      mine.add(post(authors.get(i % authors.size()), "ko", 1).getId());
    }

    List<Long> seen = new ArrayList<>();
    for (int offset = 0; ; offset += 10) {
      List<PostEntity> page = posts.findPublishedTrendingAt(null, offset, 10);
      page.stream().map(PostEntity::getId).filter(mine::contains).forEach(seen::add);
      if (page.size() < 10) {
        break;
      }
    }

    assertThat(seen).doesNotHaveDuplicates().hasSize(mine.size());
  }
}
