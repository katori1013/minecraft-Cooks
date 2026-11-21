package jp.minecraftcooks.paper.match;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import jp.minecraftcooks.MinecraftCooksPlugin;
import jp.minecraftcooks.core.recipe.Shop;
import jp.minecraftcooks.core.score.Ranking;
import jp.minecraftcooks.paper.Messages;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.*;

import static jp.minecraftcooks.paper.Messages.comp;
import static jp.minecraftcooks.paper.Messages.text;

/** ボスバー(残り時間)と、全員共通のサイドバー(店・ラウンド・順位)。 */
final class Hud {
    private final MinecraftCooksPlugin plugin;
    private final MatchManager match;
    private final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
    private Scoreboard board;
    private Objective objective;
    private final List<Component> lastLines = new ArrayList<>();
    private boolean shown;

    Hud(MinecraftCooksPlugin plugin, MatchManager match) {
        this.plugin = plugin;
        this.match = match;
    }

    void show() {
        if (!shown) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            objective = board.registerNewObjective("minecraft-cooks", Criteria.DUMMY,
                    plugin.messages().get("hud.sidebar-title"));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            objective.numberFormat(NumberFormat.blank());
            lastLines.clear();
            shown = true;
        }
        for (Player p : Bukkit.getOnlinePlayers()) show(p);
    }

    void show(Player p) {
        if (!shown) return;
        p.showBossBar(bar);
        p.setScoreboard(board);
    }

    void hide() {
        if (!shown) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideBossBar(bar);
            if (p.getScoreboard() == board) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        shown = false;
        board = null;
        objective = null;
    }

    /** 毎秒呼ぶ。force なら、変わっていなくても書き直す。 */
    void update(boolean force) {
        if (!shown) return;
        Messages msg = plugin.messages();
        String shopName = match.currentShop().map(Shop::name).orElse("-");
        String time = mmss(match.ticksLeft());
        int round = match.roundIndex() + 1;
        int rounds = match.roundCount();
        float progress = match.phaseTicks() <= 0 ? 0f : Math.max(0f, Math.min(1f, match.ticksLeft() / (float) match.phaseTicks()));
        switch (match.state()) {
            case COUNTDOWN -> {
                bar.name(msg.get("hud.bossbar-countdown"));
                bar.color(BossBar.Color.YELLOW);
            }
            case PLAYING -> {
                bar.name(msg.get("hud.bossbar", text("shop", shopName), text("round", round), text("rounds", rounds),
                        text("time", time)));
                bar.color(match.ticksLeft() <= 600 ? BossBar.Color.RED : BossBar.Color.GREEN);
            }
            case PAUSED -> {
                bar.name(msg.get("hud.bossbar-paused", text("shop", shopName), text("time", time)));
                bar.color(BossBar.Color.WHITE);
            }
            case BREAK -> {
                bar.name(msg.get("hud.bossbar-break", text("time", time)));
                bar.color(BossBar.Color.BLUE);
            }
            default -> { }
        }
        bar.progress(progress);

        List<Component> lines = new ArrayList<>();
        lines.add(msg.get("hud.sidebar-shop", text("shop", shopName)));
        lines.add(msg.get("hud.sidebar-round", text("round", round), text("rounds", rounds)));
        lines.add(Component.empty());
        for (Ranking.Ranked r : match.liveRanking()) {
            lines.add(msg.get("hud.sidebar-line", text("place", r.place()), comp("team", match.teamName(r.r().teamId())),
                    text("score", r.r().total())));
        }
        if (force || !lines.equals(lastLines)) writeSidebar(lines);
    }

    private void writeSidebar(List<Component> lines) {
        if (objective == null) return;
        // 行数が減ったときのために、前の行を消してから書く
        for (int i = lines.size(); i < lastLines.size(); i++) board.resetScores("line" + i);
        for (int i = 0; i < lines.size(); i++) {
            Score s = objective.getScore("line" + i);
            s.setScore(lines.size() - i);
            s.customName(lines.get(i));
        }
        lastLines.clear();
        lastLines.addAll(lines);
    }

    private static String mmss(int ticks) {
        int sec = Math.max(0, (ticks + 19) / 20);
        return String.format("%d:%02d", sec / 60, sec % 60);
    }
}
