package com.example.short_link.link.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LinkRepositoryTest {

  @Autowired private LinkRepository repository;

  @Test
  void existsByShortCode() {
    repository.save(new LinkEntity("https://example.com", "abc1234"));

    assertThat(repository.existsByShortCode("abc1234")).isTrue();
    assertThat(repository.existsByShortCode("missing")).isFalse();
  }
}
