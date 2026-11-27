# Production readiness audit

**Plugin:** MoneyEvent (`com.larpsmp.moneyevent`)  
**Started:** 2026-11-27  
**Completed:** 2026-11-27  
**Git baseline:** `main` @ `6e028a6` (clean working tree at start)  
**Production target:** Single Paper 1.21.11 process + PostgreSQL 16 (not multi-node)  
**Constraint:** No live deploy, no production DB changes, no DNS/hosting changes

Status values: `NOT REVIEWED` | `REVIEWED` | `ISSUE IDENTIFIED` | `FIX IN PROGRESS` | `FIXED` | `VERIFIED` | `BLOCKED` | `ACCEPTED RISK`

---

## 1. Executive summary

LarpSMP’s MoneyEvent plugin is feature-complete for the coded competition systems (auth, economy, teams, combat, capture, spawn, auctions, Ace Hunt, Gladiators, roles/authz). A deep audit found real integrity and security defects; those that were safely fixable in-repo were remediated and covered by tests.

**Verdict: READY WITH CONDITIONS**

The plugin can be prepared for an event-host deploy, but ops prerequisites (hosting, backups, roster, map, secrets, playtest) remain outside this code audit. In-game two-client play was not executed here.

---

## 2. Baseline

| Check | Result |
|-------|--------|
| `git status` | Clean, `main...origin/main` |
| Postgres `127.0.0.1:5433` | Accepting connections |
| Baseline `./gradlew cleanTest test` | **180 tests, 0 failures** |
| Baseline `./gradlew build` | SUCCESS |
| Post-fix `./gradlew cleanTest test build` | **183 tests, 0 failures**; JAR `money-event-0.1.0.jar` |
| CI | Added `.github/workflows/ci.yml` (not yet observed on GitHub) |
| In-game two-client playtest | `BLOCKED` (no Minecraft client in this environment) |

---

## 3. System inventory

| System | Package / path | Status | Notes |
|--------|----------------|--------|-------|
| Bootstrap / lifecycle | `MoneyEventPlugin` | VERIFIED | Fail-closed when DB down; severe warning if `auth.enabled=false` |
| Auth / email / rate limits | `auth/`, `email/` | VERIFIED | Pepper gate, login timing, recovery UX fixed |
| Roles / authz | `AccountPermissionAuthorizer`, V015 | VERIFIED | Production authorizer on `main` |
| Economy / wallets | `wallet/`, V003, V007 | VERIFIED | Death+rescue atomic; auction settle atomic |
| Teams / participation | `team/` | REVIEWED | Competitor filters wired |
| Player bodies / identity | `playerstate/` | REVIEWED | Autosave + quit wipe; concurrent login in-memory |
| Combat / kills | `combat/` | REVIEWED | 60s window; deathId per kill |
| Capture points | `capture/` | VERIFIED | Failed payout leaves session retryable |
| Spawn protection | `spawn/` | REVIEWED | Respawn-only; config AABB |
| Auctions | `auction/` | VERIFIED | Atomic pay + settlement recovery |
| Ace Hunt | `acehunt/` | VERIFIED | Environmental ace survival by design; test added |
| Gladiators | `gladiator/` | VERIFIED | Account-based admin exemption; payout retry message |
| Commands / HUD | `command/`, `display/` | REVIEWED | Single sidebar; account-based `/larp` authz |
| Offline pay notices | `notification/` | ACCEPTED RISK | Filesystem under plugin data folder |
| Config / migrations | `config/`, `db/`, V001–V015 | VERIFIED | Pepper policy + docs updated |
| Build / Docker / scripts | Gradle, `docker/`, `dev-server.sh` | REVIEWED | `plugin.yml` libraries match Gradle versions |
| Documentation | `documentation/` | VERIFIED | Stale claims corrected; audit linked |
| CI | `.github/workflows/ci.yml` | FIXED | Postgres service + Gradle test/build |

---

## 4. Findings

| ID | Severity | Area | Status | Summary |
|----|----------|------|--------|---------|
| F1 | CRITICAL | Auction pay | VERIFIED | Transfer then settlement could diverge → double-charge risk. Fixed with atomic transfer+settlement and lot recovery. |
| F2 | HIGH | Capture payout | VERIFIED | Session/day completion on failed payout. Fixed: leave ACTIVE and retry same `captureId`. |
| F3 | HIGH | Death + rescue | VERIFIED | Separate commits. Fixed: single `commitSettlement` with both settlement rows. |
| F4 | HIGH | Email pepper | VERIFIED | Public/dev pepper with Resend. Fixed: email challenges require explicit `LARPSMP_EMAIL_CODE_PEPPER`. |
| F5 | MEDIUM | Auth enumeration | VERIFIED | Login Argon2 skip; recovery cooldown UX. Fixed: dummy hash verify; cooldown maps to Accepted. |
| F6 | MEDIUM | Signup enumeration | ACCEPTED RISK | Distinct username/email taken messages kept for private 12-player UX. |
| F7 | MEDIUM | Gladiator authz | VERIFIED | Replaced Bukkit `isOp()` with account admin check. |
| F8 | MEDIUM | Gladiator payout | ACCEPTED RISK | Partial team pay before abort remains; re-run idempotent; ops message improved. |
| F9 | LOW | Ace Hunt tests | VERIFIED | Environmental ace death survival coverage added. |
| F10 | LOW | Docs / ops | VERIFIED | README/$200, roadmap, schema, secrets docs updated. |
| F11 | INFORMATIONAL | Multi-node | ACCEPTED RISK | Single-JVM design; documented as unsupported. |
| F12 | INFORMATIONAL | Offline pay files | ACCEPTED RISK | Notices on disk; money already in Postgres. |

---

## 5. Remediation (code)

### Economy / auction / capture

- [`JdbcMoneyRepository`](../../src/main/java/com/larpsmp/moneyevent/wallet/JdbcMoneyRepository.java): multi-settlement commits; transfer+settlement in one TX.
- [`MoneyService`](../../src/main/java/com/larpsmp/moneyevent/wallet/MoneyService.java): `planRescue` + combined death/rescue commit; `auctionPaymentWithSettlement`.
- [`AuctionService`](../../src/main/java/com/larpsmp/moneyevent/auction/AuctionService.java): atomic pay; recover lot mark from existing settlement.
- [`CaptureService`](../../src/main/java/com/larpsmp/moneyevent/capture/CaptureService.java): do not complete/day-complete on payout failure.

### Auth / config / gladiators

- [`IntegrationsConfig`](../../src/main/java/com/larpsmp/moneyevent/config/IntegrationsConfig.java) + [`MoneyEventPlugin`](../../src/main/java/com/larpsmp/moneyevent/MoneyEventPlugin.java): require pepper with Resend; severe log if auth disabled.
- [`AuthService`](../../src/main/java/com/larpsmp/moneyevent/auth/AuthService.java) / [`PasswordHasher`](../../src/main/java/com/larpsmp/moneyevent/auth/PasswordHasher.java): dummy Argon2 on unknown login; recovery cooldown non-enumerating.
- [`GladiatorListener`](../../src/main/java/com/larpsmp/moneyevent/gladiator/GladiatorListener.java): account-backed admin exemption only.
- [`GladiatorService`](../../src/main/java/com/larpsmp/moneyevent/gladiator/GladiatorService.java): clearer partial-payout retry message.

### Tests / CI / docs

- New/extended tests in economy, capture, auction, ace hunt, config.
- [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml).
- README, getting-started, configuration, roadmap, schema, known-gaps, testing docs.

---

## 6. Testing and verification

| Check | Result |
|-------|--------|
| Unit + integration JUnit | **183 passed**, 0 failed, 0 skipped (local Postgres) |
| `./gradlew build` | PASS — `build/libs/money-event-0.1.0.jar` |
| Dependency versions | PostgreSQL 42.7.4, HikariCP 6.2.1, BouncyCastle 1.79 — match `plugin.yml` `libraries:` |
| GitHub Actions CI | Added; **not yet run on GitHub in this session** |
| Live Resend HTTP | Not exercised |
| In-world Paper damage/occupancy/boss bars | Unverified (no client) |
| Manual two-client refresher | `BLOCKED` |

---

## 7. Deployment readiness

### Ready in code

- Auth fail-closed when DB unavailable
- Account-based admin/server-op authz
- Economy settlements with idempotency keys
- Event engines (capture, auction, ace hunt, gladiators) with restart persistence for core state
- Competitor/spectator filtering on wealth/standings/settlements

### Must configure manually before players join

1. Hosting (Europe preference / budget still Open)
2. Postgres + Paper on one machine/process model
3. `.env`: JDBC, Resend, **`LARPSMP_EMAIL_CODE_PEPPER`**, strong DB password
4. `auth.enabled: true`
5. Backups: world + Postgres (owner + restore drill Open)
6. Final 12 roster, captains, tiers (Karim)
7. Map: spawn AABB, capture regions, ace territories, gladiator arena/kits
8. Calendar / wealth cutoff (Open)
9. Playtest battery from [manual-testing-refresher](../04-development/manual-testing-refresher.md)

### Do not claim without playtest

Live kill attribution feel, capture contested timing, auction social flow, Ace Hunt borders, Gladiators arena geometry.

---

## 8. Outstanding risks / accepted risks

- Multi-Paper against one DB unsupported.
- In-memory auth rate limits and duplicate-login gate reset on restart.
- Signup username/email distinct errors (private event).
- Gladiator kit contents and prize amounts still Open with Karim.
- Auction item delivery remains manual.
- Ace Hunt pre-prep nominations are memory-only until session starts.
- No Shadow JAR (Paper downloads libraries — intentional).
- CI workflow not observed green on GitHub yet.

---

## 9. Changes made (overview)

| Area | Files |
|------|--------|
| Economy integrity | `JdbcMoneyRepository`, `MoneyService` |
| Auction / capture | `AuctionService`, `CaptureService` |
| Auth / pepper | `AuthService`, `PasswordHasher`, `AuthConnectionListener`, `IntegrationsConfig`, `MoneyEventPlugin` |
| Gladiators | `GladiatorListener`, `GladiatorService` |
| Tests | `EconomySettlementTest`, `CaptureServiceTest`, `AuctionServiceTest`, `AceHuntServiceTest`, `EnvSettingsTest` |
| Docs / CI | `documentation/**`, `README.md`, `.env.example`, `.github/workflows/ci.yml` |

---

## 10. Final verdict

### READY WITH CONDITIONS

No known unresolved **code** deployment blockers within the verified audit scope after remediation. Deployment remains conditional on ops configuration, secrets, backups, map/roster, and an in-game playtest pass that this environment could not run.
