package com.example.short_link.campaign.exception;

import com.example.short_link.common.exception.DomainException;

public abstract sealed class CampaignException extends RuntimeException implements DomainException
    permits CampaignArchivedException,
        CampaignBatchNotFoundException,
        CampaignNotFoundException,
        CampaignNotOwnedException,
        CampaignTerminalStateException,
        InvalidBatchRowException,
        InvalidCampaignPeriodException,
        MissingDestinationUrlException,
        MissingPostEndDestinationException,
        ReapplyOnNonEndedException {

  protected CampaignException(String message) {
    super(message);
  }
}
