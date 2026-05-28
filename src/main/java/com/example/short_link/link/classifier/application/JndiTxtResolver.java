package com.example.short_link.link.classifier.application;

import com.example.short_link.common.net.TxtResolver;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JndiTxtResolver implements TxtResolver {

  @Override
  public List<String> lookup(String host) {
    try {
      InitialDirContext ctx =
          new InitialDirContext(
              new Hashtable<>(
                  Map.of(
                      "java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory",
                      "java.naming.provider.url", "dns://1.1.1.1 dns://8.8.8.8")));
      Attributes attrs = ctx.getAttributes(host, new String[] {"TXT"});
      var txt = attrs.get("TXT");
      if (txt == null) return List.of();
      List<String> values = new ArrayList<>(txt.size());
      for (int i = 0; i < txt.size(); i++) {
        values.add(((String) txt.get(i)).replace("\"", "").trim());
      }
      return values;
    } catch (NamingException e) {
      log.debug("TXT lookup failed for {}: {}", host, e.getMessage());
      return List.of();
    }
  }
}
