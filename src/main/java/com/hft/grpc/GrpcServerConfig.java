package com.hft.grpc;

import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Boots and gracefully shuts down the gRPC server as a Spring SmartLifecycle bean.
 * The server runs on a separate port from the HTTP API (default 9090) so REST,
 * GraphQL (8080), and gRPC (9090) are independently addressable.
 *
 * Stage 4 additions:
 *  - JWT auth interceptor (enabled via grpc.server.auth.enabled=true)
 *  - TLS support (enabled via grpc.server.tls.enabled=true with cert + key paths)
 */
@Slf4j
@Configuration
public class GrpcServerConfig implements SmartLifecycle {

    @Value("${grpc.server.port:9090}")
    private int grpcPort;

    @Value("${grpc.server.tls.enabled:false}")
    private boolean tlsEnabled;

    @Value("${grpc.server.tls.cert-file:}")
    private String tlsCertFile;

    @Value("${grpc.server.tls.key-file:}")
    private String tlsKeyFile;

    private final MarketDataGrpcService marketDataService;
    private final AnalysisGrpcService   analysisService;
    private final SignalGrpcService     signalService;
    private final GrpcAuthInterceptor   authInterceptor;

    private Server server;
    private volatile boolean running = false;

    public GrpcServerConfig(MarketDataGrpcService marketDataService,
                             AnalysisGrpcService analysisService,
                             SignalGrpcService signalService,
                             GrpcAuthInterceptor authInterceptor) {
        this.marketDataService = marketDataService;
        this.analysisService   = analysisService;
        this.signalService     = signalService;
        this.authInterceptor   = authInterceptor;
    }

    @Override
    public void start() {
        try {
            NettyServerBuilder serverBuilder = NettyServerBuilder.forPort(grpcPort)
                    .addService(ServerInterceptors.intercept(marketDataService, authInterceptor))
                    .addService(ServerInterceptors.intercept(analysisService, authInterceptor))
                    .addService(ServerInterceptors.intercept(signalService, authInterceptor))
                    .maxInboundMessageSize(64 * 1024 * 1024);   // 64 MB

            if (tlsEnabled && tlsCertFile != null && !tlsCertFile.isBlank()
                    && tlsKeyFile != null && !tlsKeyFile.isBlank()) {
                serverBuilder.useTransportSecurity(new File(tlsCertFile), new File(tlsKeyFile));
                log.info("[gRPC] TLS enabled — cert={}", tlsCertFile);
            } else {
                log.warn("[gRPC] Running in PLAINTEXT mode — enable TLS for production (grpc.server.tls.enabled=true)");
            }

            server = serverBuilder.build().start();
            running = true;
            log.info("[gRPC] Server started on port {}", grpcPort);
        } catch (IOException e) {
            log.error("[gRPC] Failed to start server on port {}: {}", grpcPort, e.getMessage(), e);
            throw new IllegalStateException("gRPC server failed to start", e);
        }
    }

    @Override
    @PreDestroy
    public void stop() {
        if (server != null && !server.isShutdown()) {
            log.info("[gRPC] Shutting down server on port {}", grpcPort);
            try {
                server.shutdown().awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                server.shutdownNow();
            }
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    // Start after all Spring beans are ready but before HTTP traffic is accepted.
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }
}