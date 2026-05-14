# trji addon `v1.2.1`

A Fabric client-side mod for Hypixel Skyblock Dungeons built on top of the Odin framework. Provides automated leaping, room timing, and a custom pets UI.

**Command prefix:** `/trji`

---

## Features

### Auto Leap

Automatically leaps to a configured teammate based on your current dungeon section or on boss death triggers.

**Settings**

| Setting | Default | Description |
|---|---|---|
| Fast Leap | ON | Left-clicking InfiniLeap instantly leaps to the configured class for the current section |
| Fast Leap Delay | 250ms | Minimum time between fast leaps (100–500ms) |
| Auto Leap | ON | Automatically leap when boss death triggers are detected |
| P2 Auto Leap | ON | Auto leap when Maxor dies (per-profile) |
| P5 Auto Leap | ON | Auto leap when Necron dies (per-profile) |
| PY Auto Leap | ON | Auto leap on PY chat trigger (per-profile) |
| 3x3 Auto Leap | ON | Auto leap when Goldor dies (per-profile) |
| Leap Message | ON | Send a party chat message when leaping |
| Leap Message Text | `[TRJI] Leaping to {player}!` | Message text — use `{player}` as a placeholder for the target's name |
| Auto Profile | ON | Automatically switch the active profile to match your dungeon class when entering a dungeon |
| Print Dialogue | OFF | Log detected trigger messages to chat |
| Debug Mode | OFF | Print section coordinates and debug info to chat |

**Profiles**

Five built-in profiles ship with default class assignments per section. Profiles are stored in `~/.minecraft/config/trji/autoleap_profiles.json`.

| Profile | Skipped sections |
|---|---|
| Tank | — |
| Mage | Core, EE4, P2, PY |
| Archer | P2, PY |
| Healer | EE1, P2, PY, P5 |
| Berserker | EE1, P2, PY |

The four auto-leap toggles (P2 / P5 / PY / 3x3) are saved separately per profile.

**Sections**

Clear · EE1 · EE2 · EE3 · EE4 · Core · 3x3 · Mid · P2 · P5 · PY

EE2 and EE3 support a fallback class in case the primary target is dead.

**Commands**

```
/trji section                         Show your current dungeon section
/trji profile                         Show the active profile's class assignments
/trji profile set <section> <class>   Override a section's leap target
/trji leap <class>                    Manually leap to a class (tab-completes teammates)
```

---

### Big Timer

Tracks dungeon room completion times and stores personal bests separately for solo and team runs.

**Settings**

| Setting | Default | Description |
|---|---|---|
| Show Completion | ON | Print a message to chat when a room completes |
| Show PB | ON | Include your personal best in the completion message |

**Example output**
```
[BigTimer] Spider's Den done in 1m 32.4s (PB!)
[BigTimer] Spider's Den done in 1m 38.1s (PB: 1m 32.4s)
```

PBs are stored in `~/.minecraft/config/trji/bigtimer_pbs.json`. Custom secret counts are stored in `bigtimer_customs.json`.

**Commands**

```
/trji bt pbs                    Show all personal bests (solo & team)
/trji bt pb [room]              Show PB for a specific room, or all if omitted
/trji bt resetpbs               Clear all stored PBs
/trji bt resetpb [room]         Clear PB for a specific room
/trji bt <room> <count>         Set a custom secret count for a room (tab-completes)
```

---

### Pets Menu

Replaces the Hypixel pets menu with a modern grid-based UI. Click a pet card to equip it — the click is forwarded to the underlying inventory slot.

**Settings**

| Setting | Default | Description |
|---|---|---|
| Only Favorites | OFF | Only show pets marked with ⭐ |
| Debug Titles | OFF | Print the container title to chat when any screen opens (useful if the menu stops triggering) |

**UI**

- 4-column card grid with custom pet icon, clean name, level, and a rarity color bar
- Custom icons are loaded from `trji:textures/pets/{petname}.png`; falls back to the item texture if none exists
- Pet names are shown without level indicators, stars, or extra clutter (e.g. "Dragon" instead of "⭐ Dragon [Lvl 100]")
- Hover tooltip shows the full item tooltip — tooltips are hidden automatically when Only Favorites is on
- Transparent background — the game world is visible behind the panel

Rarity colors: Legendary (orange) · Mythic (magenta) · Epic (purple) · Rare (blue) · Uncommon (green) · Common (gray)

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.1
2. Install the [Odin mod](https://github.com/odtheking/Odin) (required dependency)
3. Drop the AutoLeap jar into your `mods/` folder
4. Launch the game — config files are created automatically on first run

---

## Building from source

```bash
./gradlew build
```

Output jar: `build/libs/`

---

## Changelog

### v1.2.1
- **Fix**: Custom pet icons were not rendering — resolved texture loading issue.

### v1.2.0
- **Custom Pet Icons**: Pets Menu now renders custom textures (`trji:textures/pets/{petname}.png`) instead of raw item icons.
- **Cleaner Pet Names**: Cards now display only the pet name, stripping level indicators, stars, and parenthetical text.
- **No Tooltips in Favorites Mode**: Hovering over cards no longer shows a tooltip when Only Favorites is enabled, keeping the view clean.

### v1.1.0
- **Auto Profile**: AutoLeap now automatically switches to the matching profile (Tank / Mage / Archer / Healer / Berserker) when you enter a dungeon, based on your detected class. Can be disabled in settings.

### v1.0.0
- Initial release: Auto Leap, Big Timer, Pets Menu
