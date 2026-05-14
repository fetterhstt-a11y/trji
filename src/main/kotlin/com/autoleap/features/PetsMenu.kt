package com.autoleap.features

import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.events.GuiEvent
import com.odtheking.odin.events.ScreenEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Category
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.Color
import com.odtheking.odin.utils.noControlCodes
import com.odtheking.odin.utils.render.DrawContextRenderer
import com.odtheking.odin.utils.render.getStringWidth
import com.odtheking.odin.utils.render.roundedFill
import com.odtheking.odin.utils.render.roundedOutline
import com.odtheking.odin.utils.render.text
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.ItemLore

object PetsMenu : Module(
    name = "Pets Menu",
    description = "Replaces the Hypixel pets menu with a modern UI.",
    category = Category.DUNGEON
) {
    private val cancelTooltip by BooleanSetting("Cancel Tooltip", true, desc = "Hides the vanilla item tooltip in the pets menu.")

    private const val COLS     = 4
    private const val CARD_W   = 70
    private const val CARD_H   = 76
    private const val GAP      = 8
    private const val PAD      = 14
    private const val HEADER_H = 26

    private val BG_COLOR      = Color(0x0D, 0x0D, 0x12, 0.95f)
    private val CARD_NORMAL   = Color(0x18, 0x18, 0x24, 1.0f)
    private val CARD_HOVERED  = Color(0x26, 0x26, 0x38, 1.0f)
    private val ACCENT        = Color(0x6A, 0x5A, 0xFF, 1.0f)
    private val TEXT_BRIGHT   = Color(0xFF, 0xFF, 0xFF, 1.0f)
    private val TEXT_DIM      = Color(0xAA, 0xAA, 0xBB, 1.0f)
    private val BORDER        = Color(0x33, 0x33, 0x50, 1.0f)

    private var active = false

    init {
        on<ScreenEvent.Open> {
            active = isPets(screen)
        }

        on<ScreenEvent.Close> {
            active = false
        }

        on<ScreenEvent.Render> {
            if (!active) return@on
            val s = screen as? AbstractContainerScreen<*> ?: return@on
            drawOverlay(guiGraphics, s, mouseX, mouseY)
        }

        on<GuiEvent.RenderSlot> {
            if (!active) return@on
            val s = screen as? AbstractContainerScreen<*> ?: return@on
            val menuSlotCount = s.menu.slots.size - 36
            if (slot.index < menuSlotCount) cancel()
        }

        on<GuiEvent.DrawTooltip> {
            if (!active || !cancelTooltip) return@on
            cancel()
        }
    }

    private fun isPets(screen: net.minecraft.client.gui.screens.Screen): Boolean {
        val title = screen.title.string.noControlCodes
        return title.trim() == "Pets"
    }

    private fun drawOverlay(
        ctx: net.minecraft.client.gui.GuiGraphics,
        screen: AbstractContainerScreen<*>,
        mx: Int,
        my: Int
    ) {
        val menuSlotCount = screen.menu.slots.size - 36
        val petSlots = screen.menu.slots
            .take(menuSlotCount)
            .filter { !it.item.isEmpty }

        val rows   = maxOf(1, (petSlots.size + COLS - 1) / COLS)
        val panelW = COLS * CARD_W + (COLS - 1) * GAP + PAD * 2
        val panelH = rows * CARD_H + (rows - 1) * GAP + PAD * 2 + HEADER_H

        val sw = mc.window.guiScaledWidth
        val sh = mc.window.guiScaledHeight
        val px = (sw - panelW) / 2
        val py = (sh - panelH) / 2

        // Dark full-screen scrim
        ctx.fill(0, 0, sw, sh, 0x88000000.toInt())

        // Panel background
        DrawContextRenderer.roundedFill(ctx, px, py, panelW, panelH, BG_COLOR.rgba, 8f, BORDER.rgba, 1f)

        // Title
        val title = screen.title.string.noControlCodes
        ctx.text(title, px + PAD, py + 7, TEXT_BRIGHT, true)

        // Accent bar under title
        ctx.roundedFill(px + PAD, py + HEADER_H - 4, panelW - PAD * 2, 2, ACCENT.rgba, 1)

        // Pet cards
        petSlots.forEachIndexed { i, slot ->
            val col = i % COLS
            val row = i / COLS
            val cx  = px + PAD + col * (CARD_W + GAP)
            val cy  = py + PAD + HEADER_H + row * (CARD_H + GAP)

            val hovered = mx in cx until cx + CARD_W && my in cy until cy + CARD_H
            val cardBg  = if (hovered) CARD_HOVERED else CARD_NORMAL

            DrawContextRenderer.roundedFill(ctx, cx, cy, CARD_W, CARD_H, cardBg.rgba, 6f, BORDER.rgba, 1f)

            // Item icon
            val iconX = cx + (CARD_W - 16) / 2
            val iconY = cy + 9
            ctx.renderItem(slot.item, iconX, iconY)

            // Pet name
            val name = slot.item.displayName.string.noControlCodes.trim()
            val nameW = getStringWidth(name)
            ctx.text(name, cx + (CARD_W - nameW) / 2, iconY + 19, TEXT_BRIGHT, false)

            // Level from lore
            val level = petLevel(slot.item)
            if (level != null) {
                val lvlW = getStringWidth(level)
                ctx.text(level, cx + (CARD_W - lvlW) / 2, iconY + 30, TEXT_DIM, false)
            }

            // Rarity stripe at card bottom
            val rarity = rarityColor(slot.item)
            ctx.roundedFill(cx + 6, cy + CARD_H - 8, CARD_W - 12, 4, rarity.rgba, 2)
        }
    }

    private fun petLevel(stack: net.minecraft.world.item.ItemStack): String? {
        val lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY)
        return lore.lines().firstOrNull {
            it.string.noControlCodes.contains("Level", ignoreCase = true)
        }?.string?.noControlCodes?.trim()
    }

    private fun rarityColor(stack: net.minecraft.world.item.ItemStack): Color {
        val lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY)
        val last = lore.lines().lastOrNull { it.string.isNotBlank() }
            ?.string?.noControlCodes?.uppercase() ?: ""
        return when {
            last.contains("LEGENDARY") -> Color(0xFF, 0xAA, 0x00, 1.0f)
            last.contains("MYTHIC")    -> Color(0xFF, 0x55, 0xFF, 1.0f)
            last.contains("EPIC")      -> Color(0xAA, 0x00, 0xFF, 1.0f)
            last.contains("RARE")      -> Color(0x55, 0x55, 0xFF, 1.0f)
            last.contains("UNCOMMON")  -> Color(0x55, 0xFF, 0x55, 1.0f)
            else                       -> Color(0xDD, 0xDD, 0xDD, 0.5f)
        }
    }
}
