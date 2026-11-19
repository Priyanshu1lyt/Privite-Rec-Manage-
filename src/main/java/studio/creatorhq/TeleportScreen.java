package studio.creatorhq;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.*;

public final class TeleportScreen extends HqScreen {
    private String view = "all";          // "all" or team id
    private boolean edit = false;         // Edit Members mode
    private String dest = null;           // null = Me, else player name
    private String query = "";
    private int page = 0;
    private final Set<String> sel = new HashSet<>();
    private int rowsPerPage = 8;

    public TeleportScreen(Screen parent) {
        super("Teleport & Groups", parent);
        view = Hq.data.active;
        selectOnlineMembers();
    }

    private void selectOnlineMembers() {
        sel.clear();
        if (!view.equals("all"))
            for (String u : Hq.data.teams.getOrDefault(view, List.of())) if (Hq.findOnline(u) != null) sel.add(u);
    }

    private List<PlayerInfo> visible() {
        List<PlayerInfo> out = new ArrayList<>();
        for (PlayerInfo p : Hq.online()) {
            String u = Hq.uuid(p), t = Hq.teamOf(u);
            if (!query.isBlank() && !Hq.name(p).toLowerCase().contains(query.toLowerCase())) continue;
            if (!view.equals("all")) {
                if (edit) { if (t != null && !t.equals(view)) continue; }
                else if (!view.equals(t)) continue;
            }
            out.add(p);
        }
        return out;
    }

    @Override
    protected void build() {
        int lx = 10, ly = 34, lw = Math.min(160, width / 3 - 10);
        int rx = lx + lw + 10, rw = width - rx - 10;

        // left panel buttons: destination
        button("Me", lx + 4, ly + 70, lw - 8, 18, () -> { dest = null; rebuildWidgets(); });
        button("\u25C0 Prev player", lx + 4, ly + 92, lw / 2 - 6, 18, () -> cycleDest(-1));
        button("Next \u25B6", lx + lw / 2 + 2, ly + 92, lw / 2 - 6, 18, () -> cycleDest(1));

        // top actions
        int aw = rw / 4 - 3;
        button("Bring Selected", rx, 6, aw, 20, this::bring);
        button("Select All (" + visible().size() + ")", rx + aw + 4, 6, aw, 20, () -> {
            for (PlayerInfo p : visible()) sel.add(Hq.uuid(p));
            rebuildWidgets();
        });
        button("Clear Selection", rx + 2 * (aw + 4), 6, aw, 20, () -> { sel.clear(); rebuildWidgets(); });
        button("Set Active Group", rx + 3 * (aw + 4), 6, aw, 20, () -> {
            if (view.equals("all")) { Hq.msg("Open a team tab first."); return; }
            Hq.data.active = view; Hq.save(); Hq.msg("Active group: " + Hq.teamName(view)); rebuildWidgets();
        });

        // search
        EditBox search = new EditBox(font, rx, 30, rw, 18, Component.literal("Search players"));
        search.setValue(query);
        search.setResponder(s -> { query = s; page = 0; rebuildWidgets(); });
        addRenderableWidget(search);
        setInitialFocus(search);

        // tabs
        int tx = rx;
        for (String id : Hq.data.teams.keySet()) {
            String label = (view.equals(id) ? "> " : "") + Hq.teamName(id) + (Hq.data.active.equals(id) ? " *" : "");
            button(label, tx, 52, 70, 20, () -> { view = id; edit = false; page = 0; selectOnlineMembers(); rebuildWidgets(); });
            tx += 72;
        }
        button((view.equals("all") ? "> " : "") + "All Players", tx, 52, 80, 20, () -> { view = "all"; edit = false; page = 0; sel.clear(); rebuildWidgets(); });
        tx += 82;
        button("+", tx, 52, 20, 20, () -> {
            String id = Hq.nextTeamId();
            if (id == null) return;
            Hq.data.teams.put(id, new ArrayList<>()); Hq.save();
            view = id; edit = false; selectOnlineMembers(); rebuildWidgets();
        });

        // edit toggle + paging
        if (!view.equals("all"))
            button(edit ? "Edit Members: ON" : "Edit Members: OFF", rx, 76, 120, 18, () -> { edit = !edit; page = 0; rebuildWidgets(); });
        if (!view.equals("all") && edit && Hq.data.teams.get(view).isEmpty() && Hq.data.teams.size() > 1)
            button("Delete Team", rx + 124, 76, 80, 18, () -> {
                Hq.data.teams.remove(view);
                if (Hq.data.active.equals(view)) Hq.data.active = Hq.data.teams.keySet().iterator().next();
                Hq.save(); view = "all"; edit = false; sel.clear(); rebuildWidgets();
            });
        button("\u25B2", rx + rw - 44, 76, 20, 18, () -> { if (page > 0) { page--; rebuildWidgets(); } });
        button("\u25BC", rx + rw - 22, 76, 20, 18, () -> { page++; rebuildWidgets(); });

        // roster
        List<PlayerInfo> list = visible();
        int top = 98;
        rowsPerPage = Math.max(1, (height - top - 8) / 20);
        int maxPage = Math.max(0, (list.size() - 1) / rowsPerPage);
        if (page > maxPage) page = maxPage;
        for (int i = 0; i < rowsPerPage; i++) {
            int idx = page * rowsPerPage + i;
            if (idx >= list.size()) break;
            PlayerInfo p = list.get(idx);
            String u = Hq.uuid(p), t = Hq.teamOf(u);
            boolean checked = (edit && !view.equals("all")) ? view.equals(t) : sel.contains(u);
            String label = (checked ? "[x] " : "[ ] ") + Hq.name(p) + (t != null ? "   (" + Hq.teamName(t) + ")" : "");
            button(label, rx, top + i * 20, rw, 18, () -> {
                if (edit && !view.equals("all")) {
                    List<String> m = Hq.data.teams.get(view);
                    if (m.contains(u)) m.remove(u);
                    else if (Hq.teamOf(u) == null) { m.add(u); Hq.data.names.put(u, Hq.name(p)); }
                    Hq.save();
                } else {
                    if (!sel.remove(u)) sel.add(u);
                }
                rebuildWidgets();
            });
        }
        // offline members stay in the group: shown greyed out, skipped on teleport
        if (!view.equals("all") && !edit && page == maxPage) {
            int used = list.size() - page * rowsPerPage;
            for (String u : Hq.data.teams.getOrDefault(view, List.of())) {
                if (Hq.findOnline(u) != null || used >= rowsPerPage) continue;
                Button ob = button("(offline) " + Hq.data.names.getOrDefault(u, u.substring(0, 8)), rx, top + used * 20, rw, 18, () -> {});
                ob.active = false;
                used++;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        page = Math.max(0, page - (int) Math.signum(sy));
        rebuildWidgets();
        return true;
    }

    private void cycleDest(int d) {
        List<PlayerInfo> on = Hq.online();
        on.removeIf(p -> Hq.name(p).equalsIgnoreCase(Hq.myName()));
        if (on.isEmpty()) { dest = null; rebuildWidgets(); return; }
        int i = -1;
        for (int k = 0; k < on.size(); k++) if (Hq.name(on.get(k)).equalsIgnoreCase(dest == null ? "" : dest)) i = k;
        i = (i + d + on.size()) % on.size();
        dest = Hq.name(on.get(i));
        rebuildWidgets();
    }

    private void bring() {
        if (dest != null && Hq.findOnlineByName(dest) == null) {
            Hq.msg("Cancelled: " + dest + " is not online.");
            return;
        }
        String target = dest == null ? Hq.myName() : dest;
        int moved = 0, offline = 0;
        for (String u : sel) {
            PlayerInfo p = Hq.findOnline(u);
            if (p == null) { offline++; continue; }
            if (Hq.name(p).equalsIgnoreCase(target)) continue;
            Hq.cmd("tp " + Hq.name(p) + " " + target);
            moved++;
        }
        Hq.msg("Moved " + moved + " player(s) to " + (dest == null ? "you" : dest)
                + (offline > 0 ? " (" + offline + " offline skipped)" : ""));
    }

    private int selectedOnline() {
        int n = 0;
        for (String u : sel) if (Hq.findOnline(u) != null) n++;
        return n;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        int lx = 10, ly = 34, lw = Math.min(160, width / 3 - 10);
        g.fill(lx, ly, lx + lw, height - 10, 0x80204AA0);
        super.render(g, mx, my, dt);
        g.drawString(font, "Destination", lx + 6, ly + 6, 0xFFFFFFFF);
        g.drawString(font, dest == null ? "Me" : dest, lx + 6, ly + 18, 0xFFFFD866);
        String from = view.equals("all") ? "All Players" : Hq.teamName(view);
        g.drawWordWrap(font, Component.literal("Bring " + selectedOnline() + " player(s) from " + from
                + " to " + (dest == null ? "Me" : dest)), lx + 6, ly + 32, lw - 12, 0xFFFFFFFF);
        String dim = "?";
        if (dest == null) dim = minecraft.level.dimension().identifier().toString();
        else {
            PlayerInfo di = Hq.findOnlineByName(dest);
            Player pl = di == null ? null : minecraft.level.getPlayerByUUID(di.getProfile().id());
            dim = pl != null ? minecraft.level.dimension().identifier().toString()
                    : "other dimension / out of range (server resolves it on teleport)";
        }
        g.drawWordWrap(font, Component.literal("Dimension: " + dim), lx + 6, ly + 118, lw - 12, 0xFFAAAAAA);
        g.drawWordWrap(font, Component.literal(edit ? "EDIT MEMBERS: checkboxes change team membership."
                : "Checkboxes choose who teleports."), lx + 6, ly + 150, lw - 12, 0xFFFFFFFF);
    }
}
