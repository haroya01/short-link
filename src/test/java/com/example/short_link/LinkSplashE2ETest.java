package com.example.short_link;

import static com.example.short_link.support.TestCacheCleaner.clear;
import static org.assertj.core.api.Assertions.assertThat;
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
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.visit.domain.repository.LinkVisitOptionRepository;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
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
class LinkSplashE2ETest {

  private static final String SAFARI =
      "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like"
          + " Gecko) Version/17.0 Mobile/15E148 Safari/604.1";

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository userRepository;
  @Autowired private LinkRepository linkRepository;
  @Autowired private LinkVisitOptionRepository visitOptions;
  @Autowired private JwtTokenService jwt;
  @Autowired private CacheManager cacheManager;

  @BeforeEach
  void clearLinkCache() {
    clear(cacheManager, "link");
  }

  @Test
  void visitorsSeeTheOwnersMessageAndButtonBeforeMovingOn() throws Exception {
    UserEntity owner = newUser();
    linkRepository.save(
        new LinkEntity("https://shop.example.com/sale", "spl0001", owner.getId(), null));
    long ctaId = createCta(owner, "앱 설치하기", "https://apps.example.com/install");

    setSplash(
            owner,
            "spl0001",
            "{\"enabled\":true,\"message\":\"쿠폰 코드 SPRING20 <b>\",\"seconds\":2,\"ctaId\":"
                + ctaId
                + "}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.splash.enabled").value(true))
        .andExpect(jsonPath("$.splash.seconds").value(2))
        .andExpect(jsonPath("$.splash.ctaId").value(ctaId));

    mvc.perform(get("/spl0001").header("User-Agent", SAFARI).header("Accept-Language", "ko-KR"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("쿠폰 코드 SPRING20 &lt;b&gt;")))
        .andExpect(content().string(containsString(">앱 설치하기</a>")))
        .andExpect(content().string(containsString("data-u=\"https://shop.example.com/sale\"")))
        .andExpect(content().string(containsString("<span id=\"n\">2</span>초 후 자동으로 이동해요")))
        .andExpect(content().string(containsString("바로 가기")));
    mvc.perform(get("/api/v1/links/spl0001/detail").header("Authorization", bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.splash.message").value("쿠폰 코드 SPRING20 <b>"))
        .andExpect(jsonPath("$.splash.ctaId").value(ctaId));
    assertThat(visitOptions.findShortCodesUsingSplashCta(ctaId))
        .containsExactly(new ShortCode("spl0001"));
  }

  @Test
  void aSwitchedOffSplashKeepsTheDraftButRedirectsStraightAway() throws Exception {
    UserEntity owner = newUser();
    linkRepository.save(
        new LinkEntity("https://plain.example.com/", "spl0002", owner.getId(), null));

    setSplash(owner, "spl0002", "{\"enabled\":false,\"message\":\"나중에 쓸 안내\",\"seconds\":3}")
        .andExpect(status().isOk());

    mvc.perform(get("/spl0002").header("User-Agent", SAFARI))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://plain.example.com/"));
    mvc.perform(get("/api/v1/links/spl0002/detail").header("Authorization", bearer(owner)))
        .andExpect(jsonPath("$.splash.enabled").value(false))
        .andExpect(jsonPath("$.splash.message").value("나중에 쓸 안내"));
  }

  @Test
  void linkPreviewBotsStillGetTheCardInsteadOfTheSplash() throws Exception {
    UserEntity owner = newUser();
    linkRepository.save(
        new LinkEntity("https://shop.example.com/card", "spl0003", owner.getId(), null));
    setSplash(owner, "spl0003", "{\"enabled\":true,\"message\":\"잠깐!\",\"seconds\":3}")
        .andExpect(status().isOk());

    mvc.perform(get("/spl0003").header("User-Agent", "facebookexternalhit/1.1"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("og:url")))
        .andExpect(content().string(not(containsString("잠깐!"))));
  }

  @Test
  void aSplashNeedsAMessageAndTheOwnersOwnButton() throws Exception {
    UserEntity owner = newUser();
    UserEntity stranger = newUser();
    linkRepository.save(
        new LinkEntity("https://shop.example.com/x", "spl0004", owner.getId(), null));
    long strangersCta = createCta(stranger, "남의 버튼", "https://elsewhere.example.com/");

    setSplash(owner, "spl0004", "{\"enabled\":true,\"message\":\"   \"}")
        .andExpect(status().isBadRequest());
    setSplash(owner, "spl0004", "{\"enabled\":true,\"message\":\"안내\",\"seconds\":9}")
        .andExpect(status().isBadRequest());
    setSplash(
            owner,
            "spl0004",
            "{\"enabled\":true,\"message\":\"안내\",\"ctaId\":" + strangersCta + "}")
        .andExpect(status().isBadRequest());
  }

  private org.springframework.test.web.servlet.ResultActions setSplash(
      UserEntity owner, String code, String splashJson) throws Exception {
    return mvc.perform(
        patch("/api/v1/links/" + code + "/visit-options")
            .header("Authorization", bearer(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"splash\":" + splashJson + "}"));
  }

  private long createCta(UserEntity owner, String label, String url) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/ctas")
                    .header("Authorization", bearer(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"label\":\"" + label + "\",\"url\":\"" + url + "\"}"))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return ((Number) JsonPath.read(body, "$.id")).longValue();
  }

  private UserEntity newUser() {
    String tag = UUID.randomUUID().toString();
    return userRepository.save(new UserEntity(tag + "@x.com", "google", tag));
  }

  private String bearer(UserEntity user) {
    return "Bearer " + jwt.createAccessToken(user.getId(), "USER");
  }
}
