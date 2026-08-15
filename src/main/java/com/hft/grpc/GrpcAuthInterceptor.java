package com.hft.grpc;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * gRPC server interceptor that validates the JWT from the Authorization metadata header.
 *
 * Enabled via: grpc.server.auth.enabled=true (default false — plaintext dev mode).
 * Token format: "Authorization: Bearer <jwt_token>"
 *
 * On success, the decoded JWT is stored in the gRPC Context so downstream service
 * handlers can read the caller's identity via JWT_CTX_KEY.get().
 *
 * On failure, the call is closed with UNAUTHENTICATED before reaching the service.
 */
@Slf4j
@Component
public class GrpcAuthInterceptor implements ServerInterceptor {

    /** Context key for the validated JWT — readable inside any gRPC service handler. */
    public static final Context.Key<DecodedJWT> JWT_CTX_KEY = Context.key("hft-jwt");

    private static final Metadata.Key<String> AUTH_HEADER =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final JWTVerifier verifier;
    private final boolean     authEnabled;

    public GrpcAuthInterceptor(
            @Value("${hft.jwt.secret}") String secret,
            @Value("${grpc.server.auth.enabled:false}") boolean authEnabled) {
        this.verifier    = JWT.require(Algorithm.HMAC256(secret)).build();
        this.authEnabled = authEnabled;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        if (!authEnabled) {
            return next.startCall(call, headers);
        }

        String authHeader = headers.get(AUTH_HEADER);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            call.close(
                    Status.UNAUTHENTICATED.withDescription("Missing or malformed Authorization header"),
                    new Metadata());
            return new ServerCall.Listener<>() {};
        }

        try {
            DecodedJWT jwt = verifier.verify(authHeader.substring(7));
            Context ctx = Context.current().withValue(JWT_CTX_KEY, jwt);
            return Contexts.interceptCall(ctx, call, headers, next);
        } catch (JWTVerificationException e) {
            log.warn("[gRPC] Auth rejected for method {}: {}", call.getMethodDescriptor().getFullMethodName(), e.getMessage());
            call.close(
                    Status.UNAUTHENTICATED.withDescription("JWT validation failed: " + e.getMessage()),
                    new Metadata());
            return new ServerCall.Listener<>() {};
        }
    }
}