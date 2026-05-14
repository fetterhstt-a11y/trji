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
import com.odtheking.odin.utils.render.text
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.component.ItemLore

object PetsMenu : Module(
    name = "Pets Menu",
    description = "Replaces the Hypixel pets menu with a modern UI.",
    category = Category.DUNGEON
) {
    private val onlyFavorites by BooleanSetting("Only Favorites", false, desc = "Only show pets marked as favorites (⭐).")

    private const val COLS   = 4
    private const val CARD_W = 70
    private const val CARD_H = 76
    private const val GAP    = 8
    private const val PAD    = 14
    private const val HDR    = 26

    private val BG       = Color(0x0D, 0x0D, 0x12, 0.95f)
    private val CARD_NRM = Color(0x18, 0x18, 0x24, 1.0f)
    private val CARD_HOV = Color(0x26, 0x26, 0x38, 1.0f)
    private val ACCENT   = Color(0x6A, 0x5A, 0xFF, 1.0f)
    private val WHITE    = Color(0xFF, 0xFF, 0xFF, 1.0f)
    private val DIM      = Color(0xAA, 0xAA, 0xBB, 1.0f)
    private val BORDER   = Color(0x33, 0x33, 0x50, 1.0f)

    private val hoveredSlotField = AbstractContainerScreen::class.java
        .getDeclaredField("hoveredSlot")
        .also { it.isAccessible = true }

    private val renderTooltipMethod = AbstractContainerScreen::class.java
        .getDeclaredMethod("renderTooltip", net.minecraft.client.gui.GuiGraphics::class.java, Int::class.java, Int::class.java)
        .also { it.isAccessible = true }

    private var active = false
    private var lastMx = 0
    private var lastMy = 0
    // slot.index -> [cardX, cardY, w, h]
    private val cardBounds = mutableMapOf<Int, IntArray>()

    init {
        on<ScreenEvent.Open> {
            active = screen.title.string.noControlCodes.trim() == "Pets"
            cardBounds.clear()
        }

        on<ScreenEvent.Close> {
            active = false
            cardBounds.clear()
        }

        // GuiEvent.Render fires at HEAD of AbstractContainerScreen.render — cancel it,
        // draw our custom UI, then call screen.renderTooltip for the hovered card.
        on<GuiEvent.Render> {
            if (!active) return@on
            val s = screen as? AbstractContainerScreen<*> ?: return@on
            cancel()
            lastMx = mouseX
            lastMy = mouseY
            draw(guiGraphics, s, mouseX, mouseY)
        }

        // Cancel vanilla click; map to the card the cursor is over.
        on<ScreenEvent.MouseClick> {
            if (!active) return@on
            cancel()
            val entry = cardBounds.entries.firstOrNull { (_, b) ->
                lastMx in b[0] until b[0] + b[2] && lastMy in b[1] until b[1] + b[3]
            } ?: return@on
            val s = screen as? AbstractContainerScreen<*> ?: return@on
            mc.gameMode?.handleInventoryMouseClick(s.menu.containerId, entry.key, 0, ClickType.PICKUP, mc.player ?: return@on)
        }
    }

    private fun draw(ctx: net.minecraft.client.gui.GuiGraphics, screen: AbstractContainerScreen<*>, mx: Int, my: Int) {
        val menuSlots = screen.menu.slots.size - 36
        val pets = screen.menu.slots
            .take(menuSlots)
            .filter { !it.item.isEmpty && (!onlyFavorites || it.item.displayName.string.contains("⭐")) }

        val rows   = maxOf(1, (pets.size + COLS - 1) / COLS)
        val panelW = COLS * CARD_W + (COLS - 1) * GAP + PAD * 2
        val panelH = rows * CARD_H + (rows - 1) * GAP + PAD * 2 + HDR

        val sw = mc.window.guiScaledWidth
        val sh = mc.window.guiScaledHeight
        val px = (sw - panelW) / 2
        val py = (sh - panelH) / 2

        ctx.fill(0, 0, sw, sh, 0x88000000.toInt())
        DrawContextRenderer.roundedFill(ctx, px, py, panelW, panelH, BG.rgba, 8f, BORDER.rgba, 1f)

        ctx.text(screen.title.string.noControlCodes, px + PAD, py + 7, WHITE, true)
        ctx.roundedFill(px + PAD, py + HDR - 4, panelW - PAD * 2, 2, ACCENT.rgba, 1)

        cardBounds.clear()
        var hoveredSlot: Slot? = null

        pets.forEachIndexed { i, slot ->
            val col = i % COLS
            val row = i / COLS
            val cx  = px + PAD + col * (CARD_W + GAP)
            val cy  = py + PAD + HDR + row * (CARD_H + GAP)
            val hovered = mx in cx until cx + CARD_W && my in cy until cy + CARD_H
            if (hovered) hoveredSlot = slot

            DrawContextRenderer.roundedFill(ctx, cx, cy, CARD_W, CARD_H,
                if (hovered) CARD_HOV.rgba else CARD_NRM.rgba, 6f, BORDER.rgba, 1f)

            ctx.renderItem(slot.item, cx + (CARD_W - 16) / 2, cy + 9)

            val name = slot.item.displayName.string.noControlCodes.trim()
            ctx.text(name, cx + (CARD_W - getStringWidth(name)) / 2, cy + 30, WHITE, false)

            val level = petLevel(slot.item)
            if (level != null) {
                ctx.text(level, cx + (CARD_W - getStringWidth(level)) / 2, cy + 41, DIM, false)
            }

            ctx.roundedFill(cx + 6, cy + CARD_H - 8, CARD_W - 12, 4, rarityColor(slot.item).rgba, 2)

            cardBounds[slot.index] = intArrayOf(cx, cy, CARD_W, CARD_H)
        }

        // Point hoveredSlot at the card under the cursor so renderTooltip shows the right item.
        if (hoveredSlot != null) {
            runCatching { hoveredSlotField.set(screen, hoveredSlot) }
            runCatching { renderTooltipMethod.invoke(screen, ctx, mx, my) }
            runCatching { hoveredSlotField.set(screen, null) }
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
