# Manual testing refresher (what exists today)

Use this to **boot the stack**, reconnect mentally with what is already on `main`, and **verify it with two Minecraft clients**.

This covers **Implemented features** suitable for a two-client smoke pass. Auctions, Ace Hunt, and Gladiators are still not coded.

Related: [Getting started](getting-started.md) · [Implementation status](../05-status/implementation-status.md) · [Roadmap](../05-status/roadmap.md)

---

## 0. Setup: start everything

### Prerequisites

- Minecraft Java **1.21.11** (two clients — two launches / two instances)
- Repo on **`main`**, up to date
- Resend keys in `.env` (signup email verification)

```bash
cd /path/to/LarpSMP
git checkout main
git pull origin main
cp -n .env.example .env   # only if .env missing
# edit .env: LARPSMP_RESEND_API_KEY and LARPSMP_RESEND_FROM_EMAIL
```

### Start servers (one command)

```bash
./scripts/dev-server.sh
```

That starts:

| Service | Address | Default login |
|---------|---------|---------------|
| **Paper** | `localhost:25565` | In-game Sign up / Log in |
| **Postgres** | `127.0.0.1:5433` | `larpsmp` / `larpsmp` |
| **pgAdmin** | http://localhost:5050 | `admin@larpsmp.dev` / `admin` |

Wait until the script says the server is ready and the console shows auth/Postgres ready.

### Two clients

| Client | Suggested use | Minecraft profile tip |
|--------|---------------|------------------------|
| **Client A** | Primary tester + **op** (admin commands) | e.g. offline name `PlayerA` |
| **Client B** | Second account / payments / team mate | e.g. offline name `PlayerB` |

Offline mode is on — different client nicknames are fine. After signup/login, in-game identity is the **LarpSMP username**, not the launcher nickname. Vanilla commands must also accept that username (e.g. client nick `Yamite` logged in as account `secondgoose` → `/gamemode survival secondgoose` works; `/gamemode survival yamite` should not be required).

**Op Client A** (needed for `/larp …`):

In the Paper console:

```text
op <ClientA_MinecraftName>
```

Use the name the server sees on join (before/at connection). If unsure, check console join lines. After auth, tab list shows the LarpSMP username — ops are tied to the Minecraft profile UUID.

### Stop

- Paper: type `stop` in the server console  
- Docker (optional): `docker compose --env-file .env -f docker/docker-compose.yml down`

### Clean database (optional full reset)

```bash
docker compose --env-file .env -f docker/docker-compose.yml down -v
docker compose --env-file .env -f docker/docker-compose.yml up -d
# restart Paper / re-run ./scripts/dev-server.sh
```

Then both players Sign up again from scratch.

---

## 1. Authentication

### What exists

- Pre-join dialogs (you are not in the world until auth succeeds)
- Sign up → email verification code → join
- Log in every reconnect (no auto-login)
- Forgot password / find username via email
- Portable login: same LarpSMP account can rebind to another Minecraft client
- Concurrent login blocked (same account cannot be online twice)
- Signup creates a wallet with **$200** (today: every account, not only competitors)

### Test A1 — Sign up Client A

1. Client A → Multiplayer → `localhost`
2. Click **Sign up**
3. Create username e.g. `alice`, email you can read, password ≥ 8 chars
4. Enter the 6-digit code from email
5. Confirm you enter the world
6. Confirm sidebar shows **Wallet $200** (and Team Money `—` until on a team)

### Test A2 — Sign up Client B

1. Client B joins `localhost` with a **different** Minecraft profile
2. Sign up as e.g. `bob` with a **different** email
3. Verify code → join
4. Confirm Wallet **$200**

### Test A3 — Log in after disconnect

1. Client A disconnects
2. Reconnect same Minecraft profile
3. Use **Log in** with `alice` + password (not Sign up again)
4. Confirm balance still **$200** (not $400)

### Test A4 — Concurrent login reject

1. Client A online as `alice`
2. Client B tries to **Log in** as `alice` with the same password
3. Expect rejection: account already online
4. Client A stays in the world

### Test A5 — Portable rebind (optional)

1. Client A logs out as `alice`
2. From Client B’s Minecraft profile, **Log in** as `alice`
3. Expect success; that profile is now bound to `alice`
4. Inventory/location should be `alice`’s body, not `bob`’s

### Test A6 — Bad credentials / taken names

1. Sign up with an existing username → error  
2. Log in with wrong password → error  
3. Cancel / back to menu → disconnect cleanly  

---

## 2. Account-keyed bodies (inventory / location)

### What exists

- Inventory, armor, ender chest, location, health/food, XP, gamemode, effects saved per **LarpSMP account**
- Autosave (~60s) + save on quit
- Switching accounts on one client loads that account’s body
- Quit clears the vanilla UUID shell so the next account on that client does not inherit items

### Test B1 — Persist across reconnect

1. As `alice`: pick up items, note coordinates, maybe place a marker
2. Disconnect and Log in again as `alice`
3. Confirm inventory and roughly the same location

### Test B2 — Account switch isolation (needs portable login or second signup on one client)

1. On one Minecraft client, play as `alice`, put a unique item in inventory (e.g. diamond)
2. Quit; Log in as `bob` on that **same** client
3. Confirm `bob` does **not** have alice’s diamond
4. Quit; Log in as `alice` again → diamond back

### Test B3 — Display name

1. Both online
2. Confirm tab list / nametag-ish identity shows LarpSMP usernames (`alice`, `bob`), not only launcher nicknames

### Test B4 — Vanilla commands use account username

Requires Client A op’d (ops are UUID-based; op before or after auth is fine).

1. Client B joins with launcher nick ≠ account username (e.g. nick `Yamite`, account `bob`)
2. From Client A (or console): `/gamemode survival bob` — must succeed
3. `/tp alice bob` (or `/tp bob alice`) — must succeed using account names
4. Confirm `/gamemode survival yamite` is **not** required (launcher nick should not be the target operators need)
5. Console log on admit should mention `launcher='…'` alongside the account username

---

## 3. Money (wallets, pay, admin)

### What exists

| Feature | Command / UI |
|---------|----------------|
| Own balance | `/balance` |
| Public balance | `/balance <player>` |
| Pay (online or offline) | `/pay <player> <amount>` |
| Admin give/take/set | `/larp money give\|take\|set <player> <amount> <reason…>` |
| Sidebar | Team Money + Wallet |
| Offline pay notification | Shown when recipient next joins |

**Admin requires op or console.** Whole dollars only. No cents. Cannot go negative. No `/top` yet. No death money yet.

### Test M1 — Balances and public lookup

1. Both online  
2. `alice`: `/balance` → $200  
3. `alice`: `/balance bob` → $200  
4. `bob`: `/balance alice` → $200  

### Test M2 — Online `/pay`

1. `alice`: `/pay bob 50`  
2. Expect: alice ~$150, bob ~$250  
3. Both get chat feedback  
4. Sidebars update  

### Test M3 — Invalid `/pay`

| Action | Expect |
|--------|--------|
| `/pay bob 0` or `-5` or `12.5` | Rejected |
| `/pay alice 10` (self) | Rejected |
| `/pay bob 9999` (more than balance) | Rejected; balance unchanged |
| `/pay nobody 10` | No wallet / not found |

### Test M4 — Offline `/pay`

1. Client B disconnects (`bob` offline)  
2. `alice`: `/pay bob 25`  
3. Confirm alice balance drops; no crash  
4. Client B logs in as `bob`  
5. Expect payment notification + correct balance  

### Test M5 — Internal team pay does not change team total (after teams assigned)

1. Put `alice` and `bob` on **Blue** (see Teams tests)  
2. Note Team Money on sidebar (sum of both; with only two members it sums those two)  
3. `/pay` between them  
4. Team Money **unchanged**; personal wallets change  

### Test M6 — Admin money (Client A must be op)

```text
/larp money give bob 100 playtest bonus
/larp money take bob 40 correction
/larp money set alice 200 reset for test
```

Confirm reasons required; balances and sidebar update; unauthorized (non-op) Client B gets rejected.

### Test M7 — Cross-account pay leaves competing wealth (concept)

1. If you create a third admin-only account later, paying it reduces competing wallets  
2. With two competitors only: paying each other keeps combined wealth the same  

---

## 4. Teams (roster admin + team wealth)

### What exists

| Action | Command | Also |
|--------|---------|------|
| Open admin UI | `/larp teams` | Nether Star (ops) |
| List / CRUD | `/larp team list\|create\|rename\|color\|delete` | Dialog |
| Assign / remove | `/larp team set <player> <team>` · `unset` | Dialog |
| Captain | `/larp captain set\|clear` | Dialog |
| Tier | `/larp tier set\|clear\|list` | Dialog |
| Team wealth | Sidebar **Team Money** | Sum of roster wallets |

Seeded teams: **Blue**, **Red**, **Yellow**. Soft warnings for duplicate tiers / size — edits still allowed. No player `/team` command yet. No nametag colors yet.

### Test T1 — List seeded teams

As op `alice`:

```text
/larp team list
```

Expect Blue / Red / Yellow (and any warnings about empty rosters).

### Test T2 — Assign both players

```text
/larp team set alice blue
/larp team set bob blue
```

1. Both see **Team Money** = sum of their wallets (e.g. if $150 + $250 → $400)  
2. `/larp team list` shows both on Blue  

### Test T3 — Captains and tiers

```text
/larp captain set blue alice
/larp tier set alice 1
/larp tier set bob 2
/larp tier list
```

1. Confirm captain set  
2. Confirm tiers listed  
3. Optionally set both to tier 1 → expect **soft warning** about duplicate tier (should still apply)

### Test T4 — Move player / unset

```text
/larp team set bob red
```

1. Blue Team Money drops bob’s wallet; Red shows bob  
2. If bob was captain of Blue, captain should clear  

```text
/larp team unset bob
```

Bob’s Team Money → `—`

### Test T5 — Dialog UI

1. Op: `/larp teams` or use Teams Admin Nether Star  
2. Walk hub → assign / tier / captain screens  
3. Confirm same results as chat commands  

### Test T6 — Non-op blocked

1. Non-op `bob`: `/larp team list` or `/larp money give …`  
2. Expect not authorized  

---

## 5. Sidebar / display (what you should see)

### What exists

```text
Team Money
$X,XXX   (or — if no team)
··········
Wallet
$YYY
```

### Test D1 — Updates after money and roster changes

1. Pay → Wallet updates quickly  
2. Join/leave team → Team Money updates  
3. Admin give → both relevant numbers update  

No `/top`, no hide team totals, no kill leaderboard yet.

---

## 6. Spawn protection

**Need:** Client A op; defined spawn region; Client B as victim.

| # | Steps | Pass if |
|---|-------|---------|
| S1 | `/larp spawn define pos1` then move and `/larp spawn define pos2` (or `here` for a single block) | Status shows a defined region |
| S2 | `/larp spawn status` | Shows protection seconds (default 10) + region corners |
| S3 | Kill Client B so they respawn **inside** the region; Client A punches them within a few seconds | Punch deals **no** damage |
| S4 | Wait for protection to expire (or have B attack A to clear early); A punches B again | Damage applies normally |
| S5 | Have B walk into spawn **without** dying/respawning; A punches B | Damage applies (walking in is not protected) |
| S6 | While B is protected, A shoots an arrow / hits with a pet if available | Projectile/pet damage cancelled |
| S7 | While B is protected, push them into lava/fire or let them fall | Environmental damage still applies |
| S8 | Try breaking a block inside the spawn region | Break is cancelled for everyone |

---

## 7. Persistence / restart

### Test R1 — Server restart

1. Note balances, teams, tiers, inventories  
2. Paper console: `stop`  
3. Start again (`./scripts/dev-server.sh` or start Paper as you usually do)  
4. Both Log in  
5. Confirm wallets, team membership, tiers, captains, bodies unchanged  

Postgres must still be running (Compose usually stays up).

### Test R2 — pgAdmin sanity (optional)

1. Open http://localhost:5050  
2. Browse `accounts`, `wallets`, `wallet_transactions`, `teams`, `team_memberships`, `competitor_tiers`, `account_player_states`  
3. Confirm rows match what you did in-game  

---

## Suggested two-client run order (full refresher)

Do in order (~30–45 minutes):

1. Start stack (`./scripts/dev-server.sh`)  
2. **A1–A3** auth signup/login both clients; **op** Client A  
3. **B1** inventory persist  
4. **M1–M4** balances + pay online/offline  
5. **T1–T5** teams assign, captain, tier, dialog  
6. **M5–M6** team wealth unchanged on internal pay; admin money  
7. **D1** watch sidebar  
8. **R1** restart + re-login  
9. Optional: **A4** concurrent login, **A5** rebind, **B2** account isolation  

---

## Quick command cheat sheet

```text
# Players
/balance
/balance <player>
/pay <player> <amount>

# Ops only
/larp money give <player> <amount> <reason>
/larp money take <player> <amount> <reason>
/larp money set <player> <amount> <reason>
/larp teams
/larp team list
/larp team set <player> <team>
/larp team unset <player>
/larp team create <name> [color]
/larp team rename <team> <name>
/larp team color <team> <color>
/larp team delete <team>
/larp captain set <team> <player>
/larp captain clear <team>
/larp tier set <player> <1-4>
/larp tier clear <player>
/larp tier list
/larp spawn define <pos1|pos2|here>
/larp spawn status
/larp spawn reload
/larp capture start|pause|resume|stop|status|define|reload
/top teams
/top money
```

Console: `op <name>` · `stop`

---

## Intentionally skip (not implemented)

Do not expect these to work yet:

- `/top kills`  
- Auctions, Ace Hunt, Gladiators  
- `/bid`, competitor role flags, colored team nametags  

If something above **should** work and fails, note: which test ID, both usernames, exact command, and server log lines.
