package jp.minecraftcooks.paper.command;

import jp.minecraftcooks.MinecraftCooksPlugin;
import jp.minecraftcooks.core.EquipType;
import jp.minecraftcooks.core.kitchen.EquipmentDef;
import jp.minecraftcooks.core.kitchen.KitchenLayout;
import jp.minecraftcooks.core.team.TeamRoster;
import jp.minecraftcooks.core.team.TeamSplitter;
import jp.minecraftcooks.paper.Messages;
import jp.minecraftcooks.paper.kitchen.Kitchen;
import jp.minecraftcooks.paper.kitchen.KitchenRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

import static jp.minecraftcooks.paper.Messages.text;

/** /cg の全サブコマンド。 */
public final class CgCommand implements TabExecutor {
    private final MinecraftCooksPlugin plugin;

    public CgCommand(MinecraftCooksPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private void usage(CommandSender s, String u) {
        msg().send(s, "general.usage", text("usage", u));
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        // 観戦だけは、運営でなくても使える(観戦者・試合を終えた人のため)
        if (a.length >= 1 && a[0].equalsIgnoreCase("spectate")) {
            spectate(s, Arrays.copyOfRange(a, 1, a.length));
            return true;
        }
        if (!s.hasPermission("minecraft-cooks.admin")) {
            msg().send(s, "general.no-permission");
            return true;
        }
        if (a.length == 0) {
            usage(s, "/cg <match|team|exclude|score|kitchen|wand|practice|spectate|settings|reload> ...");
            return true;
        }
        String[] rest = Arrays.copyOfRange(a, 1, a.length);
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "kitchen" -> kitchen(s, rest);
            case "wand" -> wand(s, rest);
            case "practice" -> practice(s, rest);
            case "match" -> match(s, rest);
            case "team" -> team(s, rest);
            case "exclude" -> exclude(s, rest);
            case "score" -> score(s, rest);
            case "spectate" -> spectate(s, rest);
            case "reload" -> reload(s);
            case "settings" -> {
                if (s instanceof Player p) plugin.settingsGui().open(p);
                else msg().send(s, "general.players-only");
            }
            default -> usage(s, "/cg <match|team|exclude|score|kitchen|wand|practice|spectate|settings|reload> ...");
        }
        return true;
    }

    // ---------- kitchen ----------

    private void kitchen(CommandSender s, String[] a) {
        String u = "/cg kitchen <create|remove|list|setspawn|clone|reset>";
        if (a.length == 0) {
            usage(s, u);
            return;
        }
        KitchenRegistry reg = plugin.kitchens();
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (!(s instanceof Player p)) {
                    msg().send(s, "general.players-only");
                    return;
                }
                if (a.length < 3) {
                    usage(s, "/cg kitchen create <id> <表示名>");
                    return;
                }
                if (reg.get(a[1]).isPresent()) {
                    msg().send(s, "kitchen.exists", text("id", a[1]));
                    return;
                }
                reg.create(a[1], String.join(" ", Arrays.copyOfRange(a, 2, a.length)), p.getWorld());
                msg().send(s, "kitchen.created", text("id", a[1]));
            }
            case "remove" -> {
                if (a.length < 2) {
                    usage(s, "/cg kitchen remove <id>");
                    return;
                }
                Optional<Kitchen> k = findKitchen(s, a[1]);
                if (k.isEmpty() || busy(s, k.get())) return;
                plugin.entities().removeAll(k.get());
                reg.remove(k.get().id());
                msg().send(s, "kitchen.removed", text("id", a[1]));
            }
            case "list" -> {
                msg().send(s, "kitchen.list-header", text("count", reg.all().size()));
                for (Kitchen k : reg.all()) {
                    String area = k.hasArea() ? k.corner1() + " 〜 " + k.corner2() : "未登録";
                    String spawn = k.spawnRaw() == null ? "未登録" : String.format("%.1f,%.1f,%.1f",
                            k.spawnRaw().getX(), k.spawnRaw().getY(), k.spawnRaw().getZ());
                    msg().send(s, "kitchen.list-line", text("id", k.id()), text("name", k.displayName()),
                            text("equip", k.equipment().size()), text("area", area), text("spawn", spawn));
                }
            }
            case "setspawn" -> {
                if (!(s instanceof Player p)) {
                    msg().send(s, "general.players-only");
                    return;
                }
                if (a.length < 2) {
                    usage(s, "/cg kitchen setspawn <id>");
                    return;
                }
                Optional<Kitchen> k = findKitchen(s, a[1]);
                if (k.isEmpty()) return;
                if (k.get().worldName() != null && !k.get().worldName().equals(p.getWorld().getName())) {
                    msg().send(s, "wand.other-world");
                    return;
                }
                k.get().setSpawn(p.getLocation());
                reg.save();
                msg().send(s, "kitchen.spawn-set", text("id", k.get().id()));
            }
            case "clone" -> {
                if (a.length < 3) {
                    usage(s, "/cg kitchen clone <元> <先>");
                    return;
                }
                Optional<Kitchen> from = findKitchen(s, a[1]);
                Optional<Kitchen> to = findKitchen(s, a[2]);
                if (from.isEmpty() || to.isEmpty() || busy(s, to.get())) return;
                clone(s, from.get(), to.get());
            }
            case "reset" -> {
                if (a.length < 2) {
                    usage(s, "/cg kitchen reset <id>");
                    return;
                }
                Optional<Kitchen> k = findKitchen(s, a[1]);
                if (k.isEmpty()) return;
                plugin.resetKitchen(k.get());
                msg().send(s, "kitchen.reset", text("id", k.get().id()));
            }
            default -> usage(s, u);
        }
    }

    private void clone(CommandSender s, Kitchen from, Kitchen to) {
        if (!from.hasArea() || !to.hasArea()) {
            msg().send(s, "kitchen.clone-no-area");
            return;
        }
        if (!KitchenLayout.sameSize(from.corner1(), from.corner2(), to.corner1(), to.corner2())) {
            msg().send(s, "kitchen.clone-size-mismatch");
        }
        List<EquipmentDef> shifted = KitchenLayout.cloneShifted(from.equipment(),
                from.corner1(), from.corner2(), to.corner1(), to.corner2());
        KitchenRegistry reg = plugin.kitchens();
        plugin.entities().removeAll(to);
        reg.clearEquipment(to);
        org.bukkit.World w = to.world();
        int count = 0;
        for (EquipmentDef d : shifted) {
            if (w != null && reg.at(w, d.pos()).isPresent()) {
                KitchenRegistry.Located other = reg.at(w, d.pos()).get();
                msg().send(s, "wand.occupied", text("pos", d.pos()), text("id", other.kitchen().id()));
                continue;
            }
            reg.addEquipment(to, d);
            plugin.entities().spawn(to, d);
            count++;
        }
        if (from.spawnRaw() != null && to.spawnRaw() == null) {
            org.bukkit.Location sp = from.spawnRaw();
            jp.minecraftcooks.core.BlockPos shift = jp.minecraftcooks.core.BlockPos.min(to.corner1(), to.corner2())
                    .minus(jp.minecraftcooks.core.BlockPos.min(from.corner1(), from.corner2()));
            sp.add(shift.x(), shift.y(), shift.z());
            to.setSpawn(sp);
            reg.save();
        }
        msg().send(s, "kitchen.cloned", text("from", from.id()), text("to", to.id()), text("count", count));
    }

    private Optional<Kitchen> findKitchen(CommandSender s, String id) {
        Optional<Kitchen> k = plugin.kitchens().get(id);
        if (k.isEmpty()) msg().send(s, "kitchen.not-found", text("id", id));
        return k;
    }

    private boolean busy(CommandSender s, Kitchen k) {
        if (plugin.isKitchenBusy(k.id())) {
            msg().send(s, "kitchen.in-use");
            return true;
        }
        return false;
    }

    // ---------- wand ----------

    private void wand(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            msg().send(s, "general.players-only");
            return;
        }
        if (a.length < 2) {
            usage(s, "/cg wand <キッチンID> <設備の種類|area> [番号]");
            return;
        }
        Optional<Kitchen> k = findKitchen(s, a[0]);
        if (k.isEmpty()) return;
        String mode = a[1].toLowerCase(Locale.ROOT);
        int slot = 0;
        if (!"area".equals(mode)) {
            Optional<EquipType> t = EquipType.byKey(mode);
            if (t.isEmpty()) {
                msg().send(s, "wand.bad-type", text("type", a[1]), text("types", equipKeys()));
                return;
            }
            if (t.get() == EquipType.COUNTER) {
                int max = plugin.settings().getOrderSlots();
                try {
                    slot = a.length >= 3 ? Integer.parseInt(a[2]) : 0;
                } catch (NumberFormatException e) {
                    slot = 0;
                }
                if (slot < 1 || slot > max) {
                    msg().send(s, "wand.need-slot", text("max", max));
                    return;
                }
            }
        }
        p.getInventory().addItem(plugin.items().wand(k.get().id(), mode, slot));
        msg().send(s, "wand.given", text("id", k.get().id()), text("mode", mode + (slot > 0 ? " #" + slot : "")));
    }

    private static String equipKeys() {
        return Arrays.stream(EquipType.values()).map(EquipType::key).collect(Collectors.joining(", ")) + ", area";
    }

    // ---------- team / exclude / score ----------

    private Optional<java.util.UUID> findPlayer(CommandSender s, String name) {
        Player online = org.bukkit.Bukkit.getPlayerExact(name);
        if (online != null) return Optional.of(online.getUniqueId());
        org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayerIfCached(name);
        if (off != null) return Optional.of(off.getUniqueId());
        msg().send(s, "general.unknown-player", text("name", name));
        return Optional.empty();
    }

    private static String nameOf(java.util.UUID id) {
        String n = org.bukkit.Bukkit.getOfflinePlayer(id).getName();
        return n == null ? id.toString().substring(0, 8) : n;
    }

    private static String names(Collection<java.util.UUID> ids) {
        if (ids.isEmpty()) return "-";
        return ids.stream().map(CgCommand::nameOf).collect(Collectors.joining(", "));
    }

    /** チームがなければ、番号に合わせた初期の名前と色で作る(team-3 なら 緑チーム)。 */
    private void ensureTeam(String teamId) {
        TeamRoster r = plugin.match().roster();
        if (r.team(teamId).isPresent()) return;
        int idx = r.teamIds().size();
        try {
            idx = Integer.parseInt(teamId.replace("team-", "")) - 1;
        } catch (NumberFormatException ignored) {
            // 番号でなければ、作った順の色にする
        }
        idx = Math.floorMod(idx, TeamRoster.DEFAULT_COLORS.size());
        r.createTeam(teamId, TeamRoster.DEFAULT_NAMES.get(idx), TeamRoster.DEFAULT_COLORS.get(idx));
    }

    private void team(CommandSender s, String[] a) {
        String u = "/cg team <auto|add|remove|move|name|color|kitchen|list|clear>";
        if (a.length == 0) {
            usage(s, u);
            return;
        }
        var match = plugin.match();
        TeamRoster r = match.roster();
        try {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "auto" -> {
                    if (r.isLocked()) throw new IllegalStateException();
                    List<java.util.UUID> players = new ArrayList<>();
                    for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                        if (!match.excluded().contains(p.getUniqueId())) players.add(p.getUniqueId());
                    }
                    TeamSplitter.Split split = TeamSplitter.split(players, new Random(), 5, 8);
                    r.clear();
                    match.spectators().clear();
                    for (int i = 0; i < split.teams().size(); i++) {
                        String id = "team-" + (i + 1);
                        r.createTeam(id, TeamRoster.DEFAULT_NAMES.get(i), TeamRoster.DEFAULT_COLORS.get(i));
                        for (java.util.UUID m : split.teams().get(i)) r.addMember(id, m);
                    }
                    match.spectators().addAll(split.spectators());
                    msg().send(s, "team.auto-done", text("teams", split.teams().size()));
                    for (String id : r.teamIds()) {
                        org.bukkit.Bukkit.getServer().sendMessage(msg().get("team.auto-team-line",
                                Messages.comp("team", match.teamName(id)), text("members", names(r.members(id)))));
                    }
                    org.bukkit.Bukkit.getServer().sendMessage(msg().get("team.auto-spectators",
                            text("members", names(split.spectators()))));
                }
                case "add", "move" -> {
                    // add <チーム> <プレイヤー> / move <プレイヤー> <チーム>
                    if (a.length < 3) {
                        usage(s, a[0].equalsIgnoreCase("add") ? "/cg team add <チーム> <プレイヤー>" : "/cg team move <プレイヤー> <チーム>");
                        return;
                    }
                    boolean add = a[0].equalsIgnoreCase("add");
                    String teamId = add ? a[1] : a[2];
                    String player = add ? a[2] : a[1];
                    Optional<java.util.UUID> id = findPlayer(s, player);
                    if (id.isEmpty()) return;
                    if (r.isLocked()) throw new IllegalStateException();
                    ensureTeam(teamId);
                    r.addMember(teamId, id.get());
                    match.spectators().remove(id.get());
                    msg().send(s, "team.added", text("player", player), Messages.comp("team", match.teamName(teamId)));
                }
                case "remove" -> {
                    if (a.length < 2) {
                        usage(s, "/cg team remove <プレイヤー>");
                        return;
                    }
                    Optional<java.util.UUID> id = findPlayer(s, a[1]);
                    if (id.isEmpty()) return;
                    r.removeMember(id.get());
                    msg().send(s, "team.removed", text("player", a[1]));
                }
                case "name" -> {
                    if (a.length < 3) {
                        usage(s, "/cg team name <チーム> <名前>");
                        return;
                    }
                    if (r.team(a[1]).isEmpty()) {
                        msg().send(s, "team.not-found", text("team", a[1]));
                        return;
                    }
                    String name = String.join(" ", Arrays.copyOfRange(a, 2, a.length));
                    r.setName(a[1], name);
                    msg().send(s, "team.renamed", text("team", a[1]), text("name", name));
                }
                case "color" -> {
                    if (a.length < 3) {
                        usage(s, "/cg team color <チーム> <色>");
                        return;
                    }
                    if (r.team(a[1]).isEmpty()) {
                        msg().send(s, "team.not-found", text("team", a[1]));
                        return;
                    }
                    String c = a[2].toLowerCase(Locale.ROOT);
                    if (net.kyori.adventure.text.format.NamedTextColor.NAMES.value(c) == null) {
                        msg().send(s, "team.bad-color", text("color", a[2]),
                                text("colors", String.join(", ", net.kyori.adventure.text.format.NamedTextColor.NAMES.keys())));
                        return;
                    }
                    r.setColor(a[1], c);
                    msg().send(s, "team.recolored", Messages.comp("team", match.teamName(a[1])), text("color", c));
                }
                case "kitchen" -> {
                    if (a.length < 3) {
                        usage(s, "/cg team kitchen <チーム> <キッチンID>");
                        return;
                    }
                    if (r.team(a[1]).isEmpty()) {
                        msg().send(s, "team.not-found", text("team", a[1]));
                        return;
                    }
                    if (findKitchen(s, a[2]).isEmpty()) return;
                    // 同じキッチンを指定していたチームからは外す
                    for (String other : r.teamIds()) {
                        if (r.kitchenOf(other).filter(a[2]::equals).isPresent()) r.assignKitchen(other, null);
                    }
                    r.assignKitchen(a[1], a[2]);
                    msg().send(s, "team.kitchen-set", Messages.comp("team", match.teamName(a[1])), text("kitchen", a[2]));
                }
                case "list" -> {
                    msg().send(s, "team.list-header");
                    for (String id : r.teamIds()) {
                        msg().send(s, "team.list-line", Messages.comp("team", match.teamName(id)), text("id", id),
                                text("kitchen", r.kitchenOf(id).orElse("自動")), text("members", names(r.members(id))));
                    }
                    if (!match.spectators().isEmpty()) {
                        msg().send(s, "team.auto-spectators", text("members", names(match.spectators())));
                    }
                }
                case "clear" -> {
                    r.clear();
                    match.spectators().clear();
                    msg().send(s, "team.cleared");
                }
                default -> usage(s, u);
            }
        } catch (IllegalStateException e) {
            msg().send(s, "team.locked");
        }
    }

    private void exclude(CommandSender s, String[] a) {
        if (a.length < 2 || !(a[0].equalsIgnoreCase("add") || a[0].equalsIgnoreCase("remove"))) {
            usage(s, "/cg exclude <add|remove> <プレイヤー>");
            return;
        }
        Optional<java.util.UUID> id = findPlayer(s, a[1]);
        if (id.isEmpty()) return;
        if (a[0].equalsIgnoreCase("add")) {
            plugin.match().excluded().add(id.get());
            msg().send(s, "team.excluded", text("player", a[1]));
        } else {
            plugin.match().excluded().remove(id.get());
            msg().send(s, "team.included", text("player", a[1]));
        }
    }

    private void score(CommandSender s, String[] a) {
        if (a.length < 3 || !a[0].equalsIgnoreCase("add")) {
            usage(s, "/cg score add <チーム> <点>");
            return;
        }
        int pts;
        try {
            pts = Integer.parseInt(a[2]);
        } catch (NumberFormatException e) {
            usage(s, "/cg score add <チーム> <点(整数、マイナス可)>");
            return;
        }
        plugin.match().adjustScore(s, a[1], pts);
    }

    // ---------- spectate / reload ----------

    private void spectate(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            msg().send(s, "general.players-only");
            return;
        }
        var match = plugin.match();
        if (a.length >= 1 && a[0].equalsIgnoreCase("stop")) {
            if (match.stopSpectating(p)) msg().send(s, "spectate.stopped");
            return;
        }
        boolean admin = p.hasPermission("minecraft-cooks.admin");
        java.util.UUID id = p.getUniqueId();
        boolean allowed = admin || match.spectators().contains(id) || match.excluded().contains(id)
                || match.roster().teamOf(id).isEmpty() || !match.isRunning();
        if (!allowed) {
            msg().send(s, "spectate.not-allowed");
            return;
        }
        // 遊んでいる最中の人は観戦に切り替えられない
        if (plugin.runtimeFor(p).isPresent()) {
            msg().send(s, "spectate.not-allowed");
            return;
        }
        Optional<Kitchen> k;
        if (a.length >= 1) {
            k = findKitchen(s, a[0]);
        } else {
            k = plugin.kitchens().all().stream().filter(x -> plugin.isKitchenBusy(x.id())).findFirst()
                    .or(() -> plugin.kitchens().all().stream().findFirst());
            if (k.isEmpty()) msg().send(s, "kitchen.not-found", text("id", "-"));
        }
        if (k.isEmpty()) return;
        Optional<org.bukkit.Location> spawn = k.get().spawn();
        if (spawn.isEmpty()) {
            msg().send(s, "match.kitchen-not-ready", text("id", k.get().id()));
            return;
        }
        match.startSpectating(p);
        p.teleport(spawn.get());
        msg().send(s, "spectate.started", text("kitchen", k.get().displayName()));
    }

    private void reload(CommandSender s) {
        if (plugin.match().isRunning()) {
            msg().send(s, "general.reload-in-match");
            return;
        }
        String err = plugin.reloadData();
        if (err == null) msg().send(s, "general.reloaded");
        else msg().send(s, "general.reload-failed", text("error", err));
    }

    // ---------- match ----------

    private void match(CommandSender s, String[] a) {
        String u = "/cg match <start quick [店]|start normal|pause|resume|stop|redo-round>";
        if (a.length == 0) {
            usage(s, u);
            return;
        }
        var m = plugin.match();
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "start" -> {
                if (a.length < 2) {
                    usage(s, u);
                    return;
                }
                switch (a[1].toLowerCase(Locale.ROOT)) {
                    case "quick" -> m.start(s, jp.minecraftcooks.paper.match.MatchManager.Mode.QUICK,
                            a.length >= 3 ? Optional.of(a[2]) : Optional.empty());
                    case "normal" -> m.start(s, jp.minecraftcooks.paper.match.MatchManager.Mode.NORMAL, Optional.empty());
                    default -> usage(s, u);
                }
            }
            case "pause" -> m.pause(s);
            case "resume" -> m.resume(s);
            case "stop" -> m.stop(s);
            case "redo-round" -> m.redoRound(s);
            default -> usage(s, u);
        }
    }

    // ---------- practice ----------

    private void practice(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            msg().send(s, "general.players-only");
            return;
        }
        if (a.length == 0) {
            usage(s, "/cg practice <キッチンID> [店] / /cg practice stop");
            return;
        }
        if (a[0].equalsIgnoreCase("stop")) {
            if (plugin.practice().stop(p)) msg().send(s, "play.practice-stop");
            return;
        }
        Optional<Kitchen> k = findKitchen(s, a[0]);
        if (k.isEmpty()) return;
        // 自分の練習は止めてから始め直す。ほかの人が使っているキッチンや、試合中の人は不可
        plugin.practice().stop(p);
        if (plugin.isKitchenBusy(k.get().id()) || plugin.runtimeFor(p).isPresent()) {
            msg().send(s, "play.practice-busy");
            return;
        }
        String shopId = a.length >= 2 ? a[1] : plugin.recipes().shopIds().get(0);
        Optional<jp.minecraftcooks.core.recipe.Shop> shop = plugin.recipes().shop(shopId);
        if (shop.isEmpty()) {
            msg().send(s, "play.shop-unknown", text("shop", shopId), text("shops", String.join(", ", plugin.recipes().shopIds())));
            return;
        }
        var rt = plugin.newRuntime(k.get(), shop.get(), System.nanoTime(), () -> List.of(p));
        plugin.practice().start(p, rt);
        k.get().spawn().ifPresent(p::teleport);
        msg().send(s, "play.practice-start", text("kitchen", k.get().displayName()), text("shop", shop.get().name()));
    }

    // ---------- tab ----------

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("minecraft-cooks.admin"))  {
            if (a.length == 1) return "spectate".startsWith(a[0].toLowerCase(Locale.ROOT)) ? List.of("spectate") : List.of();
            if (a.length == 2 && a[0].equalsIgnoreCase("spectate")) {
                List<String> l = new ArrayList<>(kitchenIds());
                l.add("stop");
                return l.stream().filter(x -> x.startsWith(a[1].toLowerCase(Locale.ROOT))).toList();
            }
            return List.of();
        }
        List<String> out = complete(a);
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }

    private static List<String> onlineNames() {
        return org.bukkit.Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    private List<String> kitchenIds() {
        return plugin.kitchens().all().stream().map(Kitchen::id).toList();
    }

    private List<String> complete(String[] a) {
        if (a.length == 1) return List.of("match", "team", "exclude", "score", "kitchen", "wand", "practice", "spectate", "settings", "reload");
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("kitchen")) {
            if (a.length == 2) return List.of("create", "remove", "list", "setspawn", "clone", "reset");
            String op = a[1].toLowerCase(Locale.ROOT);
            if (a.length == 3 && !op.equals("create") && !op.equals("list")) return kitchenIds();
            if (a.length == 4 && op.equals("clone")) return kitchenIds();
        }
        if (sub.equals("team")) {
            if (a.length == 2) return List.of("auto", "add", "remove", "move", "name", "color", "kitchen", "list", "clear");
            String op = a[1].toLowerCase(Locale.ROOT);
            List<String> teams = plugin.match().roster().teamIds();
            if (a.length == 3) {
                if (op.equals("remove") || op.equals("move")) return onlineNames();
                if (op.equals("add") || op.equals("name") || op.equals("color") || op.equals("kitchen")) return teams;
            }
            if (a.length == 4) {
                if (op.equals("add")) return onlineNames();
                if (op.equals("move")) return teams;
                if (op.equals("color")) return List.copyOf(net.kyori.adventure.text.format.NamedTextColor.NAMES.keys());
                if (op.equals("kitchen")) return kitchenIds();
            }
        }
        if (sub.equals("exclude")) {
            if (a.length == 2) return List.of("add", "remove");
            if (a.length == 3) return onlineNames();
        }
        if (sub.equals("score")) {
            if (a.length == 2) return List.of("add");
            if (a.length == 3) return plugin.match().roster().teamIds();
        }
        if (sub.equals("spectate") && a.length == 2) {
            List<String> l = new ArrayList<>(kitchenIds());
            l.add("stop");
            return l;
        }
        if (sub.equals("match")) {
            if (a.length == 2) return List.of("start", "pause", "resume", "stop", "redo-round");
            if (a.length == 3 && a[1].equalsIgnoreCase("start")) return List.of("quick", "normal");
            if (a.length == 4 && a[2].equalsIgnoreCase("quick")) return plugin.recipes().shopIds();
        }
        if (sub.equals("practice")) {
            if (a.length == 2) {
                List<String> l = new ArrayList<>(kitchenIds());
                l.add("stop");
                return l;
            }
            if (a.length == 3) return plugin.recipes().shopIds();
        }
        if (sub.equals("wand")) {
            if (a.length == 2) return kitchenIds();
            if (a.length == 3) {
                List<String> l = new ArrayList<>();
                for (EquipType t : EquipType.values()) l.add(t.key());
                l.add("area");
                return l;
            }
            if (a.length == 4 && a[2].equalsIgnoreCase("counter")) {
                List<String> l = new ArrayList<>();
                for (int i = 1; i <= plugin.settings().getOrderSlots(); i++) l.add(String.valueOf(i));
                return l;
            }
        }
        return List.of();
    }
}
