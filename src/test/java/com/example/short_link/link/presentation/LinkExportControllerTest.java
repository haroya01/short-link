package com.example.short_link.link.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
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
class LinkExportControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private LinkRepository linkRepository;
  @Autowired private ClickEventRepository clickRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private JwtTokenService jwt;

  @Test
  void exportsEventsAsCsvForOwner() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-csv1"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "csv001", owner.getId(), null));
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .userAgent("ua")
            .clientIp("1.2.3.4")
            .deviceClass("desktop")
            .countryCode("KR")
            .cityName("Seoul")
            .bot(false)
            .build());
    String token = jwt.createAccessToken(owner.getId(), "USER");

    var result =
        mvc.perform(
                get("/api/v1/links/csv001/events.csv").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
            .andExpect(
                header()
                    .string("Content-Disposition", "attachment; filename=\"csv001-events.csv\""))
            .andReturn();
    String body = result.getResponse().getContentAsString();
    assertThat(body)
        .startsWith(
            "clicked_at,country,region,city,device,os,browser,channel,referrer_host,language,is_bot,bot_name");
    assertThat(body).contains("KR");
    assertThat(body).contains("Seoul");
  }

  @Test
  void exportsStatsByDimension() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-csv2"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "csv002", owner.getId(), null));
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .userAgent("ua")
            .clientIp("1.2.3.4")
            .deviceClass("mobile")
            .bot(false)
            .build());
    String token = jwt.createAccessToken(owner.getId(), "USER");

    var result =
        mvc.perform(
                get("/api/v1/links/csv002/stats.csv")
                    .header("Authorization", "Bearer " + token)
                    .param("dimension", "device"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
            .andReturn();
    String body = result.getResponse().getContentAsString();
    assertThat(body).startsWith("device,count");
    assertThat(body).contains("mobile,1");
  }

  @Test
  void rejectsInvalidDimension() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-csv3"));
    linkRepository.save(new LinkEntity("https://example.com", "csv003", owner.getId(), null));
    String token = jwt.createAccessToken(owner.getId(), "USER");

    mvc.perform(
            get("/api/v1/links/csv003/stats.csv")
                .header("Authorization", "Bearer " + token)
                .param("dimension", "weird"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_EXPORT_DIMENSION"));
  }

  @Test
  void rejectsNonOwner() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-csv4"));
    UserEntity attacker = userRepository.save(new UserEntity("a@x.com", "google", "g-csv4a"));
    linkRepository.save(new LinkEntity("https://example.com", "csv004", owner.getId(), null));
    String token = jwt.createAccessToken(attacker.getId(), "USER");

    mvc.perform(get("/api/v1/links/csv004/events.csv").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsAnonymous() throws Exception {
    mvc.perform(get("/api/v1/links/csv005/events.csv")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/links/csv005/stats.csv")).andExpect(status().isUnauthorized());
  }
}
