package com.example.short_link;

import static com.example.short_link.support.TestCacheCleaner.clear;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LinkOpensAtE2ETest {

  private static final String SAFARI =
      "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like"
          + " Gecko) Version/17.0 Mobile/15E148 Safari/604.1";

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository userRepository;
  @Autowired private LinkRepository linkRepository;
  @Autowired private JwtTokenService jwt;
  @Autowired private CacheManager cacheManager;

  @BeforeEach
  void clearLinkCache() {
    clear(cacheManager, "link");
  }

  @Test
  void beforeItOpensVisitorsSeeWhenAndBotsGetACardWithoutTheDestination() throws Exception {
    UserEntity owner = newUser();
    linkRepository.save(
        new LinkEntity("https://secret-launch.example.com/drop", "opn0001", owner.getId(), null));
    Instant opensAt = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    setOptions(owner, "opn0001", "{\"opensAt\":\"" + opensAt + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.opensAt").value(opensAt.toString()));

    mvc.perform(get("/opn0001").header("User-Agent", SAFARI).header("Accept-Language", "ko-KR"))
        .andExpect(status().isForbidden())
        .andExpect(content().string(containsString("아직 열리지 않은 링크예요")))
        .andExpect(content().string(containsString("datetime=\"" + opensAt + "\"")))
        .andExpect(content().string(not(containsString("secret-launch.example.com"))));
    mvc.perform(get("/opn0001").header("User-Agent", "facebookexternalhit/1.1"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("og:url")))
        .andExpect(content().string(not(containsString("secret-launch.example.com"))));
    mvc.perform(get("/api/v1/links/opn0001/detail").header("Authorization", bearer(owner)))
        .andExpect(jsonPath("$.opensAt").value(opensAt.toString()));
  }

  @Test
  void aProtectedLinkDoesNotAskForThePasswordBeforeItOpens() throws Exception {
    UserEntity owner = newUser();
    linkRepository.save(new LinkEntity("https://x.example.com/", "opn0002", owner.getId(), null));
    mvc.perform(
            patch("/api/v1/links/opn0002/protection")
                .header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"open-sesame\"}"))
        .andExpect(status().isOk());
    setOptions(owner, "opn0002", "{\"opensAt\":\"" + Instant.now().plus(1, ChronoUnit.DAYS) + "\"}")
        .andExpect(status().isOk());

    mvc.perform(get("/opn0002").header("User-Agent", SAFARI).header("Accept-Language", "en"))
        .andExpect(status().isForbidden())
        .andExpect(content().string(containsString("This link isn’t open yet")))
        .andExpect(content().string(not(containsString("name=\"password\""))));
    mvc.perform(
            post("/opn0002")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("password=open-sesame"))
        .andExpect(status().isForbidden())
        .andExpect(content().string(not(containsString("https://x.example.com/"))));
  }

  @Test
  void aPastOpeningTimeOrAClearedOneRedirectsNormally() throws Exception {
    UserEntity owner = newUser();
    linkRepository.save(
        new LinkEntity("https://past.example.com/", "opn0003", owner.getId(), null));
    linkRepository.save(
        new LinkEntity("https://cleared.example.com/", "opn0004", owner.getId(), null));

    setOptions(
            owner, "opn0003", "{\"opensAt\":\"" + Instant.now().minus(1, ChronoUnit.HOURS) + "\"}")
        .andExpect(status().isOk());
    setOptions(owner, "opn0004", "{\"opensAt\":\"" + Instant.now().plus(1, ChronoUnit.DAYS) + "\"}")
        .andExpect(status().isOk());
    setOptions(owner, "opn0004", "{\"clearOpensAt\":true}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.opensAt").doesNotExist());

    mvc.perform(get("/opn0003").header("User-Agent", SAFARI))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://past.example.com/"));
    mvc.perform(get("/opn0004").header("User-Agent", SAFARI))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://cleared.example.com/"));
  }

  @Test
  void aLinkCannotOpenAfterItExpires() throws Exception {
    UserEntity owner = newUser();
    Instant expiresAt = Instant.now().plus(1, ChronoUnit.DAYS);
    linkRepository.save(
        new LinkEntity("https://x.example.com/", "opn0005", owner.getId(), expiresAt));

    setOptions(owner, "opn0005", "{\"opensAt\":\"" + expiresAt.plus(1, ChronoUnit.HOURS) + "\"}")
        .andExpect(status().isBadRequest());
    setOptions(
            owner,
            "opn0005",
            "{\"opensAt\":\""
                + Instant.now().plus(1, ChronoUnit.HOURS)
                + "\",\"clearOpensAt\":true}")
        .andExpect(status().isBadRequest());
  }

  private ResultActions setOptions(UserEntity owner, String code, String json) throws Exception {
    return mvc.perform(
        patch("/api/v1/links/" + code + "/visit-options")
            .header("Authorization", bearer(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private UserEntity newUser() {
    String tag = UUID.randomUUID().toString();
    return userRepository.save(new UserEntity(tag + "@x.com", "google", tag));
  }

  private String bearer(UserEntity user) {
    return "Bearer " + jwt.createAccessToken(user.getId(), "USER");
  }
}
