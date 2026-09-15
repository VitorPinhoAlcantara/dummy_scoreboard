# Changelog

## [1.2] - 2026-09-15

### Added
- Crafting recipe for the Scoreboard Dummy Spawner: a Training Dummy Spawner + 1 diamond
  (shapeless, position doesn't matter).
- Built-in default modpack: if `modpackId`/`workerBaseUrl`/`workerApiKey` are left blank, the mod
  now uses a default modpack and worker already baked into the jar (not exposed in the config)
  instead of falling back to local-only mode. Modpacks with their own config (like ATM 11) keep
  working exactly as before, with their own dedicated leaderboard. The default modpack's display
  name is intentionally blank, so no name shows up in the global leaderboard's title in that case.

### Fixed
- A communication problem with the worker (network down, wrong key, unexpected response) was shown
  with the same generic "your record didn't make the cut" message, as if the player had simply been
  outranked. These cases now show their own message explaining there was a communication problem,
  instead of looking like a lost rank race.

## [1.1] - 2026-09-13

### Added
- Rank colors: #1's name in green, #2 and #3 in yellow (both local and global boards).
- The global leaderboard's title now shows the modpack's name when `modpackDisplayName` is
  configured (e.g. "Global Leaderboard - ATM 11"). Purely cosmetic, new optional config.
- Per-IP rate limiting on the worker (30/min on `/leaderboard`, 10/min on `/submit`) using
  Cloudflare's native Rate Limiting binding - free on any plan, protects against someone hammering
  the endpoints directly.
- Combat attribute tampering detection (e.g. `/attribute ... base set`): a hit only counts toward
  the leaderboard if the player's combat attributes (damage, attack speed, max health, armor,
  knockback resistance, luck) match vanilla's own defaults. Legitimate mods always apply their
  bonuses as modifiers, never touching the base value directly - so a base outside the default is a
  strong signal of a command/exploit, not a false positive. Checked both on the mod (blocks the hit
  outright, local and global) and independently on the worker (rejects the submission even if
  someone tries to skip the mod's check with a modified client or a direct POST).
- Players in Creative mode no longer have hits counted toward either leaderboard.
- The worker's submission key is now **per modpack** (an `api_key` column on the `modpacks` table)
  instead of a single shared secret - if one modpack's key leaks, only that modpack is affected.

## [1.0.1] - 2026-09-12

### Fixed
- Submitting a record to the global leaderboard silently failed whenever the item captured in the
  audit snapshot (weapon, armor, etc.) had an enchantment, trim, or other registry-backed data. The
  reason: the snapshot was encoded with plain `JsonOps`, which has no access to the game's
  registries - encoding failed (`Can't access registry ResourceKey[...]`) and the mod treated that
  as if the player simply hadn't beaten the record, showing the generic "the leaderboard updated
  and your record didn't make the cut :(" message with no indication it was actually an error.
  Fixed by using `RegistryOps` (which carries the server's registry access) both when submitting to
  the worker and when persisting locally to disk.

## [1.0] - 2026-09-12

First test release. Still experimental - use on test servers before relying on this in a "real"
modpack.

### Added
- `ScoreboardDummyEntity`: a new training dummy variant that opens a leaderboard screen on
  right-click instead of the base dummy's equipment menu.
- **Biggest hit** leaderboard (not summed damage, not DPS - just the hardest single hit landed).
- **Local** leaderboard (per server, live) and **global** leaderboard (shared across every server
  running the same modpack), with a button to switch between the two.
- Chat notification when a player beats a record, followed by a second message confirming (or not)
  once the record is validated against the freshest global leaderboard data.
- Full audit snapshot per record (inventory, armor, curios, effects, attributes, main/off-hand) -
  kept for audit purposes only; the public leaderboard only shows the nickname and damage.
- Own backend: Cloudflare Worker + D1, with a read endpoint (`GET /leaderboard/:modpackId`) and a
  write endpoint (`POST /submit`), with server-side caching/debounce so the worker doesn't get
  hammered on every hit.
- Modpack allowlist: the worker only accepts/lists a `modpackId` that's been pre-registered by hand
  in the database - any unknown id is silently ignored, so nobody can guess a real modpack's id and
  pollute its leaderboard.
- Local leaderboard persistence to disk (survives a server restart).
- Server config: `modpackId`, `workerBaseUrl`, `workerApiKey`, `leaderboardRefreshSeconds`
  (default 600s), and `recordDebounceSeconds` (default 20s) - all blank/at their factory default
  until the admin configures them.

### Notes
- Without `modpackId`/`workerBaseUrl` configured, the mod uses a local stub instead of talking to a
  real worker - so the mod still works (local leaderboard only) even with no configuration at all.
