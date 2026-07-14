package com.example.short_link.post.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글쓰기 파이프라인 가혹 조건 — 캡 경계(±1자)·이미지 백 장·초대형 표·리스트 천 개·발행 스무 번의 리비전 누적과 최고(最古)/최신
 * 복원·유니코드(ZWJ·결합자·RTL)·태그 캡까지, "보통 글"에선 절대 안 밟는 지점들을 실제 스택(DB·컨버터)으로 밟는다. 전부 왕복 고정점이어야 한다 — 극한에서
 * 본문이 변형되면 자동저장이 그 크기 그대로 더티 루프를 돈다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PostWriteStressTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final int MARKDOWN_CAP = 200_000;

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;

  private String token(String seed) {
    UserEntity user = userRepository.save(new UserEntity(seed + "@x.com", "google", seed));
    return jwt.createAccessToken(user.getId(), "USER");
  }

  private long createDraft(String token, String slug) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/posts")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"slug\":\"" + slug + "\",\"title\":\"가혹\",\"languageTag\":\"ko\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(body).get("id").asLong();
  }

  private String putMarkdown(String token, long postId, String markdown) throws Exception {
    String body =
        mvc.perform(
                put("/api/v1/posts/" + postId + "/markdown")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(JSON.writeValueAsString(Map.of("markdown", markdown))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(body).get("markdown").asText();
  }

  private String getMarkdown(String token, long postId) throws Exception {
    String body =
        mvc.perform(
                get("/api/v1/posts/" + postId + "/markdown")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(body).get("markdown").asText();
  }

  /** 정규화 후 저장→로드→재저장이 고정점인지 — 극한 입력용 공용 단언. */
  private void assertFixedPoint(String token, long postId, String markdown) throws Exception {
    String canonical = putMarkdown(token, postId, markdown);
    assertThat(getMarkdown(token, postId)).isEqualTo(canonical);
    assertThat(putMarkdown(token, postId, canonical)).isEqualTo(canonical);
  }

  // MARK: 본문 한도의 실효 경계 — 총 20만 자(요청 캡) · 블록 500개 · 블록당 10만 자
  // "20만 자까지 된다"는 요청 필드 캡이고, 실제로는 블록 수·블록 길이 가드가 먼저 문이다.
  // 셋 다 경계 정확히(±1)에서 갈라지는지 고정한다.

  @Test
  void bodyAtExactCapWithinBlockLimitsPassesAndOneOverIsRejected() throws Exception {
    String token = token("g-st-cap");
    long id = createDraft(token, "st-cap");

    // 398개 문단 × 500자 + 구분 개행 = 199,794자 → 마지막 문단에 206자 패딩 = 정확히 200,000자.
    String paragraph = "가".repeat(500);
    StringBuilder sb = new StringBuilder(MARKDOWN_CAP + 8);
    for (int i = 0; i < 398; i++) {
      if (i > 0) {
        sb.append("\n\n");
      }
      sb.append(paragraph);
    }
    sb.append("가".repeat(MARKDOWN_CAP - sb.length()));
    String atCap = sb.toString();
    assertThat(atCap).hasSize(MARKDOWN_CAP);

    mvc.perform(
            put("/api/v1/posts/" + id + "/markdown")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("markdown", atCap))))
        .andExpect(status().isOk());

    mvc.perform(
            put("/api/v1/posts/" + id + "/markdown")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("markdown", atCap + "가"))))
        .andExpect(status().isBadRequest());

    // 캡 본문이 저장 후에도 통째로 돌아온다(잘림 없음).
    assertThat(getMarkdown(token, id)).hasSize(MARKDOWN_CAP);
  }

  @Test
  void fiveHundredBlocksPassAndFiveHundredOneAreRejectedWithReadableReason() throws Exception {
    String token = token("g-st-blocks");
    long id = createDraft(token, "st-blocks");

    String fiveHundred =
        IntStream.rangeClosed(1, 500).mapToObj(i -> "문단 " + i).collect(Collectors.joining("\n\n"));
    mvc.perform(
            put("/api/v1/posts/" + id + "/markdown")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("markdown", fiveHundred))))
        .andExpect(status().isOk());

    String oneOver = fiveHundred + "\n\n문단 501";
    String reason =
        mvc.perform(
                put("/api/v1/posts/" + id + "/markdown")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(JSON.writeValueAsString(Map.of("markdown", oneOver))))
            .andExpect(status().isBadRequest())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // 에디터가 그대로 띄울 수 있는 사유 — 익명 "invalid argument" 금지.
    assertThat(JSON.readTree(reason).get("detail").asText()).contains("500");
  }

  @Test
  void singleBlockAtHundredThousandCharsPassesAndOneOverIsRejected() throws Exception {
    String token = token("g-st-block-len");
    long id = createDraft(token, "st-block-len");

    String at100k = "가".repeat(100_000);
    mvc.perform(
            put("/api/v1/posts/" + id + "/markdown")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("markdown", at100k))))
        .andExpect(status().isOk());

    String reason =
        mvc.perform(
                put("/api/v1/posts/" + id + "/markdown")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(JSON.writeValueAsString(Map.of("markdown", at100k + "가"))))
            .andExpect(status().isBadRequest())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(JSON.readTree(reason).get("detail").asText()).contains("100,000");
  }

  // MARK: 사진 백 장 — 마커·캡션 포함 전부 IMAGE 블록으로 왕복

  @Test
  void hundredImagesWithMarkersRoundTrip() throws Exception {
    String token = token("g-st-img");
    long id = createDraft(token, "st-images");

    String md =
        IntStream.rangeClosed(1, 100)
            .mapToObj(
                i ->
                    "!["
                        + (i % 3 == 0 ? "«wide» " : "")
                        + "«120"
                        + i % 10
                        + "x800» 사진 "
                        + i
                        + "](https://cdn.example/stress/"
                        + i
                        + ".png \"캡션 "
                        + i
                        + "\")")
            .collect(Collectors.joining("\n\n"));

    assertFixedPoint(token, id, md);
  }

  // MARK: 초대형 표 — 100행 × 8열, 이스케이프 파이프 섞음

  @Test
  void hugeTableRoundTrips() throws Exception {
    String token = token("g-st-table");
    long id = createDraft(token, "st-table");

    String header =
        "| "
            + IntStream.rangeClosed(1, 8).mapToObj(c -> "열" + c).collect(Collectors.joining(" | "))
            + " |";
    String sep =
        "| "
            + IntStream.rangeClosed(1, 8)
                .mapToObj(c -> c % 2 == 0 ? "---:" : ":---:")
                .collect(Collectors.joining(" | "))
            + " |";
    String body =
        IntStream.rangeClosed(1, 100)
            .mapToObj(
                r ->
                    "| "
                        + IntStream.rangeClosed(1, 8)
                            .mapToObj(c -> c == 4 ? "파이프 \\| " + r : "셀 " + r + "-" + c)
                            .collect(Collectors.joining(" | "))
                        + " |")
            .collect(Collectors.joining("\n"));

    assertFixedPoint(token, id, header + "\n" + sep + "\n" + body);
  }

  // MARK: 리스트 천 개 — 중첩 순환·번호/글머리 혼합 인접 그룹

  @Test
  void thousandListItemsRoundTrip() throws Exception {
    String token = token("g-st-list");
    long id = createDraft(token, "st-list");

    StringBuilder sb = new StringBuilder();
    for (int i = 1; i <= 1000; i++) {
      String pad = "  ".repeat(i % 4);
      sb.append(pad).append(i % 5 == 0 ? "- 글머리 " + i : (i % 3) + 1 + ". 항목 " + i).append('\n');
    }
    // 정규화(번호 재정렬) 1회 후 고정점 — 원문 그대로일 필요는 없고, 흔들리지 않아야 한다.
    String canonical = putMarkdown(token, id, sb.toString().strip());
    assertThat(putMarkdown(token, id, canonical)).isEqualTo(canonical);
    assertThat(getMarkdown(token, id)).isEqualTo(canonical);
  }

  // MARK: 발행 스무 번 — 리비전 누적, 최고(最古)·최신 양끝 복원

  @Test
  void twentyPublishCyclesAccumulateRevisionsAndRestoreBothEnds() throws Exception {
    String token = token("g-st-rev");
    long id = createDraft(token, "st-revisions");

    String first = null;
    String last = null;
    for (int v = 1; v <= 20; v++) {
      String canonical = putMarkdown(token, id, "# 버전 " + v + "\n\n본문 " + v);
      if (v == 1) {
        first = canonical;
      }
      last = canonical;
      mvc.perform(
              post("/api/v1/posts/" + id + "/publish").header("Authorization", "Bearer " + token))
          .andExpect(status().isOk());
    }

    String revisionsBody =
        mvc.perform(
                get("/api/v1/posts/" + id + "/revisions")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode revisions = JSON.readTree(revisionsBody);
    assertThat(revisions).hasSize(20);

    mvc.perform(
            post("/api/v1/posts/" + id + "/revisions/1/restore")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    assertThat(getMarkdown(token, id)).isEqualTo(first);

    mvc.perform(
            post("/api/v1/posts/" + id + "/revisions/20/restore")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    assertThat(getMarkdown(token, id)).isEqualTo(last);
  }

  // MARK: CRLF 붙여넣기 — \r 가 블록 본문에 새면 안 된다(프론트 markdown-to-blocks 와 미러)

  @Test
  void crlfPasteIsNormalizedAndStable() throws Exception {
    String token = token("g-st-crlf");
    long id = createDraft(token, "st-crlf");

    String canonical = putMarkdown(token, id, "# 제목\r\n\r\n본문 줄\r\n둘째 줄");
    assertThat(canonical).doesNotContain("\r");
    assertThat(getMarkdown(token, id)).isEqualTo(canonical);
    assertThat(putMarkdown(token, id, canonical)).isEqualTo(canonical);
  }

  // MARK: 유니코드 가혹 — ZWJ 가족·결합자·RTL 이 모든 블록 종류를 통과

  @Test
  void unicodeStressRoundTrips() throws Exception {
    String token = token("g-st-uni");
    long id = createDraft(token, "st-unicode");

    String family = "👨‍👩‍👧‍👦"; // ZWJ 가족
    String combining = "가́나́"; // 결합 악센트
    String rtl = "مرحبا بالعالم";
    String md =
        """
        # 제목 %s

        문단 %s 과 **볼드 %s** 그리고 `코드 %s`

        - 항목 %s
        1. 번호 %s

        > 인용 %s

        | 셀 %s | 값 |
        | --- | --- |
        | %s | %s |
        """
            .formatted(
                family, combining, rtl, family, rtl, combining, family, rtl, family, combining)
            .strip();

    assertFixedPoint(token, id, md);
  }

  // MARK: 태그 캡 — 100개(각 80자)는 통과, 101개는 400

  @Test
  void tagCapBoundary() throws Exception {
    String token = token("g-st-tags");
    long id = createDraft(token, "st-tags");

    var hundred = IntStream.rangeClosed(1, 100).mapToObj(i -> "태그" + i + "가".repeat(70)).toList();
    mvc.perform(
            patch("/api/v1/posts/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("tags", hundred))))
        .andExpect(status().isOk());

    var overCap = IntStream.rangeClosed(1, 101).mapToObj(i -> "태그" + i).toList();
    mvc.perform(
            patch("/api/v1/posts/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("tags", overCap))))
        .andExpect(status().isBadRequest());
  }
}
