# Testing

## Automated

```bash
./gradlew build
```

JUnit 5 tests under `src/test/java/com/larpsmp/moneyevent/`. Many integration tests need Postgres (`LARPSMP_JDBC_URL`).

### Coverage areas (existing)

| Area | Examples |
|------|----------|
| Auth | Signup/login/verify, rate limit, password/code hashers, integration flows |
| Money | Starting balance, transfer/add/remove/set, concurrency, team wealth, formats, command controller |
| Teams | CRUD, captains, tiers, colors, soft warnings |
| Player state | Repository round-trip, codec, session manager |
| Config / email | EnvSettings, templates |

### Gaps

No automated tests yet for Bukkit sidebar UI, death/rescue/kill/auction gameplay (not implemented), or Resend HTTP client live calls.

## Manual / local Paper

Use `./scripts/dev-server.sh`, then exercise:

1. Sign up + email verify + wallet $200
2. Reconnect + log in (no second grant of a *new* wallet row — balance preserved)
3. `/pay` online and offline
4. `/larp money give|take|set`
5. `/larp teams` dialog: assign team, captain, tier
6. Sidebar Team Money updates when roster or balances change
7. Restart Paper; confirm wallets, teams, tiers, bodies persist

Full multiplayer economy scenarios for death/rescue/captures belong in the [roadmap](../05-status/roadmap.md) once those features exist.
