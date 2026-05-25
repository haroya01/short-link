package com.example.short_link.link.redirect.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
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
class RedirectControllerEdgeTest {

  @Autowired private MockMvc mvc;
  @Autowired private LinkRepository repository;
  @Autowired private UserRepository userRepository;

  @Test
  void redirectAcceptsAndroidUserAgent() throws Exception {
    repository.save(new LinkEntity("https://example.com", "edge001"));
    mvc.perform(
            get("/edge001")
                .header(
                    "User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://example.com"));
  }

  @Test
  void redirectAcceptsIosUserAgent() throws Exception {
    repository.save(new LinkEntity("https://example.com", "edge002"));
    mvc.perform(
            get("/edge002")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605"))
        .andExpect(status().isFound());
  }

  @Test
  void redirectAcceptsWindowsUserAgent() throws Exception {
    repository.save(new LinkEntity("https://example.com", "edge003"));
    mvc.perform(
            get("/edge003")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/123"))
        .andExpect(status().isFound());
  }

  @Test
  void redirectAcceptsLinuxUserAgent() throws Exception {
    repository.save(new LinkEntity("https://example.com", "edge004"));
    mvc.perform(get("/edge004").header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) Chrome/123"))
        .andExpect(status().isFound());
  }

  @Test
  void redirectAcceptsMacUserAgent() throws Exception {
    repository.save(new LinkEntity("https://example.com", "edge005"));
    mvc.perform(
            get("/edge005")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) Chrome/123"))
        .andExpect(status().isFound());
  }

  @Test
  void redirectWithSrcAndReferrer() throws Exception {
    repository.save(new LinkEntity("https://example.com", "edge006"));
    mvc.perform(
            get("/edge006")
                .param("src", "twitter")
                .header("Referer", "https://t.co/x")
                .header("User-Agent", "Mozilla/5.0"))
        .andExpect(status().isFound());
  }

  @Test
  void redirectUsesXForwardedForFirstHopAsClientIp() throws Exception {
    repository.save(new LinkEntity("https://example.com", "edge007"));
    mvc.perform(
            get("/edge007")
                .header("X-Forwarded-For", "203.0.113.5, 10.0.0.1, 192.168.1.1")
                .header("User-Agent", "Mozilla/5.0"))
        .andExpect(status().isFound());
  }

  @Test
  void redirectOnDeletedOwnerLinkStillWorksIfOwnerNotDeleted() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("o@x.com", "google", "g-eo"));
    repository.save(new LinkEntity("https://example.com", "edge008", user.getId(), null));
    mvc.perform(get("/edge008")).andExpect(status().isFound());
  }
}
