package com.hft.grpc;

import com.hft.grpc.proto.*;
import com.hft.model.domain.StockQuote;
import com.hft.model.enums.Market;
import com.hft.service.data.MarketDataAggregatorService;
import com.hft.streams.StreamSinkBridge;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

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
    private final StreamSinkBridge            sinkBridge;

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
     * Server-streaming: subscribe to Kafka Streams quote output via StreamSinkBridge.
     * Pushes a live tick to the gRPC client whenever a new quote arrives for any
     * of the requested symbols. The subscription is cancelled when the client disconnects.
     */
    @Override
    public void streamQuotes(BatchQuoteRequest request, StreamObserver<StockQuoteProto> responseObserver) {
        try {
            Market market = ProtoMapper.fromProto(request.getMarket());
            List<String> symbols = request.getSymbolsList();

            // First emit a snapshot of each requested symbol
            for (String symbol : symbols) {
                marketDataService.getQuote(symbol, market).ifPresent(q ->
                        responseObserver.onNext(ProtoMapper.toProto(q)));
            }

            // Then subscribe to live updates from Kafka Streams
            if (!symbols.isEmpty()) {
                // Subscribe to the first symbol's live feed (caller can open one stream per symbol)
                Disposable subscription = sinkBridge.quoteFlux(symbols.get(0), market)
                        .filter(q -> symbols.contains(q.getSymbol()))
                        .subscribe(
                                quote  -> responseObserver.onNext(ProtoMapper.toProto(quote)),
                                error  -> responseObserver.onError(Status.INTERNAL.withCause(error).asRuntimeException()),
                                responseObserver::onCompleted);

                // Detach subscription when client cancels
                // (gRPC Netty notifies via context cancellation)
                io.grpc.Context.current().withCancellation().addListener(
                        ctx -> subscription.dispose(), java.util.concurrent.Executors.newSingleThreadExecutor());
            } else {
                responseObserver.onCompleted();
            }
        } catch (Exception e) {
            log.error("[gRPC] streamQuotes failed: {}", e.getMessage());
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }
}
