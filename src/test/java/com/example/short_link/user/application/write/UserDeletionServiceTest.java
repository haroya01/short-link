package com.example.short_link.user.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.link.stats.infrastructure.persistence.JpaClickEventRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserDeletionServiceTest {

  @Autowired private UserDeletionService deletionService;
  @Autowired private UserRepository userRepository;
  @Autowired private LinkRepository linkRepository;
  @Autowired private ClickEventRepository clickEventRepository;
  @Autowired private JpaClickEventRepository jpaClickEventRepository;
  @Autowired private RefreshTokenStore refreshTokenStore;
  @Autowired private JdbcTemplate jdbc;
  @PersistenceContext private EntityManager em;

  @Test
  void cascadesAcrossUserLinksAndClicks() {
    UserEntity user = userRepository.save(new UserEntity("del@example.com", "google", "g-del"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com/x", "del0001", user.getId(), null));
    ClickEventEntity click =
        clickEventRepository.save(
            ClickEventEntity.builder().linkId(link.linkId()).bot(false).build());
    refreshTokenStore.save(user.getId(), "jti-1", Duration.ofMinutes(5));

    // deleteAccount is a soft delete — it just flags the row and revokes refresh tokens.
    // hardDelete is what the scheduled cleanup eventually runs to actually purge data.
    deletionService.deleteAccount(user.getId());
    assertThat(userRepository.findById(user.getId())).isPresent();
    assertThat(refreshTokenStore.exists(user.getId(), "jti-1")).isFalse();

    deletionService.hardDelete(user.getId());
    assertThat(userRepository.findById(user.getId())).isEmpty();
    assertThat(linkRepository.findByShortCode(new ShortCode("del0001"))).isEmpty();
    assertThat(jpaClickEventRepository.findById(click.getId())).isEmpty();
  }

  @Test
  void leavesAnonymousLinksUntouched() {
    UserEntity user = userRepository.save(new UserEntity("del2@example.com", "google", "g-del2"));
    LinkEntity owned =
        linkRepository.save(new LinkEntity("https://example.com/y", "del0002", user.getId(), null));
    LinkEntity anonymous = linkRepository.save(new LinkEntity("https://example.com/z", "anon0001"));

    deletionService.hardDelete(user.getId());

    assertThat(linkRepository.findByShortCode(new ShortCode("del0002"))).isEmpty();
    assertThat(linkRepository.findByShortCode(new ShortCode("anon0001"))).isPresent();
  }

  @Test
  void throwsForUnknownUser() {
    assertThatThrownBy(() -> deletionService.deleteAccount(9_999_999L))
        .isInstanceOf(UserException.class);
  }

  // posts/comment/post_like/user_follow/notification 등 블로그 시대 FK 에는 ON DELETE CASCADE 가
  // 없다 — 슬라이스별 eraser 가 먼저 비워주지 않으면 users DELETE 가 FK 위반으로 영구 실패한다.
  @Test
  void hardDeleteClearsBlogAndSocialRowsBlockingTheUserDelete() {
    UserEntity author = userRepository.save(new UserEntity("blog@example.com", "google", "g-blog"));
    UserEntity other =
        userRepository.save(new UserEntity("other@example.com", "google", "g-other"));
    Long authorId = author.getId();
    Long otherId = other.getId();

    long authorPost = insertPost(authorId, "mine");
    long otherPost = insertPost(otherId, "theirs");
    jdbc.update("UPDATE posts SET like_count = 1 WHERE id = ?", otherPost);
    jdbc.update(
        "INSERT INTO post_block (post_id, block_type, content, block_order, created_at, updated_at)"
            + " VALUES (?, 'MARKDOWN', 'body', 0, NOW(6), NOW(6))",
        authorPost);
    jdbc.update(
        "INSERT INTO post_revision (post_id, version_number, title_snapshot, content_json,"
            + " created_at) VALUES (?, 1, 'title', '{}', NOW(6))",
        authorPost);
    jdbc.update(
        "INSERT INTO comment (post_id, user_id, body, created_at, updated_at)"
            + " VALUES (?, ?, 'mine on theirs', NOW(6), NOW(6))",
        otherPost,
        authorId);
    jdbc.update(
        "INSERT INTO comment (post_id, user_id, body, created_at, updated_at)"
            + " VALUES (?, ?, 'theirs on mine', NOW(6), NOW(6))",
        authorPost,
        otherId);
    jdbc.update(
        "INSERT INTO post_like (post_id, user_id, created_at) VALUES (?, ?, NOW(6))",
        otherPost,
        authorId);
    jdbc.update(
        "INSERT INTO user_follow (follower_id, following_id, created_at) VALUES (?, ?, NOW(6))",
        authorId,
        otherId);
    jdbc.update(
        "INSERT INTO user_follow (follower_id, following_id, created_at) VALUES (?, ?, NOW(6))",
        otherId,
        authorId);
    jdbc.update(
        "INSERT INTO notification (recipient_user_id, type, actor_user_id, created_at)"
            + " VALUES (?, 'LIKE', ?, NOW(6))",
        authorId,
        otherId);
    jdbc.update(
        "INSERT INTO notification (recipient_user_id, type, actor_user_id, created_at)"
            + " VALUES (?, 'LIKE', ?, NOW(6))",
        otherId,
        authorId);
    jdbc.update(
        "INSERT INTO series (user_id, slug, title, created_at, updated_at)"
            + " VALUES (?, 's1', 'series', NOW(6), NOW(6))",
        authorId);
    Long seriesId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    jdbc.update(
        "INSERT INTO series_subscription (user_id, series_id, created_at) VALUES (?, ?, NOW(6))",
        otherId,
        seriesId);
    jdbc.update(
        "INSERT INTO cta (user_id, label, url, created_at, updated_at)"
            + " VALUES (?, 'cta', 'https://example.com', NOW(6), NOW(6))",
        authorId);
    jdbc.update(
        "INSERT INTO blog_webhook (user_id, url, secret, events, created_at)"
            + " VALUES (?, 'https://example.com/hook', 'whsec-0123456789abcdef',"
            + " 'POST_PUBLISHED', NOW(6))",
        authorId);

    deletionService.hardDelete(authorId);
    // users DELETE 는 flush 시점에야 실행된다 — 강제로 내보내 FK 체크가 실제로 일어나게 한다
    // (테스트 롤백 속에서 증발하면 이 테스트는 아무것도 증명하지 못한다).
    em.flush();

    assertThat(userRepository.findById(authorId)).isEmpty();
    assertThat(userRepository.findById(otherId)).isPresent();
    assertThat(count("SELECT COUNT(*) FROM posts WHERE user_id = ?", authorId)).isZero();
    assertThat(count("SELECT COUNT(*) FROM posts WHERE user_id = ?", otherId)).isEqualTo(1);
    assertThat(
            count(
                "SELECT COUNT(*) FROM comment WHERE user_id = ? OR post_id = ?",
                authorId,
                authorPost))
        .isZero();
    assertThat(count("SELECT COUNT(*) FROM post_like WHERE user_id = ?", authorId)).isZero();
    assertThat(
            jdbc.queryForObject("SELECT like_count FROM posts WHERE id = ?", Long.class, otherPost))
        .isZero();
    assertThat(
            count(
                "SELECT COUNT(*) FROM user_follow WHERE follower_id = ? OR following_id = ?",
                authorId,
                authorId))
        .isZero();
    assertThat(
            count(
                "SELECT COUNT(*) FROM notification WHERE recipient_user_id = ? OR actor_user_id = ?",
                authorId,
                authorId))
        .isZero();
    assertThat(count("SELECT COUNT(*) FROM series WHERE user_id = ?", authorId)).isZero();
    assertThat(count("SELECT COUNT(*) FROM series_subscription WHERE series_id = ?", seriesId))
        .isZero();
    assertThat(count("SELECT COUNT(*) FROM cta WHERE user_id = ?", authorId)).isZero();
    assertThat(count("SELECT COUNT(*) FROM blog_webhook WHERE user_id = ?", authorId)).isZero();
  }

  private long insertPost(Long userId, String slug) {
    jdbc.update(
        "INSERT INTO posts (user_id, slug, title, status, created_at, updated_at)"
            + " VALUES (?, ?, 'title', 'PUBLISHED', NOW(6), NOW(6))",
        userId,
        slug);
    Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    return id == null ? -1 : id;
  }

  private long count(String sql, Object... args) {
    Long n = jdbc.queryForObject(sql, Long.class, args);
    return n == null ? -1 : n;
  }
}
