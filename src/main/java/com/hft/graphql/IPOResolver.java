package com.hft.graphql;

import com.hft.model.domain.IPOData;
import com.hft.repository.IPODataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class IPOResolver {

    private static final List<String> ACTIVE_STATUSES = List.of("UPCOMING", "OPEN", "CLOSED", "LISTED");

    private final IPODataRepository ipoRepo;

    @QueryMapping
    public IPOData ipoRecommendation(@Argument String symbol) {
        log.debug("[GraphQL] ipoRecommendation: {}", symbol);
        return ipoRepo.findFirstBySymbolOrderByLastUpdatedDesc(symbol.toUpperCase()).orElse(null);
    }

    @QueryMapping
    public List<IPOData> activeIpoRecommendations() {
        log.debug("[GraphQL] activeIpoRecommendations");
        return ipoRepo.findByStatusIn(ACTIVE_STATUSES);
    }
}
