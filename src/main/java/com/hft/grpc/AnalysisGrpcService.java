package com.hft.grpc;

import com.hft.grpc.proto.*;
import com.hft.model.enums.Market;
import com.hft.service.analysis.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * gRPC service for technical, sentiment, fundamental, and macro analysis.
 * Exposes the same Spring services already wired to the GraphQL layer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisGrpcService extends AnalysisServiceGrpc.AnalysisServiceImplBase {

    private final TechnicalAnalysisService   taService;
    private final SentimentAnalysisService   sentimentService;
    private final FundamentalAnalysisService fundamentalService;
    private final MacroGeopoliticalService   macroService;

    @Override
    public void getTechnical(AnalysisRequest request, StreamObserver<TechnicalIndicatorsProto> responseObserver) {
        try {
            Market market = ProtoMapper.fromProto(request.getMarket());
            var result = taService.analyze(request.getSymbol(), market);
            if (result.isEmpty()) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("No technical data for " + request.getSymbol())
                        .asRuntimeException());
                return;
            }
            responseObserver.onNext(ProtoMapper.toProto(result.get()));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[gRPC] getTechnical failed for {}: {}", request.getSymbol(), e.getMessage());
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    @Override
    public void getSentiment(AnalysisRequest request, StreamObserver<SentimentDataProto> responseObserver) {
        try {
            Market market = ProtoMapper.fromProto(request.getMarket());
            var result = sentimentService.analyzeSentiment(request.getSymbol(), market);
            responseObserver.onNext(ProtoMapper.toProto(result));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[gRPC] getSentiment failed for {}: {}", request.getSymbol(), e.getMessage());
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    @Override
    public void getFundamentals(AnalysisRequest request, StreamObserver<FundamentalDataProto> responseObserver) {
        try {
            Market market = ProtoMapper.fromProto(request.getMarket());
            var result = fundamentalService.analyze(request.getSymbol(), market);
            if (result.isEmpty()) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("No fundamental data for " + request.getSymbol())
                        .asRuntimeException());
                return;
            }
            responseObserver.onNext(ProtoMapper.toProto(result.get()));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[gRPC] getFundamentals failed for {}: {}", request.getSymbol(), e.getMessage());
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    @Override
    public void getMacro(MarketRequest request, StreamObserver<MacroDataProto> responseObserver) {
        try {
            Market market = ProtoMapper.fromProto(request.getMarket());
            var result = macroService.getMacroData(market);
            responseObserver.onNext(ProtoMapper.toProto(result));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("[gRPC] getMacro failed for {}: {}", request.getMarket(), e.getMessage());
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }
}
