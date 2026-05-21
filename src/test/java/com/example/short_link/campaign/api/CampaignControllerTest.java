package com.example.short_link.campaign.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.campaign.domain.CampaignPostEndAction;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
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
class CampaignControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;
  @Autowired private ObjectMapper json;

  private String bearer(String suffix) {
    UserEntity user =
        userRepository.save(new UserEntity("u-" + suffix + "@x.com", "google", suffix));
    return "Bearer " + jwt.createAccessToken(user.getId(), "USER");
  }

  @Test
  void rejectsAnonymousCreate() throws Exception {
    String body =
        json.writeValueAsString(
            Map.of("name", "x", "endsAt", Instant.now().plusSeconds(3600).toString()));

    mvc.perform(post("/api/v1/campaigns").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createAndDetailRoundTrip() throws Exception {
    String token = bearer("rt");
    String body =
        json.writeValueAsString(
            Map.of(
                "name", "spring drop",
                "endsAt", Instant.now().plusSeconds(3600).toString(),
                "postEndAction", "KEEP"));

    String created =
        mvc.perform(
                post("/api/v1/campaigns")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("spring drop"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.batchCount").value(0))
            .andReturn()
            .getResponse()
            .getContentAsString();

    Long id = ((Number) json.readValue(created, Map.class).get("id")).longValue();

    mvc.perform(get("/api/v1/campaigns/" + id).header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("spring drop"));
  }

  @Test
  void rejectsCreateWhenRedirectMissingDestination() throws Exception {
    String token = bearer("rd");
    String body =
        json.writeValueAsString(
            Map.of(
                "name", "redir",
                "endsAt", Instant.now().plusSeconds(3600).toString(),
                "postEndAction", "REDIRECT"));

    mvc.perform(
            post("/api/v1/campaigns")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listReturnsOnlyOwnerCampaigns() throws Exception {
    String mine = bearer("mine");
    String others = bearer("others");
    String body =
        json.writeValueAsString(
            Map.of("name", "mine-c", "endsAt", Instant.now().plusSeconds(3600).toString()));
    mvc.perform(
            post("/api/v1/campaigns")
                .header("Authorization", mine)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/v1/campaigns")
                .header("Authorization", others)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "name",
                            "others-c",
                            "endsAt",
                            Instant.now().plusSeconds(3600).toString()))))
        .andExpect(status().isCreated());

    mvc.perform(get("/api/v1/campaigns").header("Authorization", mine))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("mine-c"));
  }

  @Test
  void patchUpdatesPolicy() throws Exception {
    String token = bearer("up");
    Long id =
        createCampaign(
            token, "orig", Instant.now().plusSeconds(3600), CampaignPostEndAction.KEEP, null);

    Instant newEnd = Instant.now().plusSeconds(7200);
    String body =
        json.writeValueAsString(
            Map.of(
                "name", "renamed",
                "endsAt", newEnd.toString(),
                "postEndAction", "REDIRECT",
                "postEndDestinationUrl", "https://post.example.com"));

    mvc.perform(
            patch("/api/v1/campaigns/" + id)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("renamed"))
        .andExpect(jsonPath("$.postEndAction").value("REDIRECT"));
  }

  @Test
  void deleteArchivesAndPreservesEntity() throws Exception {
    String token = bearer("arc");
    Long id =
        createCampaign(
            token, "arc-c", Instant.now().plusSeconds(3600), CampaignPostEndAction.KEEP, null);

    mvc.perform(delete("/api/v1/campaigns/" + id).header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"));

    mvc.perform(get("/api/v1/campaigns/" + id).header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"));
  }

  private Long createCampaign(
      String token,
      String name,
      Instant endsAt,
      CampaignPostEndAction postEndAction,
      String postEndDestinationUrl)
      throws Exception {
    var body = new java.util.HashMap<String, Object>();
    body.put("name", name);
    body.put("endsAt", endsAt.toString());
    body.put("postEndAction", postEndAction.name());
    if (postEndDestinationUrl != null) {
      body.put("postEndDestinationUrl", postEndDestinationUrl);
    }
    String response =
        mvc.perform(
                post("/api/v1/campaigns")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return ((Number) json.readValue(response, Map.class).get("id")).longValue();
  }
}
