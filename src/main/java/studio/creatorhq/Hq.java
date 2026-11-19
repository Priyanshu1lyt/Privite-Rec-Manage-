package studio.creatorhq;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Shared state: saved groups (per server, per PC) and small helpers. */
public final class Hq {
    public static final class Data {
        public Map<String, List<String>> teams = new LinkedHashMap<>();
        public Map<String, String> names = new HashMap<>();
        public String active = "a";
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static Data data = new Data();
    private static Path path;

    private Hq() {}

    public static Minecraft mc() { return Minecraft.getInstance(); }

    public static void load() {
        Minecraft mc = mc();
        String key = "singleplayer";
        if (mc.getCurrentServer() != null) key = mc.getCurrentServer().ip;
        key = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        path = FabricLoader.getInstance().getConfigDir().resolve("creatorhq").resolve(key + ".json");
        data = new Data();
        try {
            if (Files.exists(path)) {
                Data d = GSON.fromJson(Files.readString(path), Data.class);
                if (d != null) data = d;
            }
        } catch (Exception ignored) { }
        if (data.teams == null) data.teams = new LinkedHashMap<>();
        if (data.names == null) data.names = new HashMap<>();
        data.teams.putIfAbsent("a", new ArrayList<>());
        data.teams.putIfAbsent("b", new ArrayList<>());
        if (data.active == null || !data.teams.containsKey(data.active)) data.active = "a";
    }

    public static void save() {
        try {
            if (path == null) return;
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(data));
        } catch (Exception ignored) { }
    }

    public static String teamName(String id) { return "Team " + id.toUpperCase(Locale.ROOT); }

    public static String nextTeamId() {
        for (char c = 'a'; c <= 'z'; c++) if (!data.teams.containsKey(String.valueOf(c))) return String.valueOf(c);
        return null;
    }

    public static String teamOf(String uuid) {
        for (var e : data.teams.entrySet()) if (e.getValue().contains(uuid)) return e.getKey();
        return null;
    }

    public static List<PlayerInfo> online() {
        List<PlayerInfo> l = new ArrayList<>();
        if (mc().getConnection() != null) l.addAll(mc().getConnection().getOnlinePlayers());
        l.sort(Comparator.comparing(Hq::name, String.CASE_INSENSITIVE_ORDER));
        return l;
    }

    public static String name(PlayerInfo p) { return p.getProfile().name(); }
    public static String uuid(PlayerInfo p) { return p.getProfile().id().toString(); }

    public static PlayerInfo findOnline(String uuid) {
        for (PlayerInfo p : online()) if (uuid(p).equals(uuid)) return p;
        return null;
    }

    public static PlayerInfo findOnlineByName(String n) {
        for (PlayerInfo p : online()) if (name(p).equalsIgnoreCase(n)) return p;
        return null;
    }

    public static String myName() { return mc().player.getGameProfile().name(); }
    public static String myUuid() { return mc().player.getUUID().toString(); }

    /** Sends a normal vanilla command as the player (works on any server that lets you use it). */
    public static void cmd(String command) {
        if (mc().player != null) mc().player.connection.sendCommand(command);
    }

    public static void msg(String text) {
        if (mc().player != null) mc().player.displayClientMessage(Component.literal(text), true);
    }

    /** Bring a saved group to a destination player (null = me). */
    public static void bringGroup(String teamId, String destName) {
        List<String> members = data.teams.get(teamId);
        if (members == null) { msg("No such group: " + teamId); return; }
        String dest = destName == null ? myName() : destName;
        if (destName != null && findOnlineByName(destName) == null) {
            msg("Cancelled: " + destName + " is not online.");
            return;
        }
        int moved = 0, offline = 0;
        for (String u : members) {
            PlayerInfo p = findOnline(u);
            if (p == null) { offline++; continue; }
            if (name(p).equalsIgnoreCase(dest)) continue;
            cmd("tp " + name(p) + " " + dest);
            moved++;
        }
        msg("Moved " + moved + " from " + teamName(teamId) + " to " + (destName == null ? "you" : dest)
                + (offline > 0 ? " (" + offline + " offline skipped)" : ""));
    }
}
