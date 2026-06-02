package com.example.short_link.campaign.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.campaign.application.CampaignBatchExportService;
import com.example.short_link.campaign.application.CampaignBatchService;
import com.example.short_link.campaign.application.dto.BatchWithLink;
import com.example.short_link.campaign.application.write.CampaignBatchBulkCommand;
import com.example.short_link.campaign.application.write.CampaignBatchCreateCommand;
import com.example.short_link.campaign.application.write.CampaignBatchUpdateCommand;
import com.example.short_link.campaign.domain.CampaignBatchEntity;
import com.example.short_link.campaign.exception.CampaignErrorCode;
import com.example.short_link.campaign.exception.CampaignException;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Slice test — routing / validation / serialization / status mapping. Service 는 mock. */
@KurlWebMvcTest(controllers = CampaignBatchController.class)
@Import(CampaignExceptionHandler.class)
class CampaignBatchControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private CampaignBatchService service;
  @MockitoBean private CampaignBatchExportService exportService;
  @MockitoBean private ShortLinkUrlBuilder urlBuilder;

  private static final long USER_ID = 7L;
  private static final long CAMPAIGN_ID = 42L;

  private BatchWithLink batchWithLink(String name, int quantity) {
    LinkEntity link = new LinkEntity("https://dest.example.com", "abc123", USER_ID, null);
    CampaignBatchEntity batch =
        new CampaignBatchEntity(CAMPAIGN_ID, new LinkId(100L), name, "Kim", "East", quantity, "m");
    return new BatchWithLink(batch, link);
  }

  @Test
  void rejectsAnonymousCreate() throws Exception {
    mvc.perform(
            post("/api/v1/campaigns/" + CAMPAIGN_ID + "/batches")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"quantity\":10}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createReturns201WithLocationAndShortLink() throws Exception {
    when(service.create(anyLong(), anyLong(), any(CampaignBatchCreateCommand.class)))
        .thenReturn(batchWithLink("flyer-A", 500));
    when(urlBuilder.build(any())).thenReturn("https://kurl.test/abc123");

    mvc.perform(
            post("/api/v1/campaigns/" + CAMPAIGN_ID + "/batches")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"flyer-A\",\"quantity\":500}"))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.name").value("flyer-A"))
        .andExpect(jsonPath("$.quantity").value(500))
        .andExpect(jsonPath("$.campaignId").value(CAMPAIGN_ID))
        .andExpect(jsonPath("$.destinationUrl").value("https://dest.example.com"))
        .andExpect(jsonPath("$.shortUrl").value("https://kurl.test/abc123"));
  }

  @Test
  void createRejectsNonPositiveQuantity() throws Exception {
    mvc.perform(
            post("/api/v1/campaigns/" + CAMPAIGN_ID + "/batches")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"bad\",\"quantity\":0}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createRejectsBlankName() throws Exception {
    mvc.perform(
            post("/api/v1/campaigns/" + CAMPAIGN_ID + "/batches")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"quantity\":5}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void detailReturnsBatch() throws Exception {
    when(service.detail(anyLong(), anyLong(), anyLong())).thenReturn(batchWithLink("flyer-B", 100));
    when(urlBuilder.build(any())).thenReturn("https://kurl.test/abc123");

    mvc.perform(
            get("/api/v1/campaigns/" + CAMPAIGN_ID + "/batches/5")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("flyer-B"));
  }

  @Test
  void listReturnsBatches() throws Exception {
    when(service.list(anyLong(), anyLong()))
        .thenReturn(List.of(batchWithLink("one", 10), batchWithLink("two", 20)));
    when(urlBuilder.build(any())).thenReturn("https://kurl.test/abc123");

    mvc.perform(
            get("/api/v1/campaigns/" + CAMPAIGN_ID + "/batches")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void bulkCreateReturns201WithAllRows() throws Exception {
    when(service.createBulk(anyLong(), anyLong(), any(CampaignBatchBulkCommand.class)))
        .thenReturn(
            List.of(batchWithLink("r1", 1), batchWithLink("r2", 2), batchWithLink("r3", 3)));
    when(urlBuilder.build(any())).thenReturn("https://kurl.test/abc123");

    mvc.perform(
            post("/api/v1/campaigns/" + CAMPAIGN_ID + "/batches/bulk")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"batches\":[{\"name\":\"r1\",\"quantity\":1},{\"name\":\"r2\",\"quantity\":2},"
                        + "{\"name\":\"r3\",\"quantity\":3}]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()").value(3));
  }

  @Test
  void bulkCreateRejectsEmptyList() throws Exception {
    mvc.perform(
            post("/api/v1/campaigns/" + CAMPAIGN_ID + "/batches/bulk")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batches\":[]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateReturnsUpdatedBatch() throws Exception {
    when(service.update(anyLong(), anyLong(), anyLong(), any(CampaignBatchUpdateCommand.class)))
        .thenReturn(batchWithLink("renamed", 50));
    when(urlBuilder.build(any())).thenReturn("https://kurl.test/abc123");

    mvc.perform(
            patch("/api/v1/campaigns/" + CAMPAIGN_ID + "/batches/5")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"renamed\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("renamed"));
  }

  @Test
  void deleteReturns204() throws Exception {
    mvc.perform(
            delete("/api/v1/campaigns/" + CAMPAIGN_ID + "/batches/5")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isNoContent());

    verify(service).delete(CAMPAIGN_ID, 5L, USER_ID);
  }

  @Test
  void otherOwnerGets404() throws Exception {
    when(service.create(anyLong(), anyLong(), any(CampaignBatchCreateCommand.class)))
        .thenThrow(new CampaignException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));

    mvc.perform(
            post("/api/v1/campaigns/" + CAMPAIGN_ID + "/batches")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"sneaky\",\"quantity\":1}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAMPAIGN_NOT_FOUND"));
  }

  @Test
  void qrPngReturnsImage() throws Exception {
    when(exportService.exportSinglePng(anyLong(), anyLong(), anyLong(), any()))
        .thenReturn(new byte[] {1, 2, 3});

    mvc.perform(
            get("/api/v1/campaigns/" + CAMPAIGN_ID + "/batches/5/qr")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_PNG));
  }

  @Test
  void qrZipReturnsArchive() throws Exception {
    when(exportService.exportQrZip(anyLong(), anyLong(), any())).thenReturn(new byte[] {4, 5});

    mvc.perform(
            get("/api/v1/campaigns/" + CAMPAIGN_ID + "/batches/qr-zip")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
  }

  @Test
  void csvReturnsCsv() throws Exception {
    when(exportService.exportCsv(anyLong(), anyLong())).thenReturn("name,clicks\nflyer,0\n");

    mvc.perform(
            get("/api/v1/campaigns/" + CAMPAIGN_ID + "/batches/csv")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/csv"));
  }
}
