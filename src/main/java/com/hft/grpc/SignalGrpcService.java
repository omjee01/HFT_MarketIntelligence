package com.hft.grpc;

import com.hft.grpc.proto.*;
import com.hft.model.enums.Market;
import com.hft.service.signal.RecommendationEngine;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * gRPC service for trade signals and screener.
 * StreamSignals currently does a single-shot emit; in Stage 3 it will become
 * a true Kafka-backed server-streaming endpoint via Reactor Sinks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignalGrpcService extends SignalServiceGrpc.SignalServiceImplBase {

    private final RecommendationEngine engine;

    @Override
    public void getRecommendation(RecommendationRequest request,
                                  StreamObserver<TradeRecommendationProto> responseObserver) {
        try {
            Market market = ProtoMapper.fromProto(request.getMarket());
            var result = engine.generateRecommendation(request.getSymbol(), market);
            if (result.isEmpty()) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("No recommendation for " + request.getSymbol())
                        .asRuntimeException());
                return;
            }
            responseObserver.onNext(ProtoMapper.toProto(result.get()));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[gRPC] getRecommendation failed for {}: {}", request.getSymbol(), e.getMessage());
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    @Override
    public void screenStocks(ScreenerRequest request, StreamObserver<ScreenerResponse> responseObserver) {
        try {
            Market market = ProtoMapper.fromProto(request.getMarket());
            int limit = request.getLimit() > 0 ? request.getLimit() : 10;

            List<TradeRecommendationProto> protos = engine.generateTopRecommendations(market, limit)
                    .stream()
                    // Apply optional client-side filters from ScreenerRequest
                    .filter(r -> request.getMinConfidence() <= 0
                            || r.getConfidencePercent() >= request.getMinConfidence())
                    .filter(r -> request.getMinTechnicalScore() <= 0
                            || r.getTechnicalScore() >= request.getMinTechnicalScore())
                    .filter(r -> request.getMinFundamentalScore() <= 0
                            || r.getFundamentalScore() >= request.getMinFundamentalScore())
                    .filter(r -> request.getSectorsCount() == 0
                            || request.getSectorsList().contains(r.getSector()))
                    .map(ProtoMapper::toProto)
                    .toList();

            responseObserver.onNext(ScreenerResponse.newBuilder()
                    .addAllRecommendations(protos)
                    .setTotalCount(protos.size())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[gRPC] screenStocks failed: {}", e.getMessage());
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    /**
     * Single-shot server-streaming for a specific symbol's recommendation.
     * Stage 3 will wire this to Kafka Sinks for real-time streaming.
     */
    @Override
    public void streamSignals(RecommendationRequest request,
                              StreamObserver<TradeRecommendationProto> responseObserver) {
        try {
            Market market = ProtoMapper.fromProto(request.getMarket());
            engine.generateRecommendation(request.getSymbol(), market)
                    .ifPresent(r -> responseObserver.onNext(ProtoMapper.toProto(r)));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[gRPC] streamSignals failed for {}: {}", request.getSymbol(), e.getMessage());
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }
}
