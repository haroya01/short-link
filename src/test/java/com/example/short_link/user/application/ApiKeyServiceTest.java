package com.example.short_link.user.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.user.application.ApiKeyService.IssuedApiKey;
import com.example.short_link.user.domain.ApiKeyEntity;
import com.example.short_link.user.domain.ApiKeyRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import io.queryaudit.junit5.QueryAudit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@QueryAudit
class ApiKeyServiceTest {

  @Autowired private ApiKeyService service;
  @Autowired private ApiKeyRepository repository;
  @Autowired private UserRepository userRepository;

  @Test
  void issuedKeyHasCorrectPrefixAndIsResolvable() {
    UserEntity user = userRepository.save(new UserEntity("k@example.com", "google", "g-k1"));
    IssuedApiKey issued = service.issue(user.getId(), "test key");

    assertThat(issued.rawKey()).startsWith("kurl_");
    assertThat(issued.rawKey().length()).isEqualTo("kurl_".length() + 32);

    var resolved = service.resolve(issued.rawKey());
    assertThat(resolved).isPresent();
    assertThat(resolved.get().getUserId()).isEqualTo(user.getId());
  }

  @Test
  void revokedKeyIsNotResolvable() {
    UserEntity user = userRepository.save(new UserEntity("k2@example.com", "google", "g-k2"));
    IssuedApiKey issued = service.issue(user.getId(), null);
    assertThat(service.resolve(issued.rawKey())).isPresent();

    boolean removed = service.revoke(user.getId(), issued.id());
    assertThat(removed).isTrue();
    assertThat(service.resolve(issued.rawKey())).isEmpty();
  }

  @Test
  void otherUserCannotRevoke() {
    UserEntity owner = userRepository.save(new UserEntity("ko@example.com", "google", "g-ko"));
    UserEntity other = userRepository.save(new UserEntity("kx@example.com", "google", "g-kx"));
    IssuedApiKey issued = service.issue(owner.getId(), null);

    boolean removed = service.revoke(other.getId(), issued.id());
    assertThat(removed).isFalse();

    ApiKeyEntity entity = repository.findById(issued.id()).orElseThrow();
    assertThat(entity.isActive()).isTrue();
  }

  @Test
  void resolveRejectsForeignPrefix() {
    assertThat(service.resolve("notakey")).isEmpty();
    assertThat(service.resolve(null)).isEmpty();
  }

  @Test
  void onlyHashIsStored() {
    UserEntity user = userRepository.save(new UserEntity("kh@example.com", "google", "g-kh"));
    IssuedApiKey issued = service.issue(user.getId(), null);
    ApiKeyEntity entity = repository.findById(issued.id()).orElseThrow();
    assertThat(entity.getKeyHash()).hasSize(64);
    assertThat(entity.getKeyHash()).doesNotContain(issued.rawKey());
  }
}
