package com.example.short_link.link.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
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
class RedirectControllerCrawlerTest {

  @Autowired private MockMvc mvc;
  @Autowired private LinkRepository repository;

  /** 메신저/SNS 봇이 링크 미리보기를 위해 접근하면 302 가 아닌 OG-tagged HTML 을 반환해야 함. 각 토큰 마다 분기 cover. */
  @Test
  void twitterBotGetsOgPreviewHtml() throws Exception {
    repository.save(new LinkEntity("https://example.com", "twb0001"));
    mvc.perform(get("/twb0001").header("User-Agent", "Twitterbot/1.0"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
  }

  @Test
  void facebookExternalHitGetsOgPreviewHtml() throws Exception {
    repository.save(new LinkEntity("https://example.com", "fbb0001"));
    mvc.perform(get("/fbb0001").header("User-Agent", "facebookexternalhit/1.1"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
  }

  @Test
  void linkedInBotGetsOgPreviewHtml() throws Exception {
    repository.save(new LinkEntity("https://example.com", "lib0001"));
    mvc.perform(get("/lib0001").header("User-Agent", "LinkedInBot/1.0"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
  }

  @Test
  void discordBotGetsOgPreviewHtml() throws Exception {
    repository.save(new LinkEntity("https://example.com", "dcb0001"));
    mvc.perform(get("/dcb0001").header("User-Agent", "Discordbot/2.0"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
  }

  @Test
  void whatsAppGetsOgPreviewHtml() throws Exception {
    repository.save(new LinkEntity("https://example.com", "wab0001"));
    mvc.perform(get("/wab0001").header("User-Agent", "WhatsApp/2.0"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
  }

  @Test
  void telegramBotGetsOgPreviewHtml() throws Exception {
    repository.save(new LinkEntity("https://example.com", "tgb0001"));
    mvc.perform(get("/tgb0001").header("User-Agent", "TelegramBot (like TwitterBot)"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
  }

  @Test
  void googleBotGetsOgPreviewHtml() throws Exception {
    repository.save(new LinkEntity("https://example.com", "ggb0001"));
    mvc.perform(get("/ggb0001").header("User-Agent", "Googlebot/2.1"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
  }

  @Test
  void embedlyGetsOgPreviewHtml() throws Exception {
    repository.save(new LinkEntity("https://example.com", "emb0001"));
    mvc.perform(get("/emb0001").header("User-Agent", "Embedly/1.0"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
  }
}
