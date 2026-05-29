package com.example.short_link.user.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.application.dto.MintedAccessToken;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserException;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MintAccessTokenUseCaseTest {

  @Mock private UserRepository userRepository;
  @Mock private JwtTokenService jwt;

  private MintAccessTokenUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new MintAccessTokenUseCase(userRepository, jwt);
  }

  private UserEntity user(long id) {
    UserEntity u = new UserEntity("u" + id + "@x.com", "google", "g-" + id);
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  @Test
  void mintsTokenWithUsersRoleAndTtl() {
    when(userRepository.findById(9L)).thenReturn(Optional.of(user(9L)));
    when(jwt.createAccessToken(9L, "USER")).thenReturn("jwt-token");
    when(jwt.accessTtl()).thenReturn(Duration.ofMinutes(30));

    MintedAccessToken minted = useCase.mintFor(9L);

    assertThat(minted.accessToken()).isEqualTo("jwt-token");
    assertThat(minted.expiresInSeconds()).isEqualTo(1800);
  }

  @Test
  void unknownUserThrows() {
    when(userRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.mintFor(404L)).isInstanceOf(UserException.class);
  }
}
