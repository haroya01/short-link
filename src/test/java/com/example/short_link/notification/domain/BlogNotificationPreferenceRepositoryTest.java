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
    // 설정이 없는 사용자는 기본 활성이고, 명시적으로 끈 설정만 조회된다.
    repository.setEnabled(1L, NotificationType.NEW_POST, true);
    repository.setEnabled(2L, NotificationType.NEW_POST, true);
    repository.setEnabled(3L, NotificationType.NEW_POST, false);
    // A muted row for a *different* type must not leak into a NEW_POST query.
    repository.setEnabled(1L, NotificationType.COMMENT, false);

    List<Long> disabled =
        repository.findDisabledUserIds(List.of(1L, 2L, 3L, 4L), NotificationType.NEW_POST);

    assertThat(disabled).containsExactly(3L);
  }
}
