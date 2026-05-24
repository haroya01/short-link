package com.example.short_link.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SwaggerApiDocsTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;

  private String adminBearer() {
    UserEntity admin =
        userRepository.save(new UserEntity("swagger-admin@example.com", "google", "g-swagger"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    return "Bearer " + jwt.createAccessToken(admin.getId(), "ADMIN");
  }

  @Test
  void apiDocsAvailable() throws Exception {
    mvc.perform(get("/v3/api-docs").header("Authorization", adminBearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openapi").exists())
        .andExpect(jsonPath("$.paths").exists());
  }

  @Test
  void swaggerUiAvailable() throws Exception {
    mvc.perform(get("/swagger-ui/index.html").header("Authorization", adminBearer()))
        .andExpect(status().isOk());
  }
}
