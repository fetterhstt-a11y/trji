package com.autoleap.features

import com.autoleap.events.InputEvent
import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.clickgui.settings.impl.NumberSetting
import com.odtheking.odin.clickgui.settings.impl.SelectorSetting
import com.odtheking.odin.events.ChatPacketEvent
import com.odtheking.odin.events.ScreenEvent
import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Category
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.modMessage
import com.odtheking.odin.utils.noControlCodes
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils
import net.minecraft.world.InteractionHand
import net.minecraft.world.inventory.ClickType
import org.lwjgl.glfw.GLFW

object AutoLeap : Module(
    name = "Auto Leap",
    description = "Automatically leaps to predefined targets in dungeons.",
    category = Category.DUNGEON
) {
    private val fastLeap by BooleanSetting("Fast Leap", desc = "Leaps to a configured class on InfiniLeap left click.")
    private val fastDelay by NumberSetting("Fast Leap Delay", 250.0f, 100.0, 500.0, 50.0, desc = "Minimum ms between fast leaps.")
    private val autoLeap by BooleanSetting("Auto Leap", desc = "Automatically leaps on boss chat triggers.")
    private val p2AutoLeap by BooleanSetting("P2 Auto Leap", true, desc = "Automatically leap on P2 chat triggers.")
    private val p5AutoLeap by BooleanSetting("P5 Auto Leap", true, desc = "Automatically leap on P5 chat triggers.")
    private val pyAutoLeap by BooleanSetting("PY Auto Leap", true, desc = "Automatically leap on PY chat triggers.")
    private val printDialogue by BooleanSetting("Print Dialogue", desc = "Sends a message when a dialogue trigger is registered.")
    private val debugMode by BooleanSetting("Debug Mode", desc = "Prints debug info to chat.")

    private val classOptions = listOf("Unknown", "Healer", "Archer", "Mage", "Berserk", "Tank")

    private val clear by SelectorSetting("Clear", "Unknown", classOptions, desc = "Leap target during Clear.")
    private val ee1 by SelectorSetting("EE1", "Unknown", classOptions, desc = "Leap target at EE1.")
    private val ee2 by SelectorSetting("EE2", "Unknown", classOptions, desc = "Leap target at EE2.")
    private val ee2Fallback by SelectorSetting("EE2 Fallback", "Unknown", classOptions, desc = "Fallback leap target at EE2 if primary is dead.")
    private val ee3 by SelectorSetting("EE3", "Unknown", classOptions, desc = "Leap target at EE3.")
    private val ee3Fallback by SelectorSetting("EE3 Fallback", "Unknown", classOptions, desc = "Fallback leap target at EE3 if primary is dead.")
    private val ee4 by SelectorSetting("EE4", "Unknown", classOptions, desc = "Leap target at EE4.")
    private val core by SelectorSetting("Core", "Unknown", classOptions, desc = "Leap target at Core.")
    private val threeByThree by SelectorSetting("3x3", "Unknown", classOptions, desc = "Leap target at 3x3.")
    private val mid by SelectorSetting("Mid", "Unknown", classOptions, desc = "Leap target at Mid.")
    private val p5 by SelectorSetting("P5", "Unknown", classOptions, desc = "Leap target at P5.")
    private val p2 by SelectorSetting("P2", "Unknown", classOptions, desc = "Leap target at P2.")
    private val py by SelectorSetting("PY", "Unknown", classOptions, desc = "Leap target at PY.")

    private var lastClick = 0L
    private var pyAutoLeaped = false
    var currentSection = "Unknown"
        private set

    // --- Leap state machine ---
    private enum class LeapState { IDLE, SWAPPING, OPENING, CLICKING }
    private var leapState = LeapState.IDLE
    private var leapTargetName = ""
    private var leapAlreadyOpen = false
    private var leapDeadline = 0L
    private var leapClickTicks = 0

    init {
        on<TickEvent.Start> {
            if (DungeonUtils.inDungeons) updateCurrentSection()
            tickLeapStateMachine()
        }

        on<ScreenEvent.Open> {
            if (leapState != LeapState.OPENING) return@on
            if (!screen.title.string.equals("Spirit Leap", ignoreCase = true)) return@on
            leapState = LeapState.CLICKING
            leapClickTicks = 0
        }

        on<ChatPacketEvent> {
            if (!autoLeap) return@on
            val message = value.noControlCodes
            when {
                p2AutoLeap && (message.contains("[BOSS] Maxor: I'M TOO YOUNG TO DIE AGAIN!") ||
                message.contains("[BOSS] Maxor: I'LL MAKE YOU REMEMBER MY DEATH!!")) -> {
                    if (printDialogue) modMessage("found dialogue: $message")
                    handleLeap(completedSection = "P2")
                }

                pyAutoLeap && !pyAutoLeaped && (message.contains("[BOSS] Storm: Ouch, that hurt!") ||
                message.contains("[BOSS] Storm: Oof")) -> {
                    if (printDialogue) modMessage("found dialogue: $message")
                    pyAutoLeaped = true
                    handleLeap(completedSection = "PY")
                }

                message.contains("[BOSS] Storm: I should have known that I stood no chance.") -> {
                    if (printDialogue) modMessage("found dialogue: $message")
                    handleLeap(completedSection = "EE1")
                }

                message.contains("[BOSS] Goldor: ...") -> {
                    if (printDialogue) modMessage("found dialogue: $message")
                    handleLeap(completedSection = "3x3")
                }

                message.contains("You are on a leap cooldown!") -> {
                    leapState = LeapState.IDLE
                }

                p5AutoLeap && message.contains("[BOSS] Necron: All this, for nothing...") -> {
                    if (printDialogue) modMessage("found dialogue: $message")
                    handleLeap(completedSection = "P5")
                }
            }
        }

        on<InputEvent.Mouse.Press> {
            if (debugMode) {
                modMessage("§7[AutoLeap] button=${buttonInfo.button}, fastLeap=$fastLeap, enabled=$enabled")
                modMessage("§7[AutoLeap] section=$currentSection, inDungeon=${DungeonUtils.inDungeons}, inBoss=${DungeonUtils.inBoss}")
            }

            if (!fastLeap || buttonInfo.button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !enabled) return@on
            if (mc.screen != null) return@on
            if (currentSection == "Unknown" && !DungeonUtils.inDungeons) return@on

            val player = mc.player ?: return@on

            if (!player.mainHandItem.displayName.string.contains("InfiniLeap", ignoreCase = true)) {
                if (debugMode) modMessage("§c[AutoLeap] Not holding leap item, skipping.")
                return@on
            }

            val now = System.currentTimeMillis()
            if (now - lastClick < fastDelay.toLong()) {
                if (debugMode) modMessage("§c[AutoLeap] Too fast (${now - lastClick}ms < ${fastDelay.toLong()}ms).")
                return@on
            }

            if (debugMode) modMessage("§a[AutoLeap] Executing fast leap...")
            handleLeap(isAutoLeap = false)
            lastClick = now
        }
    }

    fun leapToClass(targetClass: String) {
        if (leapState != LeapState.IDLE) return

        // Self dead check
        if (DungeonUtils.currentDungeonPlayer?.isDead ?: false) return

        // Find target by class
        val target = DungeonUtils.dungeonTeammatesNoSelf.find {
            it.clazz.name.equals(targetClass, ignoreCase = true)
        }
        if (target == null) {
            modMessage("§c[AutoLeap] $targetClass not found in party!")
            return
        }
        if (target.isDead) {
            modMessage("§c[AutoLeap] ${target.name} is dead!")
            return
        }

        leapTargetName = target.name

        // If already in leap menu: immediate middle click, no state machine needed
        if (isInLeapMenu()) {
            leapAlreadyOpen = true
            clickLeapTarget()
            return
        }

        leapAlreadyOpen = false

        val leapSlot = findLeapSlot()
        if (leapSlot == null) {
            modMessage("§c[AutoLeap] No leap found in hotbar!")
            return
        }

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
                // Primary detection via ScreenEvent.Open; this is the timeout fallback
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
        if (!isInLeapMenu()) {
            modMessage("§c[AutoLeap] Leap menu closed before click.")
            return
        }
        val player = mc.player ?: return
        val container = player.containerMenu
        val menuSlots = container.slots.dropLast(36)

        val slot = menuSlots.firstOrNull { slot ->
            !slot.item.isEmpty && slot.item.displayName.string.contains(leapTargetName, ignoreCase = true)
        }

        if (slot == null) {
            modMessage("§c[AutoLeap] $leapTargetName not found in leap menu.")
            player.closeContainer()
            return
        }

        modMessage("§aLeaping to §b$leapTargetName§a!")
        val button = if (leapAlreadyOpen) 2 else 0
        val clickType = if (leapAlreadyOpen) ClickType.CLONE else ClickType.PICKUP
        mc.gameMode?.handleInventoryMouseClick(container.containerId, slot.index, button, clickType, player)
        player.closeContainer()
    }

    private fun isInLeapMenu(): Boolean =
        mc.screen?.title?.string?.equals("Spirit Leap", ignoreCase = true) == true

    private fun findLeapSlot(): Int? =
        (0..8).firstOrNull { i ->
            mc.player?.inventory?.getItem(i)?.displayName?.string?.contains("InfiniLeap", ignoreCase = true) == true
        }

    private fun updateCurrentSection() {
        val player = mc.player ?: return
        val (x, y, z) = player.position().let { Triple(it.x, it.y, it.z) }

        val newSection = when {
            DungeonUtils.inDungeons && !DungeonUtils.inBoss -> "Clear"
            x in 87.0..124.0 && y in 163.0..170.0 && z in 86.0..97.0 -> "PY"
            y > 219.0 -> "P2"
            DungeonUtils.inBoss && x in 89.0..116.0 && y in 102.0..140.0 && z in 50.0..123.0 -> "EE2"
            DungeonUtils.inBoss && x in 18.0..73.0 && y in 102.0..140.0 && z in 120.0..146.0 -> "EE3"
            DungeonUtils.inBoss && x in 26.0..90.0 && y in 102.0..140.0 && z in 20.0..50.0 -> "EE4"
            DungeonUtils.inBoss && x in -7.0..21.0 && y in 102.0..140.0 && z in 50.0..116.0 -> "Core"
            DungeonUtils.inBoss && x in 32.0..67.0 && y in 110.0..131.0 && z in 58.0..116.0 -> "3x3"
            DungeonUtils.inBoss && x in 41.0..76.0 && y in 64.0..73.0 && z in 97.0..123.0 -> "Mid"
            DungeonUtils.inBoss && x in 44.0..65.0 && y in 62.0..75.0 && z in 64.0..87.0 -> "P5"
            DungeonUtils.inBoss && y in 140.0..219.0 -> "EE1"
            else -> "Unknown"
        }

        if (newSection != currentSection) {
            if (debugMode) modMessage("§7[AutoLeap] Section: $currentSection -> $newSection (${x.toInt()},${y.toInt()},${z.toInt()})")
            if (currentSection == "PY" && newSection != "PY") pyAutoLeaped = false
        }
        currentSection = newSection
    }

    private fun handleLeap(completedSection: String? = null, isAutoLeap: Boolean = true) {
        if (isAutoLeap && currentSection == "Unknown") return

        val targetSection = if (!completedSection.isNullOrEmpty() && completedSection != "Unknown") completedSection else currentSection

        val optionIndex = when (targetSection) {
            "Clear" -> clear
            "EE1"   -> ee1
            "EE2"   -> ee2
            "EE3"   -> ee3
            "EE4"   -> ee4
            "Core"  -> core
            "3x3"   -> threeByThree
            "Mid"   -> mid
            "P5"    -> p5
            "P2"    -> p2
            "PY"    -> py
            else    -> { if (!isAutoLeap) modMessage("§cAutoLeap: unknown section \"$targetSection\"."); return }
        }

        val primaryClass = classOptions.getOrElse(optionIndex) { "Unknown" }
        if (debugMode) modMessage("§7[AutoLeap] Target: $primaryClass (section=$targetSection)")
        if (primaryClass == "Unknown") return

        val targetClass = if (targetSection == "EE2" || targetSection == "EE3") {
            val primaryDead = DungeonUtils.dungeonTeammatesNoSelf
                .find { it.clazz.name.equals(primaryClass, ignoreCase = true) }?.isDead ?: false
            if (primaryDead) {
                val fallbackIndex = if (targetSection == "EE2") ee2Fallback else ee3Fallback
                val fallbackClass = classOptions.getOrElse(fallbackIndex) { "Unknown" }
                if (debugMode) modMessage("§7[AutoLeap] Primary dead, fallback: $fallbackClass")
                if (fallbackClass == "Unknown") return
                fallbackClass
            } else primaryClass
        } else primaryClass

        leapToClass(targetClass)
    }
}
