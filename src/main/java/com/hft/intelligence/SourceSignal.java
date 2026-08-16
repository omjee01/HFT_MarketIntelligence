package com.hft.intelligence;

import java.time.Instant;

/**
 * One piece of evidence from one information source, for one scoring pass.
 * context matches com.hft.ml.MLFeatureVector's 41 double fields, in declaration order —
 * ASRB does not invent a separate feature representation (ASRB_TECHNICAL_DISCLOSURE.md §4.1).
 */
public record SourceSignal(
        String sourceId,
        double score,            // 0-100
        String claimClusterId,   // topic+assertion grouping key, for corroboration counting
        Instant asOf,
        double[] context         // 41-dim
) {
    public static final int CONTEXT_DIM = 41;

    public SourceSignal {
        if (context == null || context.length != CONTEXT_DIM) {
            throw new IllegalArgumentException("context must be " + CONTEXT_DIM + "-dimensional, got "
                    + (context == null ? "null" : context.length));
        }
    }
}
