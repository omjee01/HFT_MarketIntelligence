package com.hft.graphql;

import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * GraphQL query safety limits — prevents deeply nested or overly complex queries
 * from exhausting server resources (DoS protection).
 *
 * Limits are configurable via application properties:
 *   hft.graphql.max-query-depth:       10  (default)
 *   hft.graphql.max-query-complexity: 200  (default)
 *
 * Complexity scoring: each field = 1. List fields should be annotated in schema
 * with @complexity(value: N) directives if custom scores are needed.
 *
 * A query that exceeds either limit is rejected before execution with a
 * "query complexity/depth exceeded" error — no service calls are made.
 */
@Configuration
public class GraphQLInstrumentationConfig {

    @Value("${hft.graphql.max-query-depth:10}")
    private int maxDepth;

    @Value("${hft.graphql.max-query-complexity:200}")
    private int maxComplexity;

    @Bean
    public GraphQlSourceBuilderCustomizer graphQlInstrumentationCustomizer() {
        return builder -> builder.configureGraphQl(gql -> gql
                .instrumentation(new MaxQueryDepthInstrumentation(maxDepth))
                .instrumentation(new MaxQueryComplexityInstrumentation(maxComplexity)));
    }
}