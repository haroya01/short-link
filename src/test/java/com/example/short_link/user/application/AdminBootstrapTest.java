package com.example.short_link.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminBootstrapTest {

  @Autowired private UserRepository userRepository;
  @Autowired private AdminBootstrap bootstrap;

  @Test
  void promotesExistingUserWhenEmailMatches() {
    UserEntity user = userRepository.save(new UserEntity("admin@x.com", "google", "g-bs1"));
    assertThat(user.isAdmin()).isFalse();

    ReflectionTestUtils.setField(bootstrap, "bootstrapAdminEmail", "admin@x.com");
    bootstrap.promote();

    UserEntity reloaded = userRepository.findById(user.getId()).orElseThrow();
    assertThat(reloaded.isAdmin()).isTrue();
  }

  @Test
  void noOpWhenEmailIsBlank() {
    UserEntity user = userRepository.save(new UserEntity("plain@x.com", "google", "g-bs2"));

    ReflectionTestUtils.setField(bootstrap, "bootstrapAdminEmail", "");
    bootstrap.promote();

    assertThat(userRepository.findById(user.getId()).orElseThrow().isAdmin()).isFalse();
  }

  @Test
  void noOpWhenEmailIsNull() {
    UserEntity user = userRepository.save(new UserEntity("plain2@x.com", "google", "g-bs3"));

    ReflectionTestUtils.setField(bootstrap, "bootstrapAdminEmail", null);
    bootstrap.promote();

    assertThat(userRepository.findById(user.getId()).orElseThrow().isAdmin()).isFalse();
  }

  @Test
  void noOpWhenAlreadyAdmin() {
    UserEntity user = userRepository.save(new UserEntity("ex-admin@x.com", "google", "g-bs4"));
    user.promoteToAdmin();
    userRepository.save(user);

    ReflectionTestUtils.setField(bootstrap, "bootstrapAdminEmail", "ex-admin@x.com");
    bootstrap.promote();

    assertThat(userRepository.findById(user.getId()).orElseThrow().isAdmin()).isTrue();
  }

  @Test
  void noOpWhenUserNotFound() {
    ReflectionTestUtils.setField(bootstrap, "bootstrapAdminEmail", "ghost@x.com");
    bootstrap.promote();
    assertThat(userRepository.findByEmail("ghost@x.com")).isEmpty();
  }
}
