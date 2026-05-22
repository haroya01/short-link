package com.example.short_link.campaign.application;

import com.example.short_link.link.application.CsvWriter;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Batch 묶음 export — CSV (인쇄소 발주 + 사후 정산) + QR PNG ZIP (인쇄 자산). */
@Service
@RequiredArgsConstructor
public class CampaignBatchExportService {

  private final CampaignBatchService batchService;
  private final QrPngEncoder qrEncoder;
  private final ShortLinkUrlBuilder urlBuilder;

  public String exportCsv(Long campaignId, Long ownerId) {
    List<BatchWithLink> batches = batchService.list(campaignId, ownerId);
    StringBuilder sb = new StringBuilder();
    CsvWriter.appendRow(
        sb,
        "batch_id",
        "batch_name",
        "distributor",
        "area",
        "quantity",
        "short_url",
        "destination_url",
        "memo");
    for (BatchWithLink bwl : batches) {
      CsvWriter.appendRow(
          sb,
          bwl.batch().getId(),
          bwl.batch().getName(),
          bwl.batch().getDistributorName(),
          bwl.batch().getAreaLabel(),
          bwl.batch().getQuantity(),
          urlBuilder.build(bwl.link().getShortCode()),
          bwl.link().getOriginalUrl(),
          bwl.batch().getMemo());
    }
    return sb.toString();
  }

  public byte[] exportQrZip(Long campaignId, Long ownerId) {
    return exportQrZip(campaignId, ownerId, QrOptions.defaults());
  }

  public byte[] exportQrZip(Long campaignId, Long ownerId, QrOptions options) {
    List<BatchWithLink> batches = batchService.list(campaignId, ownerId);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      for (BatchWithLink bwl : batches) {
        String shortUrl = urlBuilder.build(bwl.link().getShortCode());
        byte[] png =
            qrEncoder.encode(
                shortUrl,
                options.sizePx(),
                options.ec(),
                options.includeLabel() ? bwl.batch().getName() : null);
        zip.putNextEntry(new ZipEntry(qrFileName(bwl)));
        zip.write(png);
        zip.closeEntry();
      }
    } catch (IOException e) {
      throw new IllegalStateException("zip writing failed", e);
    }
    return out.toByteArray();
  }

  public byte[] exportSinglePng(Long campaignId, Long batchId, Long ownerId) {
    return exportSinglePng(campaignId, batchId, ownerId, QrOptions.defaults());
  }

  public byte[] exportSinglePng(Long campaignId, Long batchId, Long ownerId, QrOptions options) {
    BatchWithLink bwl = batchService.detail(campaignId, batchId, ownerId);
    return qrEncoder.encode(
        urlBuilder.build(bwl.link().getShortCode()),
        options.sizePx(),
        options.ec(),
        options.includeLabel() ? bwl.batch().getName() : null);
  }

  private static String qrFileName(BatchWithLink bwl) {
    String safe = sanitize(bwl.batch().getName());
    return String.format("%05d_%s_%s.png", bwl.batch().getId(), safe, bwl.link().getShortCode());
  }

  private static String sanitize(String s) {
    if (s == null) return "batch";
    String stripped = s.replaceAll("[^A-Za-z0-9가-힣._-]", "_");
    if (stripped.length() > 60) stripped = stripped.substring(0, 60);
    return stripped.isEmpty() ? "batch" : stripped;
  }
}
