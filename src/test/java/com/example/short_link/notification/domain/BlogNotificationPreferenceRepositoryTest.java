package com.example.short_link.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.notification.domain.repository.BlogNotificationPreferenceRepository;
import io.queryaudit.junit5.QueryAudit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@QueryAudit
class BlogNotificationPreferenceRepositoryTest {

  @Autowired private BlogNotificationPreferenceRepository repository;

  @Test
  void findDisabledUserIdsReturnsOnlyExplicitOptOutsForTheType() {
    // Two enabled rows (kept on) + one muted row + one candidate with no row at all. Only the
    // explicit opt-out for the queried type comes back — enabled rows and absent candidates default
    // to on and are never returned.
    repository.save(new BlogNotificationPreferenceEntity(1L, NotificationType.NEW_POST, true));
    repository.save(new BlogNotificationPreferenceEntity(2L, NotificationType.NEW_POST, true));
    repository.save(new BlogNotificationPreferenceEntity(3L, NotificationType.NEW_POST, false));
    // A muted row for a *different* type must not leak into a NEW_POST query.
    repository.save(new BlogNotificationPreferenceEntity(1L, NotificationType.COMMENT, false));

    List<Long> disabled =
        repository.findDisabledUserIds(List.of(1L, 2L, 3L, 4L), NotificationType.NEW_POST);

    assertThat(disabled).containsExactly(3L);
  }
}
