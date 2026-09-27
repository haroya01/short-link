package com.example.short_link;

import static com.example.short_link.support.TestCacheCleaner.clear;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LinkOpenInBrowserE2ETest {

  private static final String LINE =
      "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like"
          + " Gecko) Mobile/15E148 Safari Line/13.16.0";
  private static final String KAKAOTALK =
      "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like"
          + " Gecko) Mobile/15E148 KAKAOTALK 10.4.0";
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
  void lineVisitorsAreSentToTheirBrowserOnceTheOwnerTurnsItOn() throws Exception {
    UserEntity owner = newUser();
    linkRepository.save(
        new LinkEntity(
            "https://shop.example.com/login?ref=kurl#top", "obr0001", owner.getId(), null));

    turnOn(owner, "obr0001");

    mvc.perform(get("/obr0001").header("User-Agent", LINE))
        .andExpect(status().isFound())
        .andExpect(
            header()
                .string(
                    "Location",
                    "https://shop.example.com/login?ref=kurl&openExternalBrowser=1#top"));
    mvc.perform(get("/obr0001").header("User-Agent", SAFARI))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://shop.example.com/login?ref=kurl#top"));
  }

  @Test
  void kakaoTalkVisitorsGetAHandoffPageWithAWayToStay() throws Exception {
    UserEntity owner = newUser();
    linkRepository.save(
        new LinkEntity("https://shop.example.com/pay", "obr0002", owner.getId(), null));

    turnOn(owner, "obr0002");

    mvc.perform(get("/obr0002").header("User-Agent", KAKAOTALK).header("Accept-Language", "ko-KR"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(
            content()
                .string(
                    containsString(
                        "kakaotalk://web/openExternal?url=https%3A%2F%2Fshop.example.com%2Fpay")))
        .andExpect(content().string(containsString("href=\"https://shop.example.com/pay\"")))
        .andExpect(content().string(containsString("여기서 계속 보기")));
  }

  @Test
  void linksStayPlainUntilTheOwnerOptsIn() throws Exception {
    UserEntity owner = newUser();
    linkRepository.save(
        new LinkEntity("https://plain.example.com/", "obr0003", owner.getId(), null));

    mvc.perform(get("/obr0003").header("User-Agent", LINE))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://plain.example.com/"));
    mvc.perform(
            get("/api/v1/links/obr0003/detail").header("Authorization", "Bearer " + token(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openInBrowser").value(false));
  }

  @Test
  void onlyTheOwnerCanChangeIt() throws Exception {
    UserEntity owner = newUser();
    UserEntity stranger = newUser();
    linkRepository.save(
        new LinkEntity("https://mine.example.com/", "obr0004", owner.getId(), null));

    mvc.perform(
            patch("/api/v1/links/obr0004/visit-options")
                .header("Authorization", "Bearer " + token(stranger))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openInBrowser\":true}"))
        .andExpect(status().isForbidden());
  }

  private void turnOn(UserEntity owner, String code) throws Exception {
    mvc.perform(
            patch("/api/v1/links/" + code + "/visit-options")
                .header("Authorization", "Bearer " + token(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openInBrowser\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openInBrowser").value(true));
    mvc.perform(
            get("/api/v1/links/" + code + "/detail")
                .header("Authorization", "Bearer " + token(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openInBrowser").value(true));
  }

  private UserEntity newUser() {
    String tag = UUID.randomUUID().toString();
    return userRepository.save(new UserEntity(tag + "@x.com", "google", tag));
  }

  private String token(UserEntity user) {
    return jwt.createAccessToken(user.getId(), "USER");
  }
}
