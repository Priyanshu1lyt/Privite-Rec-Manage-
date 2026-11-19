package studio.creatorhq;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Supplier;

/**
 * Opens a screen on a later tick. Needed because the chat screen closes itself
 * (setScreen(null)) right AFTER running a command, which would instantly close
 * a screen opened from inside the command.
 */
public final class ScreenQueue {
    private static Supplier<Screen> pending;

    private ScreenQueue() {}

    public static void open(Supplier<Screen> s) { pending = s; }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (pending != null && !(mc.screen instanceof ChatScreen)) {
                Supplier<Screen> s = pending;
                pending = null;
                mc.setScreen(s.get());
            }
        });
    }
}
