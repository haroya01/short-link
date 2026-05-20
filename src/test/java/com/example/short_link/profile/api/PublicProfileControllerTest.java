package com.example.short_link.profile.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
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
class PublicProfileControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository userRepository;

  @Test
  void anonymousCanFetchProfileByUsername() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-pub"));
    user.claimUsername("publicme");
    userRepository.save(user);

    mvc.perform(get("/api/v1/public/profiles/publicme"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("publicme"));
  }

  @Test
  void notFoundProfileReturns404PlainText() throws Exception {
    mvc.perform(get("/api/v1/public/profiles/no-such-user")).andExpect(status().isNotFound());
  }

  @Test
  void listEndpointReturnsHandlesAndTotal() throws Exception {
    UserEntity u1 = userRepository.save(new UserEntity("a@x.com", "google", "g-l1"));
    UserEntity u2 = userRepository.save(new UserEntity("b@x.com", "google", "g-l2"));
    u1.claimUsername("lista");
    u2.claimUsername("listb");
    userRepository.save(u1);
    userRepository.save(u2);

    mvc.perform(get("/api/v1/public/profiles"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.total").isNumber());
  }

  @Test
  void listClampsNegativePageToZero() throws Exception {
    mvc.perform(get("/api/v1/public/profiles").param("page", "-5").param("size", "10"))
        .andExpect(status().isOk());
  }

  @Test
  void listClampsOversizeToMax() throws Exception {
    mvc.perform(get("/api/v1/public/profiles").param("size", "10000")).andExpect(status().isOk());
  }

  @Test
  void listClampsUnderscoredZeroSizeToOne() throws Exception {
    mvc.perform(get("/api/v1/public/profiles").param("size", "0")).andExpect(status().isOk());
  }
}
