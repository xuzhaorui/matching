# Matching Engine Demo

A high concurrency user matching engine demonstration built with Java 17 and Spring Boot 3.3.

## Modules
- `matching` - placeholder for future core modules.
- `contracts` - gRPC definitions shared across services.
- `gateway-ws` - WebSocket gateway exposing matching APIs.
- `match-shard` - gRPC match shard service.
- `profile-service` - user profile management.
- `allocator-service` - shard allocation logic.
- `notifier-service` - push notifications.
- `audit-service` - audit log collector.
- `infra` - Docker compose and monitoring configuration.
- `bench` - k6 benchmark scripts.

## Quick start
```bash
docker compose -f infra/docker-compose.yml up --build
```

## Benchmark
```bash
k6 run bench/k6-ws.js --out json=bench/report/result.json
```

Generated reports will be available under `bench/report`.
