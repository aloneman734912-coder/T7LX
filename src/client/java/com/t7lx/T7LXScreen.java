package com.t7lx;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class T7LXScreen extends Screen {
    private static final int BLUE = 0xFF39A9FF;
    private static final int PANEL = 0xF20B1424;
    private static final int PANEL_HEIGHT = 410;
    private boolean showCpsSlider;

    public T7LXScreen() {
        super(Component.literal("T7LX"));
    }

    @Override
    protected void init() {
        int panelWidth = 340;
        int x = (this.width - panelWidth) / 2;
        int y = (this.height - PANEL_HEIGHT) / 2;

        this.addRenderableWidget(Button.builder(freecamLabel(), button -> {
            T7LXClient.toggleFreecam();
            button.setMessage(freecamLabel());
        }).bounds(x + 18, y + 70, panelWidth - 36, 27).build());

        this.addRenderableWidget(Button.builder(crystalMacroLabel(), button -> {
            T7LXClient.toggleCrystalMacro();
            button.setMessage(crystalMacroLabel());
        }).bounds(x + 18, y + 103, panelWidth - 36, 27).build());

        this.addRenderableWidget(Button.builder(autoTotemLabel(), button -> {
            T7LXClient.toggleAutoTotem();
            button.setMessage(autoTotemLabel());
        }).bounds(x + 18, y + 136, panelWidth - 36, 27).build());

        this.addRenderableWidget(Button.builder(speedMineLabel(), button -> {
            T7LXClient.toggleSpeedMine();
            button.setMessage(speedMineLabel());
        }).bounds(x + 18, y + 169, panelWidth - 36, 27).build());

        this.addRenderableWidget(Button.builder(builderModeLabel(), button -> {
            LitematicaBuilder.toggleMode();
            button.setMessage(builderModeLabel());
        }).bounds(x + 18, y + 211, panelWidth - 36, 27).build());

        this.addRenderableWidget(Button.builder(builderRunLabel(), button -> {
            LitematicaBuilder.pauseOrResume();
            button.setMessage(builderRunLabel());
        }).bounds(x + 18, y + 244, panelWidth - 36, 27).build());

        this.addRenderableWidget(Button.builder(Component.literal("Litematica Builder: STOP"), button -> {
            LitematicaBuilder.stop();
            this.clearWidgets();
            this.init();
        }).bounds(x + 18, y + 277, panelWidth - 36, 27).build());

        if (showCpsSlider) {
            this.addRenderableWidget(new AbstractSliderButton(
                    x + 18, y + 330, panelWidth - 36, 20,
                    Component.literal("Crystal CPS: " + T7LXClient.getCrystalCps()),
                    (T7LXClient.getCrystalCps() - 1) / 39.0) {
                @Override
                protected void updateMessage() {
                    int cps = 1 + (int) Math.round(this.value * 39.0);
                    this.setMessage(Component.literal("Crystal CPS: " + cps));
                }

                @Override
                protected void applyValue() {
                    T7LXClient.setCrystalCps(1 + (int) Math.round(this.value * 39.0));
                }
            });
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        int x = (this.width - 340) / 2;
        int y = (this.height - PANEL_HEIGHT) / 2;
        boolean onCrystalOption = mouseX >= x + 18 && mouseX <= x + 322
                && mouseY >= y + 103 && mouseY <= y + 130;
        if (button == 1 && onCrystalOption) {
            showCpsSlider = !showCpsSlider;
            this.clearWidgets();
            this.init();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private static Component speedMineLabel() {
        return Component.literal("T7LXs Speed mine: " + (T7LXClient.isSpeedMineEnabled() ? "ON" : "OFF") + " (8x)");
    }

    private static Component freecamLabel() {
        return Component.literal("Freecam: " + (T7LXClient.isFreecamEnabled() ? "ON" : "OFF"));
    }

    private static Component autoTotemLabel() {
        return Component.literal("Auto Totem: " + (T7LXClient.isAutoTotemEnabled() ? "ON" : "OFF"));
    }

    private static Component crystalMacroLabel() {
        return Component.literal("Crystal Macro: " + (T7LXClient.isCrystalMacroEnabled() ? "ON" : "OFF")
                + "  (Right-click for CPS)");
    }

    private static Component builderModeLabel() {
        return Component.literal("Litematica Mode: " + LitematicaBuilder.getMode());
    }

    private static Component builderRunLabel() {
        String label = LitematicaBuilder.isRunning()
                ? (LitematicaBuilder.isPaused() ? "RESUME" : "PAUSE")
                : "START";
        return Component.literal("Litematica Builder: " + label);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int panelWidth = 340;
        int x = (this.width - panelWidth) / 2;
        int y = (this.height - PANEL_HEIGHT) / 2;

        graphics.fill(x, y, x + panelWidth, y + PANEL_HEIGHT, PANEL);
        graphics.fill(x, y, x + panelWidth, y + 3, BLUE);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(this.font, "T7LX", this.width / 2 - this.font.width("T7LX") / 2,
                y + 14, 0xFFFFFFFF, true);
        graphics.text(this.font, "CLIENT MOD MENU", this.width / 2 - this.font.width("CLIENT MOD MENU") / 2,
                y + 33, 0xFF8AA9C8, true);
        graphics.text(this.font, "Litematica: " + LitematicaBuilder.getStatus(),
                x + 18, y + 312, 0xFFB7D5F2, true);
        graphics.text(this.font, "Blocks placed: " + LitematicaBuilder.getPlacedCount(),
                x + 18, y + 326, 0xFF8AA9C8, true);
        if (showCpsSlider) {
            graphics.text(this.font, "Adjust crystal placements per second (1-40)",
                    x + 18, y + 354, 0xFF8AA9C8, true);
        }
        graphics.text(this.font, "RIGHT SHIFT  -  CLOSE MENU",
                this.width / 2 - this.font.width("RIGHT SHIFT  -  CLOSE MENU") / 2,
                y + 385, 0xFF8AA9C8, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
