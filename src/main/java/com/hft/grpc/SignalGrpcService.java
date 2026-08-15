package com.hft.grpc;

import com.hft.grpc.proto.*;
import com.hft.model.enums.Market;
import com.hft.service.signal.RecommendationEngine;
import com.hft.streams.StreamSinkBridge;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

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
    private final StreamSinkBridge     sinkBridge;

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
     * Server-streaming: subscribe to enriched signals from Kafka Streams via StreamSinkBridge.
     * Emits a snapshot recommendation first, then pushes live enriched signals as they arrive.
     */
    @Override
    public void streamSignals(RecommendationRequest request,
                              StreamObserver<TradeRecommendationProto> responseObserver) {
        try {
            Market market = ProtoMapper.fromProto(request.getMarket());
            String symbol = request.getSymbol();

            // Snapshot: emit the current recommendation immediately
            engine.generateRecommendation(symbol, market)
                    .ifPresent(r -> responseObserver.onNext(ProtoMapper.toProto(r)));

            // Live: subscribe to enriched signals for this symbol from Kafka Streams
            Disposable subscription = sinkBridge.signalFlux(market)
                    .filter(r -> symbol == null || symbol.isBlank() || symbol.equals(r.getSymbol()))
                    .subscribe(
                            signal -> responseObserver.onNext(ProtoMapper.toProto(signal)),
                            error  -> responseObserver.onError(Status.INTERNAL.withCause(error).asRuntimeException()),
                            responseObserver::onCompleted);

            io.grpc.Context.current().withCancellation().addListener(
                    ctx -> subscription.dispose(), java.util.concurrent.Executors.newSingleThreadExecutor());
        } catch (Exception e) {
            log.error("[gRPC] streamSignals failed for {}: {}", request.getSymbol(), e.getMessage());
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }
}
