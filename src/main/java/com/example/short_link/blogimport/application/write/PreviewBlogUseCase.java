package com.example.short_link.blogimport.application.write;

import com.example.short_link.blogimport.application.BlogFeed;
import com.example.short_link.blogimport.application.BlogFeeds;
import com.example.short_link.blogimport.application.feed.HtmlBlockConverter;
import com.example.short_link.blogimport.application.read.ImportPreviewView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PreviewBlogUseCase {

  private final BlogFeeds feeds;
  private final HtmlBlockConverter html;

  public ImportPreviewView execute(String url) {
    BlogFeed feed = feeds.readRecent(feeds.parse(url));
    List<ImportPreviewView.Item> items =
        feed.posts().stream()
            .map(
                post -> {
                  HtmlBlockConverter.Summary summary = html.summarize(post.html(), post.url());
                  return new ImportPreviewView.Item(
                      BlogImportRunner.title(post.title(), summary.text()),
                      post.url(),
                      post.publishedAt(),
                      HtmlBlockConverter.excerptOf(summary.text()),
                      summary.imageUrl());
                })
            .toList();
    return new ImportPreviewView(
        feed.address().platform().name(),
        feed.address().blogUrl(),
        feed.title(),
        feed.fullContent(),
        items);
  }
}
