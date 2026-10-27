# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.3.0] - 2026-10-27

### Changed

- Hunts can share spots. Placing a head where another hunt has one adds it with a note naming that hunt instead of refusing; only a second head from the same hunt is refused
- Only one hunt on a shared spot can stand there at a time: starting a hunt while another running hunt holds a shared spot reports that head as blocked, and it is placed automatically when the other hunt ends
- A leftover head block from a hunt that has ended no longer blocks a running hunt on the same spot

## [1.2.0] - 2026-10-26

### Added

- `oakhunt.place` hider permission: edit mode and placers without the rest of `oakhunt.edit`. Hiders see every head in the hunt, place with placers only, and remove or undo only heads they placed; right-clicking a head shows its number and who placed it
- Heads list and head menu show who placed each head

## [1.1.0] - 2026-10-26

### Changed

- Test payouts and `grant ... pay` need the new `oakhunt.rewards.pay` permission (OP by default), so reward managers can't pay rewards to themselves without a find

## [1.0.0] - 2026-10-26

### Added

- Initial release: head hunts managed in-game with `/headhunt`, replacing HeadBlocks
- Start, end and boot load every head chunk, so a hunt is fully placed or removed at once
- Hunt states (draft, scheduled, active, ended) with heads stored as records and placed as real blocks only while a hunt is active
- Runs: each restart is a fresh run with its own progress, or continues the last one
- Start check that skips blocked spots, drops them from the total and reports them with teleport, remove and force-place actions
- Head protection against breaking, pistons, explosions and fluids, plus self-repair on chunk load
- Edit mode with client-only previews and labels, sneak + left-click removal, a head menu and undo
- Skin sets per hunt from held heads, HeadDatabase ids, player names or textures, with fixed and random placers
- UTC and relative scheduling for starts and ends, applied on boot if missed while down
- Milestone rewards (ExecutableItems and Nexo items by id, crate keys, held vanilla items, console commands) edited in a GUI; items that don't fit go to OakOverflow
- Player journal with progress, next reward, clues and past hunts; per-player find feedback and found-head particles; optional proximity hints
- PlaceholderAPI placeholders per hunt, CoreProtect logging, and a one-off import for past hunts
