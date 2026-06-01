package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.domain.TagPrefKind;
import com.example.short_link.post.domain.UserTagPrefEntity;
import com.example.short_link.post.domain.repository.UserTagPrefRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SetTagPrefUseCaseTest {

  @Mock private UserTagPrefRepository repository;

  private SetTagPrefUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new SetTagPrefUseCase(repository);
  }

  @Test
  void followCreatesWhenAbsent() {
    when(repository.findByUserIdAndTag(9L, "개발")).thenReturn(Optional.empty());

    useCase.follow(9L, "개발");

    ArgumentCaptor<UserTagPrefEntity> saved = ArgumentCaptor.forClass(UserTagPrefEntity.class);
    verify(repository).save(saved.capture());
    assertThat(saved.getValue().getKind()).isEqualTo(TagPrefKind.FOLLOW);
    assertThat(saved.getValue().getTag()).isEqualTo("개발");
  }

  @Test
  void followFlipsAnExistingHide() {
    UserTagPrefEntity hidden = new UserTagPrefEntity(9L, "개발", TagPrefKind.HIDE);
    when(repository.findByUserIdAndTag(9L, "개발")).thenReturn(Optional.of(hidden));

    useCase.follow(9L, "개발");

    assertThat(hidden.getKind()).isEqualTo(TagPrefKind.FOLLOW);
    verify(repository).save(hidden);
  }

  @Test
  void unfollowDeletesOnlyWhenCurrentlyFollow() {
    when(repository.findByUserIdAndTag(9L, "개발"))
        .thenReturn(Optional.of(new UserTagPrefEntity(9L, "개발", TagPrefKind.HIDE)));

    useCase.unfollow(9L, "개발"); // tag is HIDE, not FOLLOW → must not delete

    verify(repository, never()).delete(any());
  }

  @Test
  void unfollowDeletesWhenFollow() {
    UserTagPrefEntity followed = new UserTagPrefEntity(9L, "개발", TagPrefKind.FOLLOW);
    when(repository.findByUserIdAndTag(9L, "개발")).thenReturn(Optional.of(followed));

    useCase.unfollow(9L, "개발");

    verify(repository).delete(followed);
  }

  @Test
  void blankTagIsNoOp() {
    useCase.follow(9L, "   ");
    verify(repository, never()).save(any());
  }
}
