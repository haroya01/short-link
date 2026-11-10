package com.example.short_link;

import static com.example.short_link.support.TestCacheCleaner.clear;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Tag("integration")
class MalformedDestinationE2ETest {

  private static final String WITH_A_SPACE = "https://example.com/a b";

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository userRepository;
  @Autowired private JwtTokenService jwt;
  @Autowired private CacheManager cacheManager;

  private String bearer;
  private String code;

  @BeforeEach
  void aLink() throws Exception {
    clear(cacheManager, "link");
    String tag = UUID.randomUUID().toString();
    UserEntity owner = userRepository.save(new UserEntity(tag + "@x.com", "google", tag));
    bearer = "Bearer " + jwt.createAccessToken(owner.getId(), "USER");
    code = "md" + tag.replace("-", "").substring(0, 8);
    mvc.perform(
            post("/api/v1/links")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com/\",\"customCode\":\"" + code + "\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  void aDestinationVisitorsCouldNotBeSentToIsRefusedWhenAdded() throws Exception {
    mvc.perform(
            post("/api/v1/links/" + code + "/destinations")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + WITH_A_SPACE + "\",\"weight\":100}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_DESTINATION_URL"));
  }

  @Test
  void aDestinationVisitorsCouldNotBeSentToIsRefusedWhenEdited() throws Exception {
    String added =
        mvc.perform(
                post("/api/v1/links/" + code + "/destinations")
                    .header("Authorization", bearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"url\":\"https://example.com/b\",\"weight\":50}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = ((Number) JsonPath.read(added, "$.id")).longValue();

    mvc.perform(
            patch("/api/v1/links/" + code + "/destinations/" + id)
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + WITH_A_SPACE + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_DESTINATION_URL"));
  }

  @Test
  void aCsvRowGetsTheSameAddressRulesAsTheApi() throws Exception {
    String tooLong = "https://example.com/" + "x".repeat(2049);
    MockMultipartFile csv =
        new MockMultipartFile(
            "file",
            "links.csv",
            "text/csv",
            ("url\n" + WITH_A_SPACE + "\n" + tooLong + "\nhttps://example.com/fine\n")
                .getBytes(StandardCharsets.UTF_8));

    String body =
        mvc.perform(multipart("/api/v1/links/bulk").file(csv).header("Authorization", bearer))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Bulk-Ok", "1"))
            .andExpect(header().string("X-Bulk-Failed", "2"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    assertThat(body).contains("UNSUPPORTED_URL_SCHEME");
  }
}
