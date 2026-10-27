package com.example.short_link.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.common.web.ApiShape.Change;
import com.example.short_link.common.web.ApiShape.Coverage;
import com.example.short_link.common.web.ApiShape.Shape;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

// Response record components are the JSON keys and the iOS app decodes them without CodingKeys, so
// a rename here is a client break even when every server test passes.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiShapeSnapshotTest {

  private static final Path SNAPSHOT = Path.of("src/test/resources/api-shape/openapi-shape.json");
  private static final Path REPORTS = Path.of("build/reports/tests/api-shape");
  private static final String REGENERATE =
      "./gradlew test --tests '*ApiShapeSnapshotTest' -PupdateApiShape";
  private static final JsonMapper JSON =
      JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;

  private String adminBearer() {
    UserEntity admin =
        userRepository.save(new UserEntity("api-shape@example.com", "google", "g-api-shape"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    return "Bearer " + jwt.createAccessToken(admin.getId(), "ADMIN");
  }

  @Test
  void theApiKeepsTheShapeClientsDecode() throws Exception {
    String docs =
        mvc.perform(get("/v3/api-docs").header("Authorization", adminBearer()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Shape current = ApiShape.of(JSON.readTree(docs));
    String rendered = JSON.writeValueAsString(current.toJson()) + "\n";
    Files.createDirectories(REPORTS);
    Files.writeString(REPORTS.resolve("openapi-shape.json"), rendered);
    Files.writeString(REPORTS.resolve("coverage.md"), coverage(ApiShape.coverage(current)));

    if (Boolean.getBoolean("api-shape.update")) {
      Files.createDirectories(SNAPSHOT.getParent());
      Files.writeString(SNAPSHOT, rendered);
      return;
    }
    assertThat(SNAPSHOT)
        .as(
            "No API shape snapshot. The current shape is in %s; run %s and commit %s.",
            REPORTS.resolve("openapi-shape.json"), REGENERATE, SNAPSHOT)
        .exists();
    String recorded = Files.readString(SNAPSHOT);
    List<Change> changes = ApiShape.diff(Shape.fromJson(JSON.readTree(recorded)), current);
    String report = ApiShape.report(changes);
    Files.writeString(REPORTS.resolve("diff.txt"), report);
    assertThat(changes)
        .as(
            "The API shape changed.%n%n%s%nIf the change is intended, run %s and commit %s."
                + " Name every BREAKING line in the PR body: old app versions decode these"
                + " responses.",
            report, REGENERATE, SNAPSHOT)
        .isEmpty();
    assertThat(recorded)
        .as("The snapshot is stale in form only; run %s.", REGENERATE)
        .isEqualTo(rendered);
  }

  private static String coverage(Coverage coverage) {
    StringBuilder text =
        new StringBuilder()
            .append("endpoints ")
            .append(coverage.endpoints())
            .append(", concrete schema ")
            .append(coverage.concrete())
            .append(", primitive ")
            .append(coverage.primitive())
            .append(", no body ")
            .append(coverage.noBody())
            .append(", generic ")
            .append(coverage.generic())
            .append("\n\n");
    coverage
        .genericEndpoints()
        .forEach(
            (endpoint, body) ->
                text.append("- ").append(endpoint).append(": ").append(body).append('\n'));
    return text.toString();
  }
}
