package com.example.short_link.admin.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminWarnUserTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;
  @Autowired private LinkNotificationRepository linkNotifications;

  private String adminToken() {
    UserEntity admin = userRepository.save(new UserEntity("wa@x.com", "google", "g-warn-a"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    return jwt.createAccessToken(admin.getId(), "ADMIN");
  }

  @Test
  void adminWarningLandsInTargetUsersLinkNotificationInbox() throws Exception {
    UserEntity target = userRepository.save(new UserEntity("t@x.com", "google", "g-warn-t"));

    mvc.perform(
            post("/api/v1/admin/users/" + target.getId() + "/warning")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shortCode\":\"abc1234\",\"message\":\"불법 사이트 홍보 링크는 약관 위반입니다\"}"))
        .andExpect(status().isNoContent());

    List<LinkNotificationEntity> inbox =
        linkNotifications.findPageForRecipient(target.getId(), null, 10);
    assertThat(inbox).hasSize(1);
    assertThat(inbox.get(0).getType()).isEqualTo(LinkNotificationType.WARNING);
    assertThat(inbox.get(0).getShortCode()).isEqualTo("abc1234");
    assertThat(inbox.get(0).getBody()).contains("약관 위반");
  }

  @Test
  void warningUnknownUserReturns404() throws Exception {
    mvc.perform(
            post("/api/v1/admin/users/999999/warning")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"x\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void blankMessageIsRejected() throws Exception {
    UserEntity target = userRepository.save(new UserEntity("t2@x.com", "google", "g-warn-t2"));

    mvc.perform(
            post("/api/v1/admin/users/" + target.getId() + "/warning")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\" \"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void plainUserCannotWarn() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("p@x.com", "google", "g-warn-p"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(
            post("/api/v1/admin/users/" + user.getId() + "/warning")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"x\"}"))
        .andExpect(status().isForbidden());
  }
}
