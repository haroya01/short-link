package com.example.short_link.blogimport.presentation.response;

import com.example.short_link.blogimport.application.read.ImportSourceView;
import java.util.List;

public record ImportSourceListResponse(List<ImportSourceView> items) {}
