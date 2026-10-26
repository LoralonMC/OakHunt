# OakHunt

Head hunts run entirely in-game: built in edit mode, hidden until they start, and restorable to the exact same spots.

## Features

- Hunts are records, not blocks: heads become real blocks only while a hunt is active, and are removed (records kept) when it ends
- Restart any hunt later in the same spots, as a fresh run or continuing the last one
- Start check: blocked spots are skipped, leave the total, and are reported with teleport, remove and force-place buttons
- Edit mode with previews only the editor sees, numbered labels, sneak + left-click removal and undo
- Skin sets per hunt (held head, HeadDatabase id, player name or UUID, texture), with fixed or random placers that are never used up
- Scheduled starts and ends in UTC or relative time (`+2h`), caught up on boot
- Milestone rewards edited in a GUI: ExecutableItems and Nexo items by id, crate keys, held vanilla items, console commands
- Reward items that don't fit go to OakOverflow
- Player journal: progress, rank, next reward, unlockable clues, past hunts
- Per-player feedback only (sounds, particles, found-head particles); no public markers that give spots away
- Protection against breaking, pistons, explosions and fluids, with self-repair when chunks load
- History is never deleted: archived heads and hunts keep their finds and results

## Requirements

- Paper 26.3
- Java 25
- PacketEvents
- Optional: PlaceholderAPI, HeadDatabase, ExecutableItems + SCore, Nexo, OakOverflow, CoreProtect, PhoenixCrates (crate key rewards)

## Installation

1. Drop the JAR into `plugins/`
2. Restart the server
3. Adjust `plugins/OakHunt/config.yml` and `messages.yml` if needed, then `/headhunt reload`

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/headhunt` | Open the journal | `oakhunt.use` |
| `/headhunt top <hunt>` | Leaderboard for the latest run | `oakhunt.use` |
| `/headhunt list` | List hunts | `oakhunt.edit` |
| `/headhunt menu [hunt]` | Admin GUI | `oakhunt.edit` |
| `/headhunt create <id>` | Create a hunt | `oakhunt.edit` |
| `/headhunt edit [hunt]` | Toggle edit mode | `oakhunt.edit` |
| `/headhunt head <hunt> [skin\|random]` | Get a placer | `oakhunt.edit` |
| `/headhunt skin <hunt> add [source]` / `remove <id>` / `list` | Manage the skin set | `oakhunt.edit` |
| `/headhunt clue <text\|clear>` | Set the clue of the head you look at | `oakhunt.edit` |
| `/headhunt check <hunt>` | Start check without starting | `oakhunt.edit` |
| `/headhunt conflicts <hunt>` | List blocked heads | `oakhunt.edit` |
| `/headhunt undo` | Undo your last edit-mode change | `oakhunt.edit` |
| `/headhunt start <hunt> [new\|continue] [date]` | Start now or schedule | `oakhunt.manage` |
| `/headhunt end <hunt> [date]` | End now or schedule | `oakhunt.manage` |
| `/headhunt cancel <hunt>` | Cancel a scheduled start or end | `oakhunt.manage` |
| `/headhunt player <player> <hunt> info\|reset\|grant <n\|all> [pay]` | Support tools (`pay` also needs `oakhunt.rewards.pay`) | `oakhunt.player` |
| `/headhunt delete <hunt>` | Archive a hunt | `oakhunt.admin` |
| `/headhunt import <file>` | Import a past hunt from `plugins/OakHunt/import/` | `oakhunt.admin` |
| `/headhunt reload` | Reload config and messages | `oakhunt.admin` |

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `oakhunt.use` | Find heads, journal, leaderboards | Everyone |
| `oakhunt.edit` | Create hunts, edit mode, placers, skins, clues, start check | OP |
| `oakhunt.manage` | Start, end, schedule; item and key rewards; hunt settings | OP |
| `oakhunt.player` | View, reset or grant a player's progress | OP |
| `oakhunt.rewards.commands` | Add or change console-command rewards | OP |
| `oakhunt.rewards.pay` | Test payouts and `grant ... pay` (rewards without a real find) | OP |
| `oakhunt.admin` | Archive hunts, reload, import | OP |
| `oakhunt.*` | All of the above | OP |

## Configuration

Hunts, heads, rewards and progress live in `oakhunt.db` and are managed in-game. `config.yml` holds server-wide defaults: which blocks a head may replace at start, find sounds and particles, found-head particles, hint distance and frequency, editor preview radius, and integration toggles. All text is in `messages.yml`.

## Placeholders

Hunt ids never contain underscores, so `<hunt>` is everything before the first one.

| Placeholder | Returns |
|-------------|---------|
| `%oakhunt_<hunt>_found%` / `_total%` / `_remaining%` / `_percent%` | Viewer's progress |
| `%oakhunt_<hunt>_position%` | Viewer's rank, `-` if unranked |
| `%oakhunt_<hunt>_top_<n>_name%` / `_value%` | Leaderboard row n, `-` if empty |
| `%oakhunt_<hunt>_state%` | `draft`, `scheduled`, `active` or `ended` |
| `%oakhunt_<hunt>_starts_in%` / `_ends_in%` | Countdown, empty if not scheduled |
| `%oakhunt_active%` | Number of active hunts |
