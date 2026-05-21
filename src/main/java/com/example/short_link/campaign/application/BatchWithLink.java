package com.example.short_link.campaign.application;

import com.example.short_link.campaign.domain.CampaignBatchEntity;
import com.example.short_link.link.domain.LinkEntity;

/** Service → Controller 사이의 결합 — 배치 생성/조회 시 link 한 번 더 조회를 피하기 위한 묶음. */
public record BatchWithLink(CampaignBatchEntity batch, LinkEntity link) {}
