package com.autoleap.features

import com.autoleap.events.InputEvent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.clickgui.settings.impl.NumberSetting
import com.odtheking.odin.clickgui.settings.impl.SelectorSetting
import com.odtheking.odin.clickgui.settings.impl.StringSetting
import com.odtheking.odin.events.ChatPacketEvent
import com.odtheking.odin.events.ScreenEvent
import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.WorldEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Category
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.modMessage
import com.odtheking.odin.utils.noControlCodes
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.components.BossHealthOverlay
import net.minecraft.client.gui.components.LerpingBossEvent
import net.minecraft.world.InteractionHand
import net.minecraft.world.inventory.ClickType
import org.lwjgl.glfw.GLFW
import java.io.File
import java.util.UUID

object AutoLeap : Module(
    name = "Auto Leap",
    description = "Automatically leaps to predefined targets in dungeons.",
    category = Category.DUNGEON
) {
    private val fastLeap by BooleanSetting("Fast Leap", desc = "Leaps to a configured class on InfiniLeap left click.")
    private val fastDelay by NumberSetting("Fast Leap Delay", 250.0f, 100.0, 500.0, 50.0, desc = "Minimum ms between fast leaps.")
    private val autoLeap by BooleanSetting("Auto Leap", desc = "Automatically leaps on boss death triggers.")
    private val p2AutoLeap by BooleanSetting("P2 Auto Leap", true, desc = "Automatically leap when Maxor dies.")
    private val p5AutoLeap by BooleanSetting("P5 Auto Leap", true, desc = "Automatically leap when Necron dies.")
    private val pyAutoLeap by BooleanSetting("PY Auto Leap", true, desc = "Automatically leap on PY chat triggers.")
    private val goldorAutoLeap by BooleanSetting("3x3 Auto Leap", true, desc = "Automatically leap when Goldor dies.")
    private val leapMessage by StringSetting("Leap Message", "§aLeaping to §b{player}§a!", desc = "Message shown when leaping. Use {player} for the target's name.")
    private val printDialogue by BooleanSetting("Print Dialogue", desc = "Sends a message when a trigger fires.")
    private val debugMode by BooleanSetting("Debug Mode", desc = "Prints debug info to chat.")

    val classOptions = listOf("Unknown", "Healer", "Archer", "Mage", "Berserk", "Tank")
    val profileNames = listOf("Tank", "Mage", "Archer", "Healer", "Berserker")
    val sectionKeys  = listOf("Clear", "EE1", "EE2", "EE2Fallback", "EE3", "EE3Fallback", "EE4", "Core", "3x3", "Mid", "P5", "P2", "PY")

    private val activeProfileSetting by SelectorSetting("Profile", "Tank", profileNames, desc = "Active leap profile.")

    // --- Section settings (GUI-visible, profile-synced) ---
    private val clearSetting        = SelectorSetting("Clear",        "Unknown", classOptions, desc = "Leap target during Clear.")
    private val ee1Setting          = SelectorSetting("EE1",          "Unknown", classOptions, desc = "Leap target at EE1.")
    private val ee2Setting          = SelectorSetting("EE2",          "Unknown", classOptions, desc = "Leap target at EE2.")
    private val ee2FallbackSetting  = SelectorSetting("EE2 Fallback", "Unknown", classOptions, desc = "Fallback at EE2 if primary is dead.")
    private val ee3Setting          = SelectorSetting("EE3",          "Unknown", classOptions, desc = "Leap target at EE3.")
    private val ee3FallbackSetting  = SelectorSetting("EE3 Fallback", "Unknown", classOptions, desc = "Fallback at EE3 if primary is dead.")
    private val ee4Setting          = SelectorSetting("EE4",          "Unknown", classOptions, desc = "Leap target at EE4.")
    private val coreSetting         = SelectorSetting("Core",         "Unknown", classOptions, desc = "Leap target at Core.")
    private val threeByThreeSetting = SelectorSetting("3x3",          "Unknown", classOptions, desc = "Leap target at 3x3.")
    private val midSetting          = SelectorSetting("Mid",          "Unknown", classOptions, desc = "Leap target at Mid.")
    private val p5Setting           = SelectorSetting("P5",           "Unknown", classOptions, desc = "Leap target at P5.")
    private val p2Setting           = SelectorSetting("P2",           "Unknown", classOptions, desc = "Leap target at P2.")
    private val pySetting           = SelectorSetting("PY",           "Unknown", classOptions, desc = "Leap target at PY.")

    // Register with module for GUI display
    @Suppress("unused") private val clear        by clearSetting
    @Suppress("unused") private val ee1          by ee1Setting
    @Suppress("unused") private val ee2          by ee2Setting
    @Suppress("unused") private val ee2Fallback  by ee2FallbackSetting
    @Suppress("unused") private val ee3          by ee3Setting
    @Suppress("unused") private val ee3Fallback  by ee3FallbackSetting
    @Suppress("unused") private val ee4          by ee4Setting
    @Suppress("unused") private val core         by coreSetting
    @Suppress("unused") private val threeByThree by threeByThreeSetting
    @Suppress("unused") private val mid          by midSetting
    @Suppress("unused") private val p5           by p5Setting
    @Suppress("unused") private val p2           by p2Setting
    @Suppress("unused") private val py           by pySetting

    private val sectionSettings: Map<String, SelectorSetting> = linkedMapOf(
        "Clear"       to clearSetting,
        "EE1"         to ee1Setting,
        "EE2"         to ee2Setting,
        "EE2Fallback" to ee2FallbackSetting,
        "EE3"         to ee3Setting,
        "EE3Fallback" to ee3FallbackSetting,
        "EE4"         to ee4Setting,
        "Core"        to coreSetting,
        "3x3"         to threeByThreeSetting,
        "Mid"         to midSetting,
        "P5"          to p5Setting,
        "P2"          to p2Setting,
        "PY"          to pySetting
    )

    // Reflection to read/write SelectorSetting.index (mutable private int field)
    private val selectorIndexField = SelectorSetting::class.java
        .getDeclaredField("index")
        .also { it.isAccessible = true }

    private fun getIndex(s: SelectorSetting): Int = selectorIndexField.getInt(s)
    private fun setIndex(s: SelectorSetting, idx: Int) = selectorIndexField.setInt(s, idx)

    // --- Profile persistence ---
    private val gson = Gson()
    private val configDir get() = FabricLoader.getInstance().configDir.resolve("trji").toFile().also { it.mkdirs() }
    private val profileFile get() = File(configDir, "autoleap_profiles.json")
    private var profileData: MutableMap<String, MutableMap<String, Int>> = mutableMapOf()
    private var lastProfileIndex = -1

    // --- Runtime state ---
    private var lastClick = 0L
    private var pyAutoLeaped = false
    var currentSection = "Unknown"
        private set

    private enum class LeapState { IDLE, SWAPPING, OPENING, CLICKING }
    private var leapState = LeapState.IDLE
    private var leapTargetName = ""
    private var leapAlreadyOpen = false
    private var leapDeadline = 0L
    private var leapClickTicks = 0

    private val bossEventsField = BossHealthOverlay::class.java.declaredFields
        .first { Map::class.java.isAssignableFrom(it.type) }
        .also { it.isAccessible = true }
    private val prevBossProgress = mutableMapOf<UUID, Float>()

    @Suppress("UNCHECKED_CAST")
    private fun bossEvents(): Map<UUID, LerpingBossEvent> =
        bossEventsField.get(mc.gui.bossOverlay) as Map<UUID, LerpingBossEvent>

    init {
        loadProfiles()

        on<WorldEvent.Load> {
            pyAutoLeaped = false
            currentSection = "Unknown"
            leapState = LeapState.IDLE
            prevBossProgress.clear()
        }

        on<TickEvent.Start> {
            if (DungeonUtils.inDungeons) updateCurrentSection()
            tickLeapStateMachine()
            syncProfile()
            if (autoLeap) checkBossDeaths()
        }

        on<ScreenEvent.Open> {
            if (leapState != LeapState.OPENING) return@on
            if (!screen.title.string.equals("Spirit Leap", ignoreCase = true)) return@on
            leapState = LeapState.CLICKING
            leapClickTicks = 0
        }

        on<ChatPacketEvent> {
            val message = value.noControlCodes
            when {
                autoLeap && pyAutoLeap && !pyAutoLeaped && (message.contains("[BOSS] Storm: Ouch, that hurt!") ||
                message.contains("[BOSS] Storm: Oof")) -> {
                    if (printDialogue) modMessage("found dialogue: $message")
                    pyAutoLeaped = true
                    handleLeap(completedSection = "PY")
                }
                message.contains("You are on a leap cooldown!") -> {
                    leapState = LeapState.IDLE
                }
            }
        }

        on<InputEvent.Mouse.Press> {
            if (!fastLeap || buttonInfo.button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !enabled) return@on
            if (mc.screen != null) return@on
            if (currentSection == "Unknown" && !DungeonUtils.inDungeons) return@on

            val player = mc.player ?: return@on
            if (!player.mainHandItem.displayName.string.contains("InfiniLeap", ignoreCase = true)) return@on

            val now = System.currentTimeMillis()
            if (now - lastClick < fastDelay.toLong()) return@on

            handleLeap(isAutoLeap = false)
            lastClick = now
        }
    }

    // --- Profile sync ---

    private fun syncProfile() {
        val current = activeProfileSetting
        if (current != lastProfileIndex) {
            lastProfileIndex = current
            val profile = profileData[profileNames[current]]
            if (profile != null) {
                for ((key, setting) in sectionSettings) {
                    setIndex(setting, profile[key] ?: 0)
                }
            }
        } else {
            val name = profileNames[current]
            val profile = profileData.getOrPut(name) { mutableMapOf() }
            var changed = false
            for ((key, setting) in sectionSettings) {
                val idx = getIndex(setting)
                if (profile[key] != idx) {
                    profile[key] = idx
                    changed = true
                }
            }
            if (changed) saveProfiles()
        }
    }

    // --- Profile API (used by commands) ---

    fun getSectionClass(section: String): String {
        val setting = sectionSettings[section] ?: return "Unknown"
        return classOptions.getOrElse(getIndex(setting)) { "Unknown" }
    }

    fun setSectionClass(section: String, className: String) {
        val key = sectionKeys.firstOrNull { it.equals(section, ignoreCase = true) }
        if (key == null) {
            modMessage("§c[AutoLeap] Unknown section: §b$section§c. Valid: ${sectionKeys.joinToString(", ")}")
            return
        }
        val idx = classOptions.indexOfFirst { it.equals(className, ignoreCase = true) }
        if (idx == -1) {
            modMessage("§c[AutoLeap] Unknown class: §b$className§c. Valid: ${classOptions.drop(1).joinToString(", ")}")
            return
        }
        setIndex(sectionSettings[key]!!, idx)
        modMessage("§7[AutoLeap] §e${profileNames[activeProfileSetting]}§7: $key → §b${classOptions[idx]}")
    }

    fun printCurrentProfile() {
        val name = profileNames[activeProfileSetting]
        modMessage("§7[AutoLeap] §e$name §7profile:")
        for ((key, setting) in sectionSettings) {
            val cls = classOptions.getOrElse(getIndex(setting)) { "Unknown" }
            modMessage("  §7${key.padEnd(12)} §b$cls")
        }
    }

    private fun loadProfiles() = runCatching {
        val f = profileFile
        if (f.exists()) {
            val type = object : TypeToken<MutableMap<String, MutableMap<String, Int>>>() {}.type
            profileData = gson.fromJson(f.readText(), type) ?: mutableMapOf()
        }
    }

    private fun saveProfiles() = runCatching { profileFile.writeText(gson.toJson(profileData)) }

    // --- Boss death detection ---

    private fun checkBossDeaths() {
        val events = runCatching { bossEvents() }.getOrNull() ?: return
        for ((id, event) in events) {
            val current = event.progress
            val prev = prevBossProgress[id]
            if (prev != null && prev > 0f && current <= 0f) {
                onBossDeath(event.name.string.noControlCodes)
            }
            prevBossProgress[id] = current
        }
        prevBossProgress.keys.retainAll(events.keys)
    }

    private fun onBossDeath(bossName: String) {
        val section = when {
            p2AutoLeap && bossName.contains("Maxor")  -> "P2"
            bossName.contains("Storm")                -> "EE1"
            goldorAutoLeap && bossName.contains("Goldor") -> "3x3"
            p5AutoLeap && bossName.contains("Necron") -> "P5"
            else -> return
        }
        if (printDialogue) modMessage("§7[AutoLeap] Boss died: $bossName → $section")
        handleLeap(completedSection = section)
    }

    // --- Leap logic ---

    fun leapToClass(targetClass: String) {
        if (leapState != LeapState.IDLE) return
        if (DungeonUtils.currentDungeonPlayer?.isDead ?: false) return

        val target = DungeonUtils.dungeonTeammatesNoSelf.find {
            it.clazz.name.equals(targetClass, ignoreCase = true)
        }
        if (target == null) { modMessage("§c[AutoLeap] $targetClass not found in party!"); return }
        if (target.isDead) { modMessage("§c[AutoLeap] ${target.name} is dead!"); return }

        leapTargetName = target.name

        if (isInLeapMenu()) {
            leapAlreadyOpen = true
            clickLeapTarget()
            return
        }

        leapAlreadyOpen = false
        val leapSlot = findLeapSlot()
        if (leapSlot == null) { modMessage("§c[AutoLeap] No leap found in hotbar!"); return }

        val player = mc.player ?: return
        if (player.inventory.selectedSlot != leapSlot) {
            player.inventory.selectedSlot = leapSlot
            leapState = LeapState.SWAPPING
            leapDeadline = System.currentTimeMillis() + 80
        } else {
            openLeapMenu()
            leapState = LeapState.OPENING
            leapDeadline = System.currentTimeMillis() + 5000
        }
    }

    private fun tickLeapStateMachine() {
        if (leapState == LeapState.IDLE) return
        val now = System.currentTimeMillis()
        when (leapState) {
            LeapState.SWAPPING -> {
                if (now >= leapDeadline) {
                    openLeapMenu()
                    leapState = LeapState.OPENING
                    leapDeadline = now + 5000
                }
            }
            LeapState.OPENING -> {
                if (now >= leapDeadline) {
                    modMessage("§c[AutoLeap] Spirit Leap menu did not open.")
                    leapState = LeapState.IDLE
                }
            }
            LeapState.CLICKING -> {
                leapClickTicks++
                if (leapClickTicks >= 2) {
                    clickLeapTarget()
                    leapState = LeapState.IDLE
                }
            }
            LeapState.IDLE -> {}
        }
    }

    private fun openLeapMenu() {
        val player = mc.player ?: return
        mc.gameMode?.useItem(player, InteractionHand.MAIN_HAND)
    }

    private fun clickLeapTarget() {
        if (!isInLeapMenu()) { modMessage("§c[AutoLeap] Leap menu closed before click."); return }
        val player = mc.player ?: return
        val container = player.containerMenu
        val menuSlots = container.slots.dropLast(36)
        val slot = menuSlots.firstOrNull { !it.item.isEmpty && it.item.displayName.string.contains(leapTargetName, ignoreCase = true) }
        if (slot == null) {
            modMessage("§c[AutoLeap] $leapTargetName not found in leap menu.")
            player.closeContainer()
            return
        }
        modMessage(leapMessage.replace("{player}", leapTargetName))
        val button = if (leapAlreadyOpen) 2 else 0
        val clickType = if (leapAlreadyOpen) ClickType.CLONE else ClickType.PICKUP
        mc.gameMode?.handleInventoryMouseClick(container.containerId, slot.index, button, clickType, player)
        player.closeContainer()
    }

    private fun isInLeapMenu() = mc.screen?.title?.string?.equals("Spirit Leap", ignoreCase = true) == true

    private fun findLeapSlot() = (0..8).firstOrNull { i ->
        mc.player?.inventory?.getItem(i)?.displayName?.string?.contains("InfiniLeap", ignoreCase = true) == true
    }

    private fun updateCurrentSection() {
        val player = mc.player ?: return
        val (x, y, z) = player.position().let { Triple(it.x, it.y, it.z) }
        val newSection = when {
            DungeonUtils.inDungeons && !DungeonUtils.inBoss                                               -> "Clear"
            x in 87.0..124.0 && y in 163.0..170.0 && z in 86.0..97.0                                    -> "PY"
            y > 219.0                                                                                     -> "P2"
            DungeonUtils.inBoss && x in 89.0..116.0 && y in 102.0..140.0 && z in 50.0..123.0            -> "EE2"
            DungeonUtils.inBoss && x in 18.0..73.0  && y in 102.0..140.0 && z in 120.0..146.0           -> "EE3"
            DungeonUtils.inBoss && x in 26.0..90.0  && y in 102.0..140.0 && z in 20.0..50.0             -> "EE4"
            DungeonUtils.inBoss && x in -7.0..21.0  && y in 102.0..140.0 && z in 50.0..116.0            -> "Core"
            DungeonUtils.inBoss && x in 32.0..67.0  && y in 110.0..131.0 && z in 58.0..116.0            -> "3x3"
            DungeonUtils.inBoss && x in 41.0..76.0  && y in 64.0..73.0   && z in 97.0..123.0            -> "Mid"
            DungeonUtils.inBoss && x in 44.0..65.0  && y in 62.0..75.0   && z in 64.0..87.0             -> "P5"
            DungeonUtils.inBoss && y in 140.0..219.0                                                     -> "EE1"
            else                                                                                          -> "Unknown"
        }
        if (newSection != currentSection && debugMode)
            modMessage("§7[AutoLeap] Section: $currentSection -> $newSection (${x.toInt()},${y.toInt()},${z.toInt()})")
        currentSection = newSection
    }

    private fun handleLeap(completedSection: String? = null, isAutoLeap: Boolean = true) {
        if (isAutoLeap && currentSection == "Unknown") return
        val targetSection = if (!completedSection.isNullOrEmpty() && completedSection != "Unknown") completedSection else currentSection

        val primaryClass = getSectionClass(targetSection)
        if (debugMode) modMessage("§7[AutoLeap] Target: $primaryClass (section=$targetSection, profile=${profileNames[activeProfileSetting]})")
        if (primaryClass == "Unknown") return

        val targetClass = if (targetSection == "EE2" || targetSection == "EE3") {
            val primaryDead = DungeonUtils.dungeonTeammatesNoSelf
                .find { it.clazz.name.equals(primaryClass, ignoreCase = true) }?.isDead ?: false
            if (primaryDead) {
                val fallbackClass = getSectionClass("${targetSection}Fallback")
                if (debugMode) modMessage("§7[AutoLeap] Primary dead, fallback: $fallbackClass")
                if (fallbackClass == "Unknown") return
                fallbackClass
            } else primaryClass
        } else primaryClass

        leapToClass(targetClass)
    }
}
