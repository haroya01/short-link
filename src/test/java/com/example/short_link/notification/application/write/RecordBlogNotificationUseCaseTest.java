package com.example.short_link.notification.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.notification.application.dto.NotificationPostRef;
import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RecordBlogNotificationUseCaseTest {

  @Mock private NotificationRepository repository;
  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  private RecordBlogNotificationUseCase useCase() {
    return new RecordBlogNotificationUseCase(repository, jsonMapper);
  }

  @Test
  void serializesPostReferenceIntoPayload() {
    when(repository.save(org.mockito.ArgumentMatchers.any(NotificationEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    useCase()
        .record(9L, NotificationType.LIKE, 2L, new NotificationPostRef(10L, "my-post", "Hi", null));

    ArgumentCaptor<NotificationEntity> saved = ArgumentCaptor.forClass(NotificationEntity.class);
    org.mockito.Mockito.verify(repository).save(saved.capture());
    NotificationEntity e = saved.getValue();
    assertThat(e.getRecipientUserId()).isEqualTo(9L);
    assertThat(e.getType()).isEqualTo(NotificationType.LIKE);
    assertThat(e.getActorUserId()).isEqualTo(2L);
    assertThat(e.getPayload()).contains("\"slug\":\"my-post\"").contains("\"title\":\"Hi\"");
  }

  @Test
  void leavesPayloadNullWhenNoPost() {
    when(repository.save(org.mockito.ArgumentMatchers.any(NotificationEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    useCase().record(9L, NotificationType.FOLLOW, 2L, null);

    ArgumentCaptor<NotificationEntity> saved = ArgumentCaptor.forClass(NotificationEntity.class);
    org.mockito.Mockito.verify(repository).save(saved.capture());
    assertThat(saved.getValue().getPayload()).isNull();
  }
}
