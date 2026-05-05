package com.example.short_link.link;

import static org.assertj.core.api.Assertions.assertThat;

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
  void saveAndFindById() {
    LinkEntity entity = new LinkEntity("https://example.com/very/long/path", "abc1234");
    LinkEntity saved = repository.save(entity);

    var found = repository.findById(saved.getId());
    assertThat(found).isPresent();
    assertThat(found.get().getOriginalUrl()).isEqualTo("https://example.com/very/long/path");
    assertThat(found.get().getShortCode()).isEqualTo("abc1234");
    assertThat(found.get().getCreatedAt()).isNotNull();
  }
}
