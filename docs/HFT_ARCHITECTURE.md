# HFT Market Intelligence Platform (HMIP)
## Complete Architecture, Design & Plan of Action

> **Version:** 1.0.0 | **Date:** 2026-05-28 | **Author:** PTD2315
> **Scope:** US Markets (NYSE, NASDAQ, AMEX) + Indian Markets (NSE, BSE)

---

## TABLE OF CONTENTS

1. [Executive Summary](#1-executive-summary)
2. [Business Requirements](#2-business-requirements)
3. [Functional Requirements (FR)](#3-functional-requirements)
4. [Non-Functional Requirements (NFR)](#4-non-functional-requirements)
5. [System Architecture Overview](#5-system-architecture-overview)
6. [Microservices Breakdown](#6-microservices-breakdown)
7. [Architectural Flow Diagram](#7-architectural-flow-diagram)
8. [Functional Flow Diagrams](#8-functional-flow-diagrams)
9. [Data Flow Diagram (DFD)](#9-data-flow-diagram)
10. [Load Flow & Capacity Planning](#10-load-flow--capacity-planning)
11. [Domain Models & Entity Design](#11-domain-models--entity-design)
12. [API Design (REST Contracts)](#12-api-design-rest-contracts)
13. [Database Design](#13-database-design)
14. [Technology Stack](#14-technology-stack)
15. [External Integrations & Data Sources](#15-external-integrations--data-sources)
16. [Analysis Algorithms & ML Pipeline](#16-analysis-algorithms--ml-pipeline)
17. [Risk Management Framework](#17-risk-management-framework)
18. [Security Architecture](#18-security-architecture)
19. [Deployment Architecture](#19-deployment-architecture)
20. [Plan of Action (Sprint-wise)](#20-plan-of-action-sprint-wise)
21. [Requirement Traceability Matrix](#21-requirement-traceability-matrix)
22. [IPO Buy/Sell Decision Engine](#22-ipo-buysell-decision-engine)
23. [Web UI Architecture](#23-web-ui-architecture)
24. [Intelligence Sourcing & Adaptive Fusion (ASRB)](#24-intelligence-sourcing--adaptive-fusion-asrb)
25. [Identity, Admin & Billing Platform](#25-identity-admin--billing-platform)

---

## 1. EXECUTIVE SUMMARY

The **HFT Market Intelligence Platform (HMIP)** is an enterprise-grade, AI-powered financial decision-support system designed to give retail and institutional investors a data-backed edge in both US and Indian markets.

### What It Does
| Capability | Description |
|---|---|
| 📊 Market Research | Real-time + historical price analysis across stocks, options, commodities, IPOs |
| 📱 Social Media Intelligence | Sentiment mining from Twitter/X, Reddit, StockTwits, Telegram |
| 🌍 Geopolitical Analysis | US-India trade relations, Fed/RBI policy, election cycles, sanctions, wars |
| 🤖 AI Prediction Engine | ML ensemble model predicting price targets with confidence intervals |
| 💡 Trade Recommendations | BUY/SELL/HOLD with entry price, exit price, profit %, entry date, exit date |
| ⚡ HFT Signals | Sub-second signal generation for algorithmic execution |
| 📈 Portfolio Tracker | Track P&L, positions, risk exposure in real time |

### Target Assets
- **Equities:** US Stocks (NYSE, NASDAQ, AMEX) + Indian Stocks (NSE, BSE)
- **Derivatives:** Options (US: CBOE) + F&O (India: NSE)
- **Commodities:** Gold, Silver, Crude Oil, Natural Gas, Agricultural (MCX India + COMEX US)
- **IPOs:** Pre-IPO analysis + subscription recommendations

---

## 2. BUSINESS REQUIREMENTS

### BR-001: Multi-Market Coverage
The system MUST support simultaneous analysis of both US markets (NYSE/NASDAQ) and Indian markets (NSE/BSE) with proper timezone handling.

### BR-002: Multi-Asset Class Support
The system MUST support Stocks, Options, Commodities, and IPOs with asset-class-specific analysis models.

### BR-003: Data-Backed Recommendations
Every recommendation MUST include:
- Data sources used
- Confidence score (0–100%)
- Risk level (LOW / MEDIUM / HIGH / VERY_HIGH)
- Entry price + entry date
- Target price + target date
- Expected profit percentage
- Stop-loss price

### BR-004: Real-Time + Batch Processing
- Real-time signals: latency < 500ms for HFT signals
- Batch analysis: daily/weekly comprehensive reports

### BR-005: Multi-Source Intelligence
The system MUST aggregate:
- Market data (price, volume, OI)
- News (financial + general)
- Social media (retail sentiment)
- Macro data (GDP, CPI, interest rates)
- Geopolitical events

---

## 3. FUNCTIONAL REQUIREMENTS

### 3.1 Data Ingestion
| ID | Requirement | Priority |
|---|---|---|
| FR-DI-001 | Ingest real-time stock quotes from US markets (Yahoo Finance / Alpha Vantage) | P0 |
| FR-DI-002 | Ingest real-time stock quotes from NSE/BSE (NSE API / Zerodha Kite) | P0 |
| FR-DI-003 | Ingest options chain data (CBOE for US, NSE for India) | P1 |
| FR-DI-004 | Ingest commodity prices (COMEX, MCX) | P1 |
| FR-DI-005 | Ingest upcoming IPO data (SEC EDGAR for US, SEBI for India) | P2 |
| FR-DI-006 | Ingest historical OHLCV data (5yr lookback minimum) | P0 |
| FR-DI-007 | Ingest news headlines from NewsAPI, Reuters, Bloomberg | P0 |
| FR-DI-008 | Ingest social media posts from Twitter/X, Reddit (r/investing, r/wallstreetbets, r/IndiaInvestments) | P1 |
| FR-DI-009 | Ingest macroeconomic data (FRED API for US, MOSPI for India) | P1 |
| FR-DI-010 | Ingest geopolitical event feeds | P2 |

### 3.2 Technical Analysis Engine
| ID | Requirement | Priority |
|---|---|---|
| FR-TA-001 | Compute Moving Averages: SMA(20,50,200), EMA(9,21,55) | P0 |
| FR-TA-002 | Compute Momentum: RSI(14), MACD(12,26,9), Stochastic | P0 |
| FR-TA-003 | Compute Volatility: Bollinger Bands, ATR, Historical Volatility | P0 |
| FR-TA-004 | Compute Volume: OBV, VWAP, Volume Profile | P0 |
| FR-TA-005 | Detect Chart Patterns: Head & Shoulders, Cup & Handle, Triangles, Double Top/Bottom | P1 |
| FR-TA-006 | Detect Candlestick Patterns: Doji, Hammer, Engulfing, Morning/Evening Star | P1 |
| FR-TA-007 | Support/Resistance Level Detection | P1 |
| FR-TA-008 | Fibonacci Retracement Levels | P2 |
| FR-TA-009 | Multi-timeframe analysis (1m, 5m, 15m, 1h, 4h, 1D, 1W) | P1 |

### 3.3 Sentiment Analysis Engine
| ID | Requirement | Priority |
|---|---|---|
| FR-SA-001 | Analyze news headline sentiment (Positive/Negative/Neutral + score) | P0 |
| FR-SA-002 | Analyze social media sentiment per ticker | P0 |
| FR-SA-003 | Track sentiment trend over time (hourly, daily) | P1 |
| FR-SA-004 | Detect viral/trending stocks (momentum in social mentions) | P1 |
| FR-SA-005 | Political figure mention impact analysis (e.g., Trump tweets, Modi speeches) | P2 |
| FR-SA-006 | Earnings call transcript sentiment analysis | P2 |

### 3.4 Fundamental Analysis Engine
| ID | Requirement | Priority |
|---|---|---|
| FR-FA-001 | P/E Ratio, P/B Ratio, EV/EBITDA analysis | P0 |
| FR-FA-002 | Revenue growth, EPS growth, margin trends | P0 |
| FR-FA-003 | Debt-to-equity, current ratio, quick ratio | P1 |
| FR-FA-004 | Dividend yield and payout ratio tracking | P1 |
| FR-FA-005 | Insider buying/selling detection | P2 |
| FR-FA-006 | Institutional holding changes (13F filings for US) | P2 |
| FR-FA-007 | Peer comparison / sector benchmarking | P1 |

### 3.5 Macroeconomic & Geopolitical Engine
| ID | Requirement | Priority |
|---|---|---|
| FR-GE-001 | Fed interest rate decisions → impact on US equities | P0 |
| FR-GE-002 | RBI interest rate decisions → impact on Indian equities | P0 |
| FR-GE-003 | Inflation (CPI/WPI) tracking for both economies | P1 |
| FR-GE-004 | Currency pair tracking: USD/INR, DXY | P1 |
| FR-GE-005 | FII/DII flow tracking for Indian markets | P1 |
| FR-GE-006 | Election cycle analysis (US Presidential, Indian Lok Sabha/State) | P2 |
| FR-GE-007 | Trade war / sanctions impact modeling | P2 |
| FR-GE-008 | Geopolitical risk index (wars, treaties, natural disasters) | P2 |
| FR-GE-009 | Oil price → sector correlation analysis | P1 |

### 3.6 ML Prediction Engine
| ID | Requirement | Priority |
|---|---|---|
| FR-ML-001 | Price direction prediction (UP/DOWN/SIDEWAYS) for next 1D, 7D, 30D | P0 |
| FR-ML-002 | Price target prediction with confidence intervals (±%) | P0 |
| FR-ML-003 | Optimal entry date prediction based on technical patterns | P1 |
| FR-ML-004 | Optimal exit date prediction based on target/stop-loss | P1 |
| FR-ML-005 | Expected profit % calculation | P0 |
| FR-ML-006 | Volatility forecasting for options pricing | P1 |
| FR-ML-007 | IPO listing gain prediction | P2 |
| FR-ML-008 | Model performance tracking (accuracy, Sharpe ratio) | P1 |

### 3.7 Signal Generation & Recommendation
| ID | Requirement | Priority |
|---|---|---|
| FR-SG-001 | Generate BUY/SELL/HOLD signals per asset | P0 |
| FR-SG-002 | Provide composite score (Technical + Fundamental + Sentiment + Macro) | P0 |
| FR-SG-003 | Generate short-term recommendations (1-7 days) | P0 |
| FR-SG-004 | Generate medium-term recommendations (1-4 weeks) | P1 |
| FR-SG-005 | Generate long-term recommendations (1-6 months) | P2 |
| FR-SG-006 | Top N recommendations per market per day | P0 |
| FR-SG-007 | Options strategy recommendations (Covered Call, Protective Put, Iron Condor, etc.) | P2 |
| FR-SG-008 | Commodity trade setups | P1 |
| FR-SG-009 | IPO subscription recommendations (Apply/Avoid) with expected listing gain | P1 |

### 3.8 Portfolio & Risk Management
| ID | Requirement | Priority |
|---|---|---|
| FR-RM-001 | Position sizing based on Kelly Criterion / Fixed Fractional | P1 |
| FR-RM-002 | Portfolio-level VaR (Value at Risk) calculation | P1 |
| FR-RM-003 | Dynamic stop-loss calculation (ATR-based) | P0 |
| FR-RM-004 | Correlation matrix across holdings | P2 |
| FR-RM-005 | Max drawdown tracking | P1 |
| FR-RM-006 | Real-time P&L tracking | P1 |

### 3.9 Notification & Alerting
| ID | Requirement | Priority |
|---|---|---|
| FR-NT-001 | Real-time price alerts (threshold-based) | P0 |
| FR-NT-002 | Signal change alerts (BUY→SELL, etc.) | P0 |
| FR-NT-003 | Earnings/results date reminders | P1 |
| FR-NT-004 | Breaking news alerts for watched tickers | P1 |
| FR-NT-005 | Daily morning briefing (top 5 opportunities) | P1 |

---

## 4. NON-FUNCTIONAL REQUIREMENTS

| Category | Requirement | Target |
|---|---|---|
| **Performance** | HFT signal generation latency | < 500ms |
| **Performance** | API response time (dashboard) | < 2 seconds |
| **Performance** | Batch analysis cycle time | < 30 minutes |
| **Scalability** | Concurrent users | 10,000+ |
| **Scalability** | Tickers monitored simultaneously | 5,000+ |
| **Availability** | System uptime | 99.9% |
| **Reliability** | Data accuracy | 99.5% |
| **Security** | Data encryption (transit + rest) | AES-256, TLS 1.3 |
| **Security** | API authentication | JWT + OAuth 2.0 |
| **Compliance** | US: SEC/FINRA guidelines | Required |
| **Compliance** | India: SEBI guidelines | Required |
| **Observability** | Distributed tracing | OpenTelemetry |
| **Resilience** | Circuit breaker for external APIs | Resilience4j |

---

## 5. SYSTEM ARCHITECTURE OVERVIEW

```
╔══════════════════════════════════════════════════════════════════════════════════════════╗
║                        HFT MARKET INTELLIGENCE PLATFORM (HMIP)                           ║
║                              HIGH-LEVEL ARCHITECTURE                                     ║
╚══════════════════════════════════════════════════════════════════════════════════════════╝

┌───────────────────────────────────────────────────────────────────────────────────────────┐
│                              CLIENT LAYER                                                 │
│  ┌────────────────┐  ┌───────────────┐  ┌──────────────┐  ┌──────────────────────────┐    │
│  │  Web Dashboard │  │ Mobile App    │  │  REST API    │  │  WebSocket Streaming     │    │
│  │  (React.js)    │  │ (React Native)│  │  Clients     │  │  (Real-time Signals)     │    │
│  └──────┬─────────┘  └──────┬────────┘  └──────┬───────┘  └───────────┬──────────────┘    │
└─────────┼───────────────────┼──────────────────┼──────────────────────┼───────────────────┘
          │                   │                  │                      │
          └───────────────────┴──────────────────┴──────────────────────┘
                                               │
                                    ┌──────────▼──────────┐
                                    │    API GATEWAY      │
                                    │  (Spring Cloud GW)  │
                                    │  - Rate Limiting    │
                                    │  - Auth (JWT/OAuth) │
                                    │  - Load Balancing   │
                                    └──────────┬──────────┘
                                               │
         ┌─────────────────────────────────────┼───────────────────────────────────────────┐
         │                    MICROSERVICES LAYER                                          │
         │                                                                                 │
         │  ┌──────────────┐  ┌──────────────┐  ┌─────────────┐  ┌─────────────────────┐   │
         │  │  Market Data │  │   Sentiment  │  │ Fundamental │  │  Macro/Geopolitical │   │
         │  │  Service     │  │   Analysis   │  │  Analysis   │  │     Service         │   │
         │  │  (US+India)  │  │   Service    │  │  Service    │  │                     │   │
         │  └──────┬───────┘  └──────┬───────┘  └──────┬──────┘  └─────────┬───────────┘   │
         │         │                 │                 │                   │               │
         │  ┌──────▼──────┐   ┌──────▼───────┐  ┌──────▼──────┐  ┌─────────▼──────────┐    │
         │  │  Technical  │   │   Social     │  │     ML      │  │   Signal           │    │
         │  │  Analysis   │   │   Media      │  │  Prediction │  │   Generator        │    │
         │  │  Service    │   │   Service    │  │  Engine     │  │   Service          │    │
         │  └──────┬──────┘   └──────┬───────┘  └──────┬──────┘  └─────────┬──────────┘    │
         │         │                 │                 │                   │               │
         │  ┌──────▼──────┐   ┌──────▼───────┐  ┌──────▼──────┐  ┌─────────▼──────────┐    │
         │  │    Risk     │   │  Portfolio   │  │   IPO       │  │  Notification      │    │
         │  │  Management │   │  Tracker     │  │  Analysis   │  │  Service           │    │
         │  │  Service    │   │  Service     │  │  Service    │  │                    │    │
         │  └─────────────┘   └──────────────┘  └─────────────┘  └────────────────────┘    │
         └─────────────────────────────────────────────────────────────────────────────────┘
                                               │
         ┌─────────────────────────────────────▼──────────────────────────────────────────┐
         │                          EVENT STREAMING LAYER                                 │
         │                   Apache Kafka (Event Bus / Message Queue)                     │
         │  Topics: market-data, signals, sentiment, news, alerts, portfolio-updates      │
         └──────────────────────────────────────┬─────────────────────────────────────────┘
                                                │
         ┌──────────────────────────────────────▼─────────────────────────────────────────┐
         │                                DATA LAYER                                      │
         │  ┌───────────────┐  ┌──────────────┐  ┌────────────────┐  ┌─────────────────┐  │
         │  │  PostgreSQL   │  │  InfluxDB    │  │   Redis Cache  │  │  Elasticsearch  │  │
         │  │  (Trades,     │  │  (Time-Series│  │  (Real-time    │  │  (News Search,  │  │
         │  │   Portfolio,  │  │   OHLCV,     │  │   Quotes,      │  │   Sentiment     │  │
         │  │   Users)      │  │   Indicators)│  │   Signals)     │  │   Index)        │  │
         │  └───────────────┘  └──────────────┘  └────────────────┘  └─────────────────┘  │
         └─────────────────────────────────────────────────────────────────────────────────┘
                                                │
         ┌──────────────────────────────────────▼─────────────────────────────────────────┐
         │                     EXTERNAL DATA SOURCES LAYER                                │
         │  US Markets:                          Indian Markets:                          │
         │  ┌──────────────┐ ┌──────────────┐   ┌──────────────┐ ┌────────────────────┐   │
         │  │ Alpha Vantage│ │ Yahoo Finance│   │  NSE India   │ │  BSE India         │   │
         │  │ (Quotes/News)│ │ (OHLCV/Opts) │   │  (Quotes/F&O)│ │  (Quotes/IPO)      │   │
         │  └──────────────┘ └──────────────┘   └──────────────┘ └────────────────────┘   │
         │  ┌──────────────┐ ┌──────────────┐   ┌──────────────┐ ┌────────────────────┐   │
         │  │  FRED API    │ │  SEC EDGAR   │   │ Zerodha Kite │ │  SEBI (IPO data)   │   │
         │  │  (Macro)     │ │  (Filings)   │   │  (Live Data) │ │  (Regulatory)      │   │
         │  └──────────────┘ └──────────────┘   └──────────────┘ └────────────────────┘   │
         │  Social/News:                                                                  │
         │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐  ┌───────────────────┐     │
         │  │  NewsAPI     │ │ Twitter/X    │ │   Reddit API │  │  StockTwits API   │     │
         │  │  (Headlines) │ │ (Sentiment)  │ │  (WSB, etc.) │  │  (Stock Sentiment)│     │
         │  └──────────────┘ └──────────────┘ └──────────────┘  └───────────────────┘     │
         └────────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. MICROSERVICES BREAKDOWN

### 6.1 Service Catalog

| Service Name | Responsibility | Port | Tech |
|---|---|---|---|
| **api-gateway** | Auth, rate limiting, routing | 8080 | Spring Cloud Gateway |
| **market-data-service** | Real-time + historical price fetching | 8081 | Spring Boot + OkHttp |
| **technical-analysis-service** | Indicator computation, pattern detection | 8082 | Spring Boot + Commons Math |
| **sentiment-analysis-service** | NLP on news + social media | 8083 | Spring Boot + OpenNLP |
| **fundamental-analysis-service** | Ratio analysis, earnings data | 8084 | Spring Boot + JPA |
| **macro-geo-service** | Macro + geopolitical data and scoring | 8085 | Spring Boot |
| **social-media-service** | Twitter/Reddit/StockTwits ingestion | 8086 | Spring Boot + WebSocket |
| **ml-prediction-service** | Price prediction, target computation | 8087 | Spring Boot + SMILE ML |
| **signal-generator-service** | Composite signal generation | 8088 | Spring Boot + Kafka |
| **risk-management-service** | Position sizing, VaR, stop-loss | 8089 | Spring Boot |
| **portfolio-service** | Track positions, P&L, history | 8090 | Spring Boot + JPA |
| **ipo-analysis-service** | IPO research and subscription advice | 8091 | Spring Boot |
| **notification-service** | Alerts via email/SMS/WebSocket | 8092 | Spring Boot + WebSocket |
| **recommendation-engine** | Aggregates all analysis → final call | 8093 | Spring Boot + Kafka |
| **config-service** | Centralized configuration | 8888 | Spring Cloud Config |
| **discovery-service** | Service registry | 8761 | Eureka Server |

---

### 6.2 Detailed Service Breakdown

#### SERVICE-1: Market Data Service
```
┌─────────────────────────────────────────────────────────────┐
│                   MARKET DATA SERVICE                       │
│                                                             │
│  Responsibilities:                                          │
│  • Poll US market data (NYSE/NASDAQ/AMEX) every 500ms       │
│  • Poll Indian market data (NSE/BSE) every 500ms            │
│  • Store raw OHLCV in InfluxDB (time-series)                │
│  • Publish to Kafka topic: "raw-market-data"                │
│  • Maintain 5-year historical data                          │
│  • Handle market hours (US: 9:30am–4pm EST)                 │
│                    (India: 9:15am–3:30pm IST)               │
│                                                             │
│  API Integrations:                                          │
│  ├── Alpha Vantage (primary - US)                           │
│  ├── Yahoo Finance (backup - US + India ADRs)               │
│  ├── NSE Official API (India)                               │
│  └── BSE Official API (India)                               │
│                                                             │
│  Key Endpoints:                                             │
│  GET /api/v1/market/quote/{symbol}                          │
│  GET /api/v1/market/history/{symbol}?from=&to=              │
│  GET /api/v1/market/options/{symbol}                        │
│  GET /api/v1/market/commodities                             │
│  WebSocket: /ws/live-quotes                                 │
└─────────────────────────────────────────────────────────────┘
```

#### SERVICE-2: Technical Analysis Service
```
┌─────────────────────────────────────────────────────────────┐
│               TECHNICAL ANALYSIS SERVICE                    │
│                                                             │
│  Indicators Computed:                                       │
│  TREND:                                                     │
│  ├── SMA (20, 50, 200 periods)                              │
│  ├── EMA (9, 21, 55 periods)                                │
│  ├── Supertrend                                             │
│  └── Parabolic SAR                                          │
│                                                             │
│  MOMENTUM:                                                  │
│  ├── RSI (14) — Overbought>70, Oversold<30                  │
│  ├── MACD (12,26,9) — Signal line crossover                 │
│  ├── Stochastic Oscillator (14,3,3)                         │
│  └── Williams %R                                            │
│                                                             │
│  VOLATILITY:                                                │
│  ├── Bollinger Bands (20,2)                                 │
│  ├── ATR (14) — for stop-loss sizing                        │
│  ├── Keltner Channels                                       │
│  └── Historical Volatility (20,30 days)                     │
│                                                             │
│  VOLUME:                                                    │
│  ├── OBV (On-Balance Volume)                                │
│  ├── VWAP                                                   │
│  ├── Volume Profile                                         │
│  └── MFI (Money Flow Index)                                 │
│                                                             │
│  PATTERNS:                                                  │
│  Chart: H&S, Cup&Handle, Triangle, Flag, Wedge              │
│  Candle: Doji, Hammer, Shooting Star, Engulfing             │
│                                                             │
│  Output: TechnicalScore (0–100) + TechnicalSignal           │
└─────────────────────────────────────────────────────────────┘
```

#### SERVICE-3: Sentiment Analysis Service
```
┌─────────────────────────────────────────────────────────────┐
│              SENTIMENT ANALYSIS SERVICE                     │
│                                                             │
│  Pipeline:                                                  │
│  1. Ingest text data (news, tweets, reddit posts)           │
│  2. Pre-processing: tokenize, remove noise                  │
│  3. Entity recognition: extract ticker symbols              │
│  4. Sentiment scoring: VADER + keyword lexicon              │
│  5. Aggregate per ticker per time window                    │
│  6. Output: SentimentScore + trend direction                │
│                                                             │
│  Data Sources:                                              │
│  ├── NewsAPI (financial news headlines)                     │
│  ├── Twitter/X (cashtag mentions, #AAPL, #RELIANCE)         │
│  ├── Reddit: r/investing, r/wallstreetbets, r/stocks        │
│  │          r/IndiaInvestments, r/IndianStockMarket         │
│  ├── StockTwits (dedicated stock sentiment)                 │
│  └── Google News RSS feeds                                  │
│                                                             │
│  Scoring Model:                                             │
│  • Positive keywords: bullish, surge, beat, strong, buy     │
│  • Negative keywords: bearish, crash, miss, weak, sell      │
│  • Score Range: -1.0 (very bearish) to +1.0 (very bullish)  │
│  • Weighted by source credibility + recency                 │
│                                                             │
│  Special Handling:                                          │
│  • Political tweets affecting markets (Fed chair, PM Modi)  │
│  • Earnings surprise news                                   │
│  • Viral retail momentum (meme stock detection)             │
└─────────────────────────────────────────────────────────────┘
```

#### SERVICE-4: Macro/Geopolitical Service
```
┌─────────────────────────────────────────────────────────────┐
│            MACRO / GEOPOLITICAL SERVICE                     │
│                                                             │
│  US Macro Indicators:                                       │
│  ├── Fed Funds Rate (FRED API)                              │
│  ├── CPI / PPI (inflation)                                  │
│  ├── GDP Growth Rate                                        │
│  ├── Unemployment Rate                                      │
│  ├── 10Y Treasury Yield                                     │
│  ├── DXY (Dollar Index)                                     │
│  └── VIX (Fear Index)                                       │
│                                                             │
│  India Macro Indicators:                                    │
│  ├── RBI Repo Rate                                          │
│  ├── CPI / WPI (inflation)                                  │
│  ├── GDP Growth Rate (MOSPI)                                │
│  ├── FII / DII Net Flows (NSE data)                         │
│  ├── USD/INR exchange rate                                  │
│  └── India VIX                                              │
│                                                             │
│  Geopolitical Events (scored 0–10 severity):                │
│  ├── Wars / Military conflicts                              │
│  ├── Trade wars / Sanctions                                 │
│  ├── Election cycles                                        │
│  ├── Central bank policy changes                            │
│  ├── Natural disasters                                      │
│  └── Pandemics / Health crises                              │
│                                                             │
│  Impact Mapping:                                            │
│  Event → Affected Sectors → Affected Tickers                │
│  e.g., "Oil price spike" → Energy UP, Airlines DOWN         │
│        "Rate hike" → Banks UP, Real Estate DOWN             │
│        "INR depreciation" → IT exporters UP, Oil cos DOWN   │
└─────────────────────────────────────────────────────────────┘
```

#### SERVICE-5: ML Prediction Engine
```
┌─────────────────────────────────────────────────────────────┐
│               ML PREDICTION ENGINE                          │
│                                                             │
│  Models Used (Ensemble):                                    │
│                                                             │
│  Model-1: Random Forest Classifier                          │
│  ├── Features: 45 technical indicators                      │
│  ├── Target: Price direction (UP/DOWN/SIDEWAYS)             │
│  └── Accuracy target: 65%+                                  │
│                                                             │
│  Model-2: LSTM Neural Network                               │
│  ├── Features: OHLCV + sentiment score (60-day window)      │
│  ├── Target: Next day / 7-day price                         │
│  └── Used for price target estimation                       │
│                                                             │
│  Model-3: Linear Regression (Baseline)                      │
│  ├── Features: Volume, Price momentum, RSI                  │
│  └── Target: Short-term price range                         │
│                                                             │
│  Model-4: Sentiment + Price Correlation                     │
│  ├── Pearson correlation of sentiment score vs price        │
│  └── Lag analysis (sentiment leads price by N hours)        │
│                                                             │
│  Composite Score Formula:                                   │
│  FinalScore =                                               │
│    (Technical_Score × 0.35)                                 │
│    + (Fundamental_Score × 0.25)                             │
│    + (Sentiment_Score × 0.20)                               │
│    + (Macro_Score × 0.15)                                   │
│    + (ML_Model_Score × 0.05)                                │
│                                                             │
│  Output per Recommendation:                                 │
│  ├── Signal: BUY / SELL / HOLD / STRONG_BUY / STRONG_SELL   │
│  ├── Entry Price (current market price range)               │
│  ├── Target Price (ML predicted)                            │
│  ├── Stop Loss (ATR-based, 2× ATR below entry)              │
│  ├── Expected Profit %                                      │
│  ├── Confidence Score (0–100%)                              │
│  ├── Expected Holding Period (days)                         │
│  ├── Entry Date (today or next dip date)                    │
│  └── Exit Date (predicted based on target achievement)      │
└─────────────────────────────────────────────────────────────┘
```

#### SERVICE-6: Signal Generator & Recommendation Engine
```
┌─────────────────────────────────────────────────────────────┐
│         SIGNAL GENERATOR + RECOMMENDATION ENGINE            │
│                                                             │
│  Input:                                                     │
│  ├── TechnicalAnalysisResult (from TA Service)              │
│  ├── SentimentResult (from Sentiment Service)               │
│  ├── FundamentalResult (from Fundamental Service)           │
│  ├── MacroResult (from Macro Service)                       │
│  └── MLPrediction (from ML Engine)                          │
│                                                             │
│  Signal Decision Matrix:                                    │
│                                                             │
│  Composite Score → Signal:                                  │
│  ├── 80–100 → STRONG BUY  🔥                                │
│  ├── 65–79  → BUY         ✅                                │
│  ├── 45–64  → HOLD        ⏸️                                │
│  ├── 30–44  → SELL        ❌                                │
│  └──  0–29  → STRONG SELL 💀                                │
│                                                             │
│  Additional Filters:                                        │
│  • Risk filter: HIGH_RISK items need confidence > 80%       │
│  • Liquidity filter: min avg volume = 100K shares/day       │
│  • Market cap filter: min ₹500Cr (India) / $1B (US)         │
│                                                             │
│  Daily Output:                                              │
│  • Top 5 US Stocks to BUY                                   │
│  • Top 5 Indian Stocks to BUY                               │
│  • Top 3 Options Setups                                     │
│  • Top 3 Commodity Trades                                   │
│  • Active IPO recommendations                               │
│  • Stocks to AVOID (SELL / STRONG SELL)                     │
└─────────────────────────────────────────────────────────────┘
```

---

## 7. ARCHITECTURAL FLOW DIAGRAM

```
╔══════════════════════════════════════════════════════════════════════╗
║                    ARCHITECTURAL DATA FLOW                           ║
╚══════════════════════════════════════════════════════════════════════╝

STEP 1: DATA INGESTION (Real-time: every 500ms | Batch: every 1 hour)
───────────────────────────────────────────────────────────────────────
External APIs                 Data Ingestion Layer              Kafka Topics
                                                               
Alpha Vantage ──────────────► Market Data Service ────────────► [market-data-raw]
Yahoo Finance ──────────────►                                  
NSE India ───────────────────►                                 
BSE India ───────────────────►                                 

NewsAPI ─────────────────────► News Ingestion Service ─────────► [news-raw]
Reuters RSS ─────────────────►                                 

Twitter/X ───────────────────► Social Media Service ───────────► [social-raw]
Reddit API ──────────────────►                                 
StockTwits ──────────────────►                                 

FRED API ────────────────────► Macro Service ──────────────────► [macro-data]
World Bank ──────────────────►                                 
RBI/MOSPI ───────────────────►                                 

STEP 2: STREAM PROCESSING (Apache Kafka Streams / Flink)
───────────────────────────────────────────────────────────────────────
[market-data-raw] ──────────► TA Computation Worker ──────────► [ta-results]
[ta-results]     ──────────► Pattern Detection Worker ────────► [patterns]
[news-raw]       ──────────► NLP Sentiment Worker ────────────► [sentiment-scores]
[social-raw]     ──────────► Social Sentiment Worker ─────────► [sentiment-scores]
[macro-data]     ──────────► Macro Scoring Worker ────────────► [macro-scores]

STEP 3: AGGREGATION & SCORING
───────────────────────────────────────────────────────────────────────
[ta-results]      ──┐
[sentiment-scores]──┤
[macro-scores]    ──┼──► ML Prediction Engine ──────────────────► [ml-predictions]
[fundamental-db]  ──┘

STEP 4: SIGNAL GENERATION
───────────────────────────────────────────────────────────────────────
[ml-predictions] ──────────► Signal Generator ──────────────────► [signals]
                             ├── Apply risk filters
                             ├── Apply liquidity filters
                             └── Generate final recommendation

STEP 5: STORAGE & DELIVERY
───────────────────────────────────────────────────────────────────────
[signals] ─────────────────► PostgreSQL (persist recommendations)
          ─────────────────► Redis (cache for fast API reads)
          ─────────────────► WebSocket (push to clients)
          ─────────────────► Notification Service (alerts)

STEP 6: CLIENT ACCESS
───────────────────────────────────────────────────────────────────────
Redis Cache ────────────────► REST API (GET /recommendations)
WebSocket ──────────────────► Dashboard (real-time updates)
Notification ───────────────► Email / Push Notification
```

---

## 8. FUNCTIONAL FLOW DIAGRAMS

### 8.1 Stock Recommendation Flow (End-to-End)

```
User Request: "Top 5 Indian Stocks to Buy Today"
      │
      ▼
┌─────────────────┐
│  Recommendation │
│  Controller     │
│  GET /api/v1/   │
│  recommend/     │
│  stocks/india   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐      ┌─────────────────┐
│  Recommendation │      │   Redis Cache   │
│  Engine         │─────►│  (5 min TTL)    │
│                 │      │  HIT → return   │
│  Orchestrates:  │      │  MISS → compute │
└────────┬────────┘      └─────────────────┘
         │ (cache miss)
         ├──────────────────────────────────────────────────────────┐
         │                                                          │
         ▼                                                          ▼
┌─────────────────┐                                    ┌──────────────────┐
│  Market Data    │                                    │  Fundamental     │
│  Service        │                                    │  Analysis Service│
│  Fetch NSE top  │                                    │  P/E, P/B, EPS,  │
│  500 stocks     │                                    │  Revenue Growth  │
│  OHLCV 60 days  │                                    │  FundScore: 0-100│
└────────┬────────┘                                    └────────┬─────────┘
         │                                                      │
         ▼                                                      │
┌─────────────────┐       ┌─────────────────────────────────────┘
│  Technical      │       │
│  Analysis Svc   │       │
│  RSI, MACD,     │       │
│  BB, Patterns   │       │
│  TechScore:0-100│       │
└────────┬────────┘       │
         │                │
         └───────┬────────┘
                 │
                 ├──────────────────────────────┐
                 │                              │
                 ▼                              ▼
        ┌────────────────┐           ┌──────────────────┐
        │ Sentiment Svc  │           │  Macro/Geo Svc   │
        │ News + Social  │           │  RBI Rate, FII,  │
        │ SentScore:-1..1│           │  USD/INR, IndVIX │
        └────────┬───────┘           │  MacroScore:0-100│
                 │                   └────────┬─────────┘
                 └──────────────┬─────────────┘
                                │
                                ▼
                    ┌──────────────────────────┐
                    │    ML Prediction Engine  │
                    │                          │
                    │  Input:                  │
                    │  • TechScore             │
                    │  • FundScore             │
                    │  • SentimentScore        │
                    │  • MacroScore            │
                    │                          │
                    │  Compute:                │
                    │  CompositeScore =        │
                    │  (Tech×0.35 +            │
                    │   Fund×0.25 +            │
                    │   Sent×0.20 +            │
                    │   Macro×0.15 +           │
                    │   ML×0.05)               │
                    │                          │
                    │  Output:                 │
                    │  • Signal: BUY/SELL/HOLD │
                    │  • Target Price          │
                    │  • Stop Loss             │
                    │  • Profit % = 15.3%      │
                    │  • Confidence = 78%      │
                    │  • Entry Date: Today     │
                    │  • Exit Date: Jun 15     │
                    └───────────┬──────────────┘
                                │
                                ▼
                    ┌──────────────────────────┐
                    │  Risk Management         │
                    │  • Position Size (Kelly) │
                    │  • Max Loss Limit        │
                    │  • Portfolio Correlation │
                    └───────────┬──────────────┘
                                │
                                ▼
                    ┌──────────────────────────┐
                    │  Final Recommendation    │──► Cache in Redis
                    │  Sorted by Score         │──► Persist to DB
                    │  Top 5 Returned          │──► Push via WebSocket
                    └──────────────────────────┘
```

### 8.2 IPO Analysis Flow

```
New IPO Detected (SEBI/SEC Filing)
         │
         ▼
┌────────────────────────────┐
│     IPO Analysis Service   │
│                            │
│  1. Company Research       │──► Fetch from SEBI/SEC/Bloomberg
│     • Business model       │
│     • Revenue trajectory   │
│     • Promoter background  │
│                            │
│  2. Valuation Analysis     │──► Compare with listed peers
│     • Price band analysis  │    P/E at issue price
│     • Industry P/E compare │    EV/Sales ratio
│     • GMP (Grey Market)    │
│                            │
│  3. Subscription Analysis  │──► Track during open window
│     • Retail QIB HNI subs  │
│     • Historical GMP trend │
│     • Lead manager track   │
│                            │
│  4. Listing Gain Predictor │──► ML model trained on 500+ IPOs
│     • Price band position  │    Subscription ratio
│     • Market sentiment     │    Sector momentum
│                            │
│  5. Final Call:            │
│     APPLY (Strong/Normal)  │
│     AVOID                  │
│     Expected Gain: +23%    │
│     Listing Date: Jun 5    │
└────────────────────────────┘
```

### 8.3 Options Strategy Flow

```
User Watchlist Ticker (e.g., "NIFTY Options")
         │
         ▼
┌───────────────────────────────┐
│   Options Analysis Engine     │
│                               │
│  1. Fetch Options Chain       │──► NSE API (India) / CBOE (US)
│     All strikes + expiry      │    OI, Volume, IV, Greeks
│                               │
│  2. IV Analysis               │
│     • Current IV vs HV        │──► IV Rank / IV Percentile
│     • IV Skew analysis        │    Options overpriced/cheap?
│                               │
│  3. OI Analysis               │
│     • OI distribution         │──► Support/Resistance from OI
│     • Change in OI            │    Buildup vs Unwinding
│     • PCR (Put/Call Ratio)    │
│                               │
│  4. Strategy Selection        │
│     • Bullish + Low IV        │──► Bull Call Spread
│     • Bearish + Low IV        │──► Bear Put Spread
│     • High IV + Neutral       │──► Iron Condor / Short Straddle
│     • Directional + News      │──► Long Straddle / Strangle
│                               │
│  5. Output:                   │
│     Strategy: Iron Condor     │
│     Strike Range: 23000-24000 │
│     Max Profit: ₹2,500        │
│     Max Loss:   ₹500          │
│     Entry Date: Today         │
│     Exit Date: Expiry-2days   │
│     Probability of Profit: 72%│
└───────────────────────────────┘
```

---

## 9. DATA FLOW DIAGRAM

### 9.1 Level-0 Context DFD

```
         ┌────────────────────────────────────────────────────────┐
         │                                                        │
  Market ──────────────────────────────────────────────────►      │
  Exchanges                                                │      │
  (NSE,BSE,                      H F T                     │      │
   NYSE,NASDAQ)                                            │      │
                                Market                     │      │
  News/Social ────────────────► Intelligence ──────────────► Users/
  Media                         Platform                   │ Traders/
                                (HMIP)                     │ Investors
  Macro/Geo ──────────────────────────────────────────────►│
  Data                                                     │
  (FRED, RBI)                   ┌───────────────────────┐  │
         │                      │ Recommendations       │  │
         │                      │ Signals               │──┘
         │                      │ Reports               │
         │                      │ Alerts                │
         └──────────────────────┘───────────────────────┘
```

### 9.2 Level-1 Detailed DFD

```
                   ┌──────────────┐
                   │  D1: OHLCV   │ ◄── Market Exchanges
                   │  Time-Series │
                   │  (InfluxDB)  │
                   └──────┬───────┘
                          │
                          ▼
                   ┌──────────────┐        ┌───────────────┐
                   │  P1: Tech    │◄───────┤ D2: TA Cache  │
                   │  Analysis    │        │ (Redis)       │
                   │  Processor   │        └───────────────┘
                   └──────┬───────┘
                          │ TechnicalScore
                          │
                   ┌──────▼───────┐
                   │  D3: News &  │ ◄── News APIs
                   │  Social Feed │ ◄── Social Media
                   └──────┬───────┘
                          │
                          ▼
                   ┌──────────────┐
                   │  P2: NLP     │
                   │  Sentiment   │
                   │  Analysis    │
                   └──────┬───────┘
                          │ SentimentScore
                          │
                   ┌──────▼───────┐        ┌───────────────┐
                   │  P3: Fund.   │◄───────┤ D4: Filings   │
                   │  Analysis    │        │ (SEC/SEBI)    │
                   │  Processor   │        └───────────────┘
                   └──────┬───────┘
                          │ FundamentalScore
                          │
                   ┌──────▼───────┐        ┌───────────────┐
                   │  P4: Macro   │◄───────┤ D5: Macro DB  │
                   │  + Geo       │        │ (FRED, RBI)   │
                   │  Processor   │        └───────────────┘
                   └──────┬───────┘
                          │ MacroScore
                          │
                          ▼
                   ┌──────────────────────────────┐
                   │  P5: ML Prediction Engine    │
                   │  Inputs: Tech+Fund+Sent+Macro│
                   │  Output: CompositeScore,     │
                   │          Target, Confidence  │
                   └──────────────┬───────────────┘
                                  │
                                  ▼
                   ┌──────────────────────────────┐
                   │  P6: Signal Generator        │
                   │  BUY/SELL/HOLD + Details     │
                   └──────────────┬───────────────┘
                                  │
                   ┌──────────────┼───────────────┐
                   ▼              ▼               ▼
           ┌───────────┐  ┌──────────────┐ ┌──────────────┐
           │ D6: Reco  │  │  D7: Alert   │ │  D8: Portfolio│
           │ Store     │  │  Queue       │ │  DB          │
           │(PostgreSQL│  │  (Kafka)     │ │  (Postgres)  │
           └─────┬─────┘  └──────┬───────┘ └──────┬───────┘
                 │               │                │
                 ▼               ▼                ▼
           REST API       WebSocket Push    P&L Reports
           Consumers      (Real-time)       Portfolio View
```

---

## 10. LOAD FLOW & CAPACITY PLANNING

### 10.1 Data Volume Estimates

| Data Type | Volume | Frequency | Daily Records |
|---|---|---|---|
| US Stock Quotes | 5,000 tickers × 500ms polls | Real-time | ~57M |
| India Stock Quotes | 2,000 tickers × 500ms polls | Real-time | ~23M |
| News Articles | 10,000 articles/day | Batch hourly | 10K |
| Social Media Posts | 500,000 posts/day | Near real-time | 500K |
| Options Chain Updates | 200 tickers × 5min | Every 5 min | ~576K |
| Technical Indicators | 7,000 tickers | Per new candle | ~7K/min |
| Recommendations | 7,000 tickers | Daily refresh | ~7K |

### 10.2 System Load Model

```
┌───────────────────────────────────────────────────────────────────┐
│                     LOAD FLOW DIAGRAM                             │
│                                                                   │
│  PEAK LOAD HOURS:                                                 │
│  US Market:   9:30am–10:30am EST (market open) → HIGHEST          │
│  India Market: 9:15am–10:15am IST (market open) → HIGHEST         │
│  Both Markets overlap: 9:30pm–10:30pm IST (US pre-market)         │
│                                                                   │
│  ┌────────────────────────────────────────────────────────────┐   │
│  │          LOAD TIMELINE (IST)                               │   │
│  │                                                            │   │
│  │ 12am──4am──8am──9:15am──11am──3:30pm──9:30pm──11:30pm──    │   │
│  │  │     │     │    │       │      │       │       │         │   │
│  │  LOW   LOW  MED  HIGH    MED   MED     HIGH    MED         │   │
│  │                  │(NSE)               │(NYSE)              │   │
│  └────────────────────────────────────────────────────────────┘   │
│                                                                   │
│  THROUGHPUT REQUIREMENTS:                                         │
│  ┌─────────────────────────────────┬──────────┬──────────────┐    │
│  │ Service                         │ Avg RPS  │ Peak RPS     │    │
│  ├─────────────────────────────────┼──────────┼──────────────┤    │
│  │ Market Data Ingestion           │ 10,000   │ 50,000       │    │
│  │ Technical Analysis              │ 500      │ 2,000        │    │
│  │ Sentiment Processing            │ 100      │ 500          │    │
│  │ Signal Generation               │ 50       │ 200          │    │
│  │ REST API (Recommendations)      │ 200      │ 1,000        │    │
│  │ WebSocket Streaming             │ 5,000    │ 20,000       │    │
│  └─────────────────────────────────┴──────────┴──────────────┘    │
│                                                                   │
│  KAFKA PARTITION STRATEGY:                                        │
│  market-data-raw:   64 partitions (by ticker symbol hash)         │
│  ta-results:        32 partitions                                 │
│  signals:           16 partitions                                 │
│  notifications:      8 partitions                                 │
│                                                                   │
│  CACHING STRATEGY:                                                │
│  ├── Live quotes:    Redis (TTL: 500ms)                           │
│  ├── TA indicators:  Redis (TTL: 5 min)                           │
│  ├── Recommendations: Redis (TTL: 5 min)                          │
│  └── Historical data: InfluxDB + in-memory Guava cache            │
│                                                                   │
│  INFRASTRUCTURE SIZING (Cloud):                                   │
│  ├── Market Data Service:  4 pods × (4 CPU, 8GB RAM)              │
│  ├── TA Service:           2 pods × (8 CPU, 16GB RAM)             │
│  ├── ML Engine:            2 pods × (8 CPU, 32GB RAM) + GPU       │
│  ├── Kafka Cluster:        3 brokers × (8 CPU, 32GB RAM)          │
│  ├── InfluxDB:             3-node cluster (SSD-backed)            │
│  ├── PostgreSQL:           1 primary + 2 replicas                 │
│  └── Redis:                3-node cluster (sentinel)              │
└───────────────────────────────────────────────────────────────────┘
```

---

## 11. DOMAIN MODELS & ENTITY DESIGN

### 11.1 Core Entity Model

```
┌─────────────────────────────────────────────────────────────────┐
│                    DOMAIN MODEL DIAGRAM                         │
└─────────────────────────────────────────────────────────────────┘

AssetType (Enum)              Market (Enum)
─────────────                 ─────────────
STOCK                         US_NYSE
OPTION                        US_NASDAQ
COMMODITY                     US_AMEX
IPO                           INDIA_NSE
MUTUAL_FUND                   INDIA_BSE
ETF

SignalType (Enum)              RiskLevel (Enum)
─────────────────             ─────────────────
STRONG_BUY                    VERY_LOW
BUY                           LOW
HOLD                          MEDIUM
SELL                          HIGH
STRONG_SELL                   VERY_HIGH
WATCH

TimeHorizon (Enum)             AssetStatus (Enum)
──────────────────             ──────────────────
SHORT_TERM (1-7 days)          ACTIVE
MEDIUM_TERM (1-4 weeks)        SUSPENDED
LONG_TERM (1-6 months)         DELISTED
                               UPCOMING_IPO

┌──────────────────────────────────────────────────────────────────┐
│                        StockQuote                                │
├──────────────────────────────────────────────────────────────────┤
│  + symbol: String            (e.g., "RELIANCE.NSE", "AAPL")      │
│  + companyName: String                                           │
│  + market: Market                                                │
│  + assetType: AssetType                                          │
│  + currentPrice: BigDecimal                                      │
│  + openPrice: BigDecimal                                         │
│  + highPrice: BigDecimal                                         │
│  + lowPrice: BigDecimal                                          │
│  + closePrice: BigDecimal                                        │
│  + volume: Long                                                  │
│  + marketCap: BigDecimal                                         │
│  + dayChangePercent: Double                                      │
│  + weekChangePercent: Double                                     │
│  + fiftyTwoWeekHigh: BigDecimal                                  │
│  + fiftyTwoWeekLow: BigDecimal                                   │
│  + timestamp: LocalDateTime                                      │
│  + currency: String          (USD / INR)                         │
└──────────────────────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────────────────────┐
│                     TechnicalIndicators                          │
├──────────────────────────────────────────────────────────────────┤
│  + symbol: String                                                │
│  + sma20, sma50, sma200: Double                                  │
│  + ema9, ema21, ema55: Double                                    │
│  + rsi14: Double             (0–100)                             │
│  + macdLine, macdSignal, macdHistogram: Double                   │
│  + bollingerUpper, bollingerMiddle, bollingerLower: Double       │
│  + atr14: Double                                                 │
│  + obv: Long                                                     │
│  + vwap: Double                                                  │
│  + stochasticK, stochasticD: Double                              │
│  + williamsR: Double                                             │
│  + historicalVolatility20: Double                                │
│  + technicalScore: Double    (0–100 composite)                   │
│  + trendDirection: String    (UPTREND/DOWNTREND/SIDEWAYS)        │
│  + detectedPatterns: List<String>                                │
│  + supportLevel: Double                                          │
│  + resistanceLevel: Double                                       │
│  + computedAt: LocalDateTime                                     │
└──────────────────────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────────────────────┐
│                      SentimentData                               │
├──────────────────────────────────────────────────────────────────┤
│  + symbol: String                                                │
│  + overallSentimentScore: Double   (-1.0 to +1.0)                │
│  + newsSentimentScore: Double                                    │
│  + socialSentimentScore: Double                                  │
│  + mentionCount24h: Integer        (social media)                │
│  + mentionTrend: String            (RISING/FALLING/STABLE)       │
│  + positiveNewsCount: Integer                                    │
│  + negativeNewsCount: Integer                                    │
│  + keyHeadlines: List<String>      (top 5 headlines)             │
│  + isTrending: Boolean                                           │
│  + twitterVolume24h: Long                                        │
│  + redditMentions24h: Long                                       │
│  + computedAt: LocalDateTime                                     │
└──────────────────────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────────────────────┐
│                     FundamentalData                              │
├──────────────────────────────────────────────────────────────────┤
│  + symbol: String                                                │
│  + peRatio: Double                                               │
│  + pbRatio: Double                                               │
│  + evToEbitda: Double                                            │
│  + epsCurrentYear: Double                                        │
│  + epsGrowthYoY: Double            (%)                           │
│  + revenueGrowthYoY: Double        (%)                           │
│  + netProfitMargin: Double         (%)                           │
│  + debtToEquity: Double                                          │
│  + currentRatio: Double                                          │
│  + dividendYield: Double           (%)                           │
│  + roce: Double                    (Return on Capital Employed)  │
│  + roe: Double                     (Return on Equity)            │
│  + promoterHolding: Double         (% for India)                 │
│  + institutionalHolding: Double    (%)                           │
│  + fundamentalScore: Double        (0–100)                       │
│  + valuationStatus: String         (UNDERVALUED/FAIRLY/OVERVAL)  │
│  + lastUpdated: LocalDate                                        │
└──────────────────────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────────────────────┐
│                        MacroData                                 │
├──────────────────────────────────────────────────────────────────┤
│  + market: Market                                                │
│  + interestRate: Double            (Fed Rate / RBI Repo Rate)    │
│  + inflationRate: Double           (CPI %)                       │
│  + gdpGrowthRate: Double           (%)                           │
│  + unemploymentRate: Double        (US only)                     │
│  + currencyPair: String            (USD/INR)                     │
│  + currencyRate: Double                                          │
│  + vixLevel: Double                (fear index)                  │
│  + tenYearYield: Double            (US only)                     │
│  + fiiNetFlow: BigDecimal          (India: crores)               │
│  + macroScore: Double              (0–100, higher = better env)  │
│  + macroSentiment: String          (POSITIVE/NEUTRAL/NEGATIVE)   │
│  + geopoliticalRiskScore: Double   (0–10, 10 = max risk)         │
│  + lastUpdated: LocalDateTime                                    │
└──────────────────────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────────────────────┐
│                    TradeRecommendation  (CORE)                   │
├──────────────────────────────────────────────────────────────────┤
│  + id: UUID                                                      │
│  + symbol: String                                                │
│  + companyName: String                                           │
│  + market: Market                                                │
│  + assetType: AssetType                                          │
│  + signal: SignalType              (BUY/SELL/HOLD/etc.)          │
│  + timeHorizon: TimeHorizon        (SHORT/MEDIUM/LONG)           │
│  + riskLevel: RiskLevel                                          │
│  + entryPrice: BigDecimal          (recommended entry price)     │
│  + entryPriceRange: String         (e.g., "₹2,350–₹2,400")       │
│  + targetPrice: BigDecimal                                       │
│  + stopLossPrice: BigDecimal                                     │
│  + expectedProfitPercent: Double   (e.g., 18.5)                  │
│  + maxRiskPercent: Double          (stop loss %)                 │
│  + riskRewardRatio: Double         (e.g., 3.2 = 3.2:1)           │
│  + entryDate: LocalDate                                          │
│  + exitDate: LocalDate             (expected exit)               │
│  + holdingPeriodDays: Integer                                    │
│  + compositeScore: Double          (0–100)                       │
│  + confidencePercent: Double       (0–100)                       │
│  + technicalScore: Double                                        │
│  + fundamentalScore: Double                                      │
│  + sentimentScore: Double                                        │
│  + macroScore: Double                                            │
│  + keyReasons: List<String>        (bullet points WHY)           │
│  + keyRisks: List<String>          (what could go wrong)         │
│  + dataSources: List<String>       (which APIs backed this)      │
│  + relatedNews: List<String>       (top 3 news headlines)        │
│  + sectorName: String                                            │
│  + sectorOutlook: String           (POSITIVE/NEUTRAL/NEGATIVE)   │
│  + generatedAt: LocalDateTime                                    │
│  + validUntil: LocalDateTime                                     │
│  + status: String                  (ACTIVE/CLOSED/EXPIRED)       │
└──────────────────────────────────────────────────────────────────┘
```

---

## 12. API DESIGN (REST CONTRACTS)

### 12.1 Recommendation APIs

```
BASE URL: /api/v1

─────────────────────────────────────────────────────────────────
RECOMMENDATIONS
─────────────────────────────────────────────────────────────────

GET /recommendations/daily
→ Get today's top recommendations across all asset classes
Query: market=US|INDIA|ALL, assetType=STOCK|OPTION|COMMODITY|IPO
Response: {
  "date": "2026-05-28",
  "market": "INDIA",
  "recommendations": [
    {
      "rank": 1,
      "symbol": "RELIANCE.NSE",
      "companyName": "Reliance Industries Ltd",
      "signal": "STRONG_BUY",
      "entryPrice": 2387.50,
      "entryPriceRange": "₹2,380–₹2,400",
      "targetPrice": 2780.00,
      "stopLossPrice": 2250.00,
      "expectedProfitPercent": 16.44,
      "maxRiskPercent": 5.75,
      "riskRewardRatio": 2.86,
      "entryDate": "2026-05-28",
      "exitDate": "2026-07-15",
      "holdingPeriodDays": 48,
      "compositeScore": 84.2,
      "confidencePercent": 79.0,
      "timeHorizon": "MEDIUM_TERM",
      "riskLevel": "MEDIUM",
      "keyReasons": [
        "RSI 45 - Recovering from oversold zone",
        "MACD bullish crossover confirmed",
        "Strong Q4 earnings beat: +23% YoY",
        "FII buying last 5 sessions: ₹1,200Cr net",
        "Jio subscriber growth momentum continues"
      ],
      "keyRisks": [
        "Crude oil price spike could impact margin",
        "USD/INR above 85 is a headwind",
        "Broad market correction risk (India VIX at 15)"
      ],
      "technicalScore": 78,
      "fundamentalScore": 82,
      "sentimentScore": 72,
      "macroScore": 68,
      "sectorName": "Conglomerate / Energy / Telecom",
      "sectorOutlook": "POSITIVE"
    }
  ]
}

─────────────────────────────────────────────────────────────────

GET /recommendations/stock/{symbol}
→ Deep-dive recommendation for a specific symbol
Response: Full TradeRecommendation object + all sub-scores

POST /recommendations/watchlist
→ Get recommendations for user's custom watchlist
Body: { "symbols": ["AAPL", "RELIANCE.NSE", "NIFTY50"], "market": "ALL" }

GET /recommendations/ipo
→ All active IPOs with recommendations
Response: IPORecommendation list with apply/avoid + expected listing gain

GET /recommendations/options
→ Top options strategies for current market conditions
Response: OptionsStrategyRecommendation list

GET /recommendations/commodities
→ Commodity trade setups
Response: CommodityRecommendation list (Gold, Silver, Oil, etc.)

─────────────────────────────────────────────────────────────────
MARKET DATA
─────────────────────────────────────────────────────────────────

GET /market/quote/{symbol}
→ Real-time quote

GET /market/history/{symbol}?from=2025-01-01&to=2026-05-28&interval=1D
→ Historical OHLCV

GET /market/options/{symbol}?expiry=2026-06-26
→ Options chain with Greeks

GET /market/screener
Query: market=INDIA, signal=BUY, riskLevel=LOW, minConfidence=70
→ Screened stocks matching criteria

─────────────────────────────────────────────────────────────────
ANALYSIS
─────────────────────────────────────────────────────────────────

GET /analysis/technical/{symbol}
→ All technical indicators + patterns

GET /analysis/sentiment/{symbol}
→ Sentiment scores + headlines + social data

GET /analysis/fundamental/{symbol}
→ All fundamental ratios + scoring

GET /analysis/macro/{market}
→ Macro + geopolitical data for US or India

GET /analysis/correlation
→ Sector correlation matrix

─────────────────────────────────────────────────────────────────
PORTFOLIO
─────────────────────────────────────────────────────────────────

POST /portfolio/positions             → Add position
GET  /portfolio/positions             → View all positions
GET  /portfolio/pnl                   → Real-time P&L
GET  /portfolio/risk                  → VaR, drawdown, exposure
DELETE /portfolio/positions/{id}      → Close position

─────────────────────────────────────────────────────────────────
WEBSOCKET ENDPOINTS (Real-time)
─────────────────────────────────────────────────────────────────

/ws/live-quotes          → Subscribe to real-time price feed
/ws/signals              → Subscribe to new signal generation
/ws/news                 → Subscribe to breaking news alerts
/ws/portfolio            → Subscribe to portfolio P&L updates
```

---

## 13. DATABASE DESIGN

### 13.1 Schema Overview

```
PostgreSQL (Relational — for transactions, portfolio, users):

TABLE: trade_recommendations
  id UUID PK
  symbol VARCHAR(20)
  market VARCHAR(20)
  asset_type VARCHAR(20)
  signal VARCHAR(20)
  entry_price DECIMAL(15,2)
  target_price DECIMAL(15,2)
  stop_loss_price DECIMAL(15,2)
  expected_profit_pct DECIMAL(5,2)
  confidence_pct DECIMAL(5,2)
  composite_score DECIMAL(5,2)
  entry_date DATE
  exit_date DATE
  time_horizon VARCHAR(20)
  risk_level VARCHAR(20)
  generated_at TIMESTAMP
  valid_until TIMESTAMP
  status VARCHAR(20)

TABLE: portfolio_positions
  id UUID PK
  user_id UUID FK
  symbol VARCHAR(20)
  asset_type VARCHAR(20)
  quantity DECIMAL(15,4)
  avg_buy_price DECIMAL(15,2)
  current_price DECIMAL(15,2)
  unrealized_pnl DECIMAL(15,2)
  realized_pnl DECIMAL(15,2)
  entry_date DATE
  exit_date DATE
  status VARCHAR(20)

TABLE: fundamental_data
  symbol VARCHAR(20) PK
  pe_ratio DECIMAL(10,2)
  pb_ratio DECIMAL(10,2)
  eps_growth_yoy DECIMAL(10,2)
  revenue_growth_yoy DECIMAL(10,2)
  fundamental_score DECIMAL(5,2)
  last_updated DATE

TABLE: users
  id UUID PK
  email VARCHAR(255) UNIQUE
  risk_appetite VARCHAR(20)
  preferred_markets VARCHAR(50)
  subscription_tier VARCHAR(20)
  created_at TIMESTAMP

─────────────────────────────────────────────────────────────────
InfluxDB (Time-Series — for OHLCV, indicators):

MEASUREMENT: stock_quotes
  tags: symbol, market, asset_type
  fields: open, high, low, close, volume, vwap
  timestamp: nanosecond precision

MEASUREMENT: technical_indicators
  tags: symbol, market, interval
  fields: rsi, macd, ema9, ema21, sma50, sma200, bb_upper, bb_lower
  timestamp: per candle close

MEASUREMENT: sentiment_scores
  tags: symbol, market, source
  fields: score, mention_count
  timestamp: hourly

─────────────────────────────────────────────────────────────────
Redis (Cache — for hot data):

Keys:
  quote:{symbol}              → TTL 500ms (live prices)
  ta:{symbol}:{interval}      → TTL 5min (indicators)
  recommendation:{symbol}     → TTL 5min
  sentiment:{symbol}          → TTL 15min
  top-recommendations:us      → TTL 5min
  top-recommendations:india   → TTL 5min
  macro:us                    → TTL 1hr
  macro:india                 → TTL 1hr
```

---

## 14. TECHNOLOGY STACK

### 14.1 Backend

| Layer | Technology | Purpose |
|---|---|---|
| **Language** | Java 21 (LTS) | Core language with Virtual Threads |
| **Framework** | Spring Boot 3.2 | Microservices backbone |
| **API Layer** | Spring Web MVC + WebFlux | REST + Reactive streams |
| **API Gateway** | Spring Cloud Gateway | Routing, auth, rate limit |
| **Service Discovery** | Eureka Server | Microservice registry |
| **Config** | Spring Cloud Config | Centralized configuration |
| **Messaging** | Apache Kafka 3.7 | Event streaming bus |
| **Resilience** | Resilience4j | Circuit breakers, retries |
| **Caching** | Redis + Caffeine | Multi-layer caching |
| **Persistence** | Spring Data JPA | PostgreSQL ORM |
| **Time-Series DB** | InfluxDB 2.x | OHLCV + indicators |
| **Search** | Elasticsearch | News + sentiment search |
| **HTTP Client** | OkHttp 4.x | External API calls |
| **Math/Stats** | Apache Commons Math3 | Statistical calculations |
| **Scheduling** | Quartz Scheduler | Batch jobs + cron tasks |
| **Monitoring** | Micrometer + Prometheus | Metrics collection |
| **Tracing** | OpenTelemetry | Distributed tracing |
| **Logging** | SLF4J + Logback | Structured logging |
| **Security** | Spring Security + JWT | Authentication |
| **Docs** | SpringDoc OpenAPI 3 | Swagger UI |
| **Testing** | JUnit 5 + Mockito | Unit + integration tests |

### 14.2 External APIs

| API | Market | Data | Cost |
|---|---|---|---|
| **Alpha Vantage** | US | Quotes, fundamentals, news | Free/Premium |
| **Yahoo Finance** (unofficial) | US + India | OHLCV, options | Free |
| **NSE India** | India | Live quotes, F&O, OI | Free |
| **BSE India** | India | Quotes, IPO | Free |
| **Zerodha Kite API** | India | Live feed, order book | ₹2000/month |
| **NewsAPI.org** | Both | Headlines, news body | Free/Pro |
| **Twitter/X API v2** | Both | Tweets, cashtags | Free tier |
| **Reddit API (PRAW)** | Both | Subreddit posts | Free |
| **StockTwits API** | US | Stock-specific sentiment | Free |
| **FRED API** | US | Macro indicators | Free |
| **World Bank API** | Both | GDP, inflation | Free |
| **SEC EDGAR** | US | 10-K, 13-F filings | Free |
| **SEBI (scraping)** | India | IPO details | Free |

---

## 15. EXTERNAL INTEGRATIONS & DATA SOURCES

### 15.1 Integration Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                  EXTERNAL API INTEGRATION MAP                   │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │           CIRCUIT BREAKER PATTERN (Resilience4j)         │   │
│  │                                                          │   │
│  │  Each external API call is wrapped in:                   │   │
│  │  1. Circuit Breaker (open after 5 failures)              │   │
│  │  2. Retry (3 attempts with exponential backoff)          │   │
│  │  3. Rate Limiter (respect API limits)                    │   │
│  │  4. Timeout (5 second hard limit)                        │   │
│  │  5. Fallback (use cached data if available)              │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  PRIORITY FAILOVER:                                             │
│  US Stock Data:  Alpha Vantage → Yahoo Finance → Mock/Cache     │
│  India Data:     NSE API → BSE API → Kite API → Cache           │
│  News:           NewsAPI → RSS Feeds → GNews → Cache            │
│  Social:         Twitter/X → Reddit → StockTwits → Cache        │
└─────────────────────────────────────────────────────────────────┘
```

---

## 16. ANALYSIS ALGORITHMS & ML PIPELINE

### 16.1 Technical Score Calculation

```
TechnicalScore (0-100):

TREND SIGNALS (35 points max):
├── Price vs SMA200: +10 if above, -10 if below
├── SMA50 vs SMA200 (Golden/Death Cross): +8 / -8
├── Price vs SMA50: +7 if above, -7 if below
├── EMA21 vs EMA55 crossover: +5 / -5
└── Supertrend direction: +5 / -5

MOMENTUM SIGNALS (30 points max):
├── RSI(14): 40-60=0, 60-70=+5, 70-80=+3, <30=+8(oversold bounce)
│           30-40=-5, 20-30=-8, >80=-5(overbought)
├── MACD crossover: Signal line cross +10 / -10
├── MACD histogram: Expanding positive +5 / -5
└── Stochastic: Oversold bounce +5 / -5

VOLUME SIGNALS (20 points max):
├── OBV rising with price: +8
├── Volume > 20-day avg on green day: +7
└── VWAP: Price above VWAP = +5

VOLATILITY SIGNALS (15 points max):
├── Bollinger Band squeeze breakout: +8
├── ATR contraction (low volatility coil): +4
└── Price near lower BB (bounce setup): +3

Final TechnicalScore = sum of all applicable signals (0–100)
Normalized to prevent negative (floor at 0, cap at 100)
```

### 16.2 Composite Score & ML Pipeline

```
COMPOSITE SCORE PIPELINE:
─────────────────────────────────────────────────────────────────

Step 1: Normalize all sub-scores to 0–100 scale

Step 2: Apply sector-specific weights
  Growth sectors (Tech, Pharma):   Technical 30%, Fundamental 30%
  Value sectors (BFSI, FMCG):     Fundamental 35%, Technical 25%
  Commodities:                     Macro 40%, Technical 35%
  IPOs:                           Fundamental 45%, Sentiment 25%

Step 3: Apply market condition adjustment
  Bull market (VIX < 15): increase Technical weight +5%
  Bear market (VIX > 25): increase Fundamental weight +5%
  High volatility: increase Macro weight +5%

Step 4: Ensemble model output
  CompositeScore = Σ(weight_i × score_i) for all dimensions

Step 5: Confidence interval
  Confidence = f(data_freshness, source_count, model_accuracy, 
                  historical_performance_for_this_ticker)

Step 6: Price Target Estimation
  Target = CurrentPrice × (1 + expected_return)
  expected_return = regression_model(TechnicalScore, SentimentScore,
                                    historical_avg_returns, sector_momentum)

Step 7: Stop Loss
  StopLoss = EntryPrice - (2 × ATR14)
  (Dynamic: adjusted as price moves in favor)

Step 8: Exit Date
  ExitDate = EntryDate + avg_days_to_reach_target(sector, signal_strength)
  Based on: historical analysis of similar setups
```

---

## 17. RISK MANAGEMENT FRAMEWORK

```
┌─────────────────────────────────────────────────────────────────┐
│                   RISK MANAGEMENT LAYERS                        │
│                                                                 │
│  LAYER 1: TRADE-LEVEL RISK                                      │
│  ├── Stop Loss: 2× ATR below entry (dynamic)                    │
│  ├── Position Size: Max 5% of portfolio per trade               │
│  ├── Kelly Criterion: f* = (bp-q)/b                             │
│  │   b = profit ratio, p = win probability, q = 1-p             │
│  └── R:R Ratio: Minimum 2:1 required for BUY signal             │
│                                                                 │
│  LAYER 2: PORTFOLIO-LEVEL RISK                                  │
│  ├── Max 20% in single sector                                   │
│  ├── Max 30% in single market (US or India)                     │
│  ├── VaR(95%, 1-day) < 2% of portfolio                          │
│  └── Max correlation between holdings < 0.7                     │
│                                                                 │
│  LAYER 3: MACRO RISK FILTERS                                    │
│  ├── If VIX > 30: reduce signal strength, increase stop buffer  │
│  ├── If India VIX > 20: tighten India stops                     │
│  ├── No new BUY signals 2 days before Fed/RBI announcement      │
│  └── Reduce position sizes during earnings season               │
│                                                                 │
│  RISK LABELS:                                                   │
│  VERY_LOW:  R:R > 4:1, Confidence > 85%, Low volatility         │
│  LOW:       R:R > 3:1, Confidence > 75%                         │
│  MEDIUM:    R:R > 2:1, Confidence > 65%                         │
│  HIGH:      R:R > 1.5:1, Confidence > 55%                       │
│  VERY_HIGH: Options/Commodities/High-beta stocks                │
└─────────────────────────────────────────────────────────────────┘
```

---

## 18. SECURITY ARCHITECTURE

```
┌─────────────────────────────────────────────────────────────────┐
│                    SECURITY LAYERS                              │
│                                                                 │
│  AUTHENTICATION:                                                │
│  ├── JWT tokens (15 min access + 7 day refresh)                 │
│  ├── OAuth 2.0 with Google/GitHub SSO                           │
│  └── API Key authentication for programmatic access             │
│                                                                 │
│  AUTHORIZATION:                                                 │
│  ├── ROLE_FREE: Basic recommendations (top 3 only)              │
│  ├── ROLE_PREMIUM: Full recommendations + options + IPOs        │
│  └── ROLE_ADMIN: System management + raw data access            │
│                                                                 │
│  DATA SECURITY:                                                 │
│  ├── TLS 1.3 for all API communications                         │
│  ├── API keys stored as Kubernetes Secrets / Vault              │
│  ├── PII data encrypted at rest (AES-256)                       │
│  └── Database credentials via environment variables             │
│                                                                 │
│  RATE LIMITING:                                                 │
│  ├── Free tier: 100 API calls/hour                              │
│  ├── Premium: 10,000 API calls/hour                             │
│  └── Admin: Unlimited                                           │
│                                                                 │
│  COMPLIANCE:                                                    │
│  ├── "For informational purposes only" disclaimer on all reco   │
│  ├── Not SEBI/SEC registered investment advisor disclosure      │
│  └── Risk warnings prominently displayed                        │
└─────────────────────────────────────────────────────────────────┘
```

---

## 19. DEPLOYMENT ARCHITECTURE

```
┌─────────────────────────────────────────────────────────────────────┐
│                  KUBERNETES DEPLOYMENT (AWS EKS / GCP GKE)          │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                     INGRESS LAYER                            │   │
│  │  AWS ALB / Nginx Ingress Controller + CloudFront CDN         │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                  APPLICATION NAMESPACE                       │   │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────┐  │   │
│  │  │  api-gateway │ │ market-data  │ │ recommendation-engine│  │   │
│  │  │  (2 pods)    │ │ (4 pods)     │ │ (2 pods)             │  │   │
│  │  └──────────────┘ └──────────────┘ └──────────────────────┘  │   │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────┐  │   │
│  │  │  ta-service  │ │  sentiment   │ │  ml-prediction       │  │   │
│  │  │  (2 pods)    │ │  (2 pods)    │ │  (2 pods + GPU)      │  │   │
│  │  └──────────────┘ └──────────────┘ └──────────────────────┘  │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                  INFRASTRUCTURE NAMESPACE                    │   │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────┐  │   │
│  │  │  Kafka       │ │  PostgreSQL  │ │  Redis Cluster       │  │   │
│  │  │  (3 brokers) │ │  (1P + 2R)   │ │  (3 nodes)           │  │   │
│  │  └──────────────┘ └──────────────┘ └──────────────────────┘  │   │
│  │  ┌──────────────┐ ┌──────────────┐                           │   │
│  │  │  InfluxDB    │ │Elasticsearch │                           │   │
│  │  │  (3-node)    │ │  (3-node)    │                           │   │
│  │  └──────────────┘ └──────────────┘                           │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │              MONITORING NAMESPACE                            │   │
│  │  Prometheus + Grafana + OpenTelemetry Collector + Jaeger     │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### CI/CD Pipeline

```
Developer Push → GitHub Actions:
  Step 1: Unit Tests (JUnit 5)
  Step 2: Integration Tests
  Step 3: SonarQube Code Analysis
  Step 4: Docker Build + Push to ECR/GCR
  Step 5: Helm Chart Update
  Step 6: ArgoCD → Auto-deploy to Dev
  Step 7: Manual Gate → Deploy to Production
```

---

## 20. PLAN OF ACTION (SPRINT-WISE)

### Phase 1: Foundation (Weeks 1–4)
```
SPRINT 1 (Week 1–2): Core Infrastructure
  ✅ Set up Spring Boot 3.2 multi-module project
  ✅ Configure H2 (dev) + PostgreSQL (prod) databases
  ✅ Set up Kafka (local Docker)
  ✅ Set up Redis (local Docker)
  ✅ Create all domain model classes (entities/enums/DTOs)
  ✅ Create base service interfaces and abstract classes
  ✅ Configure Spring Security + JWT authentication
  ✅ Set up API documentation (SpringDoc/Swagger)

SPRINT 2 (Week 3–4): Market Data Foundation
  ✅ Implement Market Data Service
     ├── Alpha Vantage integration (US quotes)
     ├── Yahoo Finance integration (backup)
     ├── NSE India API integration (Indian quotes)
     └── BSE India API integration
  ✅ Build historical data ingestion (5-year OHLCV)
  ✅ Implement real-time quote polling (500ms interval)
  ✅ Store raw data in InfluxDB
  ✅ Publish events to Kafka (market-data-raw topic)
  ✅ Build REST endpoint: GET /market/quote/{symbol}
  ✅ Build REST endpoint: GET /market/history/{symbol}
```

### Phase 2: Analysis Engines (Weeks 5–10)
```
SPRINT 3 (Week 5–6): Technical Analysis Engine
  ✅ Implement all trend indicators (SMA, EMA, Supertrend)
  ✅ Implement all momentum indicators (RSI, MACD, Stochastic)
  ✅ Implement volatility indicators (BB, ATR, HV)
  ✅ Implement volume indicators (OBV, VWAP, MFI)
  ✅ Implement TechnicalScore composite (0-100 algorithm)
  ✅ Implement basic pattern detection (Golden/Death Cross, etc.)
  ✅ Build REST endpoint: GET /analysis/technical/{symbol}
  ✅ Cache results in Redis (5-min TTL)

SPRINT 4 (Week 7): Fundamental Analysis Engine
  ✅ Integrate Alpha Vantage Fundamentals API
  ✅ Implement ratio calculations (P/E, P/B, EV/EBITDA, etc.)
  ✅ Implement FundamentalScore algorithm
  ✅ Build REST endpoint: GET /analysis/fundamental/{symbol}
  ✅ Implement SEC EDGAR integration (US 10-K/13-F)
  ✅ Database: persist fundamental data (weekly refresh)

SPRINT 5 (Week 8): Sentiment Analysis Engine
  ✅ Integrate NewsAPI for financial headlines
  ✅ Implement NLP sentiment scoring (keyword lexicon + VADER)
  ✅ Integrate Twitter/X API (cashtag monitoring)
  ✅ Integrate Reddit API (r/investing, r/wallstreetbets)
  ✅ Implement SentimentScore aggregation algorithm
  ✅ Build REST endpoint: GET /analysis/sentiment/{symbol}
  ✅ Build trending ticker detection

SPRINT 6 (Week 9–10): Macro & Geopolitical Engine
  ✅ Integrate FRED API (US macro data)
  ✅ Integrate RBI/MOSPI data (India macro)
  ✅ Implement FII/DII flow tracking
  ✅ Build geopolitical event scoring system
  ✅ Implement sector impact mapping (rate hike → banks, etc.)
  ✅ Build REST endpoint: GET /analysis/macro/{market}
  ✅ Build USD/INR correlation analysis
```

### Phase 3: Prediction & Signals (Weeks 11–16)
```
SPRINT 7 (Week 11–12): ML Prediction Engine
  ✅ Build feature engineering pipeline (45 features)
  ✅ Implement weighted composite scoring model
  ✅ Implement Linear Regression baseline
  ✅ Implement price target estimation algorithm
  ✅ Implement stop-loss calculation (ATR-based)
  ✅ Implement exit date prediction
  ✅ Implement confidence score calculation
  ✅ Train model on 2 years of historical data

SPRINT 8 (Week 13–14): Signal Generator
  ✅ Build Signal Generator Service
  ✅ Implement composite score → BUY/SELL/HOLD logic
  ✅ Implement risk filters (liquidity, market cap, R:R)
  ✅ Implement TradeRecommendation object construction
  ✅ Build Risk Management Service (Kelly Criterion, VaR)
  ✅ Build daily top-N ranking engine
  ✅ Kafka: publish to signals topic

SPRINT 9 (Week 15–16): IPO & Options Engines
  ✅ IPO Analysis Service
     ├── Upcoming IPO data ingestion (SEBI/SEC)
     ├── Valuation vs. peers analysis
     ├── Listing gain predictor (ML model)
     └── Subscription recommendation (Apply/Avoid)
  ✅ Options Analysis Service
     ├── Options chain data ingestion
     ├── IV analysis (IV Rank/Percentile)
     ├── OI analysis (PCR, buildup)
     └── Strategy recommendation (Iron Condor, etc.)
  ✅ Commodity Analysis Service
```

### Phase 4: Platform & APIs (Weeks 17–20)
```
SPRINT 10 (Week 17–18): REST API & WebSocket
  ✅ Complete all REST endpoints (documented above)
  ✅ Implement WebSocket streaming
     ├── /ws/live-quotes
     ├── /ws/signals
     └── /ws/news
  ✅ API Gateway with rate limiting
  ✅ Response caching layer (Redis)
  ✅ Portfolio Service (positions, P&L tracking)

SPRINT 11 (Week 19): Dashboard & Notifications
  ✅ Notification Service
     ├── Email alerts (Spring Mail)
     ├── WebSocket push notifications
     └── Daily morning briefing generation
  ✅ Stock Screener endpoint
  ✅ Batch job: daily report generation (Quartz)
  ✅ Performance tracking (model accuracy dashboard)

SPRINT 12 (Week 20): Testing, Hardening & Docs
  ✅ Unit test coverage > 80%
  ✅ Integration tests for all APIs
  ✅ Load testing (JMeter / Gatling)
  ✅ Security testing (OWASP ZAP)
  ✅ Complete API documentation (Swagger)
  ✅ Docker Compose for local full-stack dev
  ✅ Kubernetes Helm charts for deployment
```

---

## 21. REQUIREMENT TRACEABILITY MATRIX

| Business Req | Functional Req | Service | API Endpoint | Status |
|---|---|---|---|---|
| BR-001 (Multi-Market) | FR-DI-001, FR-DI-002 | market-data-service | /market/quote/{symbol} | Sprint 2 |
| BR-002 (Multi-Asset) | FR-DI-003,4,5 | market-data-service | /market/options, /market/commodities | Sprint 9 |
| BR-003 (Data-backed) | FR-SG-001,2 | recommendation-engine | /recommendations/daily | Sprint 8 |
| BR-003 (Profit %) | FR-ML-005 | ml-prediction-service | /recommendations/stock/{symbol} | Sprint 7 |
| BR-003 (Entry/Exit dates) | FR-ML-003,4 | ml-prediction-service | Included in recommendation | Sprint 7 |
| BR-004 (Real-time < 500ms) | FR-SG-001 | signal-generator | /ws/signals | Sprint 10 |
| BR-004 (Batch reports) | FR-SG-006 | recommendation-engine | /recommendations/daily | Sprint 11 |
| BR-005 (Market data) | FR-DI-001,2 | market-data-service | /market/* | Sprint 2 |
| BR-005 (News/Sentiment) | FR-SA-001,2 | sentiment-analysis | /analysis/sentiment/* | Sprint 5 |
| BR-005 (Social media) | FR-DI-008 | social-media-service | /analysis/sentiment/* | Sprint 5 |
| BR-005 (Macro/Geo) | FR-GE-001 to 009 | macro-geo-service | /analysis/macro/* | Sprint 6 |

---

## 22. IPO BUY/SELL DECISION ENGINE

### 22.1 Why IPOs Need a Separate Model

`RecommendationEngine` and `BacktestRunner` both assume price history exists (SMA200 warm-up,
`WalkForwardValidator` 80/20 splits). A brand-new IPO has **zero** OHLCV bars before listing and
fewer than 20 bars for its first month of trading — the standard TA path is mathematically undefined,
not just noisy. IPOs therefore get a dedicated two-phase model instead of being forced through the
existing pipeline.

The distinction that matters: pre-listing the decision is "**apply** to the IPO or not" (a
subscription decision, output `APPLY_STRONG / APPLY / RISKY / AVOID` — this is already the shape of
`IPOData.recommendation`). Post-listing it becomes "**buy/hold/sell** the now-trading stock" — a
different decision with a different action space. One service phase feeds into the other.

### 22.2 Phase 1 — Pre-Listing (Apply / Avoid)

New service: `com.hft.ipo.IPOAnalysisService` (this closes the gap left in the original design —
`IPOData`/`IPODataRepository` already exist from the foundation build, but no scoring service was
ever implemented against them).

Inputs — all already present on `IPOData`, no new ingestion needed for the scoring math itself:

```
ValuationScore  ← peAtIssuePrice, industryPeAvg, evToSalesAtIssuePrice
DemandScore     ← gmpPercent, retail/qib/nii/overallSubscriptionTimes
QualityScore    ← leadManagerTrackRecordScore
SentimentScore  ← SentimentAnalysisService.analyzeSentiment(companyName)   [reused, existing service]
RegimeContext   ← MacroGeopoliticalService (VIX regime + sector momentum) [reused, existing service]
```

Formulas:

```
ValuationScore = 100 − clamp((peAtIssuePrice / industryPeAvg − 1) × 120, −30, 70)
  // priced below industry P/E scores higher; richly priced issues cap out near 30

DemandScore = clamp(gmpPercent × 1.8, 0, 60)
            + clamp(overallSubscriptionTimes × 2, 0, 30)
            + (qibSubscriptionTimes > retailSubscriptionTimes ? 10 : 0)
  // GMP is weighted heaviest — it is the grey market's own forward price-discovery signal;
  // QIB-led (institutional) oversubscription is a stronger quality tell than retail-led

QualityScore = clamp(leadManagerTrackRecordScore, 0, 100), default 50 when null
  // same null-safe "default to neutral" convention as MLFeatureExtractor (Stage 5)

CompositeScore = 0.25×ValuationScore + 0.35×DemandScore + 0.20×SentimentScore + 0.20×QualityScore
  // decomposes this doc's §16.2 "IPOs: Fundamental 45%, Sentiment 25%" into fields that
  // actually exist on IPOData: Valuation+Quality together stand in for "Fundamental"

predictedListingGainPercent = gmpPercent × 0.75 + (CompositeScore − 50) × 0.3
  // GMP historically overstates actual listing gain ~25%; the second term is a
  // confirmation-bonus / conflict-penalty adjustment, same shape as EnsembleModel (Stage 5)
```

Recommendation thresholds (reuses the existing 4-state `IPOData.recommendation` field):

```
APPLY_STRONG : CompositeScore ≥ 75  AND  predictedListingGainPercent ≥ 15%
APPLY        : CompositeScore ≥ 60
RISKY        : 40 ≤ CompositeScore < 60,  OR  (DemandScore high, ValuationScore < 30 —
                "hype-driven pop, weak fundamentals")
AVOID        : CompositeScore < 40,  OR  peAtIssuePrice > industryPeAvg × 1.5 with DemandScore < 40
```

Re-scoring cadence: `@Scheduled`, **not** a new Kafka topic — IPO subscription data updates a
handful of times a day, not at HFT speed, so streaming infrastructure would be unjustified
complexity here. Runs every 15 minutes during `[subscriptionOpenDate, subscriptionCloseDate]`,
once daily otherwise for `UPCOMING` issues — recommendation can (and does, in practice) flip
mid-window as Day-3 QIB subscription numbers land.

### 22.3 Phase 2 — Post-Listing (Hold / Sell)

Once `OHLCVDataRepository` has a first bar for the symbol, `status` moves to `LISTED` and the
decision becomes a graduated hand-off, keyed off bar count:

```
Day 0 (listingDate) — the flip decision:
  actualListingGainPercent = (listingOpenPrice − issuePriceHigh) / issuePriceHigh × 100

  actualGain ≥ 1.5 × predictedListingGainPercent            → PARTIAL_SELL (lock in the pop)
  actualGain < predictedListingGainPercent AND QualityScore ≥ 60 → HOLD  (thesis intact)
  actualGain < 0 AND QualityScore < 60                       → SELL (pop failed AND thesis weak)
  otherwise                                                  → HOLD

Days 1–19 (< WARMUP_BARS — reuses BacktestRunner's existing WARMUP_BARS=20 constant
           as the graduation threshold, not a new number):
  Full TechnicalAnalysisService is undefined (needs SMA200). A reduced-indicator scorer
  (com.hft.ipo.IPOLifecycleScorer) runs instead, using only what's computable this early:
    • 5-day / 10-day realized volatility
    • "held above listing-day open" gap-fill check
    • volume trend vs Day 0–2 (fading volume = fading momentum)
    • relative strength vs sector index
  Undefined long-window indicators are omitted from the score, not defaulted to zero —
  same degrade-gracefully convention MLFeatureExtractor uses for missing MacroData/SentimentData.

Day 20+ (bar count ≥ WARMUP_BARS):
  Symbol "graduates" — IPOAnalysisService hands off to the standard RecommendationEngine /
  TechnicalAnalysisService pipeline. From here it is scored exactly like any other listed stock;
  IPOData's job is done. Graduation check: earliest OHLCVData bar for the symbol is ≥ 20
  trading days old.
```

### 22.4 New Surface Area

```
com.hft.ipo.IPOAnalysisService     — Phase 1 scoring + @Scheduled re-scoring job
com.hft.ipo.IPOLifecycleScorer     — Phase 2 reduced-indicator scorer, Day 0–19
com.hft.graphql.IPOResolver        — @QueryMapping ipoRecommendation(symbol), activeIpoRecommendations
REST GET /recommendations/ipo      — speced in §12.1, not yet implemented; delivered by this stage
```

No new Kafka topic, no new port — reuses existing `@Scheduled` infra and the existing GraphQL/REST
layer. IPO application decisions and post-listing trade decisions are surfaced as two clearly
distinct fields (`ipoRecommendation` vs the standard `TradeRecommendation`) so the UI never
conflates "should I subscribe" with "should I sell."

---

## 23. WEB UI ARCHITECTURE

### 23.1 Design Goals

```
- Lightest possible payload — no SPA framework, no bundler required in production
- One-click theme cycle: Light → Dark → Auto (Auto follows the OS's own light/dark schedule)
- One responsive layout, phone width through ultra-wide monitor — no separate mobile build
```

### 23.2 Why Static-Served, Not a Separate Frontend Service

A separate Node/Nginx frontend service would double the ops surface (new container, new port,
new CORS policy, new CI job) to satisfy a "lightest UI" goal — a contradiction. Spring Boot already
serves static content from `src/main/resources/static/` on the **same origin** as `/graphql`, REST
(`/market`, `/analysis`, `/recommendations`), and `/graphql-ws`. No CORS config, no new port, ships
inside the existing `bootJar`.

### 23.3 Stack

| Layer | Choice | Why |
|---|---|---|
| Markup | Plain HTML5, single `index.html` app shell | Zero build step |
| Styling | CSS3 custom properties + Grid/Flexbox | Theming + responsive layout, no CSS framework |
| Interactivity | Vanilla ES modules JS | No React/Vue/Angular runtime tax |
| Live data | Native `fetch` (REST) + native `WebSocket` with a ~3KB hand-rolled `graphql-ws` subprotocol client | Apollo Client alone is 30KB+ gzipped — not needed just to read + subscribe |
| Charts | Hand-rolled Canvas 2D sparklines/candlesticks | No chart.js/d3 dependency for MVP |
| State | `localStorage` (theme) + in-memory JS objects | No Redux/MobX needed at this scale |

Optional, deliberately deferred: Alpine.js (~15KB gzip) — add only if hand-rolled DOM updates
become a real maintenance problem, not up front.

### 23.4 File Layout

```
src/main/resources/static/
  index.html                — app shell: header, nav, main grid, theme-toggle button
  css/
    theme.css                 — CSS custom-property palettes (light / dark / auto)
    layout.css                 — responsive grid, breakpoints
  js/
    app.js                      — boot, hash router, view mounting
    graphql-client.js           — fetch() POST helper + graphql-ws subscription client
    theme-toggle.js             — 3-state cycle, localStorage persistence, FOUC guard
    views/
      dashboard.js                — StockDashboard query + liveQuote subscription
      recommendations.js          — /recommendations/daily + watchlistSignals subscription
      backtest.js                  — runBacktest mutation + backtestProgress subscription
      ipo.js                        — /recommendations/ipo + ipoRecommendation query (§22)
```

### 23.5 Theme Mechanics — Light / Dark / Auto

```css
:root                                              { --bg:#fff; --fg:#111; --accent:#2563eb; }  /* light, default */
:root:not([data-theme="light"]):not([data-theme="dark"])
  @media (prefers-color-scheme: dark)              { --bg:#0b0e14; --fg:#e6e6e6; }               /* auto: follows OS */
:root[data-theme="dark"]                           { --bg:#0b0e14; --fg:#e6e6e6; }               /* explicit dark wins */
```

- One header button cycles `light → dark → auto → light`; icon reflects current state (☀ / 🌙 / 🖥).
- Choice persisted to `localStorage.theme`, applied via a small inline `<script>` in `<head>`
  before first paint (flash-of-wrong-theme guard).
- **"Auto" is the daylight mode**: it defers to the OS's own light/dark schedule rather than
  hardcoding a clock or doing geolocation/sunrise math the OS already owns.

### 23.6 Responsive Layout

```
Mobile   (<768px):    1-column stack, bottom tab bar nav, 44px+ touch targets
Tablet   (768–1200px): 2-column CSS Grid, left icon-rail nav
Desktop  (>1200px):    auto-fit grid — repeat(auto-fit, minmax(320px, 1fr)), left sidebar nav
```

`@media (hover: hover)` gates hover-only affordances so touch devices never get stuck-hover states.

### 23.7 Data Flow

```
First paint  → REST GET (/market/quote, /recommendations/daily, /recommendations/ipo)
               fast first paint, no WebSocket handshake needed
Live updates → GraphQL subscriptions over the existing /graphql-ws:
               liveQuote, liveSignals, watchlistSignals, backtestProgress, ipoRecommendation
Auth         → JWT in sessionStorage (not localStorage — smaller XSS persistence window),
               Authorization: Bearer on REST/GraphQL, connection_init payload on the WS handshake
               — reuses the existing Stage 4 SecurityConfig JWT filter chain, zero backend changes
```

### 23.8 Deployment Impact

No new service, no new port, no new container, no Node/npm in the production build path.
`gradle bootJar` packages `static/` automatically into the same JAR. Node is optional and
local-dev-only (e.g. a live-reload static file server) — never a build dependency.

---

## 24. INTELLIGENCE SOURCING & ADAPTIVE FUSION (ASRB)

### 24.1 The Gap This Closes

A code audit (2026-08-16) found that the platform's "AI-powered, news/social/macro-driven"
positioning was only partly real:

```
REAL:     US/India quotes (Alpha Vantage, NSE), US fundamentals (Alpha Vantage OVERVIEW),
          Alpha Vantage NEWS_SENTIMENT
WIRED BUT OFF BY DEFAULT: NewsAPI.org, FRED macro data (both real integrations, gated by
          config flags defaulting to false outside prod)
FAKE:     Social sentiment = Math.random() noise on top of the news score. Twitter/Reddit
          config keys declared in YAML, never read by any Java code. US macro fallback,
          all of India macro, and geopolitical risk = hardcoded literals. India fundamentals
          = DB passthrough with no external fetch.
```

Sections 24 and 25 replace this with real sourcing and a purpose-built fusion algorithm,
plus the identity/consent infrastructure needed to do it legally.

### 24.2 Real Sources — Free/No-Key Tier (build now)

```
NewsAPI.org        — flip existing `newsApiEnabled` default to true (already coded)
FRED                — flip existing `fredEnabled` default to true (already coded)
Reddit              — free API tier, OAuth app-only auth, r/investing r/stocks r/wallstreetbets
                       r/IndiaInvestments — replaces the Math.random() social score
StockTwits          — free public API, no auth required for read endpoints
SEC EDGAR           — full-text search API, free, no key. 10-K/10-Q/8-K filings, insider
                       trading forms — real company-history signal (§3.1's FR-DI-005 origin)
GDELT Project       — free, open, ~100-language global news/event database. Real replacement
                       for the hardcoded geopoliticalRiskScore constants
RBI / NSE India     — RBI DBIE open data (rates, CPI, GDP), NSE FII/DII daily flow reports —
                       replaces 100%-hardcoded India macro
Screener.in / NSE   — replaces DB-only India fundamentals placeholder
  filings
WebIntelligenceCrawler — RSS/sitemap-driven (not a general spider), jsoup-based fetch+parse,
                       per-domain politeness/rate-limiting, robots.txt-respecting. Walks
                       company IR pages and news sites' own published feeds. Scheduled batch,
                       not real-time. GDELT already covers most broad open-web news crawl
                       needs, so this is for targeted per-symbol IR/filing pages GDELT misses.
```

**Hard constraint carried through every source above and the crawler:** no ToS-violating
scraping, no robots.txt bypass, no credential-based access without the explicit read-only
consent flow in §25. This is a standing constraint on every mechanism in this document, not
a one-time check — see §24.6.

Twitter/X API and paid press-release wires are deferred: Twitter/X has no free tier, and
wire services are typically paid — both require a budget decision, not an engineering one.

### 24.3 User-Supplied Read-Only Sources (BYOC)

Site users may optionally connect their own Twitter/X, Reddit, or paid news portal account
(read-only OAuth scopes only) to widen the source pool. See §25.3 for the account model —
this requires real user identity, which did not exist in the codebase prior to this stage.

**Policy: platform-pooled benefit.** A connected account's read-only signal feeds the shared
source pool used by every user's predictions, not just the connecting user's own — this was
an explicit product decision (favoring data coverage over the simpler per-user-only model),
made on the condition that it stays within per-source legal/ToS limits (§24.6) and carries
an explicit, accurate consent disclosure:

> "The information read from your connected account is used read-only. We never store your
> account's identity content or personal details — only a derived signal (a sentiment/topic
> score) is retained, and it helps improve prediction robustness for all users of the
> platform, not only you."

That disclosure needs one precision fix before it ships (§24.6): "personal/identity
information won't be used/stored EVER" is likely too strong a claim to make verbatim — the
`ConnectedAccount` record itself (which account is linked, to which platform user) is
personal data under GDPR/DPDP even if post content isn't retained. The corrected claim is
"we don't store your posts, messages, or account content — only a derived numeric signal,"
which is both true and still a strong, user-friendly guarantee.

### 24.4 The ASRB Algorithm

**Adaptive Source Reliability Bandit** — fuses N heterogeneous, individually unreliable,
non-stationary, sometimes-correlated, sometimes-adversarial information sources into one
posterior confidence score per symbol. Full mathematical specification, prior-art
comparison, and novelty argument: **`docs/ASRB_TECHNICAL_DISCLOSURE.md`** (prepared as
engineering input for IP counsel — see that document's disclaimer before relying on any
novelty claim in it).

Summary pipeline (six steps, run per source per scoring pass):

```
1. CORRELATION DISCOUNT   — down-weight a source's evidence in proportion to how much its
                             recent signal is explained by co-movement with already-counted
                             sources this pass (prevents N correlated sources reporting the
                             same underlying event from being counted as N independent
                             confirmations)

2. MISINFORMATION DISCOUNT — down-weight evidence by a risk score built from: this source's
                             historical reliability (reuses step 3's own posterior), whether
                             independent uncorrelated sources corroborate the specific claim,
                             and whether claim-velocity is spiking faster than corroboration
                             can follow (the classic early-rumor signature). A high-risk,
                             high-velocity claim ALSO emits a separate narrative/reputational
                             RiskLevel flag on the affected symbol — a false narrative can
                             still move price and damage a real business (worked example:
                             §ASRB doc §6) even while we discount it as a truth signal

3. POSTERIOR UPDATE        — update this source's Bayesian-linear reliability posterior with
                             the doubly-discounted evidence, applying an exponential decay to
                             existing sufficient statistics first (non-stationarity: a source
                             can silently degrade and the posterior must be able to forget)

4. STABILITY INDEX         — compute this source's posterior drift-velocity and variance-
                             shrinkage rate, calibrated relative to the current cross-source
                             population (not a fixed constant — self-calibrates across
                             market regimes)

5. POLICY SELECTION        — stability index above its population-relative threshold → rank
                             by Gittins index (provably optimal once a source is
                             well-characterized and stationary). Below threshold (new,
                             sparse, or actively drifting source) → Thompson-sample from the
                             posterior instead (Gittins indices aren't valid outside the
                             stationary case; TS degrades gracefully)

6. AGGREGATE                — blend weighted samples/indices into the composite posterior
                             score, feeding RecommendationEngine / EnsembleModel /
                             IPOAnalysisService exactly as today — no interface changes to
                             the existing consumers, only better inputs than the current
                             random/hardcoded values
```

Reward signal (what "was this source right?" means in step 3): the existing
`recordSignalOutcome` mutation and `BacktestTrade` results — both already built in Stages 5–6.
No new outcome-tracking infrastructure, no training loop, no replay buffer, no GPU — this
rides on Redis with the same TTL pattern `ModelPerformanceTracker` already established.

### 24.5 Open Engineering Decisions

```
LSTM vs. transformer/attention encoder for the neural-linear context layer and for claim/
  stance clustering (the corroboration-count feature in step 2): a transformer-based sentence
  encoder is the stronger default by current practice for both temporal source-behavior
  sequences and claim-similarity clustering; LSTM remains viable if lower compute/complexity
  is preferred. Not yet decided.
Exact discount/decay constants (γ for non-stationarity, correlation-penalty curve,
  risk-aversion factor in step 2): require calibration against real backtest data, not
  guessed up front.
```

### 24.6 Legal & Compliance Posture

**This is engineering-informed analysis, not legal advice — recommend counsel review before
any BYOC pooling ships to real users, especially outside the jurisdictions named below.**

Two separate legal axes apply, and they don't move together:

```
1. PLATFORM DEVELOPER TERMS (contract law — this is the sharper, more universal risk)
   Each source's Developer Agreement is a contract between us and that platform, not
   between us and the connecting user. A connecting user's consent does not waive OUR
   contractual obligations to Twitter/X, Reddit, etc. Several platforms' developer terms
   historically restrict using one authenticated user's access to build aggregate products
   that serve or benefit OTHER users/third parties, independent of what the connecting user
   agreed to. This must be checked PER SOURCE, individually, before enabling pooling for
   that source — a blanket "we have user consent" does not clear this. Likely order of
   risk (lowest to highest, subject to actual review): Reddit's API terms have historically
   been more permissive for this use than Twitter/X's; paid news portal subscriber
   agreements (WSJ/FT/Bloomberg-style) typically explicitly forbid extracting content via
   your own login to benefit non-subscribers, which platform-pooling would do by definition.

2. DATA PROTECTION LAW (privacy law — jurisdiction-dependent, but broadly applicable)
   GDPR (EU/UK), India's DPDP Act 2023 (directly relevant — this platform explicitly targets
   NSE/BSE/Indian users), and CCPA/CPRA (California) all apply if users from those
   jurisdictions connect accounts. Consent-based processing is workable (this is an opt-in
   feature), but requires: accurate disclosure of what's actually retained (§24.3's
   correction), a defined retention period even for derived signals, and a working
   deletion/disconnect path (§25.3) — "we don't store personal data" is not the same claim
   as "we don't store your post content," and only the second is accurate here since the
   ConnectedAccount linkage itself is personal data.
```

Also required before production, independent of BYOC: a real Terms of Service and Privacy
Policy for the platform itself (does not exist yet), and the existing "informational purposes
only, not investment advice" disclaimer (already used elsewhere in this doc) should be
reviewed against actual investment-advice regulation (SEC/RIA rules in the US, SEBI rules in
India) given predictions are now partly built from pooled third-party social signal.

---

## 25. IDENTITY, ADMIN & BILLING PLATFORM

### 25.1 Why This Exists

`SecurityConfig.java` has a JWT filter chain, but it's disabled
(`// http.addFilterBefore(jwtAuthFilter, ...)`, line 58) and there is no `User` entity
anywhere in the codebase. §24.3's pooled BYOC model needs real per-user identity to hang
`ConnectedAccount` records off — this stage builds that, plus the admin/billing surface
needed to operate a real multi-user product (registration, usage tracking, charges,
violation handling, deregistration).

### 25.2 Scope

```
Registration / login       — real User entity, actual JWT issuance (the currently-disabled
                              filter gets wired up, not just left commented out)
Roles                       — USER, ADMIN at minimum (matches the ROLE_PREMIUM mention
                              already sketched in §18 Security Architecture)
Admin console               — user list/search, suspend/reinstate, usage/activity view,
                              ToS-violation handling (e.g. a connected account a provider
                              flags or revokes), manual deregistration
Billing                     — charges, transactions, payment method, plan/tier — deferred
                              provider choice (Stripe-shaped integration is the likely fit,
                              not yet decided)
Deregistration              — user-initiated account deletion; must cascade to revoking and
                              deleting all ConnectedAccount tokens (§24.3, §24.6 axis 2)
```

This is a genuinely separate stage from the ML/data-sourcing work in §24 — different
concerns (auth, payments, admin UX) — and is sequenced independently.

### 25.3 Connected Account Model

```
com.hft.identity.User                  (@Entity — does not exist yet, this stage creates it)
com.hft.identity.ConnectedAccount      (@Entity)
  userId, provider (TWITTER_X | REDDIT | ...), encryptedAccessToken,
  encryptedRefreshToken, grantedScopes, connectedAt, expiresAt, lastUsedAt, revokedAt
com.hft.identity.ReadOnlyScopePolicy   — per-provider allowlist of permitted OAuth scopes;
                                         connect flow checks granted scopes against this
                                         allowlist and FAILS CLOSED — a provider bundle that
                                         includes write scope is refused at connect-time,
                                         not merely "not used" after the fact
com.hft.identity.ConnectedAccountService — connect (OAuth2 auth-code flow, read scopes only),
                                         disconnect (revokes with provider, hard-deletes
                                         locally), refresh
```

Token storage: field-level encryption, key managed outside the application config (KMS or
equivalent) — not an app-level key sitting beside the database, given these are third-party
account credentials.

### 25.4 Test User

Until this stage is implemented, there is no functioning login. Once built, **PTD2315**
(git identity on this repo) / **omanu01@gmail.com** is the designated first seed user, with
both USER and ADMIN roles, for testing the connected-account flow end to end.

---

## APPENDIX A: SAMPLE RECOMMENDATION OUTPUT

```json
{
  "date": "2026-05-28",
  "market": "INDIA_NSE",
  "totalAnalyzed": 1847,
  "topRecommendations": [
    {
      "rank": 1,
      "symbol": "HDFCBANK.NSE",
      "companyName": "HDFC Bank Limited",
      "signal": "STRONG_BUY",
      "assetType": "STOCK",
      "timeHorizon": "MEDIUM_TERM",
      "riskLevel": "LOW",
      "entryPrice": 1672.50,
      "entryPriceRange": "₹1,660–₹1,685",
      "targetPrice": 1980.00,
      "stopLossPrice": 1585.00,
      "expectedProfitPercent": 18.38,
      "maxRiskPercent": 5.20,
      "riskRewardRatio": 3.54,
      "entryDate": "2026-05-28",
      "exitDate": "2026-07-30",
      "holdingPeriodDays": 63,
      "compositeScore": 87.4,
      "confidencePercent": 82.0,
      "scores": {
        "technical": 85,
        "fundamental": 88,
        "sentiment": 76,
        "macro": 72,
        "ml": 79
      },
      "keyReasons": [
        "RSI(14) at 42 — emerging from oversold, historically strong bounce zone",
        "Golden cross forming: SMA50 approaching above SMA200",
        "MACD bullish crossover on weekly chart confirmed",
        "Q4 FY26 PAT beat by 14%, NII growth 18% YoY",
        "Valuation at 1.8× P/B — historically cheap for HDFC Bank",
        "FII bought ₹3,200Cr in last 5 trading sessions",
        "RBI rate cut cycle beginning: NBFC/Banking sector tailwind"
      ],
      "keyRisks": [
        "Global recession fears could trigger FII selling",
        "India VIX above 14 — some broad market nervousness",
        "NPAs may rise in SME segment post credit cycle peak"
      ],
      "relatedNews": [
        "HDFC Bank Q4 profit rises 22% to ₹17,622Cr, beats estimates",
        "RBI cuts repo rate by 25bps for second consecutive time",
        "FII inflows hit 3-month high as India macro outlook improves"
      ],
      "sectorName": "Banking & Financial Services",
      "sectorOutlook": "POSITIVE",
      "dataSources": [
        "NSE Live Data", "Alpha Vantage Fundamentals", 
        "NewsAPI Headlines", "Twitter Sentiment (14K mentions)",
        "FRED Macro Data", "RBI Policy Statement"
      ]
    }
  ]
}
```

---

## APPENDIX B: PROJECT STRUCTURE

```
hft-market-intelligence/
├── pom.xml (parent - multi-module)
├── docs/
│   └── HFT_ARCHITECTURE.md   ← this file
│
├── src/main/java/com/hft/
│   ├── HFTApplication.java
│   ├── config/
│   │   ├── AppConfig.java
│   │   ├── KafkaConfig.java
│   │   ├── RedisConfig.java
│   │   ├── WebSocketConfig.java
│   │   └── SecurityConfig.java
│   │
│   ├── model/
│   │   ├── enums/
│   │   │   ├── AssetType.java
│   │   │   ├── Market.java
│   │   │   ├── SignalType.java
│   │   │   ├── RiskLevel.java
│   │   │   └── TimeHorizon.java
│   │   ├── domain/
│   │   │   ├── StockQuote.java
│   │   │   ├── TechnicalIndicators.java
│   │   │   ├── SentimentData.java
│   │   │   ├── FundamentalData.java
│   │   │   ├── MacroData.java
│   │   │   ├── IPOData.java
│   │   │   ├── OptionsData.java
│   │   │   └── TradeRecommendation.java
│   │   └── dto/
│   │       ├── RecommendationRequest.java
│   │       ├── RecommendationResponse.java
│   │       └── ScreenerRequest.java
│   │
│   ├── service/
│   │   ├── data/
│   │   │   ├── MarketDataService.java (interface)
│   │   │   ├── AlphaVantageService.java
│   │   │   ├── YahooFinanceService.java
│   │   │   └── NSEIndiaService.java
│   │   ├── analysis/
│   │   │   ├── TechnicalAnalysisService.java
│   │   │   ├── FundamentalAnalysisService.java
│   │   │   ├── SentimentAnalysisService.java
│   │   │   └── MacroGeopoliticalService.java
│   │   ├── social/
│   │   │   ├── NewsService.java
│   │   │   ├── TwitterService.java
│   │   │   └── RedditService.java
│   │   ├── ml/
│   │   │   └── MLPredictionService.java
│   │   ├── signal/
│   │   │   ├── SignalGeneratorService.java
│   │   │   └── RecommendationEngine.java
│   │   ├── risk/
│   │   │   └── RiskManagementService.java
│   │   ├── ipo/
│   │   │   └── IPOAnalysisService.java
│   │   ├── options/
│   │   │   └── OptionsAnalysisService.java
│   │   ├── portfolio/
│   │   │   └── PortfolioService.java
│   │   └── notification/
│   │       └── NotificationService.java
│   │
│   ├── controller/
│   │   ├── RecommendationController.java
│   │   ├── MarketDataController.java
│   │   ├── AnalysisController.java
│   │   ├── PortfolioController.java
│   │   └── IPOController.java
│   │
│   ├── repository/
│   │   ├── TradeRecommendationRepository.java
│   │   ├── FundamentalDataRepository.java
│   │   └── PortfolioPositionRepository.java
│   │
│   └── util/
│       ├── TechnicalIndicatorUtil.java
│       ├── SentimentUtil.java
│       ├── DateUtil.java
│       └── MarketHoursUtil.java
│
└── src/main/resources/
    ├── application.yml
    ├── application-dev.yml
    └── application-prod.yml
```

---

*Document Version: 1.0 | Last Updated: 2026-05-28*
*HFT Market Intelligence Platform — HMIP*
*All trading signals are for informational/educational purposes only.*
*Not investment advice. Consult a SEBI/SEC-registered advisor for financial decisions.*
