package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkRepository;
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
    LinkCreated created = service.create("https://example.com/very/long/path");

    assertThat(created.shortCode()).hasSize(7);
    assertThat(created.shortCode()).matches("[0-9A-Za-z]{7}");
    assertThat(repository.existsByShortCode(created.shortCode())).isTrue();
  }

  @Test
  void createsDistinctCodesForSameUrl() {
    LinkCreated first = service.create("https://example.com");
    LinkCreated second = service.create("https://example.com");

    assertThat(first.shortCode()).isNotEqualTo(second.shortCode());
  }
}
