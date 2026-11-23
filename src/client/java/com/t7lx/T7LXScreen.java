package com.t7lx;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class T7LXScreen extends Screen {
    private static final int BLUE = 0xFF39A9FF;
    private static final int PANEL = 0xF20B1424;
    private Button freecamButton;

    public T7LXScreen() {
        super(Component.literal("T7LX"));
    }

    @Override
    protected void init() {
        int panelWidth = 330;
        int x = (this.width - panelWidth) / 2;
        int y = (this.height - 210) / 2;
        freecamButton = Button.builder(freecamLabel(), button -> {
            T7LXClient.toggleFreecam();
            button.setMessage(freecamLabel());
        }).bounds(x + 18, y + 76, panelWidth - 36, 28).build();
        this.addRenderableWidget(freecamButton);
    }

    private static Component freecamLabel() {
        return Component.literal("Freecam: " + (T7LXClient.isFreecamEnabled() ? "ON" : "OFF"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int panelWidth = 330;
        int panelHeight = 210;
        int x = (this.width - panelWidth) / 2;
        int y = (this.height - panelHeight) / 2;

        graphics.fill(x, y, x + panelWidth, y + panelHeight, PANEL);
        graphics.fill(x, y, x + panelWidth, y + 3, BLUE);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(this.font, "T7LX", this.width / 2 - this.font.width("T7LX") / 2, y + 18, 0xFFFFFFFF, true);
        graphics.text(this.font, "CLIENT MOD MENU", this.width / 2 - this.font.width("CLIENT MOD MENU") / 2,
                y + 38, 0xFF8AA9C8, true);
        graphics.text(this.font, "RIGHT SHIFT  -  CLOSE MENU",
                this.width / 2 - this.font.width("RIGHT SHIFT  -  CLOSE MENU") / 2, y + 172, 0xFF8AA9C8, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
