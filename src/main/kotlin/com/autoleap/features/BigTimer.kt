package com.autoleap.features

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.events.BlockInteractEvent
import com.odtheking.odin.events.ChatPacketEvent
import com.odtheking.odin.events.RoomEnterEvent
import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.WorldEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Category
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.modMessage
import com.odtheking.odin.utils.noControlCodes
import com.odtheking.odin.utils.skyblock.PartyUtils
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils
import net.fabricmc.loader.api.FabricLoader
import java.io.File

object BigTimer : Module(
    name = "Big Timer",
    description = "Tracks dungeon room completion times and personal bests.",
    category = Category.DUNGEON
) {
    private val showCompletion by BooleanSetting("Show Completion", true, desc = "Show a message in chat when a room is completed.")
    private val showPB by BooleanSetting("Show PB", true, desc = "Include personal best comparison in the completion message.")

    private val gson = Gson()
    private val configDir: File
        get() = FabricLoader.getInstance().configDir.resolve("trji").toFile().also { it.mkdirs() }
    private val pbFile get() = File(configDir, "bigtimer_pbs.json")
    private val customsFile get() = File(configDir, "bigtimer_customs.json")

    // pbData[runType][roomName] = best ms
    internal var pbData: MutableMap<String, MutableMap<String, Long>> = mutableMapOf()
    internal var customSecrets: MutableMap<String, Int> = mutableMapOf()

    private var roomStartTime = 0L
    private var currentRoomName = ""
    private var currentRoomMax = 0
    private var roomStartSecrets = 0
    private var roomCompleted = false
    private var fakeSecrets = 0
    private var fakePositions = mutableSetOf<String>()
    private var lastInteractPos: Triple<Int, Int, Int>? = null

    init {
        loadData()

        on<WorldEvent.Load> {
            resetRoomState()
        }

        on<RoomEnterEvent> {
            val data = room?.data ?: return@on
            val name = data.name.takeIf { it.isNotBlank() } ?: return@on
            val max = customSecrets[name] ?: data.secrets
            if (max <= 0) return@on
            resetRoomState()
            currentRoomName = name
            currentRoomMax = max
            roomStartSecrets = DungeonUtils.secretCount
            roomStartTime = System.currentTimeMillis()
        }

        on<TickEvent.Start> {
            if (!DungeonUtils.inDungeons || roomCompleted || roomStartTime == 0L || currentRoomMax <= 0) return@on
            if ((DungeonUtils.secretCount - roomStartSecrets) + fakeSecrets >= currentRoomMax) completeRoom()
        }

        on<BlockInteractEvent> {
            lastInteractPos = Triple(pos.x, pos.y, pos.z)
        }

        on<ChatPacketEvent> {
            if (!value.noControlCodes.contains("That chest is locked!")) return@on
            val (x, y, z) = lastInteractPos ?: return@on
            if (fakePositions.add("$x,$y,$z")) fakeSecrets++
        }
    }

    private fun completeRoom() {
        roomCompleted = true
        val elapsed = System.currentTimeMillis() - roomStartTime
        val runType = if (PartyUtils.isInParty) "team" else "solo"

        val pbs = pbData.getOrPut(runType) { mutableMapOf() }
        val prev = pbs[currentRoomName]
        val isPB = prev == null || elapsed < prev

        if (isPB) {
            pbs[currentRoomName] = elapsed
            savePBs()
        }

        if (!showCompletion) return
        val timeStr = formatMs(elapsed)
        val pbStr = when {
            !showPB -> ""
            isPB -> " §a(PB!)"
            else -> " §7(PB: ${formatMs(prev)})"
        }
        modMessage("§7[BigTimer] §b$currentRoomName §7done in §f$timeStr$pbStr")
    }

    private fun resetRoomState() {
        roomStartTime = 0L
        currentRoomName = ""
        currentRoomMax = 0
        roomStartSecrets = 0
        roomCompleted = false
        fakeSecrets = 0
        fakePositions = mutableSetOf()
        lastInteractPos = null
    }

    fun formatMs(ms: Long): String {
        val s = ms / 1000
        val tenths = (ms % 1000) / 100
        return if (s >= 60) "${s / 60}m ${s % 60}.${tenths}s" else "$s.${tenths}s"
    }

    fun printAllPBs() {
        if (pbData.isEmpty()) { modMessage("§7[BigTimer] No PBs recorded yet."); return }
        for ((runType, rooms) in pbData) {
            modMessage("§7[BigTimer] §e${runType.replaceFirstChar { it.uppercase() }} PBs:")
            for ((room, time) in rooms.entries.sortedBy { it.key }) {
                modMessage("  §b$room §7- §f${formatMs(time)}")
            }
        }
    }

    fun printRoomPBs(roomName: String) {
        var found = false
        for ((runType, rooms) in pbData) {
            val time = rooms[roomName] ?: continue
            found = true
            modMessage("§7[BigTimer] §b$roomName §7($runType): §f${formatMs(time)}")
        }
        if (!found) modMessage("§7[BigTimer] No PBs recorded for §b$roomName§7.")
    }

    fun resetAllPBs() {
        pbData.clear()
        savePBs()
        modMessage("§7[BigTimer] All PBs cleared.")
    }

    fun resetRoomPBs(roomName: String) {
        var found = false
        for ((_, rooms) in pbData) {
            if (rooms.remove(roomName) != null) found = true
        }
        if (found) {
            savePBs()
            modMessage("§7[BigTimer] PBs for §b$roomName §7cleared.")
        } else {
            modMessage("§7[BigTimer] No PBs found for §b$roomName§7.")
        }
    }

    fun setCustomSecrets(roomName: String, count: Int) {
        customSecrets[roomName] = count
        saveCustoms()
        modMessage("§7[BigTimer] §b$roomName §7custom secrets set to §f$count§7.")
    }

    private fun loadData() {
        runCatching {
            val pf = pbFile
            if (pf.exists()) {
                val type = object : TypeToken<MutableMap<String, MutableMap<String, Long>>>() {}.type
                pbData = gson.fromJson(pf.readText(), type) ?: mutableMapOf()
            }
        }
        runCatching {
            val cf = customsFile
            if (cf.exists()) {
                val type = object : TypeToken<MutableMap<String, Int>>() {}.type
                customSecrets = gson.fromJson(cf.readText(), type) ?: mutableMapOf()
            }
        }
    }

    private fun savePBs() = runCatching { pbFile.writeText(gson.toJson(pbData)) }
    private fun saveCustoms() = runCatching { customsFile.writeText(gson.toJson(customSecrets)) }
}
