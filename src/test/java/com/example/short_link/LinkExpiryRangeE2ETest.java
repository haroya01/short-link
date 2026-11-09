package com.example.short_link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
class LinkExpiryRangeE2ETest {

  private static final String PAST_2038 = "2040-01-01T00:00:00Z";

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository userRepository;
  @Autowired private JwtTokenService jwt;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private LinkRepository links;

  private final List<Long> createdUsers = new ArrayList<>();

  @AfterEach
  void deleteCommittedRows() {
    for (Long userId : createdUsers) {
      jdbc.update("DELETE FROM link WHERE user_id = ?", userId);
      jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }
  }

  @Test
  void customCodeWithAnExpiryTheColumnCannotHoldIsABadRequestNotATakenCode() throws Exception {
    String token = token();
    String code = code();

    create(
            token,
            "{\"url\":\"https://example.com\",\"customCode\":\""
                + code
                + "\",\"expiresAt\":\""
                + PAST_2038
                + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

    create(token, "{\"url\":\"https://example.com\",\"customCode\":\"" + code + "\"}")
        .andExpect(status().isCreated());
  }

  @Test
  void generatedCodeWithAnExpiryTheColumnCannotHoldIsABadRequestNotExhaustion() throws Exception {
    create(token(), "{\"url\":\"https://example.com\",\"expiresAt\":\"" + PAST_2038 + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
  }

  @Test
  void expiryBeforeTheEpochIsABadRequest() throws Exception {
    create(token(), "{\"url\":\"https://example.com\",\"expiresAt\":\"1969-12-31T00:00:00Z\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
  }

  @Test
  void lastStorableSecondIsAcceptedAndFractionsAreDropped() throws Exception {
    String token = token();
    String code = code();

    create(
            token,
            "{\"url\":\"https://example.com\",\"customCode\":\""
                + code
                + "\",\"expiresAt\":\"2038-01-01T00:00:00.900Z\"}")
        .andExpect(status().isCreated());

    assertThat(storedExpiry(code)).isEqualTo(Instant.parse("2038-01-01T00:00:00Z"));
    create(token, "{\"url\":\"https://example.com\",\"expiresAt\":\"2038-01-01T00:00:01Z\"}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateWithAnExpiryTheColumnCannotHoldIsABadRequestAndKeepsTheOldExpiry() throws Exception {
    String token = token();
    String code = code();
    create(
            token,
            "{\"url\":\"https://example.com\",\"customCode\":\""
                + code
                + "\",\"expiresAt\":\"2030-01-01T00:00:00Z\"}")
        .andExpect(status().isCreated());

    mvc.perform(
            patch("/api/v1/links/" + code)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expiresAt\":\"" + PAST_2038 + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

    assertThat(storedExpiry(code)).isEqualTo(Instant.parse("2030-01-01T00:00:00Z"));
  }

  @Test
  void csvRowWithAnExpiryTheColumnCannotHoldIsNotReportedAsATakenCode() throws Exception {
    String code = code();
    MockMultipartFile csv =
        new MockMultipartFile(
            "file",
            "links.csv",
            "text/csv",
            ("url,custom_code,expires_at\nhttps://example.com," + code + "," + PAST_2038 + "\n")
                .getBytes(StandardCharsets.UTF_8));

    String body =
        mvc.perform(
                multipart("/api/v1/links/bulk")
                    .file(csv)
                    .header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Bulk-Failed", "1"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    assertThat(body).doesNotContain("DUPLICATE_SHORT_CODE").contains("expiresAt out of range");
  }

  private ResultActions create(String token, String json) throws Exception {
    return mvc.perform(
        post("/api/v1/links")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private Instant storedExpiry(String code) {
    return links.findByShortCode(new ShortCode(code)).orElseThrow().getExpiresAt();
  }

  private String token() {
    String tag = UUID.randomUUID().toString();
    UserEntity user = userRepository.save(new UserEntity(tag + "@x.com", "google", tag));
    createdUsers.add(user.getId());
    return jwt.createAccessToken(user.getId(), "USER");
  }

  private static String code() {
    return "e2e" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  }
}
