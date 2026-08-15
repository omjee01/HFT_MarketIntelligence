package com.hft.graphql;

import com.hft.model.domain.*;
import com.hft.model.enums.Market;
import com.hft.service.analysis.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AnalysisResolver {

    private final TechnicalAnalysisService  taService;
    private final SentimentAnalysisService  sentimentService;
    private final FundamentalAnalysisService fundamentalService;
    private final MacroGeopoliticalService  macroService;

    @QueryMapping
    public TechnicalIndicators technicalAnalysis(@Argument String symbol,
                                                 @Argument Market market) {
        log.debug("[GraphQL] technicalAnalysis: {} on {}", symbol, market);
        return taService.analyze(symbol, market).orElse(null);
    }

    @QueryMapping
    public SentimentData sentiment(@Argument String symbol, @Argument Market market) {
        log.debug("[GraphQL] sentiment: {} on {}", symbol, market);
        return sentimentService.analyzeSentiment(symbol, market);
    }

    @QueryMapping
    public FundamentalData fundamentals(@Argument String symbol, @Argument Market market) {
        log.debug("[GraphQL] fundamentals: {} on {}", symbol, market);
        return fundamentalService.analyze(symbol, market).orElse(null);
    }

    @QueryMapping
    public MacroData macro(@Argument Market market) {
        log.debug("[GraphQL] macro: {}", market);
        return macroService.getMacroData(market);
    }
}
