package com.example.short_link.customdomain.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.customdomain.application.dto.DomainSummary;
import com.example.short_link.customdomain.application.read.CustomDomainQueryService;
import com.example.short_link.customdomain.application.write.DeleteCustomDomainUseCase;
import com.example.short_link.customdomain.application.write.RegisterCustomDomainUseCase;
import com.example.short_link.customdomain.domain.CustomDomainEntity;
import com.example.short_link.customdomain.domain.repository.CustomDomainRepository;
import com.example.short_link.customdomain.exception.CustomDomainException;
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

  @Autowired private RegisterCustomDomainUseCase register;
  @Autowired private DeleteCustomDomainUseCase delete;
  @Autowired private CustomDomainQueryService queryService;
  @Autowired private CustomDomainRepository repository;
  @Autowired private UserRepository userRepository;

  @Test
  void deleteRemovesDomainOwnedByUser() {
    UserEntity user = userRepository.save(new UserEntity("d@x.com", "google", "g-cdd"));
    DomainSummary saved = register.execute(user.getId(), "del.example.com");

    delete.execute(user.getId(), saved.id());

    assertThat(repository.findById(saved.id())).isEmpty();
  }

  @Test
  void deleteByNonOwnerThrows() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-cddo"));
    UserEntity attacker = userRepository.save(new UserEntity("a@x.com", "google", "g-cdda"));
    DomainSummary saved = register.execute(owner.getId(), "owners.example.com");

    assertThatThrownBy(() -> delete.execute(attacker.getId(), saved.id()))
        .isInstanceOf(CustomDomainException.class);
  }

  @Test
  void deleteUnknownIdThrows() {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-cddu"));
    assertThatThrownBy(() -> delete.execute(user.getId(), 999_999_999L))
        .isInstanceOf(CustomDomainException.class);
  }

  @Test
  void resolveOwnerReturnsNullForNullOrBlank() {
    assertThat(queryService.resolveOwner(null)).isNull();
    assertThat(queryService.resolveOwner("")).isNull();
    assertThat(queryService.resolveOwner("   ")).isNull();
  }

  @Test
  void resolveOwnerReturnsNullForUnknownDomain() {
    assertThat(queryService.resolveOwner("unknown.example.com")).isNull();
  }

  @Test
  void resolveOwnerReturnsNullForUnverifiedDomain() {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-cdru"));
    register.execute(user.getId(), "unverified.example.com");
    assertThat(queryService.resolveOwner("unverified.example.com")).isNull();
  }

  @Test
  void resolveOwnerReturnsUserIdForVerifiedDomain() {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-cdrv"));
    DomainSummary saved = register.execute(user.getId(), "verified.example.com");
    CustomDomainEntity entity = repository.findById(saved.id()).orElseThrow();
    entity.markVerified();
    repository.save(entity);

    assertThat(queryService.resolveOwner("verified.example.com")).isEqualTo(user.getId());
  }

  @Test
  void findPendingWithinWindowExcludesVerified() {
    UserEntity user = userRepository.save(new UserEntity("p@x.com", "google", "g-cdfp"));
    register.execute(user.getId(), "pending.example.com");
    DomainSummary verified = register.execute(user.getId(), "alreadyverified.example.com");
    CustomDomainEntity vEntity = repository.findById(verified.id()).orElseThrow();
    vEntity.markVerified();
    repository.save(vEntity);

    var candidates = queryService.findPendingWithinWindow();
    assertThat(candidates)
        .extracting(CustomDomainEntity::getDomain)
        .contains("pending.example.com");
    assertThat(candidates)
        .extracting(CustomDomainEntity::getDomain)
        .doesNotContain("alreadyverified.example.com");
  }
}
