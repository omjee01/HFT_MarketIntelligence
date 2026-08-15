package com.hft.grpc;

import com.hft.grpc.proto.*;
import com.hft.model.domain.StockQuote;
import com.hft.model.enums.Market;
import com.hft.service.data.MarketDataAggregatorService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * gRPC service implementation for real-time market data.
 * Delegates to the existing Spring service layer — no business logic duplication.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataGrpcService extends MarketDataServiceGrpc.MarketDataServiceImplBase {

    private final MarketDataAggregatorService marketDataService;

    @Override
    public void getQuote(QuoteRequest request, StreamObserver<StockQuoteProto> responseObserver) {
        try {
            Market market = ProtoMapper.fromProto(request.getMarket());
            StockQuote quote = marketDataService.getQuote(request.getSymbol(), market).orElse(null);
            if (quote == null) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("No quote for " + request.getSymbol())
                        .asRuntimeException());
                return;
            }
            responseObserver.onNext(ProtoMapper.toProto(quote));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[gRPC] getQuote failed for {}: {}", request.getSymbol(), e.getMessage());
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    @Override
    public void getBatchQuotes(BatchQuoteRequest request, StreamObserver<BatchQuoteResponse> responseObserver) {
        try {
            Market market = ProtoMapper.fromProto(request.getMarket());
            List<StockQuoteProto> protos = request.getSymbolsList().stream()
                    .map(symbol -> marketDataService.getQuote(symbol, market).orElse(null))
                    .filter(q -> q != null)
                    .map(ProtoMapper::toProto)
                    .toList();
            responseObserver.onNext(BatchQuoteResponse.newBuilder().addAllQuotes(protos).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[gRPC] getBatchQuotes failed: {}", e.getMessage());
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    /**
     * Server-streaming: continuously push updated quotes for the requested symbols.
     * In this stage, we do a single fetch per symbol. A full live-streaming
     * implementation (Stage 3+) will subscribe to Kafka and push on each tick.
     */
    @Override
    public void streamQuotes(BatchQuoteRequest request, StreamObserver<StockQuoteProto> responseObserver) {
        try {
            Market market = ProtoMapper.fromProto(request.getMarket());
            for (String symbol : request.getSymbolsList()) {
                marketDataService.getQuote(symbol, market).ifPresent(q ->
                        responseObserver.onNext(ProtoMapper.toProto(q)));
            }
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[gRPC] streamQuotes failed: {}", e.getMessage());
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }
}
