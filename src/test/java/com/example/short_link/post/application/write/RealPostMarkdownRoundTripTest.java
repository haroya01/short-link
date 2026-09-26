package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.example.short_link.post.application.read.PostBlockView;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class RealPostMarkdownRoundTripTest {

  private static final Set<String> IMPORT_SPLIT_CODE =
      Set.of("java-6", "java-9", "java-enum", "jpa", "jpa-entitymanager", "chat-rtt-improvement-1");
  private static final Set<String> LIST_WITHOUT_MARKERS = Set.of("hexagonal-refactor");

  private final JsonMapper json = JsonMapper.builder().build();
  private final MarkdownBlocksConverter converter = new MarkdownBlocksConverter(json);

  @TestFactory
  Stream<DynamicTest> realPostsSurviveTheMarkdownRoundTripUsedByTheApp() throws Exception {
    JsonNode corpus;
    try (InputStream in = getClass().getResourceAsStream("/post-corpus/posts.json")) {
      corpus = json.readTree(in);
    }
    List<DynamicTest> tests = new ArrayList<>();
    for (JsonNode post : corpus) {
      tests.add(
          DynamicTest.dynamicTest(
              post.path("slug").stringValue(),
              () -> {
                String slug = post.path("slug").stringValue();
                assumeFalse(
                    IMPORT_SPLIT_CODE.contains(slug),
                    "imported with list-item code split into separate blocks — pending a data repair");
                assumeFalse(
                    LIST_WITHOUT_MARKERS.contains(slug),
                    "list block stored without list markers — pending a data repair");
                List<PostBlockView> original = new ArrayList<>();
                for (JsonNode b : post.path("blocks")) {
                  original.add(
                      new PostBlockView(
                          null,
                          b.path("type").stringValue(),
                          b.path("content").isNull() ? null : b.path("content").stringValue(),
                          null));
                }
                String markdown = converter.toMarkdown(original);
                List<String> back =
                    converter.toBlocks(markdown).stream()
                        .map(b -> normalize(b.type().name(), b.content()))
                        .toList();
                List<String> expected =
                    original.stream()
                        .filter(
                            b ->
                                b.content() != null && !b.content().isBlank()
                                    || "DIVIDER".equals(b.type()))
                        .map(b -> normalize(b.type(), b.content()))
                        .toList();
                int n = Math.min(back.size(), expected.size());
                for (int k = 0; k < n; k++) {
                  assertThat(back.get(k)).as("block %d", k).isEqualTo(expected.get(k));
                }
                assertThat(back).hasSameSizeAs(expected);
              }));
    }
    return tests.stream();
  }

  private String normalize(String type, String content) {
    String c = content == null ? "" : content;
    if ("DIVIDER".equals(type)
        || "PARAGRAPH".equals(type) && c.matches("\\s*([-*_])(\\s*\\1){2,}\\s*")) {
      return "DIVIDER|";
    }
    if ("CODE".equals(type)) {
      JsonNode node = json.readTree(c);
      String lang = node.path("lang").isString() ? node.path("lang").stringValue() : "";
      return "CODE|" + lang + "|" + node.path("code").stringValue().stripTrailing();
    }
    if ("EMBED".equals(type)) {
      if (c.trim().startsWith("{")) {
        JsonNode node = json.readTree(c);
        if (node.path("url").isString()) return "EMBED|" + node.path("url").stringValue();
      }
      return "EMBED|" + c.trim();
    }
    String text =
        c.replace("\r\n", "\n")
            .replaceAll("( {2,}|\\\\)\n", "\n")
            .lines()
            .map(String::stripTrailing)
            .reduce((a, b) -> a + "\n" + b)
            .orElse("")
            .strip();
    return type + "|" + text;
  }
}
