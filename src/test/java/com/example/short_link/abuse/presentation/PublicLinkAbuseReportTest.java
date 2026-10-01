package com.example.short_link.abuse.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PublicLinkAbuseReportTest {

  @Autowired private MockMvc mvc;
  @Autowired private LinkRepository links;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void someoneWithoutAnAccountCanReportALinkTheyReceived() throws Exception {
    LinkEntity link = links.save(new LinkEntity("https://phish.example/login", "anonrep1"));

    mvc.perform(
            post("/api/v1/public/abuse-reports/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"link\":\"https://kurl.me/anonrep1?src=sms\",\"reasonCode\":\"PHISHING\"}"))
        .andExpect(status().isAccepted());

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM abuse_report WHERE subject_type = 'LINK' AND subject_id = ?"
                    + " AND reporter_user_id IS NULL AND reason_code = 'PHISHING'",
                Long.class,
                link.getId()))
        .isEqualTo(1L);
  }

  @Test
  void aLinkThatIsNotOnKurlIsNotFound() throws Exception {
    mvc.perform(
            post("/api/v1/public/abuse-reports/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"link\":\"kurl.me/nosuch42\",\"reasonCode\":\"PHISHING\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SUBJECT_NOT_FOUND"));
  }
}
