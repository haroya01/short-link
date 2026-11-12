package com.example.short_link.link.stats.application;

import com.example.short_link.link.application.dto.RecordedClick;
import java.util.List;

public interface ClickWebhookOutbox {

  void enqueue(List<RecordedClick> clicks);
}
