# Performance results - phase 1

Generated 2026-11-24T22:46:34Z

## Environment
- OS: Microsoft Windows NT 10.0.26100.0
- CPU cores: 4
- JDK: openjdk version "25.0.5.1" 2026-11-17 LTS
- Node: v24.13.0

## Results
| Metric | Budget | Measured | Pass |
|---|---|---|---|
| redirect p95 ms (300 requests, 16 threads) | 1000 | 243 | True |
| redirect errors | 0 | 0 | True |
| frontend bundle gzip KB | 500 | 80.9 | True |
