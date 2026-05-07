package com.example.short_link.link.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.domain.ClickEventEntity;
import com.example.short_link.link.domain.ClickEventRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.user.application.JwtTokenService;
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
class LinkStatsControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private LinkRepository linkRepository;
  @Autowired private ClickEventRepository clickRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private JwtTokenService jwt;

  private static ClickEventEntity humanClick(Long linkId, String device) {
    return ClickEventEntity.builder()
        .linkId(linkId)
        .userAgent("ua")
        .clientIp("1.2.3.4")
        .deviceClass(device)
        .bot(false)
        .build();
  }

  @Test
  void returnsTotalAndHumanClicksForOwner() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-s1"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "stats01", owner.getId(), null));
    for (int i = 0; i < 3; i++) {
      clickRepository.save(humanClick(link.getId(), "desktop"));
    }
    String token = jwt.createAccessToken(owner.getId());

    mvc.perform(get("/api/v1/links/stats01/stats").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.shortCode").value("stats01"))
        .andExpect(jsonPath("$.totalClicks").value(3))
        .andExpect(jsonPath("$.humanClicks").value(3))
        .andExpect(jsonPath("$.botClicks").value(0));
  }

  @Test
  void separatesBotAndHumanClicks() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-bot"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "statbot", owner.getId(), null));
    clickRepository.save(humanClick(link.getId(), "desktop"));
    clickRepository.save(humanClick(link.getId(), "mobile"));
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.getId())
            .userAgent("Googlebot")
            .clientIp("1.2.3.4")
            .deviceClass("bot")
            .bot(true)
            .build());
    String token = jwt.createAccessToken(owner.getId());

    mvc.perform(get("/api/v1/links/statbot/stats").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalClicks").value(3))
        .andExpect(jsonPath("$.humanClicks").value(2))
        .andExpect(jsonPath("$.botClicks").value(1))
        .andExpect(jsonPath("$.deviceClicks[?(@.device == 'desktop')].count").value(1))
        .andExpect(jsonPath("$.deviceClicks[?(@.device == 'mobile')].count").value(1));
  }

  @Test
  void aggregatesByDevice() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-s2"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "stats02", owner.getId(), null));
    clickRepository.save(humanClick(link.getId(), "mobile"));
    clickRepository.save(humanClick(link.getId(), "tablet"));
    clickRepository.save(humanClick(link.getId(), "desktop"));
    String token = jwt.createAccessToken(owner.getId());

    mvc.perform(get("/api/v1/links/stats02/stats").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deviceClicks[?(@.device == 'mobile')].count").value(1))
        .andExpect(jsonPath("$.deviceClicks[?(@.device == 'tablet')].count").value(1))
        .andExpect(jsonPath("$.deviceClicks[?(@.device == 'desktop')].count").value(1));
  }

  @Test
  void aggregatesByOsAndBrowser() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-osb"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "statosb", owner.getId(), null));
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.getId())
            .userAgent("ua")
            .clientIp("1.2.3.4")
            .deviceClass("mobile")
            .osName("iOS")
            .browserName("Safari")
            .bot(false)
            .build());
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.getId())
            .userAgent("ua")
            .clientIp("1.2.3.5")
            .deviceClass("desktop")
            .osName("Windows")
            .browserName("Chrome")
            .bot(false)
            .build());
    String token = jwt.createAccessToken(owner.getId());

    mvc.perform(get("/api/v1/links/statosb/stats").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.osClicks[?(@.os == 'iOS')].count").value(1))
        .andExpect(jsonPath("$.osClicks[?(@.os == 'Windows')].count").value(1))
        .andExpect(jsonPath("$.browserClicks[?(@.browser == 'Safari')].count").value(1))
        .andExpect(jsonPath("$.browserClicks[?(@.browser == 'Chrome')].count").value(1));
  }

  @Test
  void aggregatesByReferrerAndChannel() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-s3"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "stats03", owner.getId(), null));
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.getId())
            .referrer("https://www.instagram.com/p/abc")
            .userAgent("ua")
            .clientIp("1.1.1.1")
            .deviceClass("mobile")
            .bot(false)
            .build());
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.getId())
            .referrer("https://www.instagram.com/p/abc")
            .userAgent("ua")
            .clientIp("1.1.1.2")
            .deviceClass("mobile")
            .bot(false)
            .build());
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.getId())
            .referrer("https://www.youtube.com/watch")
            .userAgent("ua")
            .clientIp("1.1.1.3")
            .deviceClass("desktop")
            .bot(false)
            .build());
    clickRepository.save(humanClick(link.getId(), "desktop"));
    String token = jwt.createAccessToken(owner.getId());

    mvc.perform(get("/api/v1/links/stats03/stats").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.referrerClicks[?(@.referrer == 'https://www.instagram.com/p/abc')].count")
                .value(2))
        .andExpect(
            jsonPath("$.referrerClicks[?(@.referrer == 'https://www.youtube.com/watch')].count")
                .value(1))
        .andExpect(jsonPath("$.channelClicks[?(@.channel == 'social')].count").value(3))
        .andExpect(jsonPath("$.channelClicks[?(@.channel == 'direct')].count").value(1))
        .andExpect(jsonPath("$.channelClicks[0].channel").value("social"))
        .andExpect(jsonPath("$.timezone").value("Asia/Seoul"));
  }

  @Test
  void aggregatesByUtmCampaign() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-utm"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "statutm", owner.getId(), null));
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.getId())
            .userAgent("ua")
            .clientIp("1.1.1.1")
            .utmCampaign("spring_sale")
            .deviceClass("desktop")
            .bot(false)
            .build());
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.getId())
            .userAgent("ua")
            .clientIp("1.1.1.2")
            .utmCampaign("spring_sale")
            .deviceClass("mobile")
            .bot(false)
            .build());
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.getId())
            .userAgent("ua")
            .clientIp("1.1.1.3")
            .utmCampaign("launch")
            .deviceClass("desktop")
            .bot(false)
            .build());
    String token = jwt.createAccessToken(owner.getId());

    mvc.perform(get("/api/v1/links/statutm/stats").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.utmCampaignClicks[?(@.campaign == 'spring_sale')].count").value(2))
        .andExpect(jsonPath("$.utmCampaignClicks[?(@.campaign == 'launch')].count").value(1));
  }

  @Test
  void aggregatesByCountry() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-cty"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "statcty", owner.getId(), null));
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.getId())
            .userAgent("ua")
            .clientIp("1.1.1.1")
            .deviceClass("desktop")
            .countryCode("KR")
            .bot(false)
            .build());
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.getId())
            .userAgent("ua")
            .clientIp("1.1.1.2")
            .deviceClass("mobile")
            .countryCode("KR")
            .bot(false)
            .build());
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.getId())
            .userAgent("ua")
            .clientIp("1.1.1.3")
            .deviceClass("desktop")
            .countryCode("US")
            .bot(false)
            .build());
    String token = jwt.createAccessToken(owner.getId());

    mvc.perform(get("/api/v1/links/statcty/stats").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.countryClicks[?(@.country == 'KR')].count").value(2))
        .andExpect(jsonPath("$.countryClicks[?(@.country == 'US')].count").value(1))
        .andExpect(jsonPath("$.countryClicks[0].country").value("KR"));
  }

  @Test
  void aggregatesAdvancedDimensions() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-adv"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "statadv", owner.getId(), null));
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.getId())
            .userAgent("ua")
            .clientIp("1.1.1.1")
            .deviceClass("desktop")
            .countryCode("KR")
            .regionName("Seoul")
            .cityName("Seoul")
            .language("ko-KR")
            .referrerHost("www.instagram.com")
            .referrer("https://www.instagram.com/p/abc")
            .visitorHash("a".repeat(64))
            .bot(false)
            .build());
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.getId())
            .userAgent("ua")
            .clientIp("1.1.1.2")
            .deviceClass("mobile")
            .countryCode("KR")
            .regionName("Busan")
            .cityName("Busan")
            .language("en-US")
            .referrerHost("www.instagram.com")
            .referrer("https://www.instagram.com/p/xyz")
            .visitorHash("b".repeat(64))
            .bot(false)
            .build());
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.getId())
            .userAgent("Googlebot/2.1")
            .clientIp("1.1.1.3")
            .deviceClass("bot")
            .botName("Googlebot")
            .visitorHash("c".repeat(64))
            .bot(true)
            .build());
    String token = jwt.createAccessToken(owner.getId());

    mvc.perform(get("/api/v1/links/statadv/stats").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.uniqueClicks").value(2))
        .andExpect(jsonPath("$.regionClicks[?(@.region == 'Seoul')].count").value(1))
        .andExpect(jsonPath("$.regionClicks[?(@.region == 'Busan')].count").value(1))
        .andExpect(jsonPath("$.cityClicks[?(@.city == 'Seoul')].count").value(1))
        .andExpect(jsonPath("$.languageClicks[?(@.language == 'ko-KR')].count").value(1))
        .andExpect(jsonPath("$.languageClicks[?(@.language == 'en-US')].count").value(1))
        .andExpect(
            jsonPath("$.referrerHostClicks[?(@.host == 'www.instagram.com')].count").value(2))
        .andExpect(jsonPath("$.botClicks2[?(@.bot == 'Googlebot')].count").value(1))
        .andExpect(jsonPath("$.heatmap").isArray())
        .andExpect(jsonPath("$.firstClickAt").isString())
        .andExpect(jsonPath("$.timeToFirstClickMinutes").isNumber())
        .andExpect(jsonPath("$.velocity").isMap());
  }

  @Test
  void rejectsStatsForNotOwner() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-s4"));
    UserEntity attacker = userRepository.save(new UserEntity("a@x.com", "google", "g-s4a"));
    linkRepository.save(new LinkEntity("https://example.com", "stats04", owner.getId(), null));
    String attackerToken = jwt.createAccessToken(attacker.getId());

    mvc.perform(
            get("/api/v1/links/stats04/stats").header("Authorization", "Bearer " + attackerToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("LINK_NOT_OWNED"));
  }

  @Test
  void rejectsStatsForUnknownCode() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-s5"));
    String token = jwt.createAccessToken(user.getId());

    mvc.perform(get("/api/v1/links/missing/stats").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  void rejectsStatsWhenAnonymous() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-s6"));
    linkRepository.save(new LinkEntity("https://example.com", "stats06", owner.getId(), null));

    mvc.perform(get("/api/v1/links/stats06/stats")).andExpect(status().isUnauthorized());
  }

  @Test
  void includesReturnRateAndLifecycleAndInsights() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-rli"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "statrli", owner.getId(), null));
    for (int i = 0; i < 3; i++) {
      clickRepository.save(
          ClickEventEntity.builder()
              .linkId(link.getId())
              .userAgent("ua")
              .clientIp("1.1.1." + i)
              .deviceClass("desktop")
              .visitorHash("returner".repeat(8).substring(0, 64))
              .bot(false)
              .build());
    }
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.getId())
            .userAgent("ua")
            .clientIp("2.2.2.2")
            .deviceClass("mobile")
            .visitorHash("newcomer".repeat(8).substring(0, 64))
            .bot(false)
            .build());
    String token = jwt.createAccessToken(owner.getId());

    mvc.perform(get("/api/v1/links/statrli/stats").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.returnRate.newVisitors").value(1))
        .andExpect(jsonPath("$.returnRate.returningVisitors").value(1))
        .andExpect(jsonPath("$.lifecycle.dayClicks").isArray())
        .andExpect(jsonPath("$.insights").isArray());
  }
}
