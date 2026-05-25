package com.example.short_link.admin.application.dto;

import java.util.List;

public record AdminActiveUsers(String period, List<Bucket> buckets) {

  public record Bucket(String bucket, long active) {}
}
