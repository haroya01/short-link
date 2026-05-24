package com.example.short_link;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.domain.CustomDomainEntity;
import com.example.short_link.link.domain.repository.CustomDomainRepository;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.lang.reflect.Field;
import java.time.Instant;
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
class CustomDomainRoutingE2ETest {

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository userRepository;
  @Autowired private CustomDomainRepository customDomainRepository;
  @Autowired private JwtTokenService jwt;

  @Test
  void customDomainHost_serves_ownerLink_andBlocksCrossUserCodes() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("cd-owner@x.com", "google", "g-cd-own"));
    UserEntity other = userRepository.save(new UserEntity("cd-other@x.com", "google", "g-cd-oth"));
    String ownerToken = jwt.createAccessToken(owner.getId());
    String otherToken = jwt.createAccessToken(other.getId());

    mvc.perform(
            post("/api/v1/links")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://owner.com\",\"customCode\":\"cd0owner\"}"))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/v1/links")
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://other.com\",\"customCode\":\"cd0other\"}"))
        .andExpect(status().isCreated());

    String domain = "go.brand-a.example.com";
    CustomDomainEntity verified =
        new CustomDomainEntity(owner.getId(), domain, "kurl-verify=ignored");
    writeField(verified, "createdAt", Instant.now());
    verified.markVerified();
    customDomainRepository.saveAndFlush(verified);

    mvc.perform(get("/cd0owner").header("Host", domain))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://owner.com"));

    mvc.perform(get("/cd0other").header("Host", domain)).andExpect(status().isNotFound());

    mvc.perform(get("/cd0owner").header("Host", "kurl.me"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://owner.com"));
    mvc.perform(get("/cd0other").header("Host", "kurl.me"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://other.com"));
  }

  @Test
  void unverifiedCustomDomain_doesNotGateRouting() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("cd-pend@x.com", "google", "g-cd-pend"));
    UserEntity other = userRepository.save(new UserEntity("cd-pend2@x.com", "google", "g-cd-pen2"));
    String ownerToken = jwt.createAccessToken(owner.getId());
    String otherToken = jwt.createAccessToken(other.getId());

    mvc.perform(
            post("/api/v1/links")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://owner-pend.com\",\"customCode\":\"cd0pend1\"}"))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/v1/links")
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://other-pend.com\",\"customCode\":\"cd0pend2\"}"))
        .andExpect(status().isCreated());

    String domain = "go.brand-pending.example.com";
    CustomDomainEntity pending =
        new CustomDomainEntity(owner.getId(), domain, "kurl-verify=pending");
    writeField(pending, "createdAt", Instant.now());
    customDomainRepository.saveAndFlush(pending);

    mvc.perform(get("/cd0pend1").header("Host", domain))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://owner-pend.com"));
    mvc.perform(get("/cd0pend2").header("Host", domain))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://other-pend.com"));
  }

  private static void writeField(Object target, String name, Object value) {
    try {
      Class<?> c = target.getClass();
      while (c != null) {
        try {
          Field f = c.getDeclaredField(name);
          f.setAccessible(true);
          f.set(target, value);
          return;
        } catch (NoSuchFieldException ignored) {
          c = c.getSuperclass();
        }
      }
      throw new NoSuchFieldException(name);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }
}
