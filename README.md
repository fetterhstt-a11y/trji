BigTimer — multi-room 0,0s bug fix

Snapshot DungeonUtils.secretCount when entering a room (roomStartSecrets)
Compare delta (secretCount - roomStartSecrets) instead of the cumulative total, so previous rooms' secrets don't instantly complete the next room
roomStartSecrets is also cleared in resetRoomState()
AutoLeap — Storm double-leap fix

Removed pyAutoLeaped = false from the section-change logic — it was resetting the guard the moment you walked out of PY, letting Storm's 2nd dialogue re-trigger the leap
Added WorldEvent.Load handler that resets pyAutoLeaped, currentSection, and leapState so state is clean at the start of each dungeon run
AutoLeap — Goldor trigger

Changed "[BOSS] Goldor: ..." → "[BOSS] Goldor: YOU ARE FACE TO FACE WITH GOLDOR!"
Font features — fully removed

Deleted FontPackRegistrar.java, FontManagerMixin.java, Inter-Regular.ttf, the font_override resource pack, and assets/minecraft/font/default.json
Removed FontPackRegistrar.register() call from AutoLeapAddon.kt
Removed FontManagerMixin from autoLeap.mixins.json
