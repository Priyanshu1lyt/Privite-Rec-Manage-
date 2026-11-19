package studio.creatorhq;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.*;

public final class GameModeScreen extends HqScreen {
    private String mode = null;           // survival/creative/adventure/spectator
    private String scope = "all";         // "all" or team id
    private String query = "";
    private int page = 0;
    private final Set<String> sel = new HashSet<>();

    public GameModeScreen(Screen parent) { super("Game Mode", parent); }

    private List<PlayerInfo> visible() {
        List<PlayerInfo> out = new ArrayList<>();
        for (PlayerInfo p : Hq.online()) {
            String u = Hq.uuid(p);
            if (!scope.equals("all") && !scope.equals(Hq.teamOf(u))) continue;
            if (!query.isBlank() && !Hq.name(p).toLowerCase().contains(query.toLowerCase())) continue;
            out.add(p);
        }
        return out;
    }

    @Override
    protected void build() {
        int lx = 10, lw = 110;
        String[] modes = {"survival", "creative", "adventure", "spectator"};
        for (int i = 0; i < 4; i++) {
            String m = modes[i];
            button((m.equals(mode) ? "> " : "") + Character.toUpperCase(m.charAt(0)) + m.substring(1), lx, 40 + i * 28, lw, 24,
                    () -> { mode = m; rebuildWidgets(); });
        }
        int rx = lx + lw + 12, rw = width - rx - 10;
        int bw = rw / 4 - 3;
        button("Me", rx, 6, bw, 20, () -> { sel.clear(); sel.add(Hq.myUuid()); rebuildWidgets(); });
        button("Select All (" + visible().size() + ")", rx + bw + 4, 6, bw, 20, () -> {
            for (PlayerInfo p : visible()) sel.add(Hq.uuid(p));
            rebuildWidgets();
        });
        button("Clear Selection", rx + 2 * (bw + 4), 6, bw, 20, () -> { sel.clear(); rebuildWidgets(); });
        button("Scope: " + (scope.equals("all") ? "All players" : Hq.teamName(scope)), rx + 3 * (bw + 4), 6, bw, 20, () -> {
            List<String> ids = new ArrayList<>();
            ids.add("all"); ids.addAll(Hq.data.teams.keySet());
            scope = ids.get((ids.indexOf(scope) + 1) % ids.size());
            page = 0; rebuildWidgets();
        });

        EditBox search = new EditBox(font, rx, 30, rw - 46, 18, Component.literal("Search players"));
        search.setValue(query);
        search.setResponder(s -> { query = s; page = 0; rebuildWidgets(); });
        addRenderableWidget(search);
        setInitialFocus(search);
        button("\u25B2", rx + rw - 44, 30, 20, 18, () -> { if (page > 0) { page--; rebuildWidgets(); } });
        button("\u25BC", rx + rw - 22, 30, 20, 18, () -> { page++; rebuildWidgets(); });

        List<PlayerInfo> list = visible();
        int top = 54, bottom = height - 34;
        int rows = Math.max(1, (bottom - top) / 20);
        int maxPage = Math.max(0, (list.size() - 1) / rows);
        if (page > maxPage) page = maxPage;
        for (int i = 0; i < rows; i++) {
            int idx = page * rows + i;
            if (idx >= list.size()) break;
            PlayerInfo p = list.get(idx);
            String u = Hq.uuid(p), t = Hq.teamOf(u);
            String cur = p.getGameMode() == null ? "?" : p.getGameMode().getName();
            button((sel.contains(u) ? "[x] " : "[ ] ") + Hq.name(p) + "  - " + cur + (t != null ? "  (" + Hq.teamName(t) + ")" : ""),
                    rx, top + i * 20, rw, 18, () -> { if (!sel.remove(u)) sel.add(u); rebuildWidgets(); });
        }
        button("Apply", width - 90, height - 28, 80, 22, this::apply);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        page = Math.max(0, page - (int) Math.signum(sy));
        rebuildWidgets();
        return true;
    }

    private void apply() {
        if (mode == null) { Hq.msg("Choose a game mode first."); return; }
        int changed = 0, same = 0, skipped = 0;
        for (String u : sel) {
            PlayerInfo p = Hq.findOnline(u);
            if (p == null) { skipped++; continue; }
            if (p.getGameMode() != null && p.getGameMode().getName().equals(mode)) { same++; continue; }
            Hq.cmd("gamemode " + mode + " " + Hq.name(p));
            changed++;
        }
        Hq.msg("Game mode " + mode + ": changed " + changed + ", already " + same + ", skipped " + skipped);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        super.render(g, mx, my, dt);
        g.drawString(font, "Mode: " + (mode == null ? "none chosen" : mode) + "   Selected: " + sel.size()
                + "   Scope: " + (scope.equals("all") ? "All players" : Hq.teamName(scope)), 10, height - 22, 0xFFFFFFFF);
    }
}
