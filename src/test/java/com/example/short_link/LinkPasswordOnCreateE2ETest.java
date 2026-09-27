package com.example.short_link;

import static com.example.short_link.support.TestCacheCleaner.clear;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class LinkPasswordOnCreateE2ETest {

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository userRepository;
  @Autowired private JwtTokenService jwt;
  @Autowired private CacheManager cacheManager;

  @BeforeEach
  void clearLinkCache() {
    clear(cacheManager, "link");
  }

  @Test
  void passwordSetAtCreationGuardsTheRedirect() throws Exception {
    String token = tokenForNewUser();
    String code =
        create(
            token,
            "{\"url\":\"https://secret.example.com/plan\",\"password\":\"open-sesame\"}",
            true);

    mvc.perform(get("/" + code))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("password")))
        .andExpect(content().string(not(containsString("secret.example.com"))));
    mvc.perform(
            post("/" + code)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("password=wrong"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/" + code)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("password=open-sesame"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("https://secret.example.com/plan")));
  }

  @Test
  void protectedLinkIsNewEvenWhenTheSameUrlWasShortenedBefore() throws Exception {
    String token = tokenForNewUser();
    String open = create(token, "{\"url\":\"https://shared.example.com/deck\"}", false);
    String locked =
        create(
            token, "{\"url\":\"https://shared.example.com/deck\",\"password\":\"pw-1234\"}", true);

    assertThat(locked).isNotEqualTo(open);
    mvc.perform(get("/" + open)).andExpect(status().isFound());
    mvc.perform(get("/" + locked))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("password")));
  }

  @Test
  void blankPasswordLeavesTheLinkOpen() throws Exception {
    String token = tokenForNewUser();
    String code =
        create(token, "{\"url\":\"https://open.example.com/\",\"password\":\"   \"}", false);

    mvc.perform(get("/" + code)).andExpect(status().isFound());
  }

  @Test
  void anonymousCreationCannotProtectALink() throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/links")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"url\":\"https://anon.example.com/\",\"password\":\"pw\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.passwordProtected").value(false))
            .andReturn()
            .getResponse()
            .getContentAsString();

    mvc.perform(get("/" + JsonPath.read(body, "$.shortCode"))).andExpect(status().isFound());
  }

  private String create(String token, String json, boolean expectProtected) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/links")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.passwordProtected").value(expectProtected))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.shortCode");
  }

  private String tokenForNewUser() {
    String tag = UUID.randomUUID().toString();
    UserEntity user = userRepository.save(new UserEntity(tag + "@x.com", "google", tag));
    return jwt.createAccessToken(user.getId(), "USER");
  }
}
