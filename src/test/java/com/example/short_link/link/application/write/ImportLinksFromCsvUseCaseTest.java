package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.link.application.dto.BulkImportResult;
import com.example.short_link.link.application.dto.BulkImportRow;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ImportLinksFromCsvUseCaseTest {

  @Autowired private ImportLinksFromCsvUseCase useCase;
  @Autowired private UserRepository userRepository;

  @Test
  void importsAllValidRows() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("bulk@example.com", "google", "g-bulk"));
    String csv =
        """
        url
        https://example.com/bulk-1
        https://example.com/bulk-2
        https://example.com/bulk-3
        """;

    BulkImportResult result =
        useCase.execute(
            new ImportLinksFromCsvCommand(
                user.getId(), new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))));

    assertThat(result.ok()).isEqualTo(3);
    assertThat(result.failed()).isZero();
    assertThat(result.rows())
        .allSatisfy(r -> assertThat(r.shortCode().value()).matches("[0-9A-Za-z]{7}"));
  }

  @Test
  void capturesPerRowFailures() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("bulk2@example.com", "google", "g-bulk2"));
    String csv =
        """
        url,custom_code
        https://example.com/ok,
        not-a-url,
        https://example.com/with-reserved,login
        """;

    BulkImportResult result =
        useCase.execute(
            new ImportLinksFromCsvCommand(
                user.getId(), new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))));

    assertThat(result.ok()).isEqualTo(1);
    assertThat(result.failed()).isEqualTo(2);
    BulkImportRow okRow = result.rows().get(0);
    assertThat(okRow.shortCode()).isNotNull();
    assertThat(result.rows().get(1).error()).isNotNull();
    assertThat(result.rows().get(2).error()).contains("RESERVED_SHORT_CODE");
  }

  @Test
  void rejectsOwnShortLinkRowsWithoutFailingTheBatch() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("bulk5@example.com", "google", "g-bulk5"));
    // 두 번째 행은 테스트 프로파일 base-url(http://localhost:8080)의 호스트 — CSV 임포트도
    // 단건 생성과 같은 자기참조 거부를 탄다.
    String csv =
        """
        url
        https://example.com/fine
        http://localhost:8080/abc123
        """;

    BulkImportResult result =
        useCase.execute(
            new ImportLinksFromCsvCommand(
                user.getId(), new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))));

    assertThat(result.ok()).isEqualTo(1);
    assertThat(result.failed()).isEqualTo(1);
    assertThat(result.rows().get(1).error()).contains("SELF_REFERENCING_URL");
  }

  @Test
  void rejectsBatchTooLarge() {
    UserEntity user = userRepository.save(new UserEntity("bulk3@example.com", "google", "g-bulk3"));
    StringBuilder csv = new StringBuilder("url\n");
    for (int i = 0; i < 101; i++) {
      csv.append("https://example.com/").append(i).append('\n');
    }
    assertThatThrownBy(
            () ->
                useCase.execute(
                    new ImportLinksFromCsvCommand(
                        user.getId(),
                        new ByteArrayInputStream(csv.toString().getBytes(StandardCharsets.UTF_8)))))
        .isInstanceOf(LinkException.class);
  }

  @Test
  void skipsBlankRowsBetweenContent() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("bulkb@example.com", "google", "g-bulkb"));
    String csv =
        """
        url
        https://example.com/a


        https://example.com/b
        """;

    BulkImportResult result =
        useCase.execute(
            new ImportLinksFromCsvCommand(
                user.getId(), new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))));

    assertThat(result.ok()).isEqualTo(2);
  }

  @Test
  void rejectsMalformedExpiresAt() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("bulke@example.com", "google", "g-bulke"));
    String csv =
        """
        url,custom_code,expires_at
        https://example.com/bad,,not-a-date
        """;

    BulkImportResult result =
        useCase.execute(
            new ImportLinksFromCsvCommand(
                user.getId(), new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))));

    assertThat(result.failed()).isEqualTo(1);
    assertThat(result.rows().get(0).error()).contains("ISO-8601");
  }

  @Test
  void rejectsBlankUrlAndUnknownScheme() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("bulks@example.com", "google", "g-bulks"));
    String csv =
        """
        url

        ftp://example.com/x
        not-a-url
        """;

    BulkImportResult result =
        useCase.execute(
            new ImportLinksFromCsvCommand(
                user.getId(), new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))));

    assertThat(result.ok()).isZero();
    assertThat(result.failed()).isEqualTo(2);
  }

  @Test
  void parsesHeaderlessCsv() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("bulk4@example.com", "google", "g-bulk4"));
    String csv = "https://example.com/no-header-1\nhttps://example.com/no-header-2,nh2code\n";

    BulkImportResult result =
        useCase.execute(
            new ImportLinksFromCsvCommand(
                user.getId(), new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))));

    assertThat(result.ok()).isEqualTo(2);
    assertThat(result.rows().get(1).shortCode().value()).isEqualTo("nh2code");
  }
}
