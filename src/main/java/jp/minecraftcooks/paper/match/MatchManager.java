package jp.minecraftcooks.paper.match;

import jp.minecraftcooks.MinecraftCooksPlugin;
import jp.minecraftcooks.core.BlockPos;
import jp.minecraftcooks.core.Settings;
import jp.minecraftcooks.core.kitchen.EquipmentDef;
import jp.minecraftcooks.core.recipe.Shop;
import jp.minecraftcooks.core.score.Ranking;
import jp.minecraftcooks.core.score.TeamResult;
import jp.minecraftcooks.core.team.TeamRoster;
import jp.minecraftcooks.paper.Messages;
import jp.minecraftcooks.paper.kitchen.Kitchen;
import jp.minecraftcooks.paper.play.KitchenAccess;
import jp.minecraftcooks.paper.play.KitchenRuntime;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.util.BoundingBox;

import java.time.Duration;
import java.util.*;

import static jp.minecraftcooks.paper.Messages.comp;
import static jp.minecraftcooks.paper.Messages.text;

/** 試合の進行。チームの名簿、ラウンド、得点を持つ。 */
public final class MatchManager implements KitchenAccess, Listener {
    public enum Mode { QUICK, NORMAL }

    public enum State { IDLE, COUNTDOWN, PLAYING, PAUSED, BREAK, FINISHED }

    /** チームごとの試合全体の成績。 */
    static final class Totals {
        int total;
        int lastRound;
        int mistakes;
    }

    private final MinecraftCooksPlugin plugin;
    private final TeamRoster roster = new TeamRoster();
    private final Set<UUID> excluded = new HashSet<>();
    private final Set<UUID> spectators = new HashSet<>();
    private final Map<String, KitchenRuntime> runtimes = new LinkedHashMap<>();
    private final Map<String, Totals> totals = new LinkedHashMap<>();
    private final Map<UUID, Location> returnTo = new HashMap<>();
    /** 試合の終わりにオフラインだった人。次に入ってきたとき、持ち物を消して元の場所へ戻す。 */
    private final Map<UUID, Location> pendingReturn = new HashMap<>();
    /** 観戦を始める前のゲームモードと場所。 */
    private final Map<UUID, GameMode> spectateMode = new HashMap<>();
    private final Map<UUID, Location> spectateFrom = new HashMap<>();
    private final Hud hud;

    private State state = State.IDLE;
    private State pausedFrom;
    private Mode mode;
    private List<String> shopOrder = List.of();
    private int roundIndex;
    private int ticksLeft;
    private int phaseTicks;
    private Settings roundSettings = new Settings();

    public MatchManager(MinecraftCooksPlugin plugin) {
        this.plugin = plugin;
        this.hud = new Hud(plugin, this);
    }

    private Messages msg() {
        return plugin.messages();
    }

    // ---------- 参照 ----------

    public TeamRoster roster() { return roster; }
    public Set<UUID> excluded() { return excluded; }
    public Set<UUID> spectators() { return spectators; }
    public State state() { return state; }
    public Mode mode() { return mode; }
    public int roundIndex() { return roundIndex; }
    public int roundCount() { return shopOrder.size(); }
    public int ticksLeft() { return ticksLeft; }
    public int phaseTicks() { return phaseTicks; }

    public boolean isRunning() {
        return state != State.IDLE && state != State.FINISHED;
    }

    public Optional<Shop> currentShop() {
        if (roundIndex >= shopOrder.size()) return Optional.empty();
        return plugin.recipes().shop(shopOrder.get(roundIndex));
    }

    public Optional<KitchenRuntime> runtimeOfTeam(String teamId) {
        return Optional.ofNullable(runtimes.get(teamId));
    }

    @Override
    public Optional<KitchenRuntime> runtimeFor(Player player) {
        if (!isRunning()) return Optional.empty();
        return roster.teamOf(player.getUniqueId()).map(runtimes::get);
    }

    public boolean isBusy(String kitchenId) {
        return isRunning() && runtimes.values().stream().anyMatch(r -> r.kitchen().id().equals(kitchenId));
    }

    /** 表示用: 合計点(試合中は、いまのラウンドの得点も足す)。 */
    public int liveTotal(String teamId) {
        Totals t = totals.get(teamId);
        int base = t == null ? 0 : t.total;
        if (state == State.PLAYING || state == State.PAUSED || state == State.COUNTDOWN) {
            KitchenRuntime rt = runtimes.get(teamId);
            if (rt != null) base += rt.roundScore();
        }
        return base;
    }

    public List<Ranking.Ranked> liveRanking() {
        List<TeamResult> rs = new ArrayList<>();
        for (String id : roster.teamIds()) {
            Totals t = totals.getOrDefault(id, new Totals());
            rs.add(new TeamResult(id, liveTotal(id), t.lastRound, t.mistakes));
        }
        return Ranking.rank(rs, roundSettings.getTieBreak());
    }

    public Component teamName(String teamId) {
        return roster.team(teamId).map(t -> Component.text(t.name(), color(t.color()))).orElse(Component.text(teamId));
    }

    public static TextColor color(String key) {
        NamedTextColor c = NamedTextColor.NAMES.value(key);
        return c == null ? NamedTextColor.WHITE : c;
    }

    public List<Player> onlineMembers(String teamId) {
        List<Player> out = new ArrayList<>();
        roster.team(teamId).ifPresent(t -> {
            for (UUID id : t.members()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) out.add(p);
            }
        });
        return out;
    }

    // ---------- 開始 ----------

    public void start(CommandSender s, Mode m, Optional<String> shopId) {
        if (isRunning()) {
            msg().send(s, "match.already-running");
            return;
        }
        List<String> teams = roster.teamIds();
        if (teams.isEmpty()) {
            msg().send(s, "match.no-teams");
            return;
        }
        for (String t : teams) {
            if (roster.members(t).isEmpty()) {
                msg().send(s, "match.empty-team", comp("team", teamName(t)));
                return;
            }
        }
        // 練習中の人は止める
        for (Player p : Bukkit.getOnlinePlayers()) plugin.practice().stop(p);

        // キッチンの割り当て: 運営が指定したものはそのまま、残りは空いているキッチンからランダム
        List<Kitchen> ready = plugin.kitchens().all().stream()
                .filter(k -> k.spawn().isPresent() && !k.counters().isEmpty()).toList();
        Set<String> used = new HashSet<>();
        for (String t : teams) roster.kitchenOf(t).ifPresent(used::add);
        List<Kitchen> free = new ArrayList<>(ready.stream().filter(k -> !used.contains(k.id())).toList());
        Collections.shuffle(free);
        int needed = (int) teams.stream().filter(t -> roster.kitchenOf(t).isEmpty()).count();
        if (free.size() < needed) {
            msg().send(s, "match.not-enough-kitchens", text("teams", teams.size()), text("kitchens", ready.size()));
            return;
        }
        Map<String, Kitchen> assign = new LinkedHashMap<>();
        Iterator<Kitchen> it = free.iterator();
        for (String t : teams) {
            Optional<String> kid = roster.kitchenOf(t);
            Kitchen k = kid.isPresent() ? plugin.kitchens().get(kid.get()).orElse(null) : it.next();
            if (k == null || k.spawn().isEmpty()) {
                msg().send(s, "match.kitchen-not-ready", text("id", kid.orElse("?")));
                return;
            }
            assign.put(t, k);
        }
        if (new HashSet<>(assign.values()).size() != assign.size()) {
            msg().send(s, "match.not-enough-kitchens", text("teams", teams.size()), text("kitchens", ready.size()));
            return;
        }
        Optional<String> wantShop = shopId;
        if (wantShop.isPresent() && plugin.recipes().shop(wantShop.get()).isEmpty()) {
            msg().send(s, "play.shop-unknown", text("shop", wantShop.get()),
                    text("shops", String.join(", ", plugin.recipes().shopIds())));
            return;
        }
        for (Map.Entry<String, Kitchen> e : assign.entrySet()) roster.assignKitchen(e.getKey(), e.getValue().id());
        for (String t : teams) {
            int n = roster.members(t).size();
            if (n != 5) msg().send(s, "match.team-size-warning", comp("team", teamName(t)), text("count", n));
        }

        mode = m;
        List<String> ids = plugin.recipes().shopIds();
        if (m == Mode.NORMAL) {
            shopOrder = List.copyOf(ids);
        } else {
            shopOrder = List.of(wantShop.orElse(ids.get(new Random().nextInt(ids.size()))));
        }
        roster.lock();
        totals.clear();
        for (String t : teams) totals.put(t, new Totals());
        returnTo.clear();
        for (String t : teams) {
            for (Player p : onlineMembers(t)) returnTo.put(p.getUniqueId(), p.getLocation());
        }
        broadcast("match.started", text("mode", plainMsg(m == Mode.QUICK ? "match.mode-quick" : "match.mode-normal")));
        hud.show();
        beginRound(0);
    }

    private String plainMsg(String key) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(msg().get(key));
    }

    private void beginRound(int index) {
        roundIndex = index;
        roundSettings = Settings.fromMap(plugin.settings().toMap());
        for (KitchenRuntime rt : runtimes.values()) {
            rt.setActive(false);
            rt.clearDisplays();
        }
        runtimes.clear();
        Shop shop = currentShop().orElseThrow();
        long seed = System.nanoTime();
        for (String t : roster.teamIds()) {
            Kitchen k = plugin.kitchens().get(roster.kitchenOf(t).orElseThrow()).orElseThrow();
            KitchenRuntime rt = plugin.newRuntime(k, shop, seed, () -> new ArrayList<>(onlineMembers(t)));
            rt.setActive(false);
            runtimes.put(t, rt);
            clearGroundItems(k);
            for (Player p : onlineMembers(t)) prepare(p, k);
        }
        broadcast("match.round-start", text("round", index + 1), text("rounds", shopOrder.size()), text("shop", shop.name()));
        state = State.COUNTDOWN;
        ticksLeft = roundSettings.getCountdownSeconds() * 20;
        phaseTicks = ticksLeft;
        hud.update(true);
    }

    /** 試合に入る準備: 持ち物を空にして、アドベンチャーモードでキッチンへ。 */
    private void prepare(Player p, Kitchen k) {
        p.getInventory().clear();
        p.getInventory().setHeldItemSlot(0);
        p.setGameMode(GameMode.ADVENTURE);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        var attr = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        p.setHealth(attr == null ? 20.0 : attr.getValue());
        p.setFireTicks(0);
        k.spawn().ifPresent(p::teleport);
    }

    // ---------- 進行 ----------

    public void tick() {
        switch (state) {
            case COUNTDOWN -> {
                if (ticksLeft % 20 == 0 && ticksLeft > 0) {
                    title(msg().get("match.countdown-title", text("n", ticksLeft / 20)), Sound.BLOCK_NOTE_BLOCK_HAT);
                }
                if (--ticksLeft <= 0) {
                    state = State.PLAYING;
                    ticksLeft = roundSettings.getRoundSeconds() * 20;
                    phaseTicks = ticksLeft;
                    for (KitchenRuntime rt : runtimes.values()) rt.setActive(true);
                    title(msg().get("match.go-title"), Sound.ENTITY_PLAYER_LEVELUP);
                    hud.update(true);
                }
            }
            case PLAYING -> {
                for (KitchenRuntime rt : runtimes.values()) rt.tick();
                if (--ticksLeft <= 0) endRound();
            }
            case BREAK -> {
                if (--ticksLeft <= 0) {
                    if (roundIndex + 1 < shopOrder.size()) beginRound(roundIndex + 1);
                    else finish();
                }
            }
            default -> { }
        }
        if (isRunning() && Bukkit.getCurrentTick() % 20 == 0) hud.update(false);
    }

    private void endRound() {
        Shop shop = currentShop().orElseThrow();
        List<TeamResult> round = new ArrayList<>();
        Map<String, int[]> detail = new HashMap<>();
        for (Map.Entry<String, KitchenRuntime> e : runtimes.entrySet()) {
            KitchenRuntime rt = e.getValue();
            rt.setActive(false);
            int penalty = rt.roundPenalty();
            int score = rt.roundScore() - penalty;
            Totals t = totals.computeIfAbsent(e.getKey(), k -> new Totals());
            t.total += score;
            t.lastRound = score;
            t.mistakes += rt.roundMistakes();
            round.add(new TeamResult(e.getKey(), score, score, rt.roundMistakes()));
            detail.put(e.getKey(), new int[]{rt.roundScore(), penalty, rt.roundMistakes()});
            clearGroundItems(rt.kitchen());
            for (Player p : onlineMembers(e.getKey())) p.getInventory().clear();
        }
        title(msg().get("match.round-end-title"), Sound.BLOCK_BELL_USE);
        broadcast("match.round-result-header", text("round", roundIndex + 1), text("shop", shop.name()));
        for (Ranking.Ranked r : Ranking.rank(round, roundSettings.getTieBreak())) {
            int[] d = detail.get(r.r().teamId());
            broadcast("match.round-result-line", text("place", r.place()), comp("team", teamName(r.r().teamId())),
                    text("score", r.r().total()), text("points", d[0]), text("penalty", d[1]), text("mistakes", d[2]));
        }
        if (roundIndex + 1 >= shopOrder.size()) showFinal();
        state = State.BREAK;
        ticksLeft = roundSettings.getBreakSeconds() * 20;
        phaseTicks = ticksLeft;
        if (roundIndex + 1 < shopOrder.size()) broadcast("match.break", text("seconds", roundSettings.getBreakSeconds()));
        hud.update(true);
    }

    private void showFinal() {
        broadcast("match.final-header");
        for (Ranking.Ranked r : finalRanking()) {
            broadcast("match.final-line", text("place", r.place()), comp("team", teamName(r.r().teamId())),
                    text("total", r.r().total()));
        }
    }

    public List<Ranking.Ranked> finalRanking() {
        List<TeamResult> rs = new ArrayList<>();
        for (Map.Entry<String, Totals> e : totals.entrySet()) {
            rs.add(new TeamResult(e.getKey(), e.getValue().total, e.getValue().lastRound, e.getValue().mistakes));
        }
        return Ranking.rank(rs, roundSettings.getTieBreak());
    }

    private void finish() {
        cleanup();
        state = State.FINISHED;
        hud.update(true);
    }

    /** 設備を空にし、持ち物を消し、元の場所へ戻す。 */
    private void cleanup() {
        for (Map.Entry<String, KitchenRuntime> e : runtimes.entrySet()) {
            KitchenRuntime rt = e.getValue();
            rt.setActive(false);
            rt.reset();
            rt.clearDisplays();
            clearGroundItems(rt.kitchen());
        }
        runtimes.clear();
        for (String t : roster.teamIds()) {
            for (UUID id : roster.members(t)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) sendBack(p);
                else pendingReturn.put(id, returnTo.get(id));
            }
        }
        returnTo.clear();
        roster.unlock();
        hud.hide();
    }

    private void sendBack(Player p) {
        p.getInventory().clear();
        Location back = returnTo.get(p.getUniqueId());
        if (back == null) back = lobby();
        if (back != null) p.teleport(back);
    }

    private Location lobby() {
        String w = plugin.getConfig().getString("lobby.world", "");
        World world = w.isEmpty() ? Bukkit.getWorlds().get(0) : Bukkit.getWorld(w);
        if (world == null) return null;
        if (w.isEmpty()) return world.getSpawnLocation();
        return new Location(world, plugin.getConfig().getDouble("lobby.x"), plugin.getConfig().getDouble("lobby.y"),
                plugin.getConfig().getDouble("lobby.z"));
    }

    public void pause(CommandSender s) {
        if (state != State.PLAYING && state != State.COUNTDOWN) {
            msg().send(s, "match.not-running");
            return;
        }
        pausedFrom = state;
        state = State.PAUSED;
        for (KitchenRuntime rt : runtimes.values()) rt.setActive(false);
        broadcast("match.paused");
        title(msg().get("match.paused-title"), Sound.BLOCK_NOTE_BLOCK_BASS);
        hud.update(true);
    }

    public void resume(CommandSender s) {
        if (state != State.PAUSED) {
            msg().send(s, "match.not-running");
            return;
        }
        state = pausedFrom == null ? State.PLAYING : pausedFrom;
        if (state == State.PLAYING) for (KitchenRuntime rt : runtimes.values()) rt.setActive(true);
        broadcast("match.resumed");
        hud.update(true);
    }

    public void stop(CommandSender s) {
        if (!isRunning()) {
            msg().send(s, "match.not-running");
            return;
        }
        cleanup();
        state = State.IDLE;
        broadcast("match.stopped");
    }

    /** いまのラウンドをやり直す。得点はこのラウンドの分だけ捨てる。 */
    public void redoRound(CommandSender s) {
        if (state != State.PLAYING && state != State.COUNTDOWN && state != State.PAUSED) {
            msg().send(s, "match.not-running");
            return;
        }
        broadcast("match.redo");
        for (Map.Entry<String, KitchenRuntime> e : runtimes.entrySet()) {
            for (Player p : onlineMembers(e.getKey())) p.getInventory().clear();
            e.getValue().reset();
        }
        beginRound(roundIndex);
    }

    public void adjustScore(CommandSender s, String teamId, int points) {
        if (roster.team(teamId).isEmpty()) {
            msg().send(s, "team.not-found", text("team", teamId));
            return;
        }
        Totals t = totals.computeIfAbsent(teamId, k -> new Totals());
        t.total += points;
        msg().send(s, "match.score-adjusted", comp("team", teamName(teamId)), text("points", points),
                text("total", t.total));
        hud.update(true);
    }

    /** 運営が試合の全員を止めるとき(プラグインの終了時)。 */
    public void shutdown() {
        if (isRunning()) cleanup();
        state = State.IDLE;
        hud.hide();
    }

    // ---------- 地面のアイテム ----------

    private void clearGroundItems(Kitchen k) {
        World w = k.world();
        if (w == null) return;
        BoundingBox box;
        if (k.hasArea()) {
            BlockPos lo = BlockPos.min(k.corner1(), k.corner2());
            BlockPos hi = BlockPos.max(k.corner1(), k.corner2());
            box = new BoundingBox(lo.x(), lo.y(), lo.z(), hi.x() + 1, hi.y() + 1, hi.z() + 1);
        } else {
            List<EquipmentDef> eq = k.equipment();
            if (eq.isEmpty()) return;
            BlockPos lo = eq.get(0).pos(), hi = eq.get(0).pos();
            for (EquipmentDef d : eq) {
                lo = BlockPos.min(lo, d.pos());
                hi = BlockPos.max(hi, d.pos());
            }
            box = new BoundingBox(lo.x(), lo.y(), lo.z(), hi.x() + 1, hi.y() + 1, hi.z() + 1).expand(8);
        }
        for (var e : w.getNearbyEntities(box, en -> en instanceof Item)) e.remove();
    }

    // ---------- 通知 ----------

    private void broadcast(String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... args) {
        Component c = msg().get(key, args);
        Bukkit.getServer().sendMessage(c);
    }

    private void title(Component main, Sound sound) {
        Title t = Title.title(main, Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(200)));
        for (String team : roster.teamIds()) {
            for (Player p : onlineMembers(team)) {
                p.showTitle(t);
                p.playSound(p.getLocation(), sound, 0.8f, 1f);
            }
        }
    }

    // ---------- 観戦 ----------

    public void startSpectating(Player p) {
        spectateMode.putIfAbsent(p.getUniqueId(), p.getGameMode());
        spectateFrom.putIfAbsent(p.getUniqueId(), p.getLocation());
        p.setGameMode(GameMode.SPECTATOR);
    }

    /** 観戦をやめて、始める前のゲームモードと場所に戻す。観戦していなければ false。 */
    public boolean stopSpectating(Player p) {
        GameMode mode = spectateMode.remove(p.getUniqueId());
        Location from = spectateFrom.remove(p.getUniqueId());
        if (mode == null) return false;
        p.setGameMode(mode);
        if (from != null) p.teleport(from);
        return true;
    }

    // ---------- 抜けて戻った人 ----------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!isRunning() && pendingReturn.containsKey(p.getUniqueId())) {
            Location back = pendingReturn.remove(p.getUniqueId());
            p.getInventory().clear();
            Location dest = back != null ? back : lobby();
            if (dest != null) p.teleport(dest);
        }
        pendingReturn.remove(p.getUniqueId());
        if (isRunning()) {
            Optional<String> team = roster.teamOf(p.getUniqueId());
            if (team.isPresent()) {
                KitchenRuntime rt = runtimes.get(team.get());
                if (rt != null) {
                    returnTo.putIfAbsent(p.getUniqueId(), lobby());
                    prepare(p, rt.kitchen());
                    msg().send(p, "match.rejoined");
                }
            }
            hud.show(p);
        }
    }
}
