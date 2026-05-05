package com.example.short_link.link;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LinkServiceTest {

  @Autowired private LinkService service;
  @Autowired private LinkRepository repository;

  @Test
  void createsAndPersistsLink() {
    CreateLinkResponse response = service.create("https://example.com/very/long/path");

    assertThat(response.shortCode()).hasSize(7);
    assertThat(response.shortCode()).matches("[0-9A-Za-z]{7}");
    assertThat(response.shortUrl()).endsWith("/" + response.shortCode());
    assertThat(repository.existsByShortCode(response.shortCode())).isTrue();
  }

  @Test
  void createsDistinctCodesForSameUrl() {
    CreateLinkResponse first = service.create("https://example.com");
    CreateLinkResponse second = service.create("https://example.com");

    assertThat(first.shortCode()).isNotEqualTo(second.shortCode());
  }
}
