package com.example.short_link.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.link.domain.ClickEventEntity;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.ClickEventRepository;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.user.application.dto.UserDataExport;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserDataExportServiceTest {

  @Autowired private UserDataExportService exportService;
  @Autowired private UserRepository userRepository;
  @Autowired private LinkRepository linkRepository;
  @Autowired private ClickEventRepository clickEventRepository;

  @Test
  void exportsUserLinksAndClicks() {
    UserEntity user = userRepository.save(new UserEntity("exp@example.com", "google", "g-exp"));
    LinkEntity link =
        linkRepository.save(
            new LinkEntity("https://example.com/exp", "exp0001", user.getId(), null));
    clickEventRepository.save(
        ClickEventEntity.builder()
            .linkId(link.getId())
            .deviceClass("mobile")
            .countryCode("KR")
            .bot(false)
            .build());

    UserDataExport data = exportService.export(user.getId());

    assertThat(data.user().email()).isEqualTo("exp@example.com");
    assertThat(data.links()).hasSize(1);
    assertThat(data.links().get(0).shortCode()).isEqualTo("exp0001");
    assertThat(data.clickEvents()).hasSize(1);
    assertThat(data.clickEvents().get(0).deviceClass()).isEqualTo("mobile");
    assertThat(data.clickEvents().get(0).countryCode()).isEqualTo("KR");
    assertThat(data.clickEvents().get(0).shortCode()).isEqualTo("exp0001");
  }

  @Test
  void emptyForUserWithoutData() {
    UserEntity user = userRepository.save(new UserEntity("e@example.com", "google", "g-e"));
    UserDataExport data = exportService.export(user.getId());
    assertThat(data.links()).isEmpty();
    assertThat(data.clickEvents()).isEmpty();
  }

  @Test
  void throwsForUnknownUser() {
    assertThatThrownBy(() -> exportService.export(9_999_999L))
        .isInstanceOf(UserNotFoundException.class);
  }
}
