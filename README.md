# Matching Engine Demo

A high concurrency user matching engine demonstration built with Java 17 and Spring Boot 3.3.

## Modules
- `matching-core` - placeholder for future core modules.
- `contracts` - gRPC definitions shared across services.
- `services/gateway-ws` - WebSocket gateway exposing matching APIs.
- `services/match-shard` - gRPC match shard service.
- `services/profile-service` - user profile management.
- `services/allocator-service` - shard allocation logic.
- `services/notifier-service` - push notifications.
- `services/audit-service` - audit log collector.
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
