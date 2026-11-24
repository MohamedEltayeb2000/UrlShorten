# Performance results - phase 3

Generated 2026-11-24T23:49:50Z

## Environment
- OS: Microsoft Windows NT 10.0.26100.0
- CPU cores: 4
- JDK: openjdk version "25.0.5.1" 2026-11-17 LTS
- Node: v24.13.0

## Results
| Metric | Budget | Measured | Pass |
|---|---|---|---|
| parallel/sequential time ratio (6 stages, 4 parallel) | 0.7 | 0.60 | True |
| analytics summary ms (after 300 clicks) | 1000 | 182 | True |
| redirect p95 ms (300 requests, 16 threads) | 1000 | 211 | True |
| redirect errors | 0 | 0 | True |
| frontend bundle gzip KB | 500 | 82.9 | True |
