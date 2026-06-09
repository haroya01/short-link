package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.UserFeedPrefEntity;
import com.example.short_link.post.domain.repository.UserFeedPrefRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SetFeedPrefUseCaseTest {

  @Mock private UserFeedPrefRepository repository;

  private SetFeedPrefUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new SetFeedPrefUseCase(repository);
  }

  @Test
  void createsRowWhenAbsent() {
    when(repository.findByUserId(9L)).thenReturn(Optional.empty());

    useCase.setDefaultTab(9L, "following");

    ArgumentCaptor<UserFeedPrefEntity> saved = ArgumentCaptor.forClass(UserFeedPrefEntity.class);
    verify(repository).save(saved.capture());
    assertThat(saved.getValue().getDefaultTab()).isEqualTo("following");
    assertThat(saved.getValue().getUserId()).isEqualTo(9L);
  }

  @Test
  void updatesExistingRow() {
    UserFeedPrefEntity existing = new UserFeedPrefEntity(9L, "recent");
    when(repository.findByUserId(9L)).thenReturn(Optional.of(existing));

    useCase.setDefaultTab(9L, "series");

    assertThat(existing.getDefaultTab()).isEqualTo("series");
    verify(repository).save(existing);
  }

  @Test
  void rejectsUnknownTab() {
    assertThatThrownBy(() -> useCase.setDefaultTab(9L, "bogus"))
        .isInstanceOf(ResponseStatusException.class);
    verify(repository, never()).save(any());
  }
}
