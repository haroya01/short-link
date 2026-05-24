package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.link.domain.CustomDomainEntity;
import com.example.short_link.link.domain.repository.CustomDomainRepository;
import com.example.short_link.link.exception.CustomDomainNotFoundException;
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
class CustomDomainServiceExtendedTest {

  @Autowired private CustomDomainService service;
  @Autowired private CustomDomainRepository repository;
  @Autowired private UserRepository userRepository;

  @Test
  void deleteRemovesDomainOwnedByUser() {
    UserEntity user = userRepository.save(new UserEntity("d@x.com", "google", "g-cdd"));
    CustomDomainService.DomainSummary saved = service.register(user.getId(), "del.example.com");

    service.delete(user.getId(), saved.id());

    assertThat(repository.findById(saved.id())).isEmpty();
  }

  @Test
  void deleteByNonOwnerThrows() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-cddo"));
    UserEntity attacker = userRepository.save(new UserEntity("a@x.com", "google", "g-cdda"));
    CustomDomainService.DomainSummary saved = service.register(owner.getId(), "owners.example.com");

    assertThatThrownBy(() -> service.delete(attacker.getId(), saved.id()))
        .isInstanceOf(CustomDomainNotFoundException.class);
  }

  @Test
  void deleteUnknownIdThrows() {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-cddu"));
    assertThatThrownBy(() -> service.delete(user.getId(), 999_999_999L))
        .isInstanceOf(CustomDomainNotFoundException.class);
  }

  @Test
  void resolveOwnerReturnsNullForNullOrBlank() {
    assertThat(service.resolveOwner(null)).isNull();
    assertThat(service.resolveOwner("")).isNull();
    assertThat(service.resolveOwner("   ")).isNull();
  }

  @Test
  void resolveOwnerReturnsNullForUnknownDomain() {
    assertThat(service.resolveOwner("unknown.example.com")).isNull();
  }

  @Test
  void resolveOwnerReturnsNullForUnverifiedDomain() {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-cdru"));
    service.register(user.getId(), "unverified.example.com");
    // Domain is unverified — resolveOwner must return null.
    assertThat(service.resolveOwner("unverified.example.com")).isNull();
  }

  @Test
  void resolveOwnerReturnsUserIdForVerifiedDomain() {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-cdrv"));
    CustomDomainService.DomainSummary saved =
        service.register(user.getId(), "verified.example.com");
    // Manually verify by mutating the entity (real verify() requires DNS).
    CustomDomainEntity entity = repository.findById(saved.id()).orElseThrow();
    entity.markVerified();
    repository.save(entity);

    assertThat(service.resolveOwner("verified.example.com")).isEqualTo(user.getId());
  }

  @Test
  void findPendingWithinWindowExcludesVerified() {
    UserEntity user = userRepository.save(new UserEntity("p@x.com", "google", "g-cdfp"));
    CustomDomainService.DomainSummary pending =
        service.register(user.getId(), "pending.example.com");
    CustomDomainService.DomainSummary verified =
        service.register(user.getId(), "alreadyverified.example.com");
    CustomDomainEntity vEntity = repository.findById(verified.id()).orElseThrow();
    vEntity.markVerified();
    repository.save(vEntity);

    var candidates = service.findPendingWithinWindow();
    assertThat(candidates)
        .extracting(CustomDomainEntity::getDomain)
        .contains("pending.example.com");
    assertThat(candidates)
        .extracting(CustomDomainEntity::getDomain)
        .doesNotContain("alreadyverified.example.com");
  }
}
