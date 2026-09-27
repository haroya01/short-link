package com.example.short_link.profile.presentation.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.ProfileBlockType;
import com.example.short_link.profile.domain.email.EmailLeadEntity;
import com.example.short_link.profile.domain.email.EmailLeadRepository;
import com.example.short_link.profile.domain.repository.ProfileBlockRepository;
import com.example.short_link.testsupport.DockerHttpTest;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class EmailLeadConcurrencyTest extends DockerHttpTest {
  @Autowired private UserRepository users;
  @Autowired private ProfileBlockRepository blocks;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;
  @MockitoSpyBean private EmailLeadRepository leads;
  @LocalServerPort private int port;

  @Test
  void concurrentDuplicateSubmissionsBothSucceedAndPreserveOneRow() throws Exception {
    long blockId = createEmailForm();
    CyclicBarrier bothReadAbsent = new CyclicBarrier(2);
    doAnswer(
            invocation -> {
              boolean exists = (boolean) invocation.callRealMethod();
              assertThat(exists).isFalse();
              bothReadAbsent.await(10, TimeUnit.SECONDS);
              return false;
            })
        .when(leads)
        .existsByBlockIdAndEmail(blockId, "reader@example.com");

    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/public/email-leads"))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"blockId\":" + blockId + ",\"email\":\"READER@example.com\"}"))
            .build();
    try (HttpClient client = HttpClient.newHttpClient()) {
      var first = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
      var second = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());

      assertThat(first.get(25, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
      assertThat(second.get(25, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM email_lead WHERE block_id = ? AND email = ?",
                Long.class,
                blockId,
                "reader@example.com"))
        .isEqualTo(1L);
  }

  @Test
  void duplicateInsertPreservesTheOriginalSubmissionAndOptOut() {
    long blockId = createEmailForm();
    long ownerId =
        jdbc.queryForObject("SELECT user_id FROM profile_block WHERE id = ?", Long.class, blockId);
    var transaction = new TransactionTemplate(transactionManager);
    transaction.executeWithoutResult(
        status -> {
          var original = new EmailLeadEntity(ownerId, blockId, "reader@example.com", "first-ip");
          original.setOptedOut(true);
          leads.save(original);
        });
    var before = jdbc.queryForMap("SELECT * FROM email_lead WHERE block_id = ?", blockId);

    transaction.executeWithoutResult(
        status ->
            leads.addIfAbsent(
                new EmailLeadEntity(ownerId, blockId, "READER@example.com", "later-ip")));

    assertThat(jdbc.queryForMap("SELECT * FROM email_lead WHERE block_id = ?", blockId))
        .isEqualTo(before);
  }

  private long createEmailForm() {
    return new TransactionTemplate(transactionManager)
        .execute(
            status -> {
              String identity = UUID.randomUUID().toString();
              UserEntity user =
                  users.save(new UserEntity(identity + "@example.com", "google", identity));
              return blocks
                  .save(new ProfileBlockEntity(user.getId(), ProfileBlockType.EMAIL_FORM, "{}", 0))
                  .getId();
            });
  }
}
