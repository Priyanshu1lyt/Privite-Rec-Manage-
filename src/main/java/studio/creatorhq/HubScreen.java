package studio.creatorhq;

import net.minecraft.client.gui.GuiGraphics;

public final class HubScreen extends HqScreen {
    public HubScreen() { super("CreatorHQ Recording Home", null); }

    @Override
    protected void build() {
        int w = 160, h = 24, x = width / 2 - w / 2, y = height / 2 - 40;
        button("Teleport", x, y, w, h, () -> minecraft.setScreen(new TeleportScreen(this)));
        button("Game Mode", x, y + 30, w, h, () -> minecraft.setScreen(new GameModeScreen(this)));
        button("Armor Trims", x, y + 60, w, h, () -> minecraft.setScreen(new GearScreen(this, 0)));
        button("Enchantments", x, y + 90, w, h, () -> minecraft.setScreen(new GearScreen(this, 1)));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        super.render(g, mx, my, dt);
        g.drawCenteredString(font, title, width / 2, height / 2 - 70, 0xFFFFFFFF);
        g.drawCenteredString(font, "Active group (L adds here): " + Hq.teamName(Hq.data.active),
                width / 2, height / 2 - 56, 0xFFFFD866);
    }
}
