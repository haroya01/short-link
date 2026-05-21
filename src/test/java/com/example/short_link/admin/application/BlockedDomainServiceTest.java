package com.example.short_link.admin.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.admin.domain.BlockedDomainRepository;
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
class BlockedDomainServiceTest {

  @Autowired private BlockedDomainService service;
  @Autowired private BlockedDomainRepository repository;

  @Test
  void normalizesAndStores() {
    service.block("https://www.bad.example.com/path", "abuse", 1L);
    assertThat(repository.findByDomain("bad.example.com")).isPresent();
    assertThat(repository.findByDomain("www.bad.example.com")).isEmpty();
  }

  @Test
  void detectsBlockedHost() {
    service.block("blocked.test", "spam", 1L);
    assertThat(service.isBlocked("https://blocked.test/foo")).isTrue();
    assertThat(service.isBlocked("https://allowed.example.com/")).isFalse();
  }

  @Test
  void detectsBlockedSubdomain() {
    service.block("badco.test", "spam", 1L);
    assertThat(service.isBlocked("https://www.badco.test/")).isTrue();
    assertThat(service.isBlocked("https://promo.subdomain.badco.test/")).isTrue();
  }

  @Test
  void unblockRemoves() {
    service.block("toremove.test", null, 1L);
    assertThat(service.isBlocked("https://toremove.test/")).isTrue();
    assertThat(service.unblock("toremove.test")).isTrue();
  }

  @Test
  void normalizeStripsScheme() {
    assertThat(BlockedDomainService.normalize("https://www.example.com/path"))
        .isEqualTo("example.com");
    assertThat(BlockedDomainService.normalize("EXAMPLE.com")).isEqualTo("example.com");
    assertThat(BlockedDomainService.normalize("  ")).isNull();
    assertThat(BlockedDomainService.normalize(null)).isNull();
  }
}
