package studio.creatorhq;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.*;

/**
 * Gear Editor. Client-only: it builds a vanilla "/item replace" command that carries the
 * item's existing components plus the new trim / enchantments, so unrelated data is kept.
 * Needs permission to run /item on the server.
 */
public final class GearScreen extends HqScreen {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private static final String[] SLOT_LABEL = {"Helmet", "Chestplate", "Leggings", "Boots", "Main Hand", "Offhand"};
    private static final String[] SLOT_CMD = {"armor.head", "armor.chest", "armor.legs", "armor.feet", "weapon.mainhand", "weapon.offhand"};
    private static final String[] FALLBACK_PATTERNS = {"sentry", "dune", "coast", "wild", "ward", "eye", "vex", "tide", "snout",
            "rib", "spire", "wayfinder", "shaper", "silence", "raiser", "host", "flow", "bolt"};
    private static final String[] FALLBACK_MATERIALS = {"quartz", "iron", "netherite", "redstone", "copper", "gold",
            "emerald", "diamond", "lapis", "amethyst", "resin"};

    private record Edit(int slot, String beforeCmd, String afterCmd, ItemStack before, ItemStack after) {}
    private record Built(String cmd, ItemStack result) {}

    /** History survives closing the screen. One Done = one entry. */
    private static final Deque<List<Edit>> UNDO = new ArrayDeque<>();
    private static final Deque<List<Edit>> REDO = new ArrayDeque<>();

    private final int focus;                 // 0 = trims, 1 = enchantments
    private final boolean[] sel = new boolean[6];
    private List<String> patterns, materials;   // full ids, index 0 = "(none)"
    private int pat = 0, mat = 0, enchIdx = 0, level = 1, enchPage = 0;
    private String levelText = "1";
    private final LinkedHashMap<String, Integer> staged = new LinkedHashMap<>();

    public GearScreen(Screen parent, int focus) {
        super("Gear Editor", parent);
        this.focus = focus;
    }

    private ItemStack stack(int i) { return minecraft.player.getItemBySlot(SLOTS[i]).copy(); }
    private boolean isArmor(int i) { return i < 4; }
    private static String shortId(String id) { return id.replace("minecraft:", ""); }

    /** Trim patterns/materials come from the running game's registries. */
    private void loadTrims() {
        if (patterns != null) return;
        var acc = minecraft.level.registryAccess();
        patterns = new ArrayList<>();
        materials = new ArrayList<>();
        acc.lookupOrThrow(Registries.TRIM_PATTERN).listElements().map(h -> h.getRegisteredName()).sorted().forEach(patterns::add);
        acc.lookupOrThrow(Registries.TRIM_MATERIAL).listElements().map(h -> h.getRegisteredName()).sorted().forEach(materials::add);
        if (patterns.isEmpty()) for (String s : FALLBACK_PATTERNS) patterns.add("minecraft:" + s);
        if (materials.isEmpty()) for (String s : FALLBACK_MATERIALS) materials.add("minecraft:" + s);
        patterns.add(0, "(none)");
        materials.add(0, "(none)");
    }

    /** Enchantments valid for EVERY selected item (registry rules + conflicts with existing ones). */
    private List<Holder.Reference<Enchantment>> enchantOptions() {
        List<ItemStack> chosen = new ArrayList<>();
        for (int i = 0; i < 6; i++) if (sel[i] && !stack(i).isEmpty()) chosen.add(stack(i));
        List<Holder.Reference<Enchantment>> out = new ArrayList<>();
        if (chosen.isEmpty()) return out;
        minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(h -> {
            for (ItemStack s : chosen) {
                if (!h.value().isSupportedItem(s)) return;
                for (Holder<Enchantment> existing : s.getEnchantments().keySet())
                    if (!existing.equals(h) && !Enchantment.areCompatible(existing, h)) return;
            }
            out.add(h);
        });
        out.sort(Comparator.comparing(h -> h.getRegisteredName()));
        return out;
    }

    @Override
    protected void build() {
        loadTrims();
        int lx = 10, lw = 130;
        for (int i = 0; i < 6; i++) {
            int k = i;
            ItemStack s = stack(i);
            String item = s.isEmpty() ? "empty" : s.getHoverName().getString();
            if (item.length() > 14) item = item.substring(0, 13) + "\u2026";
            button((sel[i] ? "[x] " : "[ ] ") + SLOT_LABEL[i] + ": " + item, lx, height - 120 + i * 18, lw + 40, 17,
                    () -> { if (!s.isEmpty()) sel[k] = !sel[k]; enchIdx = 0; rebuildWidgets(); });
        }
        int rx = lx + lw + 60, rw = width - rx - 10, cw = rw / 4 - 4;

        // columns 1 + 2: trim pattern / material (registry lists)
        button("Trim Pattern", rx, 34, cw, 18, () -> {});
        button(shortId(patterns.get(pat)), rx, 54, cw, 20, () -> { pat = (pat + 1) % patterns.size(); rebuildWidgets(); });
        button("\u25C0", rx, 76, cw / 2 - 1, 16, () -> { pat = (pat + patterns.size() - 1) % patterns.size(); rebuildWidgets(); });
        button("\u25B6", rx + cw / 2 + 1, 76, cw / 2 - 1, 16, () -> { pat = (pat + 1) % patterns.size(); rebuildWidgets(); });
        int mx2 = rx + cw + 4;
        button("Trim Material", mx2, 34, cw, 18, () -> {});
        button(shortId(materials.get(mat)), mx2, 54, cw, 20, () -> { mat = (mat + 1) % materials.size(); rebuildWidgets(); });
        button("\u25C0", mx2, 76, cw / 2 - 1, 16, () -> { mat = (mat + materials.size() - 1) % materials.size(); rebuildWidgets(); });
        button("\u25B6", mx2 + cw / 2 + 1, 76, cw / 2 - 1, 16, () -> { mat = (mat + 1) % materials.size(); rebuildWidgets(); });

        // column 3: enchantments
        int ex = mx2 + cw + 4;
        button("Enchantment", ex, 34, cw, 18, () -> {});
        List<Holder.Reference<Enchantment>> opts = enchantOptions();
        int perPage = Math.max(1, (height - 130 - 100) / 18);
        int maxPage = Math.max(0, (opts.size() - 1) / perPage);
        if (enchPage > maxPage) enchPage = maxPage;
        for (int i = 0; i < perPage; i++) {
            int idx = enchPage * perPage + i;
            if (idx >= opts.size()) break;
            int k = idx;
            String n = shortId(opts.get(idx).getRegisteredName());
            button((enchIdx == k + 1 ? "> " : "") + (staged.containsKey(opts.get(idx).getRegisteredName()) ? "* " : "") + n,
                    ex, 54 + i * 18, cw, 17, () -> { enchIdx = (enchIdx == k + 1) ? 0 : k + 1; rebuildWidgets(); });
        }
        button("\u25B2", ex, height - 130, cw / 2 - 1, 16, () -> { if (enchPage > 0) { enchPage--; rebuildWidgets(); } });
        button("\u25BC", ex + cw / 2 + 1, height - 130, cw / 2 - 1, 16, () -> { enchPage++; rebuildWidgets(); });

        // column 4: level 1..250 (typed or +/-)
        int lvx = ex + cw + 4;
        button("Level (1-250)", lvx, 34, cw, 18, () -> {});
        EditBox lv = new EditBox(font, lvx, 54, cw, 18, Component.literal("Level"));
        lv.setValue(levelText);
        lv.setFilter(s -> s.matches("\\d{0,3}"));
        lv.setResponder(s -> { levelText = s; try { level = Integer.parseInt(s); } catch (Exception e) { level = 0; } });
        addRenderableWidget(lv);
        button("+1", lvx, 76, cw / 2 - 1, 16, () -> { level = Math.min(250, Math.max(1, level) + 1); levelText = "" + level; rebuildWidgets(); });
        button("-1", lvx + cw / 2 + 1, 76, cw / 2 - 1, 16, () -> { level = Math.max(1, level - 1); levelText = "" + level; rebuildWidgets(); });
        button("250", lvx, 94, cw / 2 - 1, 16, () -> { level = 250; levelText = "250"; rebuildWidgets(); });
        button("10", lvx + cw / 2 + 1, 94, cw / 2 - 1, 16, () -> { level = 10; levelText = "10"; rebuildWidgets(); });
        button("Stage Enchant", lvx, 114, cw, 18, () -> { stageCurrent(); rebuildWidgets(); });
        button("Clear Staged", lvx, 134, cw, 18, () -> { staged.clear(); enchIdx = 0; rebuildWidgets(); });

        // bottom: Full Armor + the four actions
        int by = height - 28, bx = rx;
        button("Full Armor", bx, by - 24, 90, 20, () -> {
            for (int i = 0; i < 4; i++) sel[i] = !stack(i).isEmpty();
            rebuildWidgets();
        });
        button("Done", bx, by, 80, 22, this::done);
        button("Undo", bx + 84, by, 80, 22, () -> replay(UNDO, REDO, true));
        button("Redo", bx + 168, by, 80, 22, () -> replay(REDO, UNDO, false));
        button("Clear Selection", bx + 252, by, 110, 22, () -> { Arrays.fill(sel, false); enchIdx = 0; staged.clear(); rebuildWidgets(); });
    }

    /** Adds the highlighted enchantment + level to the staged set (checks level and conflicts between staged ones). */
    private boolean stageCurrent() {
        if (enchIdx == 0) return true;
        if (level < 1 || level > 250) { Hq.msg("Level must be 1-250."); return false; }
        List<Holder.Reference<Enchantment>> opts = enchantOptions();
        if (enchIdx - 1 >= opts.size()) { Hq.msg("Enchantment not valid for the selected items."); return false; }
        Holder.Reference<Enchantment> h = opts.get(enchIdx - 1);
        for (String other : staged.keySet()) {
            if (other.equals(h.getRegisteredName())) continue;
            for (Holder.Reference<Enchantment> o : opts)
                if (o.getRegisteredName().equals(other) && !Enchantment.areCompatible(o, h)) {
                    Hq.msg(shortId(h.getRegisteredName()) + " conflicts with staged " + shortId(other) + ".");
                    return false;
                }
        }
        staged.put(h.getRegisteredName(), level);
        enchIdx = 0;
        return true;
    }

    /** Builds the replace command and the resulting stack. */
    private Built build(int slot, ItemStack s, String pattern, String material, Map<String, Integer> ench) {
        var ops = minecraft.player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var enc = ItemStack.CODEC.encodeStart(ops, s).result();
        if (enc.isEmpty() || !(enc.get() instanceof CompoundTag ct)) return null;
        CompoundTag comps = ct.getCompoundOrEmpty("components").copy();
        if (pattern != null && material != null) {
            CompoundTag t = new CompoundTag();
            t.putString("pattern", pattern);
            t.putString("material", material);
            comps.put("minecraft:trim", t);
        }
        if (ench != null && !ench.isEmpty()) {
            CompoundTag en = comps.getCompoundOrEmpty("minecraft:enchantments").copy();
            for (var e : ench.entrySet()) en.putInt(e.getKey(), e.getValue());
            comps.put("minecraft:enchantments", en);
        }
        ct.put("components", comps);
        var parsed = ItemStack.CODEC.parse(ops, (Tag) ct).result();
        if (parsed.isEmpty()) return null;
        StringJoiner sj = new StringJoiner(",");
        for (String k : comps.keySet()) sj.add(k + "=" + comps.get(k).toString());
        int count = ct.getIntOr("count", 1);
        String cmd = "item replace entity @s " + SLOT_CMD[slot] + " with " + ct.getStringOr("id", "minecraft:air")
                + (comps.isEmpty() ? "" : "[" + sj + "]") + (count > 1 ? " " + count : "");
        return new Built(cmd, parsed.get());
    }

    private void done() {
        String p = null, m = null;
        if (pat > 0 && mat > 0) { p = patterns.get(pat); m = materials.get(mat); }
        else if (pat > 0 || mat > 0) { Hq.msg("Choose both a trim pattern and a material."); return; }
        if (!stageCurrent()) return;
        if (p == null && staged.isEmpty()) { Hq.msg("Nothing staged: pick a trim and/or enchantments."); return; }

        // every staged enchantment must still be valid for EVERY selected item
        List<Holder.Reference<Enchantment>> opts = enchantOptions();
        Set<String> valid = new HashSet<>();
        for (var h : opts) valid.add(h.getRegisteredName());
        for (String n : staged.keySet())
            if (!valid.contains(n)) { Hq.msg(shortId(n) + " is not valid for all selected items. Nothing changed."); return; }

        // validate the whole batch before sending anything (no partial edits)
        List<Edit> edits = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            if (!sel[i]) continue;
            ItemStack s = stack(i);
            if (s.isEmpty()) { Hq.msg(SLOT_LABEL[i] + " is empty. Nothing changed."); return; }
            Built before = build(i, s, null, null, null);
            Built after = build(i, s, isArmor(i) ? p : null, isArmor(i) ? m : null, staged);
            if (before == null || after == null) { Hq.msg("Could not build change for " + SLOT_LABEL[i] + ". Nothing changed."); return; }
            edits.add(new Edit(i, before.cmd(), after.cmd(), s, after.result()));
        }
        if (edits.isEmpty()) { Hq.msg("Select at least one item."); return; }
        for (Edit e : edits) Hq.cmd(e.afterCmd());
        UNDO.push(edits);
        REDO.clear();
        staged.clear(); pat = 0; mat = 0; enchIdx = 0;
        Hq.msg("Applied to " + edits.size() + " item(s).");
        rebuildWidgets();
    }

    private void replay(Deque<List<Edit>> from, Deque<List<Edit>> to, boolean undo) {
        if (from.isEmpty()) { Hq.msg("Nothing to " + (undo ? "undo" : "redo") + "."); return; }
        List<Edit> edits = from.peek();
        for (Edit e : edits) {   // reject the whole batch if any item changed since
            ItemStack expect = undo ? e.after() : e.before();
            if (!ItemStack.matches(stack(e.slot()), expect)) {
                Hq.msg((undo ? "Undo" : "Redo") + " rejected: " + SLOT_LABEL[e.slot()] + " changed since.");
                return;
            }
        }
        for (Edit e : edits) Hq.cmd(undo ? e.beforeCmd() : e.afterCmd());
        to.push(from.pop());
        Hq.msg((undo ? "Undid " : "Redid ") + edits.size() + " item(s).");
    }

    @Override
    public void onClose() {
        if (!staged.isEmpty() || pat > 0 || mat > 0 || enchIdx > 0) Hq.msg("Unapplied edits were discarded.");
        super.onClose();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        enchPage = Math.max(0, enchPage - (int) Math.signum(sy));
        rebuildWidgets();
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        super.render(g, mx, my, dt);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, 10, 34, 150, height - 130, 40, 0.0625F, mx, my, minecraft.player);
        int selCount = 0;
        for (boolean b : sel) if (b) selCount++;
        g.drawString(font, "Selected: " + selCount + (focus == 0 ? "   (focus: trims)" : "   (focus: enchantments)"),
                10, height - 132, 0xFFFFD866);
        if (!staged.isEmpty()) {
            StringJoiner sj = new StringJoiner(", ");
            staged.forEach((k, v) -> sj.add(shortId(k) + " " + v + (v > 5 ? "!" : "")));
            g.drawString(font, "Staged: " + sj, 180, height - 62, 0xFF88FF88);
        }
        if (enchantOptions().isEmpty())
            g.drawString(font, "Select an item to list enchantments", width / 2, 118, 0xFFAAAAAA);
    }
}
