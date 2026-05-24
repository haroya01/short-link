package com.example.short_link.profile.presentation.request;

import com.example.short_link.profile.application.write.ReorderItem;
import java.util.List;

public record MyProfileReorderRequest(List<ReorderItem> items) {}
