package com.example.short_link.admin.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.admin.application.BlockedDomainService;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
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
class BlockedDomainControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;
  @Autowired private BlockedDomainService service;

  @Test
  void anonymousIsRejected() throws Exception {
    mvc.perform(get("/api/v1/admin/blocked-domains")).andExpect(status().isUnauthorized());
  }

  @Test
  void plainUserCannotList() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-bdu"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(get("/api/v1/admin/blocked-domains").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanList() throws Exception {
    UserEntity admin = userRepository.save(new UserEntity("a@x.com", "google", "g-ad"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    String token = jwt.createAccessToken(admin.getId(), "ADMIN");

    mvc.perform(get("/api/v1/admin/blocked-domains").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void adminCanBlockDomain() throws Exception {
    UserEntity admin = userRepository.save(new UserEntity("a2@x.com", "google", "g-ad2"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    String token = jwt.createAccessToken(admin.getId(), "ADMIN");

    mvc.perform(
            post("/api/v1/admin/blocked-domains")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"domain\":\"evil.example.com\",\"reason\":\"phishing\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.domain").value("evil.example.com"))
        .andExpect(jsonPath("$.reason").value("phishing"));
  }

  @Test
  void adminCanUnblockExisting() throws Exception {
    UserEntity admin = userRepository.save(new UserEntity("a3@x.com", "google", "g-ad3"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    String token = jwt.createAccessToken(admin.getId(), "ADMIN");
    service.block("removeme.example.com", "test", admin.getId());

    mvc.perform(
            delete("/api/v1/admin/blocked-domains/removeme.example.com")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
  }

  @Test
  void unblockUnknownReturns404() throws Exception {
    UserEntity admin = userRepository.save(new UserEntity("a4@x.com", "google", "g-ad4"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    String token = jwt.createAccessToken(admin.getId(), "ADMIN");

    mvc.perform(
            delete("/api/v1/admin/blocked-domains/not-blocked.example.com")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  void plainUserCannotBlock() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("p@x.com", "google", "g-pu"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(
            post("/api/v1/admin/blocked-domains")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"domain\":\"evil.com\",\"reason\":\"x\"}"))
        .andExpect(status().isForbidden());
  }
}
