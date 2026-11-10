package com.example.short_link.link.domain;

import com.example.short_link.link.domain.repository.*;
import io.queryaudit.junit5.QueryAudit;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@QueryAudit
@Tag("integration")
class LinkRepositoryTest {

  @Autowired private LinkRepository repository;
}
