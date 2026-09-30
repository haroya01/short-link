package com.example.short_link.user.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.application.dto.IssuedTokens;
import com.example.short_link.user.domain.RefreshToken;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@ActiveProfiles("test")
class AuthServiceRefreshConcurrencyTest {

  private static final int ROUNDS = 20;

  @Autowired private AuthService authService;
  @Autowired private UserRepository users;
  @Autowired private JwtTokenService jwt;
  @Autowired private JdbcTemplate jdbc;
  @MockitoSpyBean private RefreshTokenStore refreshStore;

  private Long userId;

  @BeforeEach
  void saveCommittedUser() {
    String unique = UUID.randomUUID().toString();
    userId = users.save(new UserEntity(unique + "@refresh.test", "google", unique)).getId();
  }

  @AfterEach
  void deleteFixture() {
    if (userId == null) return;
    refreshStore.deleteAllForUser(userId);
    jdbc.update("DELETE FROM users WHERE id = ?", userId);
  }

  @Test
  void concurrentRefreshesOfOneTokenRotateOnceAndServeTheOtherThroughGrace() throws Exception {
    for (int round = 0; round < ROUNDS; round++) {
      RefreshToken token = jwt.createRefreshToken(userId);
      refreshStore.save(userId, token.jti(), jwt.refreshTtl());
      CyclicBarrier bothAtConsume = new CyclicBarrier(2);
      List<Boolean> consumed = new CopyOnWriteArrayList<>();
      doAnswer(
              invocation -> {
                bothAtConsume.await(10, TimeUnit.SECONDS);
                boolean won = (boolean) invocation.callRealMethod();
                consumed.add(won);
                return won;
              })
          .when(refreshStore)
          .consume(eq(userId), eq(token.jti()), any());

      List<IssuedTokens> issued = refreshTwiceAtOnce(token.token());

      assertThat(consumed).containsExactlyInAnyOrder(true, false);
      verify(refreshStore).wasRecentlyRotated(userId, token.jti());
      assertThat(refreshStore.exists(userId, token.jti())).isFalse();
      assertThat(issued)
          .extracting(tokens -> jwt.parseRefreshToken(tokens.refreshToken()).jti())
          .allSatisfy(jti -> assertThat(refreshStore.exists(userId, jti)).isTrue());
    }
  }

  private List<IssuedTokens> refreshTwiceAtOnce(String refreshToken) throws Exception {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> authService.refresh(refreshToken));
      var second = executor.submit(() -> authService.refresh(refreshToken));
      return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
    }
  }
}
