package com.example.short_link.user.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminBootstrapTest {

  @Autowired private UserRepository userRepository;

  private AdminBootstrap bootstrapWith(String email) {
    return new AdminBootstrap(userRepository, email);
  }

  @Test
  void promotesExistingUserWhenEmailMatches() {
    UserEntity user = userRepository.save(new UserEntity("admin@x.com", "google", "g-bs1"));
    assertThat(user.isAdmin()).isFalse();

    bootstrapWith("admin@x.com").promote();

    UserEntity reloaded = userRepository.findById(user.getId()).orElseThrow();
    assertThat(reloaded.isAdmin()).isTrue();
  }

  @Test
  void noOpWhenEmailIsBlank() {
    UserEntity user = userRepository.save(new UserEntity("plain@x.com", "google", "g-bs2"));

    bootstrapWith("").promote();

    assertThat(userRepository.findById(user.getId()).orElseThrow().isAdmin()).isFalse();
  }

  @Test
  void noOpWhenEmailIsNull() {
    UserEntity user = userRepository.save(new UserEntity("plain2@x.com", "google", "g-bs3"));

    bootstrapWith(null).promote();

    assertThat(userRepository.findById(user.getId()).orElseThrow().isAdmin()).isFalse();
  }

  @Test
  void noOpWhenAlreadyAdmin() {
    UserEntity user = userRepository.save(new UserEntity("ex-admin@x.com", "google", "g-bs4"));
    user.promoteToAdmin();
    userRepository.save(user);

    bootstrapWith("ex-admin@x.com").promote();

    assertThat(userRepository.findById(user.getId()).orElseThrow().isAdmin()).isTrue();
  }

  @Test
  void noOpWhenUserNotFound() {
    bootstrapWith("ghost@x.com").promote();
    assertThat(userRepository.findByEmail("ghost@x.com")).isEmpty();
  }
}
