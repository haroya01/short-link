package com.example.short_link.admin.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.admin.application.helper.BlockedDomainNormalizer;
import com.example.short_link.admin.application.read.BlockedDomainQueryService;
import com.example.short_link.admin.application.write.BlockDomainUseCase;
import com.example.short_link.admin.application.write.UnblockDomainUseCase;
import com.example.short_link.admin.domain.repository.BlockedDomainRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BlockedDomainServiceTest {

  @Autowired private BlockDomainUseCase blockDomain;
  @Autowired private UnblockDomainUseCase unblockDomain;
  @Autowired private BlockedDomainQueryService queryService;
  @Autowired private BlockedDomainRepository repository;

  @Test
  void normalizesAndStores() {
    blockDomain.execute("https://www.bad.example.com/path", "abuse", 1L);
    assertThat(repository.findByDomain("bad.example.com")).isPresent();
    assertThat(repository.findByDomain("www.bad.example.com")).isEmpty();
  }

  @Test
  void detectsBlockedHost() {
    blockDomain.execute("blocked.test", "spam", 1L);
    assertThat(queryService.isBlocked("https://blocked.test/foo")).isTrue();
    assertThat(queryService.isBlocked("https://allowed.example.com/")).isFalse();
  }

  @Test
  void detectsBlockedSubdomain() {
    blockDomain.execute("badco.test", "spam", 1L);
    assertThat(queryService.isBlocked("https://www.badco.test/")).isTrue();
    assertThat(queryService.isBlocked("https://promo.subdomain.badco.test/")).isTrue();
  }

  @Test
  void unblockRemoves() {
    blockDomain.execute("toremove.test", null, 1L);
    assertThat(queryService.isBlocked("https://toremove.test/")).isTrue();
    assertThat(unblockDomain.execute("toremove.test")).isTrue();
  }

  @Test
  void normalizeStripsScheme() {
    assertThat(BlockedDomainNormalizer.normalize("https://www.example.com/path"))
        .isEqualTo("example.com");
    assertThat(BlockedDomainNormalizer.normalize("EXAMPLE.com")).isEqualTo("example.com");
    assertThat(BlockedDomainNormalizer.normalize("  ")).isNull();
    assertThat(BlockedDomainNormalizer.normalize(null)).isNull();
  }
}
