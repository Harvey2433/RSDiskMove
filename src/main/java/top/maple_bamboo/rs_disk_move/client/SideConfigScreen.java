package top.maple_bamboo.rs_disk_move.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import top.maple_bamboo.rs_disk_move.RSDiskMove;
import top.maple_bamboo.rs_disk_move.menu.SideConfigMenu;
import top.maple_bamboo.rs_disk_move.network.OpenGuiPacket;
import top.maple_bamboo.rs_disk_move.network.PacketHandler;
import top.maple_bamboo.rs_disk_move.network.SideTogglePacket;

import java.util.Objects;

public class SideConfigScreen extends AbstractContainerScreen<SideConfigMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(RSDiskMove.MOD_ID, "textures/gui/side_config.png");

    public SideConfigScreen(SideConfigMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();

        int size = 20;
        int gap = 2;
        int step = size + gap;

        int centerX = this.leftPos + this.imageWidth / 2;
        int centerY = this.topPos + this.imageHeight / 2;

        // --- 按钮布局 (保持之前的九宫格设计) ---

        // Row 1
        addConfigButton(Direction.UP, centerX - size/2, centerY - size/2 - step);
        Button disableAllBtn = Button.builder(Component.literal(""), button -> PacketHandler.sendToServer(new SideTogglePacket(menu.getBlockPos(), -1))).bounds(centerX - size/2 + step, centerY - size/2 - step, size, size).build();
        disableAllBtn.setTooltip(Tooltip.create(Component.translatable("gui.rs_disk_move.config.disable_all")));
        this.addRenderableWidget(disableAllBtn);

        // Row 2
        addConfigButton(Direction.WEST, centerX - size/2 - step, centerY - size/2);
        addConfigButton(Direction.NORTH, centerX - size/2, centerY - size/2);
        addConfigButton(Direction.EAST, centerX - size/2 + step, centerY - size/2);

        // Row 3
        Button backBtn = Button.builder(Component.literal("<—"), button -> PacketHandler.sendToServer(new OpenGuiPacket(this.menu.getBlockPos(), 0))).bounds(centerX - size/2 - step, centerY - size/2 + step, size, size).build();
        backBtn.setTooltip(Tooltip.create(Component.translatable("gui.rs_disk_move.config.back")));
        this.addRenderableWidget(backBtn);

        addConfigButton(Direction.SOUTH, centerX - size/2, centerY - size/2 + step);
        addConfigButton(Direction.DOWN, centerX - size/2 + step, centerY - size/2 + step);
    }

    private void addConfigButton(Direction dir, int x, int y) {
        this.addRenderableWidget(new ConfigButton(x, y, 20, 20, dir));
    }

    // --- 修改点：添加左上角标题 ---
    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 绘制白色标题 "输出配置" (使用翻译键)
        guiGraphics.drawString(this.font, Component.translatable("gui.rs_disk_move.config.title"), 8, 8, 0xFFFFFF, true);

        // 如果不需要显示"Inventory"等默认文字，可以不调用 super.renderLabels
        // super.renderLabels(guiGraphics, mouseX, mouseY);
    }

    class ConfigButton extends Button {
        private final Direction dir;

        public ConfigButton(int x, int y, int width, int height, Direction dir) {
            super(x, y, width, height,
                    Component.literal(dir.getName().substring(0, 1).toUpperCase()),
                    button -> PacketHandler.sendToServer(new SideTogglePacket(menu.getBlockPos(), dir)),
                    DEFAULT_NARRATION);
            this.dir = dir;
            this.setTooltip(Tooltip.create(Component.literal(dir.getName())));
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            int config = menu.getSideConfig();
            boolean isEnabled = (config & (1 << dir.ordinal())) != 0;
            int color = isEnabled ? 0xFF0000AA : 0xFF555555;

            guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF000000);
            guiGraphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, color | 0xFF000000);

            boolean renderedBlock = false;
            if (Objects.requireNonNull(minecraft).level != null) {
                BlockPos targetPos = menu.getBlockPos().relative(dir);
                BlockState state = minecraft.level.getBlockState(targetPos);
                if (!state.isAir()) {
                    ItemStack stack = new ItemStack(state.getBlock());
                    if (!stack.isEmpty()) {
                        PoseStack pose = guiGraphics.pose();
                        pose.pushPose();
                        pose.translate(getX() + 2, getY() + 2, 0);
                        pose.scale(1.0f, 1.0f, 1.0f);
                        guiGraphics.renderItem(stack, 0, 0);
                        pose.popPose();
                        renderedBlock = true;
                    }
                }
            }

            if (!renderedBlock) {
                guiGraphics.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, 0xFFFFFFFF);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderTexture(0, TEXTURE);
        guiGraphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}