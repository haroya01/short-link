package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.link.application.ShortCodeGenerator;
import com.example.short_link.link.application.dto.LinkCreated;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest
@ActiveProfiles("test")
class CreateLinkUseCaseRetryTest {

  @Autowired private LinkRepository links;
  @Autowired private MeterRegistry meterRegistry;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private AuditLogService auditLog;
  @Autowired private CreateLinkValidator validator;
  @Autowired private LinkDefaultsWriter defaults;
  @Autowired private DedicatedLinks dedicatedLinks;

  @Autowired
  @Qualifier("linkPasswordEncoder")
  private PasswordEncoder passwordEncoder;

  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbc;

  private final List<String> createdCodes = new ArrayList<>();

  @AfterEach
  void deleteCommittedLinks() {
    for (String code : createdCodes) {
      jdbc.update("DELETE FROM link WHERE short_code = ?", code);
    }
  }

  @Test
  void aGeneratedCodeThatIsAlreadyTakenIsReplacedByTheNextOne() {
    String taken = newCode();
    String fresh = newCode();
    links.save(new LinkEntity("https://example.com/taken", taken));
    ShortCodeGenerator generator = mock(ShortCodeGenerator.class);
    when(generator.generate()).thenReturn(taken, fresh);
    CreateLinkUseCase useCase =
        new CreateLinkUseCase(
            links,
            generator,
            meterRegistry,
            events,
            auditLog,
            validator,
            defaults,
            dedicatedLinks,
            passwordEncoder,
            transactionManager,
            200L);

    LinkCreated created =
        useCase.execute(CreateLinkCommand.of("https://example.com/retried", null, null, null));

    assertThat(created.shortCode().value()).isEqualTo(fresh);
    assertThat(links.findByShortCode(new ShortCode(fresh)).orElseThrow().getOriginalUrl())
        .isEqualTo("https://example.com/retried");
    assertThat(links.findByShortCode(new ShortCode(taken)).orElseThrow().getOriginalUrl())
        .isEqualTo("https://example.com/taken");
  }

  private String newCode() {
    String code = "r" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    createdCodes.add(code);
    return code;
  }
}
