package com.hft.ipo;

import com.hft.model.domain.IPOData;
import com.hft.model.enums.Market;
import com.hft.repository.IPODataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Seeds sample/test IPOData rows so IPOAnalysisService and the new IPO endpoints have
 * something real to score against. There is no real SEBI/SEC IPO-listing ingestion in this
 * system (a separate, unbuilt concern) — this is clearly-labeled sample data, not live feed
 * data. Idempotent (skips symbols that already exist) and config-gated off in prod, same
 * pattern as com.hft.identity.TestUserSeeder.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SampleIpoSeeder implements ApplicationRunner {

    private final IPODataRepository ipoRepo;
    private final IPOAnalysisService ipoAnalysisService;

    @Value("${hft.ipo.seed-sample-data.enabled:true}")
    private boolean seedEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) return;

        LocalDate today = LocalDate.now();

        seedIfAbsent(IPOData.builder()
                .symbol("TESTIPO1")
                .companyName("Test Industries Ltd")
                .market(Market.INDIA_NSE)
                .sector("Technology")
                .industry("IT Services")
                .issuePriceLow(BigDecimal.valueOf(480))
                .issuePriceHigh(BigDecimal.valueOf(500))
                .issueSizeCrores(BigDecimal.valueOf(1200))
                .lotSize(30)
                .subscriptionOpenDate(today.minusDays(1))
                .subscriptionCloseDate(today.plusDays(2))
                .allotmentDate(today.plusDays(5))
                .listingDate(today.plusDays(9))
                .retailSubscriptionTimes(12.5)
                .qibSubscriptionTimes(45.2)
                .niiSubscriptionTimes(28.7)
                .overallSubscriptionTimes(22.3)
                .gmpPercent(18.5)
                .peAtIssuePrice(28.5)
                .industryPeAvg(32.0)
                .evToSalesAtIssuePrice(4.2)
                .leadManagers("ICICI Securities, Kotak Mahindra Capital")
                .leadManagerTrackRecordScore(68.0)
                .status("OPEN")
                .lastUpdated(LocalDateTime.now())
                .build());

        seedIfAbsent(IPOData.builder()
                .symbol("SAMPLETC")
                .companyName("Sample Tech Corp")
                .market(Market.US_NASDAQ)
                .sector("Technology")
                .industry("Software")
                .issuePriceLow(BigDecimal.valueOf(18))
                .issuePriceHigh(BigDecimal.valueOf(21))
                .issueSizeCrores(BigDecimal.valueOf(350))    // $M for US, per IPOData's field comment
                // US IPOs don't report retail/QIB/NII subscription multiples or GMP the way
                // Indian IPOs do — left null deliberately to exercise DemandScore's null path.
                .peAtIssuePrice(45.0)
                .industryPeAvg(38.0)
                .evToSalesAtIssuePrice(8.5)
                .subscriptionOpenDate(today.plusDays(10))
                .subscriptionCloseDate(today.plusDays(13))
                .listingDate(today.plusDays(14))
                .leadManagers("Goldman Sachs, Morgan Stanley")
                // leadManagerTrackRecordScore left null deliberately to exercise QualityScore's
                // "default 50 when null" path.
                .status("UPCOMING")
                .lastUpdated(LocalDateTime.now())
                .build());

        seedIfAbsent(IPOData.builder()
                .symbol("HYPEIPO")
                .companyName("Hype Ventures Ltd")
                .market(Market.INDIA_NSE)
                .sector("Consumer")
                .industry("E-commerce")
                .issuePriceLow(BigDecimal.valueOf(90))
                .issuePriceHigh(BigDecimal.valueOf(95))
                .issueSizeCrores(BigDecimal.valueOf(800))
                .lotSize(150)
                .subscriptionOpenDate(today.minusDays(1))
                .subscriptionCloseDate(today.plusDays(1))
                .allotmentDate(today.plusDays(4))
                .listingDate(today.plusDays(8))
                .retailSubscriptionTimes(85.0)
                .qibSubscriptionTimes(15.0)     // retail-led, not QIB-led -> no qibTilt bonus
                .niiSubscriptionTimes(120.0)
                .overallSubscriptionTimes(65.0) // very high demand
                .gmpPercent(45.0)               // hot grey market
                .peAtIssuePrice(95.0)
                .industryPeAvg(30.0)            // richly valued vs industry
                .evToSalesAtIssuePrice(15.0)
                .leadManagers("Axis Capital")
                .leadManagerTrackRecordScore(40.0)
                .status("OPEN")
                .lastUpdated(LocalDateTime.now())
                .build());
    }

    private void seedIfAbsent(IPOData sample) {
        if (ipoRepo.existsBySymbol(sample.getSymbol())) {
            log.debug("[SampleIpoSeeder] {} already exists, skipping", sample.getSymbol());
            return;
        }
        // Score immediately rather than leaving recommendation/predictedListingGainPercent
        // null until the next @Scheduled tick — real ingestion would do the same on arrival.
        IPOData scored = ipoAnalysisService.scoreAndSave(sample);
        log.info("[SampleIpoSeeder] Seeded sample IPO {} ({}, status={}) -> {}",
                scored.getSymbol(), scored.getMarket(), scored.getStatus(), scored.getRecommendation());
    }
}
