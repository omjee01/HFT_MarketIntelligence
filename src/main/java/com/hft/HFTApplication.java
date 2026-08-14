package com.hft;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║         HFT Market Intelligence Platform (HMIP)                 ║
 * ║                                                                  ║
 * ║  AI-powered trade intelligence for US & Indian markets.          ║
 * ║  Combines Technical, Fundamental, Sentiment & Macro analysis     ║
 * ║  to generate data-backed BUY/SELL/HOLD recommendations.          ║
 * ║                                                                  ║
 * ║  Markets:  NYSE, NASDAQ, AMEX, NSE India, BSE India             ║
 * ║  Assets:   Stocks, Options, Commodities, IPOs                   ║
 * ║  Swagger:  http://localhost:8080/swagger-ui.html                 ║
 * ║  H2 Console: http://localhost:8080/h2-console  (dev only)        ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableAsync
public class HFTApplication {

    public static void main(String[] args) {
        SpringApplication.run(HFTApplication.class, args);
    }
}