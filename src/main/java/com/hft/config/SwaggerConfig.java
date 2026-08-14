package com.hft.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 / Swagger UI configuration.
 * Access at: http://localhost:8080/swagger-ui.html
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("HFT Market Intelligence Platform API")
                        .description("""
                            ## AI-Powered Trade Intelligence for US & Indian Markets

                            Provides data-backed **BUY/SELL/HOLD** recommendations by combining:
                            - 📊 **Technical Analysis** — 40+ indicators (RSI, MACD, Bollinger, etc.)
                            - 📰 **Sentiment Analysis** — News + Social media NLP scoring
                            - 📋 **Fundamental Analysis** — P/E, EPS growth, ROE, debt ratios
                            - 🌍 **Macro/Geopolitical** — Fed/RBI rates, FII flows, VIX, Oil
                            - 🤖 **ML Prediction** — Ensemble model for price targets

                            **Markets:** NYSE | NASDAQ | NSE India | BSE India
                            **Assets:** Stocks | Options | Commodities | IPOs

                            ---
                            ⚠️ *For informational purposes only. Not investment advice.*
                            """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("HFT Platform Team")
                                .url("https://github.com/PTD2315/hft-market-intelligence"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development"),
                        new Server().url("https://api.hft-platform.com").description("Production")
                ))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT token for authenticated endpoints")));
    }
}