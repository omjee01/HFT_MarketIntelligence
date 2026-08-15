package com.hft.graphql;

import com.hft.model.domain.*;
import com.hft.service.analysis.*;
import com.hft.service.data.MarketDataAggregatorService;
import com.hft.service.signal.RecommendationEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/**
 * Field-level resolvers for StockDashboard.
 * Each field is resolved independently — GraphQL only calls what the client asked for.
 * A query asking only for quote + technical skips sentiment/fundamental/recommendation entirely.
 */
@Controller
@RequiredArgsConstructor
public class StockDashboardResolver {

    private final MarketDataAggregatorService marketDataService;
    private final TechnicalAnalysisService    taService;
    private final SentimentAnalysisService    sentimentService;
    private final FundamentalAnalysisService  fundamentalService;
    private final RecommendationEngine        engine;

    @SchemaMapping(typeName = "StockDashboard", field = "quote")
    public StockQuote quote(RecommendationResolver.StockDashboard dashboard) {
        return marketDataService.getQuote(dashboard.symbol(), dashboard.market()).orElse(null);
    }

    @SchemaMapping(typeName = "StockDashboard", field = "technical")
    public TechnicalIndicators technical(RecommendationResolver.StockDashboard dashboard) {
        return taService.analyze(dashboard.symbol(), dashboard.market()).orElse(null);
    }

    @SchemaMapping(typeName = "StockDashboard", field = "sentiment")
    public SentimentData sentiment(RecommendationResolver.StockDashboard dashboard) {
        return sentimentService.analyzeSentiment(dashboard.symbol(), dashboard.market());
    }

    @SchemaMapping(typeName = "StockDashboard", field = "fundamentals")
    public FundamentalData fundamentals(RecommendationResolver.StockDashboard dashboard) {
        return fundamentalService.analyze(dashboard.symbol(), dashboard.market()).orElse(null);
    }

    @SchemaMapping(typeName = "StockDashboard", field = "recommendation")
    public TradeRecommendation recommendation(RecommendationResolver.StockDashboard dashboard) {
        return engine.generateRecommendation(dashboard.symbol(), dashboard.market()).orElse(null);
    }
}
