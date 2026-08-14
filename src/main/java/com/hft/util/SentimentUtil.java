package com.hft.util;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Keyword-based sentiment scoring utility.
 *
 * Uses a financial-domain lexicon with weighted positive/negative terms.
 * Score range: -1.0 (very bearish) to +1.0 (very bullish).
 *
 * This is Phase-1 baseline. Phase-2 will integrate full NLP (OpenNLP/Stanford).
 */
@Component
public class SentimentUtil {

    // ─── Positive (Bullish) Keywords with Weights ─────────────────────────────
    private static final Map<String, Double> POSITIVE_WORDS = Map.ofEntries(
        Map.entry("beat", 0.8),         // earnings beat
        Map.entry("exceeded", 0.8),
        Map.entry("outperform", 0.9),
        Map.entry("upgrade", 0.9),
        Map.entry("strong", 0.6),
        Map.entry("record", 0.7),
        Map.entry("surge", 0.8),
        Map.entry("rally", 0.7),
        Map.entry("bullish", 0.9),
        Map.entry("buy", 0.7),
        Map.entry("growth", 0.6),
        Map.entry("profit", 0.6),
        Map.entry("gain", 0.6),
        Map.entry("rise", 0.5),
        Map.entry("positive", 0.5),
        Map.entry("recovery", 0.6),
        Map.entry("breakout", 0.8),
        Map.entry("expansion", 0.6),
        Map.entry("dividend", 0.5),
        Map.entry("buyback", 0.6),
        Map.entry("innovation", 0.5),
        Map.entry("deal", 0.6),
        Map.entry("acquisition", 0.5),
        Map.entry("partnership", 0.5),
        Map.entry("contract", 0.6),
        Map.entry("approval", 0.7),     // FDA/regulatory approvals
        Map.entry("launch", 0.6),
        Map.entry("momentum", 0.6),
        Map.entry("oversold", 0.7),
        Map.entry("undervalued", 0.7)
    );

    // ─── Negative (Bearish) Keywords with Weights ─────────────────────────────
    private static final Map<String, Double> NEGATIVE_WORDS = Map.ofEntries(
        Map.entry("miss", 0.8),          // earnings miss
        Map.entry("missed", 0.8),
        Map.entry("downgrade", 0.9),
        Map.entry("underperform", 0.9),
        Map.entry("sell", 0.7),
        Map.entry("bearish", 0.9),
        Map.entry("crash", 0.9),
        Map.entry("plunge", 0.8),
        Map.entry("drop", 0.6),
        Map.entry("fall", 0.5),
        Map.entry("decline", 0.6),
        Map.entry("weak", 0.6),
        Map.entry("loss", 0.7),
        Map.entry("lawsuit", 0.7),
        Map.entry("fraud", 0.9),
        Map.entry("investigation", 0.7),
        Map.entry("recall", 0.7),
        Map.entry("layoffs", 0.6),
        Map.entry("bankruptcy", 0.9),
        Map.entry("default", 0.8),
        Map.entry("warning", 0.6),
        Map.entry("concern", 0.5),
        Map.entry("risk", 0.4),
        Map.entry("overbought", 0.6),
        Map.entry("overvalued", 0.6),
        Map.entry("inflation", 0.4),
        Map.entry("recession", 0.7),
        Map.entry("tariff", 0.5),
        Map.entry("sanction", 0.6),
        Map.entry("tension", 0.5)
    );

    // ─── Amplifiers & Negators ────────────────────────────────────────────────
    private static final Set<String> AMPLIFIERS  = Set.of("very", "highly", "extremely",
                                                           "significantly", "massively", "record");
    private static final Set<String> NEGATORS    = Set.of("not", "no", "never", "don't",
                                                           "didn't", "won't", "cannot", "hardly");

    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z]+");

    /**
     * Score a text snippet for financial sentiment.
     * @param text raw news headline or social media post
     * @return sentiment score: -1.0 (very bearish) to +1.0 (very bullish)
     */
    public double score(String text) {
        if (text == null || text.isBlank()) return 0.0;
        String[] words = text.toLowerCase().split("\\s+");

        double positiveScore = 0.0;
        double negativeScore = 0.0;
        boolean negate = false;
        double amplify = 1.0;

        for (String word : words) {
            // Strip punctuation
            String clean = word.replaceAll("[^a-zA-Z]", "");
            if (clean.isEmpty()) continue;

            if (NEGATORS.contains(clean)) {
                negate = true;
                continue;
            }
            if (AMPLIFIERS.contains(clean)) {
                amplify = 1.5;
                continue;
            }

            Double posWeight = POSITIVE_WORDS.get(clean);
            Double negWeight = NEGATIVE_WORDS.get(clean);

            if (posWeight != null) {
                double adjusted = posWeight * amplify;
                if (negate) negativeScore += adjusted;
                else        positiveScore += adjusted;
            } else if (negWeight != null) {
                double adjusted = negWeight * amplify;
                if (negate) positiveScore += adjusted;
                else        negativeScore += adjusted;
            }
            // Reset modifiers after each scored word
            negate = false;
            amplify = 1.0;
        }

        double total = positiveScore + negativeScore;
        if (total == 0) return 0.0;

        double raw = (positiveScore - negativeScore) / total;
        return Math.max(-1.0, Math.min(1.0, raw));  // clamp to [-1, 1]
    }

    /**
     * Score a collection of texts and return weighted average.
     * More recent items get higher weight.
     */
    public double aggregateScore(List<String> texts) {
        if (texts == null || texts.isEmpty()) return 0.0;
        int n = texts.size();
        double weightedSum = 0;
        double totalWeight = 0;
        for (int i = 0; i < n; i++) {
            double weight = 1.0 + (double) i / n;  // newer items weighted higher
            weightedSum += score(texts.get(i)) * weight;
            totalWeight += weight;
        }
        return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
    }

    /**
     * Extract stock ticker mentions from text.
     * Looks for $SYMBOL or known patterns.
     */
    public List<String> extractTickers(String text) {
        if (text == null) return Collections.emptyList();
        List<String> tickers = new ArrayList<>();
        // Match $TICKER patterns
        Pattern tickerPattern = Pattern.compile("\\$([A-Z]{1,5})");
        var matcher = tickerPattern.matcher(text.toUpperCase());
        while (matcher.find()) {
            tickers.add(matcher.group(1));
        }
        return tickers;
    }

    /**
     * Determine if a text represents a "trending" or "viral" event.
     * Used to boost confidence on high-social-volume stocks.
     */
    public boolean isTrendingText(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("trending") || lower.contains("viral") ||
               lower.contains("breaking") || lower.contains("alert") ||
               lower.contains("urgent") || lower.contains("huge news");
    }
}