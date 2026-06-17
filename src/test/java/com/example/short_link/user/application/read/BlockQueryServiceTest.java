package com.example.short_link.user.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.BlockRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BlockQueryServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private BlockRepository blockRepository;

  private BlockQueryService service;

  @BeforeEach
  void setUp() {
    service = new BlockQueryService(userRepository, blockRepository);
  }

  private UserEntity user(long id, String username) {
    UserEntity u = new UserEntity("u" + id + "@x.com", "google", "g-" + id);
    if (username != null) {
      u.claimUsername(username);
    }
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  @Test
  void myBlocksResolvesInBlockOrder() {
    when(blockRepository.findBlockedIds(9L)).thenReturn(List.of(3L, 2L)); // newest first
    when(userRepository.findAllByIdIn(anyCollection()))
        .thenReturn(List.of(user(2L, "bob"), user(3L, "carol")));

    List<BlockedUserView> result = service.myBlocks(9L);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).username()).isEqualTo("carol"); // order from findBlockedIds preserved
    assertThat(result.get(1).username()).isEqualTo("bob");
  }

  @Test
  void myBlocksSkipsUsersWithoutHandle() {
    when(blockRepository.findBlockedIds(9L)).thenReturn(List.of(2L, 4L));
    when(userRepository.findAllByIdIn(anyCollection()))
        .thenReturn(List.of(user(2L, "bob"), user(4L, null))); // 4 has no username

    List<BlockedUserView> result = service.myBlocks(9L);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo(2L);
  }

  @Test
  void myBlocksEmptyWhenNoneBlocked() {
    when(blockRepository.findBlockedIds(9L)).thenReturn(List.of());

    assertThat(service.myBlocks(9L)).isEmpty();
  }
}
