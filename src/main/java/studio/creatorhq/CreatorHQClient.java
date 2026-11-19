package studio.creatorhq;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

public final class CreatorHQClient implements ClientModInitializer {
    private static KeyMapping homeKey, addKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("creatorhq", "recording"));
        // Default K. Controls menu se kabhi bhi rebind ho sakta hai.
        homeKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.creatorhq.home", GLFW.GLFW_KEY_K, cat));
        addKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.creatorhq.add", GLFW.GLFW_KEY_L, cat));

        ScreenQueue.init();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> Hq.load());

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null || mc.level == null) return;
            while (homeKey.consumeClick()) {
                if (mc.screen == null && requireOp(mc)) mc.setScreen(new HubScreen());
            }
            while (addKey.consumeClick()) {
                if (mc.screen == null && requireOp(mc)) addCrosshairPlayer(mc);
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("creatorhq")
                .executes(ctx -> {
                    if (!requireOp(Minecraft.getInstance())) return 0;
                    ScreenQueue.open(HubScreen::new);
                    return 1;
                })
                .then(ClientCommandManager.literal("tp")
                    .then(ClientCommandManager.argument("group", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Hq.data.teams.keySet(), b))
                        .executes(c -> {
                            if (!requireOp(Minecraft.getInstance())) return 0;
                            Hq.bringGroup(StringArgumentType.getString(c, "group").toLowerCase(), null);
                            return 1;
                        })
                        .then(ClientCommandManager.argument("player", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(Hq.online().stream().map(Hq::name).toList(), b))
                            .executes(c -> {
                                if (!requireOp(Minecraft.getInstance())) return 0;
                                Hq.bringGroup(StringArgumentType.getString(c, "group").toLowerCase(),
                                        StringArgumentType.getString(c, "player"));
                                return 1;
                            }))))));
    }

    /**
     * Operator check. Server sirf wahi commands client ko bhejta hai jo player use kar sakta hai,
     * isliye agar /gamemode ya /tp tree me hai to player OP (level 2+) hai.
     * Server me koi mod/plugin chahiye nahi.
     */
    public static boolean isOperator(Minecraft mc) {
        if (mc.getConnection() == null) return false;
        CommandNode<?> root = mc.getConnection().getCommands().getRoot();
        return root.getChild("gamemode") != null || root.getChild("tp") != null;
    }

    public static boolean requireOp(Minecraft mc) {
        if (isOperator(mc)) return true;
        Hq.msg("Operator (OP) required to use CreatorHQ.");
        return false;
    }

    private static void addCrosshairPlayer(Minecraft mc) {
        if (!(mc.crosshairPickEntity instanceof Player target) || target == mc.player) {
            Hq.msg("Nobody under the crosshair. Nothing added.");
            return;
        }
        String u = target.getUUID().toString();
        String name = target.getGameProfile().name();
        String existing = Hq.teamOf(u);
        if (existing != null) {
            Hq.msg(name + " is already in " + Hq.teamName(existing) + ". Nothing changed.");
            return;
        }
        Hq.data.teams.computeIfAbsent(Hq.data.active, k -> new java.util.ArrayList<>()).add(u);
        Hq.data.names.put(u, name);
        Hq.save();
        Hq.msg("Added " + name + " to " + Hq.teamName(Hq.data.active));
    }
}
