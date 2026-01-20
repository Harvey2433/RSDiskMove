package top.maple_bamboo.rs_disk_move.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.util.Mth;
import top.maple_bamboo.rs_disk_move.RSDiskMove;
import top.maple_bamboo.rs_disk_move.menu.DiskMoveMenu;
import top.maple_bamboo.rs_disk_move.network.MoveActionPacket;
import top.maple_bamboo.rs_disk_move.network.OpenGuiPacket;
import top.maple_bamboo.rs_disk_move.network.PacketHandler;

public class DiskMoveScreen extends AbstractContainerScreen<DiskMoveMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(RSDiskMove.MOD_ID, "textures/gui/disk_move.png");
    private Button transferBtn;

    public DiskMoveScreen(DiskMoveMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
        this.imageWidth = 185;
        this.imageHeight = 256;
        this.titleLabelY = -1000;
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();

        this.transferBtn = Button.builder(Component.literal("Start"), button -> {
            PacketHandler.sendToServer(new MoveActionPacket(this.menu.getBlockPos()));
        }).bounds(this.leftPos + 85, this.topPos + 68, 80, 20).build();
        this.addRenderableWidget(transferBtn);

        Button configBtn = Button.builder(Component.literal("Config"), button -> {
            PacketHandler.sendToServer(new OpenGuiPacket(this.menu.getBlockPos(), 1));
        }).bounds(this.leftPos + this.imageWidth, this.topPos + 5, 50, 20).build();
        configBtn.setTooltip(Tooltip.create(Component.literal("Configure I/O Sides")));
        this.addRenderableWidget(configBtn);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // 动态更新按钮文字
        if (this.menu.getData().get(1) == 1) {
            this.transferBtn.setMessage(Component.literal("Stop"));
        } else {
            // 如果完成了，显示 Finished，否则显示 Start / Resume
            long total = this.menu.getTotalItems();
            long moved = this.menu.getMovedItems();
            if (total > 0 && moved >= total) {
                this.transferBtn.setMessage(Component.literal("Finished"));
            } else if (total > 0 && moved > 0) {
                this.transferBtn.setMessage(Component.literal("Resume"));
            } else {
                this.transferBtn.setMessage(Component.literal("Start"));
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderTexture(0, TEXTURE);
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;

        guiGraphics.blit(TEXTURE, relX, relY, 0, 0, this.imageWidth, this.imageHeight);

        // 箭头
        for (int i = 0; i < 6; i++) {
            int slotY = 25 + i * 18 + 5;
            guiGraphics.drawString(this.font, ">>>", relX + 48, relY + slotY, 0x404040, false);
        }

        // --- 进度条绘制 ---
        long total = this.menu.getTotalItems();
        long moved = this.menu.getMovedItems();

        // 进度条位置：背包上方，槽位下方
        int barX = relX + 12;
        int barY = relY + 155;
        int barWidth = 162;
        int barHeight = 10;

        // 绘制背景 (灰色)
        guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF555555);

        if (total > 0) {
            // 计算进度比例
            float progress = (float) ((double) moved / (double) total);
            progress = Mth.clamp(progress, 0.0f, 1.0f);
            int fillWidth = (int) (barWidth * progress);

            // 绘制填充 (绿色)
            guiGraphics.fill(barX, barY, barX + fillWidth, barY + barHeight, 0xFF00AA00);

            // 绘制文字信息 (居中)
            String progressText = String.format("%d%% (%s / %s)", (int)(progress * 100), readableNum(moved), readableNum(total));
            int textWidth = this.font.width(progressText);
            int textX = barX + (barWidth - textWidth) / 2;
            int textY = barY + 1;

            guiGraphics.drawString(this.font, progressText, textX, textY, 0xFFFFFFFF, true);
        } else {
            // 没有任务时显示 Idle
            String text = "Idle";
            guiGraphics.drawString(this.font, text, barX + (barWidth - this.font.width(text)) / 2, barY + 1, 0xFFAAAAAA, true);
        }
    }

    // 辅助方法：格式化大数字 (如 1.2M)
    private String readableNum(long val) {
        if (val < 1000) return String.valueOf(val);
        if (val < 1000000) return String.format("%.1fk", val / 1000.0);
        return String.format("%.1fM", val / 1000000.0);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}