package com.example.short_link.admin.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.notification.domain.LinkNotificationEntity;
import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.notification.domain.repository.LinkNotificationRepository;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 도메인 차단 → 소유자 자동 경고 fan-out: 계정당 1건·수신자 locale 카피·익명/무관 도메인 제외. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BlockedDomainFanoutTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;
  @Autowired private LinkRepository linkRepository;
  @Autowired private LinkNotificationRepository linkNotifications;

  private String adminToken() {
    UserEntity admin = userRepository.save(new UserEntity("fa@x.com", "google", "g-fan-a"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    return jwt.createAccessToken(admin.getId(), "ADMIN");
  }

  @Test
  void blockingDomainWarnsEveryOwnerOnceInTheirLocale() throws Exception {
    UserEntity english = userRepository.save(new UserEntity("en@x.com", "google", "g-fan-en"));
    english.updateLocale("en");
    userRepository.save(english);
    UserEntity korean = userRepository.save(new UserEntity("ko@x.com", "google", "g-fan-ko"));
    UserEntity bystander = userRepository.save(new UserEntity("by@x.com", "google", "g-fan-by"));

    linkRepository.save(
        new LinkEntity("https://spam.example.com/a", "fan1111", english.getId(), null));
    linkRepository.save(
        new LinkEntity("https://go.spam.example.com/b", "fan2222", english.getId(), null));
    linkRepository.save(
        new LinkEntity("https://spam.example.com/c", "fan3333", korean.getId(), null));
    linkRepository.save(new LinkEntity("https://spam.example.com/anon", "fan4444"));
    linkRepository.save(
        new LinkEntity("https://notspam.example.com/d", "fan5555", bystander.getId(), null));

    mvc.perform(
            post("/api/v1/admin/blocked-domains")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"domain\":\"spam.example.com\",\"reason\":\"spam\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.warnedOwners").value(2));

    List<LinkNotificationEntity> enInbox =
        linkNotifications.findPageForRecipient(english.getId(), null, 10);
    assertThat(enInbox).hasSize(1);
    assertThat(enInbox.get(0).getType()).isEqualTo(LinkNotificationType.WARNING);
    assertThat(enInbox.get(0).getBody()).contains("spam.example.com").contains("blocked");
    assertThat(enInbox.get(0).getShortCode()).isNull();

    List<LinkNotificationEntity> koInbox =
        linkNotifications.findPageForRecipient(korean.getId(), null, 10);
    assertThat(koInbox).hasSize(1);
    assertThat(koInbox.get(0).getBody()).contains("spam.example.com").contains("차단");
    assertThat(koInbox.get(0).getShortCode()).isEqualTo("fan3333");

    assertThat(linkNotifications.findPageForRecipient(bystander.getId(), null, 10)).isEmpty();
  }

  @Test
  void blockingDomainWithNoOwnedLinksWarnsNobody() throws Exception {
    mvc.perform(
            post("/api/v1/admin/blocked-domains")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"domain\":\"lonely.example.com\",\"reason\":\"spam\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.warnedOwners").value(0));
  }
}
