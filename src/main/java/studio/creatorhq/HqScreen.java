package studio.creatorhq;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Base screen: every sub-screen gets a Back arrow in the top-left corner. */
public abstract class HqScreen extends Screen {
    protected final Screen parent;

    protected HqScreen(String title, Screen parent) {
        super(Component.literal(title));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (parent != null) {
            addRenderableWidget(Button.builder(Component.literal("\u2190 Back"), b -> onClose())
                    .bounds(6, 6, 50, 20).build());
        }
        build();
    }

    protected abstract void build();

    protected Button button(String label, int x, int y, int w, int h, Runnable r) {
        return addRenderableWidget(Button.builder(Component.literal(label), b -> r.run()).bounds(x, y, w, h).build());
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
