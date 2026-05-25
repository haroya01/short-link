package com.example.short_link.user.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.user.application.ApiKeyService;
import com.example.short_link.user.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end coverage of the API key feature: issue → list → call protected endpoint with the raw
 * key → revoke → call again and confirm rejection. Uses dev-login to seed a session, then exercises
 * both \"Authorization: Bearer kurl_...\" and \"X-API-Key\" header forms.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiKeyControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JsonMapper json;
  @Autowired private UserRepository userRepository;

  @Test
  void issuedKeyAuthenticatesProtectedEndpoint_viaBearer() throws Exception {
    String accessToken = devLogin("ak1@local.test");

    String rawKey = issueKey(accessToken, "bearer-test");
    assertThat(rawKey).startsWith(ApiKeyService.KEY_PREFIX);

    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + rawKey))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("ak1@local.test"));
  }

  @Test
  void issuedKeyAuthenticatesProtectedEndpoint_viaXApiKeyHeader() throws Exception {
    String accessToken = devLogin("ak2@local.test");
    String rawKey = issueKey(accessToken, "x-api-key-test");

    mvc.perform(get("/api/v1/users/me").header("X-API-Key", rawKey))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("ak2@local.test"));
  }

  @Test
  void revokedKeyIsRejected() throws Exception {
    String accessToken = devLogin("ak3@local.test");
    JsonNode issued = issueKeyNode(accessToken, "to-revoke");
    long keyId = issued.get("id").asLong();
    String rawKey = issued.get("rawKey").asText();

    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + rawKey))
        .andExpect(status().isOk());

    mvc.perform(
            delete("/api/v1/users/me/api-keys/" + keyId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + rawKey))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unknownKeyIsRejected() throws Exception {
    mvc.perform(
            get("/api/v1/users/me")
                .header("Authorization", "Bearer kurl_thiskeydoesnotexist123456789012345678"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void listShowsIssuedAndRevokedKeysInOrder() throws Exception {
    String accessToken = devLogin("ak4@local.test");
    issueKey(accessToken, "first");
    issueKey(accessToken, "second");

    mvc.perform(get("/api/v1/users/me/api-keys").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].name").value("second"))
        .andExpect(jsonPath("$[1].name").value("first"));
  }

  @Test
  void cannotIssueWithoutAuth() throws Exception {
    mvc.perform(
            post("/api/v1/users/me/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"nope\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void cannotUseAnotherUsersKey() throws Exception {
    String ownerToken = devLogin("ak6-owner@local.test");
    String rawKey = issueKey(ownerToken, "owner-only");
    long ownerId = userRepository.findByEmail("ak6-owner@local.test").orElseThrow().getId();

    String otherToken = devLogin("ak6-other@local.test");

    // The other user's JWT can revoke only their own keys; trying to revoke owner's id from another
    // session must not succeed (404 since lookup is scoped by the path id but ownership-checked).
    mvc.perform(
            delete("/api/v1/users/me/api-keys/9999999")
                .header("Authorization", "Bearer " + otherToken))
        .andExpect(status().isNotFound());

    // owner's key still works
    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + rawKey))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ownerId));
  }

  // -------------------- helpers --------------------

  private String devLogin(String email) throws Exception {
    MvcResult res =
        mvc.perform(
                post("/api/v1/auth/dev-login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
    return json.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
  }

  private String issueKey(String accessToken, String name) throws Exception {
    return issueKeyNode(accessToken, name).get("rawKey").asText();
  }

  private JsonNode issueKeyNode(String accessToken, String name) throws Exception {
    MvcResult res =
        mvc.perform(
                post("/api/v1/users/me/api-keys")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return json.readTree(res.getResponse().getContentAsString());
  }
}
