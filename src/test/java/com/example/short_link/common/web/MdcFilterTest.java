package com.example.short_link.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.audit.AuditLogEntity;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MdcFilterTest {

  private final MdcFilter filter = new MdcFilter();

  private String idGivenBackFor(String sent) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
    if (sent != null) {
      request.addHeader("X-Request-Id", sent);
    }
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response.getHeader("X-Request-Id");
  }

  @Test
  void aCallersIdIsKeptUpToTheLongestOneAnAuditRowCanHold() throws Exception {
    String longest = "a".repeat(MdcFilter.MAX_REQUEST_ID_LENGTH);

    assertThat(idGivenBackFor(longest)).isEqualTo(longest);
  }

  @Test
  void aCallersIdOneCharacterLongerIsReplaced() throws Exception {
    String tooLong = "a".repeat(MdcFilter.MAX_REQUEST_ID_LENGTH + 1);

    assertThat(idGivenBackFor(tooLong)).isNotEqualTo(tooLong).hasSize(36);
  }

  @Test
  void anIdThatCouldForgeALogLineIsReplaced() throws Exception {
    assertThat(idGivenBackFor("abc def")).hasSize(36);
    assertThat(idGivenBackFor("abc_def")).hasSize(36);
  }

  @Test
  void aRequestWithoutAnIdGetsOne() throws Exception {
    assertThat(idGivenBackFor(null)).hasSize(36);
  }

  @Test
  void everyIdTheFilterKeepsFitsTheAuditRowsColumn() throws Exception {
    int column =
        AuditLogEntity.class.getDeclaredField("requestId").getAnnotation(Column.class).length();

    assertThat(MdcFilter.MAX_REQUEST_ID_LENGTH).isLessThanOrEqualTo(column);
  }
}
