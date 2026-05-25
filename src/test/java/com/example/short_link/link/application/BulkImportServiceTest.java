package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class BulkImportServiceTest {

  @Autowired private BulkImportService service;
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

    BulkImportService.BulkImportResult result =
        service.importCsv(
            user.getId(), new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

    assertThat(result.ok()).isEqualTo(3);
    assertThat(result.failed()).isZero();
    assertThat(result.rows()).allSatisfy(r -> assertThat(r.shortCode()).matches("[0-9A-Za-z]{7}"));
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

    BulkImportService.BulkImportResult result =
        service.importCsv(
            user.getId(), new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

    assertThat(result.ok()).isEqualTo(1);
    assertThat(result.failed()).isEqualTo(2);
    BulkImportService.BulkImportRow okRow = result.rows().get(0);
    assertThat(okRow.shortCode()).isNotNull();
    assertThat(result.rows().get(1).error()).isNotNull();
    assertThat(result.rows().get(2).error()).contains("RESERVED_SHORT_CODE");
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
                service.importCsv(
                    user.getId(),
                    new ByteArrayInputStream(csv.toString().getBytes(StandardCharsets.UTF_8))))
        .isInstanceOf(LinkException.class);
  }

  @Test
  void parsesHeaderlessCsv() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("bulk4@example.com", "google", "g-bulk4"));
    String csv = "https://example.com/no-header-1\nhttps://example.com/no-header-2,nh2code\n";

    BulkImportService.BulkImportResult result =
        service.importCsv(
            user.getId(), new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

    assertThat(result.ok()).isEqualTo(2);
    assertThat(result.rows().get(1).shortCode()).isEqualTo("nh2code");
  }
}
