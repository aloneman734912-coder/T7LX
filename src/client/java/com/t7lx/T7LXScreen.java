package com.t7lx;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class T7LXScreen extends Screen {
    private static final int BLUE = 0xFF39A9FF;
    private static final int PANEL = 0xF20B1424;
    private static final int ROW = 0xFF14243A;

    public T7LXScreen() {
        super(Component.literal("T7LX"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int panelWidth = 330;
        int panelHeight = 210;
        int x = (width - panelWidth) / 2;
        int y = (height - panelHeight) / 2;

        graphics.fill(x, y, x + panelWidth, y + panelHeight, PANEL);
        graphics.fill(x, y, x + panelWidth, y + 3, BLUE);
        graphics.drawCenteredString(font, Component.literal("T7LX"), width / 2, y + 18, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.literal("CLIENT MOD MENU"), width / 2, y + 38, 0xFF8AA9C8);

        int rowX = x + 18;
        int rowY = y + 76;
        int rowW = panelWidth - 36;
        graphics.fill(rowX, rowY, rowX + rowW, rowY + 42, ROW);
        graphics.drawString(font, Component.literal("Freecam"), rowX + 12, rowY + 15, 0xFFFFFFFF);
        String state = T7LXClient.isFreecamEnabled() ? "ON" : "OFF";
        int stateColor = T7LXClient.isFreecamEnabled() ? 0xFF55E69A : 0xFFFF7777;
        graphics.drawString(font, Component.literal(state), rowX + rowW - 38, rowY + 15, stateColor);

        graphics.drawCenteredString(font, Component.literal("RIGHT SHIFT  •  CLOSE MENU"), width / 2, y + 172, 0xFF8AA9C8);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (width - 330) / 2 + 18;
        int y = (height - 210) / 2 + 76;
        if (button == 0 && mouseX >= x && mouseX <= x + 294 && mouseY >= y && mouseY <= y + 42) {
            T7LXClient.toggleFreecam();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
