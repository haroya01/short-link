package com.example.short_link;

import static com.example.short_link.support.TestCacheCleaner.clear;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.common.net.TxtResolver;
import com.example.short_link.customdomain.domain.repository.CustomDomainRepository;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CustomDomainVerificationE2ETest.StubTxtResolverConfig.class)
class CustomDomainVerificationE2ETest {

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository userRepository;
  @Autowired private JwtTokenService jwt;
  @Autowired private StubTxtResolver txtResolver;
  @Autowired private CacheManager cacheManager;
  @Autowired private CustomDomainRepository domainRepository;
  @Autowired private LinkRepository linkRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  private final List<Long> createdUserIds = new ArrayList<>();

  @BeforeEach
  void resetResolver() {
    txtResolver.clear();
    clear(cacheManager, "link");
  }

  @AfterEach
  void deleteCommittedFixtures() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              for (Long userId : createdUserIds) {
                domainRepository
                    .findAllByUserIdOrderByIdAsc(userId)
                    .forEach(domainRepository::delete);
                linkRepository.deleteAll(
                    linkRepository.findAllByUserIdOrderByCreatedAtDesc(userId));
                userRepository.deleteById(userId);
              }
            });
    clear(cacheManager, "link");
  }

  private UserEntity createCommittedUser(String suffix) {
    UserEntity user =
        userRepository.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix));
    createdUserIds.add(user.getId());
    return user;
  }

  @Test
  void register_thenVerify_marksDomainVerifiedAndEnablesRouting() throws Exception {
    UserEntity user = createCommittedUser("cdv-1");
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(
            post("/api/v1/links")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://owner-verify.com\",\"customCode\":\"cdv00001\"}"))
        .andExpect(status().isCreated());

    String registerBody =
        mvc.perform(
                post("/api/v1/custom-domains")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"domain\":\"go.verify-flow.example.com\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.verified").value(false))
            .andExpect(
                jsonPath("$.verificationHost").value("_kurl-verify.go.verify-flow.example.com"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    Integer domainId = JsonPath.read(registerBody, "$.id");
    String issuedToken = JsonPath.read(registerBody, "$.verificationToken");
    txtResolver.put("_kurl-verify.go.verify-flow.example.com", issuedToken);

    mvc.perform(
            post("/api/v1/custom-domains/" + domainId + "/verify")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verified").value(true));

    mvc.perform(get("/cdv00001").header("Host", "go.verify-flow.example.com"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://owner-verify.com"));
  }

  @Test
  void register_thenVerify_returns422WhenTokenMissingAtTxt() throws Exception {
    UserEntity user = createCommittedUser("cdv-2");
    String token = jwt.createAccessToken(user.getId(), "USER");

    String registerBody =
        mvc.perform(
                post("/api/v1/custom-domains")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"domain\":\"go.missing-txt.example.com\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Integer domainId = JsonPath.read(registerBody, "$.id");

    mvc.perform(
            post("/api/v1/custom-domains/" + domainId + "/verify")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void register_thenVerify_returns422WhenTokenMismatch() throws Exception {
    UserEntity user = createCommittedUser("cdv-3");
    String token = jwt.createAccessToken(user.getId(), "USER");

    String registerBody =
        mvc.perform(
                post("/api/v1/custom-domains")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"domain\":\"go.wrong-txt.example.com\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Integer domainId = JsonPath.read(registerBody, "$.id");

    txtResolver.put("_kurl-verify.go.wrong-txt.example.com", "kurl-verify=someone-elses-token");

    mvc.perform(
            post("/api/v1/custom-domains/" + domainId + "/verify")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isUnprocessableEntity());
  }

  @TestConfiguration
  static class StubTxtResolverConfig {
    @Bean
    @Primary
    StubTxtResolver stubTxtResolver() {
      return new StubTxtResolver();
    }
  }

  static class StubTxtResolver implements TxtResolver {
    private final Map<String, List<String>> records = new HashMap<>();

    void put(String host, String value) {
      records.computeIfAbsent(host, k -> new ArrayList<>()).add(value);
    }

    void clear() {
      records.clear();
    }

    @Override
    public List<String> lookup(String host) {
      return records.getOrDefault(host, List.of());
    }
  }
}
