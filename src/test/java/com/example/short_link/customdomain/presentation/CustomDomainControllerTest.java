package com.example.short_link.customdomain.presentation;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.customdomain.application.CustomDomainService;
import com.example.short_link.customdomain.application.CustomDomainService.DomainSummary;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomDomainControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;

  @MockitoBean private CustomDomainService service;

  @Test
  void anonymousListIs401() throws Exception {
    mvc.perform(get("/api/v1/custom-domains")).andExpect(status().isUnauthorized());
  }

  @Test
  void listReturnsDomains() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("c@x.com", "google", "g-cd"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(service.list(eq(user.getId()))).thenReturn(List.of());

    mvc.perform(get("/api/v1/custom-domains").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void registerReturns201() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("r@x.com", "google", "g-cdr"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(service.register(eq(user.getId()), eq("kurl.me")))
        .thenReturn(new DomainSummary(1L, "kurl.me", "tok", "host", false, null, null, null, null));

    mvc.perform(
            post("/api/v1/custom-domains")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"domain\":\"kurl.me\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.domain").value("kurl.me"));
  }

  @Test
  void verifyReturnsUpdatedSummary() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("v@x.com", "google", "g-cdv"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(service.verify(eq(user.getId()), eq(7L)))
        .thenReturn(new DomainSummary(7L, "kurl.me", "tok", "host", true, null, null, null, null));

    mvc.perform(post("/api/v1/custom-domains/7/verify").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verified").value(true));
  }

  @Test
  void deleteReturns204() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("d@x.com", "google", "g-cdd"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    doNothing().when(service).delete(anyLong(), eq(9L));

    mvc.perform(delete("/api/v1/custom-domains/9").header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
  }
}
