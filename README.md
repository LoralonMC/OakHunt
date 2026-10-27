# OakHunt

Head hunts run entirely in-game: built in edit mode, hidden until they start, and restorable to the exact same spots.

## Features

- Hunts are records, not blocks: heads become real blocks only while a hunt is active, and are removed (records kept) when it ends
- Restart any hunt later in the same spots, as a fresh run or continuing the last one
- Hunts can reuse each other's spots, as long as they don't run at the same time
- Start check: blocked spots are skipped, leave the total, and are reported with teleport, remove and force-place buttons
- Edit mode with previews only the editor sees, numbered labels, sneak + left-click removal and undo
- Hider role: helpers can hide heads and see the others, but only change their own
- Skin sets per hunt (held head, HeadDatabase id, player name or UUID, texture), with fixed or random placers that are never used up
- Scheduled starts and ends in UTC or relative time (`+2h`), caught up on boot
- Milestone rewards edited in a GUI: ExecutableItems and Nexo items by id, crate keys, OakTags chat tags (granted directly or as a voucher), held vanilla items, console commands
- Edit files: export a hunt's name, description, settings, milestones, rewards and clues to YAML, edit it outside the game, and apply it back after a preview
- Reward items that don't fit go to OakOverflow
- Player journal: progress, rank, next reward, unlockable clues, past hunts
- Per-player feedback only (sounds, particles, found-head particles); no public markers that give spots away
- Protection against breaking, pistons, explosions and fluids, with self-repair when chunks load
- History is never deleted: archived heads and hunts keep their finds and results

## Requirements

- Paper 26.3
- Java 25
- PacketEvents
- Optional: PlaceholderAPI, HeadDatabase, ExecutableItems + SCore, Nexo, OakOverflow, CoreProtect, PhoenixCrates (crate key rewards), OakTags (chat tag rewards)

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
| `/headhunt edit [hunt]` | Toggle edit mode | `oakhunt.edit` or `oakhunt.place` |
| `/headhunt head <hunt> [skin\|random]` | Get a placer | `oakhunt.edit` or `oakhunt.place` |
| `/headhunt skin <hunt> add [source]` / `remove <id>` / `list` | Manage the skin set | `oakhunt.edit` |
| `/headhunt clue <text\|clear>` | Set the clue of the head you look at | `oakhunt.edit` |
| `/headhunt check <hunt>` | Start check without starting | `oakhunt.edit` |
| `/headhunt conflicts <hunt>` | List blocked heads | `oakhunt.edit` |
| `/headhunt undo` | Undo your last edit-mode change | `oakhunt.edit` or `oakhunt.place` |
| `/headhunt start <hunt> [new\|continue] [date]` | Start now or schedule | `oakhunt.manage` |
| `/headhunt end <hunt> [date]` | End now or schedule | `oakhunt.manage` |
| `/headhunt cancel <hunt>` | Cancel a scheduled start or end | `oakhunt.manage` |
| `/headhunt player <player> <hunt> info\|reset\|grant <n\|all> [pay]` | Support tools (`pay` also needs `oakhunt.rewards.pay`) | `oakhunt.player` |
| `/headhunt delete <hunt>` | Archive a hunt | `oakhunt.admin` |
| `/headhunt import <file>` | Import a past hunt from `plugins/OakHunt/import/` | `oakhunt.admin` |
| `/headhunt export <hunt> [overwrite]` | Write the hunt's edit file to `plugins/OakHunt/edits/<hunt>.yml` | `oakhunt.manage` |
| `/headhunt apply <hunt>` | Preview the edit file's changes, with [Confirm] to apply them | `oakhunt.manage` |
| `/headhunt reload` | Reload config and messages | `oakhunt.admin` |

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `oakhunt.use` | Find heads, journal, leaderboards | Everyone |
| `oakhunt.edit` | Create hunts, edit mode, placers, skins, clues, start check | OP |
| `oakhunt.place` | Hider: edit mode and placers only; sees every head, removes only their own | OP |
| `oakhunt.manage` | Start, end, schedule; item, key and tag rewards; hunt settings; export and apply edit files | OP |
| `oakhunt.player` | View, reset or grant a player's progress | OP |
| `oakhunt.rewards.commands` | Add or change console-command rewards | OP |
| `oakhunt.rewards.pay` | Test payouts and `grant ... pay` (rewards without a real find) | OP |
| `oakhunt.admin` | Archive hunts, reload, import | OP |
| `oakhunt.*` | All of the above | OP |

## Configuration

Hunts, heads, rewards and progress live in `oakhunt.db` and are managed in-game. `config.yml` holds server-wide defaults: which blocks a head may replace at start, find sounds and particles, found-head particles, hint distance and frequency, editor preview radius, and integration toggles. All text is in `messages.yml`.

### Edit files

`/headhunt export <hunt>` writes the hunt's editable parts to `plugins/OakHunt/edits/<hunt>.yml`: display name, description, settings, milestones (message, broadcast, rewards) and clues. Heads, skins, schedules, runs and progress are not in the file and apply never changes them. The database stays the source of truth:

- The file carries a fingerprint of the hunt. `/headhunt apply` refuses if the hunt changed in-game since the export, so a stale file can't overwrite GUI edits.
- Apply lists every change first. [Confirm] carries a hash of the previewed file, so a file edited after the preview is refused rather than applied unseen.
- After applying, the file is rewritten with a fresh fingerprint, ready for the next edit.
- Export won't replace a file with unapplied edits unless you add `overwrite`.
- Every change is audited under the person who confirmed it. Command rewards in a file need no extra permission: whoever can write files on the server can already run anything.

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
