# Testing

## Automated

```bash
./gradlew build
```

JUnit 5 tests under `src/test/java/com/larpsmp/moneyevent/`. Many integration tests need Postgres (`LARPSMP_JDBC_URL`).

GitHub Actions (`.github/workflows/ci.yml`) runs `cleanTest test` plus a JAR build against a Postgres 16 service on PRs and pushes to `main`.

### Coverage areas (existing)

| Area | Examples |
|------|----------|
| Auth | Signup/login/verify, rate limit, password/code hashers, integration flows |
| Money | Starting balance, transfer/add/remove/set, concurrency, team wealth, formats, command controller |
| Teams | CRUD, captains, tiers, colors, soft warnings |
| Player state | Repository round-trip, codec, session manager |
| Config / email | EnvSettings, templates |
| Combat | Attack memory window, block ownership, kill attribution → death settlement |
| Capture | Advantage math, progress engine, session payout/idempotency/restart, temp-block rows, config load |
| Auction | Session/reps lock, bid rejects, `/auction pay` settlement idempotency, previous-bidder forfeit, runtime restore |
| Ace Hunt | Placement scoring (2/1/0, worse-place ties), prep non-scoring, hunt kill once, restart resume, phase timers, stop clears session |
| Gladiators | Double round-robin shape, kit-ban turns, 3v3/1v1 scoring, skip quota, payout idempotency ($0 place), kill money off/on, restart mid-session |
| Roles / authz | Competitor/spectator mutual exclusion, standings/wealth filters, death skip for non-competitors, plugin admin vs server operator |

### Gaps

No automated tests yet for Bukkit sidebar UI, live in-world Paper damage/occupancy/barrier meshes, boss-bar pixels, or Resend HTTP client live calls.

## Manual / local Paper

Full two-client refresher (boot steps + every Implemented feature):  
**[Manual testing refresher](manual-testing-refresher.md)**.

Quick smoke after `./scripts/dev-server.sh`:

1. Sign up + email verify + wallet $200
2. Reconnect + log in (no second grant of a *new* wallet row — balance preserved)
3. `/pay` online and offline
4. `/larp money give|take|set`
5. `/larp teams` dialog: assign team, captain, tier
6. Sidebar Team Money updates when roster or balances change
7. Restart Paper; confirm wallets, teams, tiers, bodies persist

Full multiplayer economy scenarios for death/rescue/captures belong in the [roadmap](../05-status/roadmap.md) once those features exist.
