package com.example.short_link.link.export.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = {"short-link.export.event-batch=2", "short-link.export.event-cap=3"})
@Transactional
class LinkExportServiceTest {

  @Autowired private LinkExportService service;
  @Autowired private LinkRepository linkRepository;
  @Autowired private ClickEventRepository clickRepository;
  @Autowired private UserRepository userRepository;

  @Test
  void exportEventsCsvIteratesAcrossBatches() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-csvbatch"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "csvbat1", owner.getId(), null));
    for (int i = 0; i < 5; i++) {
      clickRepository.save(
          ClickEventEntity.builder()
              .linkId(link.linkId())
              .userAgent("ua")
              .clientIp("1.2.3." + i)
              .deviceClass("desktop")
              .referrer("https://www.instagram.com/p/" + i)
              .bot(false)
              .build());
    }

    String csv = service.exportEventsCsv(owner.getId(), new ShortCode("csvbat1"));

    long dataRows = csv.lines().count() - 1;
    assertThat(dataRows).isEqualTo(3);
    assertThat(csv).contains("social");
  }

  @Test
  void exportEventsCsvCoversEachStatsDimension() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-csvdim"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "csvdim1", owner.getId(), null));
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .userAgent("ua")
            .clientIp("1.1.1.1")
            .deviceClass("mobile")
            .osName("iOS")
            .browserName("Safari")
            .countryCode("KR")
            .cityName("Seoul")
            .referrer("https://www.youtube.com/watch")
            .referrerHost("www.youtube.com")
            .bot(false)
            .build());

    for (String dim :
        new String[] {
          "daily", "hourly", "country", "city", "device", "os", "browser", "referrer", "channel"
        }) {
      String csv = service.exportStatsCsv(owner.getId(), new ShortCode("csvdim1"), dim);
      assertThat(csv).as("dimension %s", dim).isNotEmpty();
    }
  }

  @Test
  void rejectsExportForNonOwner() {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-csvno"));
    UserEntity attacker = userRepository.save(new UserEntity("a@x.com", "google", "g-csvnoa"));
    linkRepository.save(new LinkEntity("https://example.com", "csvno1", owner.getId(), null));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.exportEventsCsv(attacker.getId(), new ShortCode("csvno1")))
        .isInstanceOf(LinkException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.exportStatsCsv(attacker.getId(), new ShortCode("csvno1"), "daily"))
        .isInstanceOf(LinkException.class);
  }

  @Test
  void rejectsUnknownLink() {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-csv404"));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.exportEventsCsv(user.getId(), new ShortCode("missing")))
        .isInstanceOf(LinkException.class);
  }
}
