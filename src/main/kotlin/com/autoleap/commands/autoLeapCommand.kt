package com.autoleap.commands

import com.autoleap.features.AutoLeap
import com.autoleap.features.BigTimer
import com.github.stivais.commodore.Commodore
import com.odtheking.odin.utils.modMessage
import com.odtheking.odin.utils.skyblock.dungeon.DungeonClass
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils

val autoLeapCommand = Commodore("trji") {
    literal("section").runs {
        modMessage("§7Current section: §b${AutoLeap.currentSection}")
    }

    literal("profile") {
        literal("set") {
            executable {
                runs { section: String, cls: String -> AutoLeap.setSectionClass(section, cls) }
            }
        }
        runs { AutoLeap.printCurrentProfile() }
    }

    literal("bt") {
        literal("pbs").runs { BigTimer.printAllPBs() }
        literal("resetpbs").runs { BigTimer.resetAllPBs() }

        literal("pb") {
            executable {
                runs { roomName: String -> BigTimer.printRoomPBs(roomName) }
            }
            runs { BigTimer.printAllPBs() }
        }

        literal("resetpb") {
            executable {
                runs { roomName: String -> BigTimer.resetRoomPBs(roomName) }
            }
        }

        executable {
            runs { roomName: String, count: Int -> BigTimer.setCustomSecrets(roomName, count) }
        }
    }

    literal("leap") {
        executable {
            param("clazz") {
                suggests {
                    DungeonUtils.dungeonTeammatesNoSelf
                        .map { it.clazz.name }
                        .filter { it != DungeonClass.Unknown.name }
                        .distinct()
                }
            }
            runs { clazz: String ->
                val target = DungeonUtils.dungeonTeammatesNoSelf
                    .find { it.clazz.name.equals(clazz, ignoreCase = true) }
                if (target == null) {
                    modMessage("§cleap target not found")
                    return@runs
                }
                AutoLeap.leapToClass(target.clazz.name)
            }
        }
    }
}
