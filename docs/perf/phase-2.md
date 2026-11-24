# Performance results - phase 2

Generated 2026-11-24T23:17:46Z

## Environment
- OS: Microsoft Windows NT 10.0.26100.0
- CPU cores: 4
- JDK: openjdk version "25.0.5.1" 2026-11-17 LTS
- Node: v24.13.0

## Results
| Metric | Budget | Measured | Pass |
|---|---|---|---|
| analytics summary ms (after 300 clicks) | 1000 | 356 | True |
| redirect p95 ms (300 requests, 16 threads) | 1000 | 409 | True |
| redirect errors | 0 | 0 | True |
| frontend bundle gzip KB | 500 | 81.5 | True |
