package com.hft.controller;

import com.hft.model.domain.IPOData;
import com.hft.model.dto.ApiResponse;
import com.hft.repository.IPODataRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * IPO apply/avoid recommendations (pre-listing) and lifecycle status (post-listing) —
 * HFT_ARCHITECTURE.md §22.
 *
 * Path note: §12.1/§22.4 of the architecture doc describe this as "GET /recommendations/ipo"
 * (nested under RecommendationController), but SecurityConfig.java's PUBLIC_ENDPOINTS already
 * reserved "/api/v1/ipo/**" specifically, predating this stage. Followed the actual shipped
 * security config over the doc text; the doc's path reference is stale and worth correcting.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ipo")
@RequiredArgsConstructor
@Tag(name = "IPO", description = "IPO apply/avoid recommendations and post-listing hold/sell lifecycle")
public class IPOController {

    private static final List<String> ACTIVE_STATUSES = List.of("UPCOMING", "OPEN", "CLOSED", "LISTED");

    private final IPODataRepository ipoRepo;

    @GetMapping("/recommendations")
    @Operation(summary = "All active IPO recommendations (UPCOMING, OPEN, CLOSED, recently LISTED)")
    public ResponseEntity<ApiResponse<List<IPOData>>> getActiveRecommendations() {
        List<IPOData> active = ipoRepo.findByStatusIn(ACTIVE_STATUSES);
        return ResponseEntity.ok(ApiResponse.success(active,
                String.format("%d active IPO recommendations", active.size())));
    }

    @GetMapping("/recommendations/{symbol}")
    @Operation(summary = "IPO recommendation for a specific symbol")
    public ResponseEntity<ApiResponse<IPOData>> getRecommendation(@PathVariable String symbol) {
        Optional<IPOData> ipo = ipoRepo.findFirstBySymbolOrderByLastUpdatedDesc(symbol.toUpperCase());
        return ipo.map(i -> ResponseEntity.ok(ApiResponse.success(i)))
                .orElse(ResponseEntity.ok(ApiResponse.error("No IPO data for " + symbol, "NO_DATA")));
    }
}
