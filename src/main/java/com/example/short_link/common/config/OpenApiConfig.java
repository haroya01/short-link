package com.example.short_link.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String SCHEME_NAME = "bearerAuth";

  @Bean
  public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("kurl API")
                .version("v1")
                .description(
                    "URL shortener with click analytics. Use the Authorize button to set a JWT bearer token."))
        .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME))
        .components(
            new Components()
                .addSecuritySchemes(
                    SCHEME_NAME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Paste only the JWT (without the \"Bearer\" prefix).")));
  }
}
