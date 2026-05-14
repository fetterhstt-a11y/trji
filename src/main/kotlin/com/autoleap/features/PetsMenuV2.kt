package com.autoleap.features

import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.clickgui.settings.impl.ColorSetting
import com.odtheking.odin.clickgui.settings.impl.NumberSetting
import com.odtheking.odin.events.ScreenEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Category
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.Color
import com.odtheking.odin.utils.noControlCodes
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore

object PetsMenuV2 : Module(
    name = "Pets Menu",
    description = "Replaces the Hypixel pets screen with a custom grid UI.",
    category = Category.DUNGEON
) {
    val onlyFavorites   by BooleanSetting("Only Favorites",      false, desc = "Only show pets marked as favorites (⭐).")
    val highlightActive by BooleanSetting("Highlight Active Pet", true,  desc = "Highlight the currently equipped pet in green.")
    val showName        by BooleanSetting("Show Name",            true,  desc = "Show the pet name on each card.")
    val showLevel       by BooleanSetting("Show Level",           true,  desc = "Show the pet level number on each card.")
    val showProgress    by BooleanSetting("Show Progress",        false, desc = "Show XP progress bar toward the next level.")

    val bgColor     by ColorSetting("Background",   Color(0x0D, 0x0D, 0x12, 0.95f), true,  "Panel background color and opacity.")
    val cardColor   by ColorSetting("Card Color",   Color(0x18, 0x18, 0x24, 1.0f),  true,  "Card background color and opacity.")
    val hoverColor  by ColorSetting("Hover Color",  Color(0x28, 0x28, 0x40, 1.0f),  true,  "Card background color when hovered.")
    val accentColor by ColorSetting("Accent Color", Color(0x6A, 0x5A, 0xFF, 1.0f),  false, "Header accent line color.")

    val menuScale by NumberSetting("Menu Scale", 1.0f, 0.5f, 2.0f,  0.05f, desc = "Overall scale of the pets panel.")
    val iconSize  by NumberSetting("Icon Size",  32.0f, 16.0f, 96.0f, 2.0f, desc = "Size of the pet icon on each card (px).")

    init {
        on<ScreenEvent.Open> {
            if (!enabled) return@on
            // Don't replace our own PetsScreen
            if (screen is PetsScreen) return@on
            val clean = screen.title.string.noControlCodes.trim()
            if (!clean.startsWith("Pets")) return@on
            val menu = (screen as? AbstractContainerScreen<*>)?.menu ?: return@on
            val inv  = mc.player?.inventory ?: return@on
            mc.execute { mc.setScreen(PetsScreen(menu, inv)) }
        }
    }

    // ── Custom screen — extends AbstractContainerScreen so PetKeybinds can cast to it ──

    class PetsScreen(
        menu: AbstractContainerMenu,
        playerInventory: Inventory,
    ) : AbstractContainerScreen<AbstractContainerMenu>(menu, playerInventory, Component.literal("Pets")) {

        init {
            // Initialize required AbstractContainerScreen fields
            imageWidth = 100
            imageHeight = 100
        }

        override fun init() {
            super.init()
        }

        private companion object {
            const val BASE_COLS   = 4
            const val BASE_CARD_W = 100
            const val BASE_CARD_H = 140
            const val BASE_GAP    = 8
            const val BASE_PAD    = 14
            const val BASE_HDR    = 30

            fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
            val CARD_ACT  = argb(0xFF, 0x16, 0x2E, 0x14)
            val BDR_NRM   = argb(0xFF, 0x33, 0x33, 0x50)
            val BDR_ACT   = argb(0xFF, 0x44, 0xAA, 0x33)
            val WHITE     = argb(0xFF, 0xFF, 0xFF, 0xFF)
            val DIM       = argb(0xFF, 0xAA, 0xAA, 0xBB)
            val PROGRESS  = argb(0xFF, 0x55, 0xFF, 0x55)
            val PROG_BACK = argb(0xFF, 0x22, 0x22, 0x22)
        }

        private data class Layout(
            val cols: Int, val cardW: Int, val cardH: Int,
            val gap: Int, val pad: Int, val hdr: Int, val iconSz: Int,
        )

        private fun layout(): Layout {
            val s = PetsMenuV2.menuScale
            return Layout(
                cols   = BASE_COLS,
                cardW  = (BASE_CARD_W * s).toInt(),
                cardH  = (BASE_CARD_H * s).toInt(),
                gap    = (BASE_GAP    * s).toInt().coerceAtLeast(1),
                pad    = (BASE_PAD    * s).toInt(),
                hdr    = (BASE_HDR    * s).toInt(),
                iconSz = PetsMenuV2.iconSize.toInt().coerceIn(8, 48),
            )
        }

        private val cardBounds = mutableMapOf<Int, IntArray>()
        private var cachedPets: List<net.minecraft.world.inventory.Slot>? = null
        private var lastFilterState = false

        // ── Rendering ────────────────────────────────────────────────────────

        // Must implement — not called since we fully override render()
        override fun renderBg(ctx: GuiGraphics, partialTick: Float, mx: Int, my: Int) {}

        // Suppress the dimmed world overlay from AbstractContainerScreen
        override fun renderBackground(ctx: GuiGraphics, mx: Int, my: Int, partialTick: Float) {}

        override fun render(ctx: GuiGraphics, mx: Int, my: Int, partialTick: Float) {
            try {
                val mc   = Minecraft.getInstance()
                val sw   = mc.window.guiScaledWidth
                val sh   = mc.window.guiScaledHeight
                val lay  = layout()
                val cfg  = PetsMenuV2
                val pets = getPetsCached()
                
                if (pets.isEmpty()) {
                    onClose()
                    return
                }

                val rows   = maxOf(1, (pets.size + lay.cols - 1) / lay.cols)
                val panelW = lay.cols * lay.cardW + (lay.cols - 1) * lay.gap + lay.pad * 2
                val panelH = lay.hdr + rows * lay.cardH + (rows - 1) * lay.gap + lay.pad * 2
                val px     = (sw - panelW) / 2
                val py     = (sh - panelH) / 2

                drawPanel(ctx, px, py, panelW, panelH, cfg.bgColor.rgba)
                ctx.drawString(mc.font, "Pets", px + lay.pad, py + (lay.hdr - mc.font.lineHeight) / 2, WHITE, true)
                ctx.fill(px + lay.pad, py + lay.hdr - 3, px + panelW - lay.pad, py + lay.hdr - 1, cfg.accentColor.rgba)

                cardBounds.clear()
                var hoveredStack: ItemStack? = null

                pets.forEachIndexed { i, slot ->
                    val col    = i % lay.cols
                    val row    = i / lay.cols
                    val cx     = px + lay.pad + col * (lay.cardW + lay.gap)
                    val cy     = py + lay.pad + lay.hdr + row * (lay.cardH + lay.gap)
                    val hov    = mx in cx until cx + lay.cardW && my in cy until cy + lay.cardH
                    val active = cfg.highlightActive && isActivePet(slot.item)
                    if (hov) hoveredStack = slot.item

                    drawCard(ctx, cx, cy, lay, hov, active, cfg.cardColor.rgba, cfg.hoverColor.rgba)
                    renderIcon(ctx, slot.item, cx, cy, lay)
                    drawCardText(ctx, mc, cx, cy, lay, slot.item, active, cfg)

                    cardBounds[slot.index] = intArrayOf(cx, cy, lay.cardW, lay.cardH)
                }

                if (hoveredStack != null && !PetsMenuV2.onlyFavorites) {
                    ctx.setTooltipForNextFrame(mc.font, hoveredStack, mx.coerceIn(4, sw - 4), my.coerceIn(4, sh - 4))
                    ctx.renderDeferredElements()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onClose()
            }
        }

        private fun getPetsCached(): List<net.minecraft.world.inventory.Slot> {
            val currentFilter = PetsMenuV2.onlyFavorites
            if (cachedPets != null && lastFilterState == currentFilter) {
                return cachedPets ?: emptyList()
            }
            cachedPets = collectPets()
            lastFilterState = currentFilter
            return cachedPets ?: emptyList()
        }

        private fun drawPanel(ctx: GuiGraphics, x: Int, y: Int, w: Int, h: Int, bg: Int) {
            ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg)
            ctx.fill(x, y, x + w, y + 1, BDR_NRM)
            ctx.fill(x, y + h - 1, x + w, y + h, BDR_NRM)
            ctx.fill(x, y, x + 1, y + h, BDR_NRM)
            ctx.fill(x + w - 1, y, x + w, y + h, BDR_NRM)
        }

        private fun drawCard(ctx: GuiGraphics, x: Int, y: Int, lay: Layout, hov: Boolean, active: Boolean, baseCard: Int, hovCard: Int) {
            val bg  = when { hov -> hovCard; active -> CARD_ACT; else -> baseCard }
            val bdr = if (active) BDR_ACT else BDR_NRM
            ctx.fill(x + 1, y + 1, x + lay.cardW - 1, y + lay.cardH - 1, bg)
            ctx.fill(x, y, x + lay.cardW, y + 1, bdr)
            ctx.fill(x, y + lay.cardH - 1, x + lay.cardW, y + lay.cardH, bdr)
            ctx.fill(x, y, x + 1, y + lay.cardH, bdr)
            ctx.fill(x + lay.cardW - 1, y, x + lay.cardW, y + lay.cardH, bdr)
        }

        private fun renderIcon(ctx: GuiGraphics, stack: ItemStack, cx: Int, cy: Int, lay: Layout) {
            val sz      = lay.iconSz
            val scale   = sz / 16f
            val centerX = (cx + lay.cardW / 2).toFloat()
            val centerY = (cy + 6 + sz / 2).toFloat()

            val petName   = extractPetName(stack.displayName.string.noControlCodes.trim())
            val textureId = petName.lowercase().replace(" ", "_").replace(Regex("[^a-z0-9_]"), "")
            val loc       = Identifier.parse("trji:textures/pets/$textureId.png")
            val mc        = Minecraft.getInstance()

            val useCustom = try { mc.resourceManager.getResource(loc).isPresent } catch (e: Exception) { false }

            ctx.pose().pushMatrix()
            ctx.pose().translate(centerX, centerY)
            ctx.pose().scale(scale)
            try {
                if (useCustom) ctx.blit(loc, -8, -8, 8, 8, 0.0f, 1.0f, 0.0f, 1.0f)
                else           ctx.renderItem(stack, -8, -8)
            } catch (e: Exception) {
                ctx.renderItem(stack, -8, -8)
            }
            ctx.pose().popMatrix()
        }

        private fun drawCardText(
            ctx: GuiGraphics, mc: Minecraft,
            cx: Int, cy: Int, lay: Layout,
            stack: ItemStack, active: Boolean, cfg: PetsMenuV2,
        ) {
            var textY = cy + 6 + lay.iconSz + 3

            if (cfg.showName) {
                val cleanName = extractPetName(stack.displayName.string.noControlCodes.trim())
                val nameW = mc.font.width(cleanName)
                ctx.drawString(mc.font, cleanName, cx + (lay.cardW - nameW) / 2, textY, WHITE, false)
                textY += mc.font.lineHeight + 2
            }

            if (cfg.showLevel) {
                petLevel(stack)?.let { lvl ->
                    val lw = mc.font.width(lvl)
                    ctx.drawString(mc.font, lvl, cx + (lay.cardW - lw) / 2, textY, DIM, false)
                }
            }

            val barY = cy + lay.cardH - 6
            if (cfg.showProgress) {
                val pct = petProgress(stack)
                if (pct != null) {
                    val barX = cx + 6
                    val barW = lay.cardW - 12
                    val fill = (barW * pct).toInt().coerceIn(0, barW)
                    ctx.fill(barX, barY - 2, barX + barW, barY + 2, PROG_BACK)
                    ctx.fill(barX, barY - 2, barX + fill, barY + 2, PROGRESS)
                    return
                }
            }
            ctx.fill(cx + 6, barY - 2, cx + lay.cardW - 6, barY + 2, rarityColor(stack))
        }

        // ── Input ────────────────────────────────────────────────────────────

        override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
            return try {
                val mc    = Minecraft.getInstance()
                val mx    = event.x().toInt()
                val my    = event.y().toInt()
                val entry = cardBounds.entries.firstOrNull { (_, b) ->
                    mx in b[0] until b[0] + b[2] && my in b[1] until b[1] + b[3]
                }
                if (entry != null && mc.gameMode != null && mc.player != null) {
                    mc.gameMode?.handleInventoryMouseClick(
                        menu.containerId, entry.key, 0, ClickType.PICKUP, mc.player ?: return true
                    )
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                true
            }
        }

        // Pass unhandled keys to super so ScreenEvent.KeyPress fires for PetKeybinds
        override fun keyPressed(event: KeyEvent): Boolean {
            return try {
                if (event.key() == 256) { onClose(); return true }
                super.keyPressed(event)
            } catch (e: Exception) {
                e.printStackTrace()
                true
            }
        }

        override fun isPauseScreen() = false

        // ── Helpers ──────────────────────────────────────────────────────────

        private fun extractPetName(displayName: String): String {
            return displayName
                .replace(Regex("^[⭐\\s]+"), "")                    // leading stars/spaces
                .replace(Regex("\\[Lvl\\s+[\\w]+]\\s*"), "")       // [Lvl 100] or [Lvl MAX]
                .replace(Regex("\\s*-\\s*Level\\s+\\d+.*$"), "")   // trailing " - Level N"
                .replace(Regex("\\s*\\(.*\\)\\s*$"), "")           // trailing parenthetical
                .trim()
        }

        private fun collectPets(): List<net.minecraft.world.inventory.Slot> {
            return try {
                val totalSlots = menu.slots.size
                // Player inventory is always 36 slots at the end
                // Pets container should be before that
                if (totalSlots <= 36) return emptyList()
                
                val menuSlots = totalSlots - 36
                menu.slots.take(menuSlots).filter { slot ->
                    try {
                        if (slot.item.isEmpty) return@filter false
                        val lore = slot.item.getOrDefault(DataComponents.LORE, ItemLore.EMPTY)
                        if (lore.lines().isEmpty()) return@filter false
                        if (PetsMenuV2.onlyFavorites) {
                            slot.item.displayName.string.contains("⭐")
                        } else {
                            true
                        }
                    } catch (e: Exception) {
                        false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

        private fun isActivePet(stack: ItemStack): Boolean {
            val lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY)
            return lore.lines().any { line ->
                val t = line.string.noControlCodes.lowercase()
                t.contains("despawn") || t.contains("active pet")
            }
        }

        private fun petLevel(stack: ItemStack): String? {
            val lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY)
            val line = lore.lines().firstOrNull {
                it.string.noControlCodes.contains("Level", ignoreCase = true)
            }?.string?.noControlCodes ?: return null
            val num = Regex("\\d+").find(line)?.value
            if (num != null) return num
            return if (line.uppercase().contains("MAX")) "MAX" else null
        }

        private fun petProgress(stack: ItemStack): Float? {
            val lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY)
            val line = lore.lines().firstOrNull { l ->
                val t = l.string.noControlCodes
                t.contains("Progress", ignoreCase = true) && t.contains("%")
            }?.string?.noControlCodes ?: return null
            return Regex("(\\d+(?:\\.\\d+)?)%").find(line)
                ?.groupValues?.get(1)?.toFloatOrNull()?.div(100f)
        }

        private fun rarityColor(stack: ItemStack): Int {
            val lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY)
            val last = lore.lines().lastOrNull { it.string.isNotBlank() }
                ?.string?.noControlCodes?.uppercase() ?: ""
            return when {
                last.contains("LEGENDARY") -> argb(0xFF, 0xFF, 0xAA, 0x00)
                last.contains("MYTHIC")    -> argb(0xFF, 0xFF, 0x55, 0xFF)
                last.contains("EPIC")      -> argb(0xFF, 0xAA, 0x00, 0xFF)
                last.contains("RARE")      -> argb(0xFF, 0x55, 0x55, 0xFF)
                last.contains("UNCOMMON")  -> argb(0xFF, 0x55, 0xFF, 0x55)
                else                       -> argb(0x88, 0xDD, 0xDD, 0xDD)
            }
        }
    }
}
