package com.hft.controller;

import com.hft.model.domain.PortfolioAlert;
import com.hft.model.domain.PortfolioPosition;
import com.hft.model.dto.ApiResponse;
import com.hft.model.dto.ClosePositionRequest;
import com.hft.model.dto.OpenPositionRequest;
import com.hft.service.portfolio.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Virtual portfolio tracker (Stage 13, HFT_ARCHITECTURE.md §30) — requires auth
 * (SecurityConfig already reserves /api/v1/portfolio/** for this). Records positions the user
 * bought/sold on their OWN brokerage (Zerodha Kite / INDmoney); never executes trades itself.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/portfolio")
@RequiredArgsConstructor
@Tag(name = "Portfolio", description = "Virtual portfolio tracking and alerts — records purchases made elsewhere, never executes trades")
public class PortfolioController {

    private final PortfolioService portfolioService;

    @PostMapping("/positions")
    @Operation(summary = "Record a position after buying it on your own brokerage")
    public ResponseEntity<ApiResponse<PortfolioPosition>> openPosition(
            @Valid @RequestBody OpenPositionRequest request, Authentication authentication) {
        try {
            PortfolioPosition position = portfolioService.openPosition(authentication.getName(), request);
            return ResponseEntity.ok(ApiResponse.success(position, "Position recorded"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage(), "NOT_FOUND"));
        }
    }

    @GetMapping("/positions")
    @Operation(summary = "List your positions, optionally filtered by status (OPEN/CLOSED)")
    public ResponseEntity<ApiResponse<List<PortfolioPosition>>> listPositions(
            @RequestParam(required = false) String status, Authentication authentication) {
        List<PortfolioPosition> positions = portfolioService.listPositions(authentication.getName(), status);
        return ResponseEntity.ok(ApiResponse.success(positions));
    }

    @PostMapping("/positions/{id}/close")
    @Operation(summary = "Record that you sold this position on your own brokerage")
    public ResponseEntity<ApiResponse<PortfolioPosition>> closePosition(
            @PathVariable String id, @Valid @RequestBody ClosePositionRequest request,
            Authentication authentication) {
        try {
            PortfolioPosition position = portfolioService.closePosition(authentication.getName(), id, request);
            return ResponseEntity.ok(ApiResponse.success(position, "Position closed"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage(), "NOT_FOUND"));
        }
    }

    @DeleteMapping("/positions/{id}")
    @Operation(summary = "Remove a mistakenly-recorded position")
    public ResponseEntity<ApiResponse<Void>> deletePosition(
            @PathVariable String id, Authentication authentication) {
        try {
            portfolioService.deletePosition(authentication.getName(), id);
            return ResponseEntity.ok(ApiResponse.success(null, "Position removed"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage(), "NOT_FOUND"));
        }
    }

    @GetMapping("/alerts")
    @Operation(summary = "List your portfolio alerts (target hit / stop-loss hit / signal deteriorated)")
    public ResponseEntity<ApiResponse<List<PortfolioAlert>>> listAlerts(
            @RequestParam(defaultValue = "false") boolean unacknowledgedOnly,
            Authentication authentication) {
        List<PortfolioAlert> alerts = portfolioService.listAlerts(authentication.getName(), unacknowledgedOnly);
        return ResponseEntity.ok(ApiResponse.success(alerts));
    }

    @PostMapping("/alerts/{id}/acknowledge")
    @Operation(summary = "Acknowledge (dismiss) an alert")
    public ResponseEntity<ApiResponse<PortfolioAlert>> acknowledgeAlert(
            @PathVariable String id, Authentication authentication) {
        try {
            PortfolioAlert alert = portfolioService.acknowledgeAlert(authentication.getName(), id);
            return ResponseEntity.ok(ApiResponse.success(alert));
        } catch (NoSuchElementException e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage(), "NOT_FOUND"));
        }
    }
}
