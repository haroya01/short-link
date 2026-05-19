package com.example.short_link.link.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.queryaudit.junit5.DetectNPlusOne;
import io.queryaudit.junit5.ExpectMaxQueryCount;
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
class LinkRepositoryTest {

  @Autowired private LinkRepository repository;

  @Test
  @DetectNPlusOne
  @ExpectMaxQueryCount(5)
  void existsByShortCode() {
    repository.save(new LinkEntity("https://example.com", "abc1234"));

    assertThat(repository.existsByShortCode("abc1234")).isTrue();
    assertThat(repository.existsByShortCode("missing")).isFalse();
  }

  @Test
  @DetectNPlusOne
  @ExpectMaxQueryCount(8)
  void shortCodeIsCaseSensitive() {
    repository.save(new LinkEntity("https://example.com/lower", "abc1234"));
    repository.save(new LinkEntity("https://example.com/upper", "ABC1234"));

    assertThat(repository.existsByShortCode("abc1234")).isTrue();
    assertThat(repository.existsByShortCode("ABC1234")).isTrue();
    assertThat(repository.existsByShortCode("Abc1234")).isFalse();
  }
}
