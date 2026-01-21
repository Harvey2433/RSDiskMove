package top.maple_bamboo.rs_disk_move.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import top.maple_bamboo.rs_disk_move.RSDiskMove;
import top.maple_bamboo.rs_disk_move.menu.DiskMoveMenu;
import top.maple_bamboo.rs_disk_move.network.MoveActionPacket;
import top.maple_bamboo.rs_disk_move.network.OpenGuiPacket;
import top.maple_bamboo.rs_disk_move.network.PacketHandler;

public class DiskMoveScreen extends AbstractContainerScreen<DiskMoveMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(RSDiskMove.MOD_ID, "textures/gui/disk_move.png");
    // 齿轮图标纹理
    private static final ResourceLocation SETTINGS_TEXTURE = new ResourceLocation(RSDiskMove.MOD_ID, "textures/gui/settings.png");

    private Button transferBtn;

    public DiskMoveScreen(DiskMoveMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
        this.imageWidth = 185;
        this.imageHeight = 256;

        this.inventoryLabelY = 162;
        this.titleLabelY = 8;
    }

    @Override
    protected void init() {
        super.init();

        // 1. 转移按钮 (Start/Stop)
        this.transferBtn = Button.builder(Component.translatable("gui.rs_disk_move.start"), button -> PacketHandler.sendToServer(new MoveActionPacket(this.menu.getBlockPos()))).bounds(this.leftPos + 85, this.topPos + 68, 80, 20).build();
        this.addRenderableWidget(transferBtn);

        // 2. 配置按钮 (小齿轮)
        // 位置调整：this.leftPos + this.imageWidth + 3 (主界面右侧偏移 3px)
        // 尺寸调整：20 x 20 (小正方形)
        int configX = this.leftPos + this.imageWidth + 3;
        int configY = this.topPos + 5;

        SettingsButton configBtn = new SettingsButton(configX, configY, button -> PacketHandler.sendToServer(new OpenGuiPacket(this.menu.getBlockPos(), 1)));

        configBtn.setTooltip(Tooltip.create(Component.translatable("gui.rs_disk_move.config_tooltip")));
        this.addRenderableWidget(configBtn);
    }

    // 自定义按钮类，用于绘制 Settings.png 图标
    private static class SettingsButton extends Button {
        public SettingsButton(int x, int y, OnPress onPress) {
            super(x, y, 20, 20, Component.empty(), onPress, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            // 1. 调用父类方法绘制标准按钮背景 (灰色盒子 + 悬停高亮效果)
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);

            // 2. 绘制齿轮图标
            // 假设 Settings.png 是一个简单的正方形图标
            // 我们将其绘制在按钮中心 (按钮20x20，图标假设16x16，则偏移2px)
            RenderSystem.setShaderTexture(0, SETTINGS_TEXTURE);
            RenderSystem.enableBlend();

            // blit(texture, x, y, u, v, width, height, textureWidth, textureHeight)
            // 这里假设 Settings.png 是一张单独的图片(非图集)，大小为16x16或其他正方形尺寸
            // 最后的 16, 16 是指定读取纹理的大小，如果你原图是256x256但内容只有左上角，请相应调整
            // 如果 Settings.png 整个就是图标，可以直接写 16, 16
            guiGraphics.blit(SETTINGS_TEXTURE, this.getX() + 2, this.getY() + 2, 0, 0, 16, 16, 16, 16);

            RenderSystem.disableBlend();
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.menu.getData().get(1) == 1) {
            this.transferBtn.setMessage(Component.translatable("gui.rs_disk_move.stop"));
        } else {
            long total = this.menu.getTotalItems();
            long moved = this.menu.getMovedItems();

            if (total > 0 && moved >= total) {
                this.transferBtn.setMessage(Component.translatable("gui.rs_disk_move.finished"));
            } else if (total > 0 && moved > 0) {
                this.transferBtn.setMessage(Component.translatable("gui.rs_disk_move.resume"));
            } else {
                this.transferBtn.setMessage(Component.translatable("gui.rs_disk_move.start"));
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFFFFFF, true);
        guiGraphics.drawString(this.font, Component.translatable("gui.rs_disk_move.title"), 8, 8, 0xFFFFFF, true);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderTexture(0, TEXTURE);
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;

        guiGraphics.blit(TEXTURE, relX, relY, 0, 0, this.imageWidth, this.imageHeight);

/*
 因为使用自定义背景，这里不再需要绘制箭头
        for (int i = 0; i < 6; i++) {
            int slotY = 25 + i * 18 + 5;
            guiGraphics.drawString(this.font, ">>>", relX + 48, relY + slotY, 0x404040, false);
        }
*/

        long total = this.menu.getTotalItems();
        long moved = this.menu.getMovedItems();

        int barWidth = 160;
        int barX = relX + (this.imageWidth - barWidth) / 2;
        int barHeight = 9;
        int slotsBottomY = 25 + 6 * 18;
        int inventoryTopY = 174;
        int barY = relY + slotsBottomY + (inventoryTopY - slotsBottomY - barHeight) / 2;

        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF555555);

        if (total > 0) {
            float progress = (float) ((double) moved / (double) total);
            progress = Mth.clamp(progress, 0.0f, 1.0f);
            int fillWidth = (int) (barWidth * progress);

            guiGraphics.fill(barX, barY, barX + fillWidth, barY + barHeight, 0xFF00AA00);

            // %d%% (%s / %s)
            Component progressText = Component.translatable("gui.rs_disk_move.progress_format",
                    (int)(progress * 100), readableNum(moved), readableNum(total));

            int textWidth = this.font.width(progressText);
            int textX = barX + (barWidth - textWidth) / 2;
            int textY = barY + (barHeight - 8) / 2 + 1;

            guiGraphics.drawString(this.font, progressText, textX, textY, 0xFFFFFFFF, true);
        } else {
            Component text = Component.translatable("gui.rs_disk_move.idle");
            int textWidth = this.font.width(text);
            int textX = barX + (barWidth - textWidth) / 2;
            int textY = barY + (barHeight - 8) / 2 + 1;
            guiGraphics.drawString(this.font, text, textX, textY, 0xFFAAAAAA, true);
        }
    }

    private String readableNum(long val) {
        if (val < 1000) return String.valueOf(val);
        if (val < 1000000) return String.format("%.1fk", val / 1000.0);
        return String.format("%.1fM", val / 1000000.0);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}