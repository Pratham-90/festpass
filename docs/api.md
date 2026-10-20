# FestPass REST API

All endpoints are under `/api` and use JSON. Errors are returned as RFC 7807 `ProblemDetail` objects with a stable `code` field (added in Phase 1).

## Health

### `GET /api/health`

Public. Reports whether the API and its database are reachable.

Response `200 OK`:

```json
{
  "status": "UP",
  "database": "UP",
  "time": "2026-10-20T06:30:00Z"
}
```

`status` is `DEGRADED` and `database` is `DOWN` when PostgreSQL cannot be reached.
