package com.hft.model.domain;

import com.hft.model.enums.Market;
import com.hft.model.enums.SentimentLabel;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Aggregated sentiment data for a ticker from news + social media sources.
 * Refreshed every 15 minutes during market hours.
 */
@Entity
@Table(name = "sentiment_data",
       indexes = {
           @Index(name = "idx_sent_symbol", columnList = "symbol"),
           @Index(name = "idx_sent_computed", columnList = "computed_at")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Market market;

    // ─── Aggregate Scores (-1.0 to +1.0) ─────────────────────────────────────
    private Double overallSentimentScore;    // weighted aggregate
    private Double newsSentimentScore;       // news headlines NLP
    private Double socialSentimentScore;     // social media NLP

    @Enumerated(EnumType.STRING)
    private SentimentLabel sentimentLabel;   // VERY_BULLISH … VERY_BEARISH

    // ─── Volume Metrics ───────────────────────────────────────────────────────
    private Integer totalMentions24h;
    private Integer twitterMentions24h;
    private Integer redditMentions24h;
    private Integer stockTwitsMentions24h;
    private Integer newsArticles24h;

    @Column(length = 20)
    private String mentionTrend;             // RISING / FALLING / STABLE

    private Boolean isTrending;             // in top 20 social mentions today

    // ─── Breakdown ────────────────────────────────────────────────────────────
    private Integer positiveNewsCount;
    private Integer negativeNewsCount;
    private Integer neutralNewsCount;

    // ─── Key Content ──────────────────────────────────────────────────────────
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sentiment_headlines",
                     joinColumns = @JoinColumn(name = "sentiment_id"))
    @Column(name = "headline", length = 400)
    private List<String> keyHeadlines;    // top 5 news headlines

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sentiment_tweets",
                     joinColumns = @JoinColumn(name = "sentiment_id"))
    @Column(name = "tweet", length = 500)
    private List<String> topTweets;       // top 3 influential tweets

    // ─── Institutional & Political Signals ───────────────────────────────────
    private Boolean hasFedMention;        // US: Fed/Powell mentioned
    private Boolean hasRbiMention;        // India: RBI/Governor mentioned
    private Boolean hasPoliticalRisk;     // political event flag

    @Column(length = 300)
    private String specialAlert;          // e.g. "Insider selling detected"

    // ─── Metadata ─────────────────────────────────────────────────────────────
    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

    @Column(length = 20)
    private String windowPeriod;          // "24H", "1H", "7D"

    // ─── Helpers ──────────────────────────────────────────────────────────────
    public boolean isBullish()  { return overallSentimentScore != null && overallSentimentScore > 0.2; }
    public boolean isBearish()  { return overallSentimentScore != null && overallSentimentScore < -0.2; }
    public boolean isNeutral()  { return !isBullish() && !isBearish(); }

    /** Convert -1..+1 score to 0..100 for composite scoring */
    public double getNormalizedScore() {
        if (overallSentimentScore == null) return 50.0;
        return (overallSentimentScore + 1.0) / 2.0 * 100.0;
    }
}