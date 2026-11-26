# Glossary

| Term | Definition |
|------|------------|
| **LarpSMP** | The five-day private Minecraft competition / event. |
| **MoneyEvent** | The Paper plugin artifact name in this repository. |
| **Account** | Permanent LarpSMP identity (`accounts.id`) after signup. Wallets, teams, and bodies key off this ID — not the Minecraft client nickname alone. |
| **Wallet** | Personal whole-dollar balance for one account. Cannot go negative. |
| **Team wealth** | Sum of the personal wallets of competitors on a team’s roster. Not a separate bank. |
| **Tier** | Roster balance rank 1 (strongest) through 4. Also sets **death cost** for the victim. Does not by itself change health/damage/gear. |
| **Captain** | Team contact who nominates the auction representative. |
| **Death payment** | Money moved after an attributed enemy kill, based on the victim’s tier. |
| **Shortfall cover** | When the victim cannot pay the full death cost, teammates (including offline) cover the remainder. |
| **Rescue** | When a death payment would drop a team below **$40** wealth, every team receives **$100** total (**$25** per member) before the payment finishes. Only death-related calculations trigger it. |
| **Kill money** | Whether death payments and killer payouts are active (global / event / player override scopes — **Confirmed** rules, not fully **Implemented**). |
| **Capture point** | Contested platform that fills a progress bar; winning team gets **$50** split across roster members. |
| **Advantage** | Leading team’s player count minus the second-largest team on the point. |
| **Auction** | Mystery shulker lots hosted by Amira; representatives bid; payment goes to `akiracreates`. |
| **Ace** | Day 3 Ace Hunt: one nominated player per team sent to a separated territory to prepare, then hunted. |
| **Gladiators** | Structured arena combat event (Day 4 design: team fights + 1v1s with kits). |
| **Keep inventory** | Vanilla setting used for specific events (e.g. Ace Hunt); must be restored afterward. |
| **Sidebar / Wallet HUD** | Always-on scoreboard panel showing Team Money and personal Wallet (**Implemented**). |
| **Starting balance** | **$200** granted once when an account becomes a competitor (team assign or `/larp role set … competitor`). |
| **Competitor** | Account with `account_participation.competitor = true`. Eligible for standings, capture advantage, death money, and starting balance. |
| **Spectator (account)** | Account with `account_participation.spectator = true`. Does not count for capture or competing wealth. Distinct from Ace Hunt / Gladiators event spectator state. |
| **Plugin admin** | Row in `account_plugin_admins`. Runs `/larp` admin commands. |
| **Server operator** | Row in `account_server_operators`. Bukkit op + plugin admin. |
