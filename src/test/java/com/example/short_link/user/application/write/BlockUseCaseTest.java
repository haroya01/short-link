package com.example.short_link.user.application.write;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.user.domain.UserBlockEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.BlockRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BlockUseCaseTest {

  @Mock private UserRepository userRepository;
  @Mock private BlockRepository blockRepository;

  private BlockUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new BlockUseCase(userRepository, blockRepository);
  }

  private UserEntity user(long id, String username) {
    UserEntity u = new UserEntity("u" + id + "@x.com", "google", "g-" + id);
    u.claimUsername(username);
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  @Test
  void blockCreatesEdgeWhenNew() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    when(blockRepository.existsByBlockerIdAndBlockedId(9L, 2L)).thenReturn(false);

    useCase.block(9L, "bob");

    verify(blockRepository).save(any(UserBlockEntity.class));
  }

  @Test
  void blockIsIdempotent() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    when(blockRepository.existsByBlockerIdAndBlockedId(9L, 2L)).thenReturn(true);

    useCase.block(9L, "bob");

    verify(blockRepository, never()).save(any());
  }

  @Test
  void blockingYourselfIsRejected() {
    when(userRepository.findByUsername("me")).thenReturn(Optional.of(user(9L, "me")));

    assertThatThrownBy(() -> useCase.block(9L, "me")).isInstanceOf(UserException.class);
    verify(blockRepository, never()).save(any());
  }

  @Test
  void blockingUnknownUserThrows() {
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.block(9L, "ghost")).isInstanceOf(UserException.class);
  }

  @Test
  void unblockDeletesExistingEdge() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    when(blockRepository.findByBlockerIdAndBlockedId(9L, 2L))
        .thenReturn(Optional.of(new UserBlockEntity(9L, 2L)));

    useCase.unblock(9L, "bob");

    verify(blockRepository).delete(any(UserBlockEntity.class));
  }

  @Test
  void unblockWhenNotBlockedIsNoOp() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    when(blockRepository.findByBlockerIdAndBlockedId(9L, 2L)).thenReturn(Optional.empty());

    useCase.unblock(9L, "bob");

    verify(blockRepository, never()).delete(any());
  }
}
