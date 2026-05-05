package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkEntity;
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
    LinkEntity result = service.create("https://example.com/very/long/path");

    assertThat(result.getShortCode()).hasSize(7);
    assertThat(result.getShortCode()).matches("[0-9A-Za-z]{7}");
    assertThat(result.getOriginalUrl()).isEqualTo("https://example.com/very/long/path");
    assertThat(repository.existsByShortCode(result.getShortCode())).isTrue();
  }

  @Test
  void createsDistinctCodesForSameUrl() {
    LinkEntity first = service.create("https://example.com");
    LinkEntity second = service.create("https://example.com");

    assertThat(first.getShortCode()).isNotEqualTo(second.getShortCode());
  }
}
