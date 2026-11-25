# Performance results - phase 4

Generated 2026-11-25T00:08:04Z

## Environment
- OS: Microsoft Windows NT 10.0.26100.0
- CPU cores: 4
- JDK: openjdk version "25.0.5.1" 2026-11-17 LTS
- Node: v24.13.0

## Results
| Metric | Budget | Measured | Pass |
|---|---|---|---|
| parallel/sequential time ratio (6 stages, 4 parallel) | 0.75 | 0.59 | True |
| analytics summary ms (after 300 clicks) | 1000 | 443 | True |
| redirect p95 ms (300 requests, 16 threads) | 1000 | 430 | True |
| redirect errors | 0 | 0 | True |
| frontend bundle gzip KB | 500 | 84.6 | True |
