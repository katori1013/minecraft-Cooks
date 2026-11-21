package jp.minecraftcooks.paper.gui;

import jp.minecraftcooks.MinecraftCooksPlugin;
import jp.minecraftcooks.core.EquipType;
import jp.minecraftcooks.core.Settings;
import jp.minecraftcooks.core.score.TieBreak;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * /cg settings のチェストメニュー。左クリックで+、右クリックで−、シフトを押しながらだと10倍。
 * 変えるたびに settings.yml に保存する。
 */
public final class SettingsGui implements Listener {

    /** 項目1つ。change には、押した向き(+1/−1)× 倍率(1か10)が渡る。 */
    private record Entry(Material icon, String name, String step, Supplier<String> value, IntConsumer change) {}

    /** このメニューかどうかを見分けるための持ち主。 */
    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, Entry> entries = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final MinecraftCooksPlugin plugin;

    public SettingsGui(MinecraftCooksPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        Holder h = new Holder();
        h.inventory = Bukkit.createInventory(h, 54, plugin.messages().get("settings.title"));
        fill(h);
        p.openInventory(h.inventory);
    }

    private Map<Integer, Entry> entries() {
        Settings s = plugin.settings();
        Map<Integer, Entry> m = new LinkedHashMap<>();
        // 1段目: 試合の時間と注文
        m.put(0, new Entry(Material.CLOCK, "ラウンドの制限時間", "10秒", () -> s.getRoundSeconds() + "秒",
                d -> s.setRoundSeconds(clamp(s.getRoundSeconds() + d * 10, 10, 3600))));
        m.put(1, new Entry(Material.CLOCK, "休憩", "5秒", () -> s.getBreakSeconds() + "秒",
                d -> s.setBreakSeconds(clamp(s.getBreakSeconds() + d * 5, 0, 600))));
        m.put(2, new Entry(Material.CLOCK, "カウントダウン", "1秒", () -> s.getCountdownSeconds() + "秒",
                d -> s.setCountdownSeconds(clamp(s.getCountdownSeconds() + d, 0, 60))));
        m.put(3, new Entry(Material.PAPER, "注文の件数(納品場所の数)", "1", () -> s.getOrderSlots() + "件",
                d -> s.setOrderSlots(clamp(s.getOrderSlots() + d, 1, 9))));
        m.put(4, new Entry(Material.SHEARS, "切る回数", "1回", () -> s.getChopCount() + "回",
                d -> s.setChopCount(clamp(s.getChopCount() + d, 1, 50))));
        m.put(5, new Entry(Material.CAULDRON, "調理器具に入る数", "1個", () -> s.getMaxCookerItems() + "個",
                d -> s.setMaxCookerItems(clamp(s.getMaxCookerItems() + d, 1, 9))));
        m.put(6, new Entry(Material.GOLD_NUGGET, "料理1個あたりの点", "10点", () -> s.getPointsPerDish() + "点",
                d -> s.setPointsPerDish(clamp(s.getPointsPerDish() + d * 10, 0, 100000))));
        m.put(7, new Entry(Material.REDSTONE, "減点の割合(間違い1つあたり)", "0.05%",
                () -> String.format("%.2f%%", s.getPenaltyRate() * 100),
                d -> s.setPenaltyRate(Math.max(0, Math.round((s.getPenaltyRate() + d * 0.0005) * 10000) / 10000.0))));
        m.put(8, new Entry(Material.COMPARATOR, "同点のときの決め方", "クリックで切り替え", () -> tieBreakName(s.getTieBreak()),
                d -> {
                    TieBreak[] v = TieBreak.values();
                    s.setTieBreak(v[Math.floorMod(s.getTieBreak().ordinal() + Integer.signum(d), v.length)]);
                }));
        m.put(9, new Entry(Material.LEVER, "送信の確認の待ち時間", "0.5秒",
                () -> String.format("%.1f秒", s.getSubmitConfirmTicks() / 20.0),
                d -> s.setSubmitConfirmTicks(clamp(s.getSubmitConfirmTicks() + d * 10, 10, 400))));

        // 3段目: 調理の時間(設備ごとに、完成までと焦げるまで)
        int slot = 18;
        for (EquipType t : EquipType.values()) {
            if (!t.isCooker()) continue;
            String label = equipName(t);
            m.put(slot++, new Entry(Material.FURNACE, label + ": 完成まで", "1秒",
                    () -> secs(s.cookTime(t).cookTicks()),
                    d -> s.setCookTime(t, new Settings.CookTime(clamp(s.cookTime(t).cookTicks() + d * 20, 20, 12000),
                            s.cookTime(t).burnTicks()))));
            m.put(slot++, new Entry(Material.MAGMA_BLOCK, label + ": 焦げるまで", "1秒",
                    () -> secs(s.cookTime(t).burnTicks()),
                    d -> s.setCookTime(t, new Settings.CookTime(s.cookTime(t).cookTicks(),
                            clamp(s.cookTime(t).burnTicks() + d * 20, 20, 12000)))));
        }

        // 5段目: 難しさの段階(k の始まり、最少、最多)
        slot = 36;
        List<Settings.Tier> tiers = s.getTiers();
        for (int i = 0; i < tiers.size() && i < 3; i++) {
            final int idx = i;
            String label = "段階" + (i + 1);
            m.put(slot++, new Entry(Material.LADDER, label + ": 何件目から(k)", "1",
                    () -> "k=" + s.getTiers().get(idx).fromK(),
                    d -> setTier(s, idx, t -> new Settings.Tier(clamp(t.fromK() + d, 0, 10000), t.minDishes(), t.maxDishes()))));
            m.put(slot++, new Entry(Material.BOWL, label + ": 料理の最少", "1",
                    () -> s.getTiers().get(idx).minDishes() + "個",
                    d -> setTier(s, idx, t -> {
                        int min = clamp(t.minDishes() + d, 1, 20);
                        return new Settings.Tier(t.fromK(), min, Math.max(min, t.maxDishes()));
                    })));
            m.put(slot++, new Entry(Material.BOWL, label + ": 料理の最多", "1",
                    () -> s.getTiers().get(idx).maxDishes() + "個",
                    d -> setTier(s, idx, t -> {
                        int max = clamp(t.maxDishes() + d, 1, 20);
                        return new Settings.Tier(t.fromK(), Math.min(max, t.minDishes()), max);
                    })));
        }

        // 6段目: 料理の個数ごとの倍率
        slot = 45;
        for (int i = 0; i < s.getMultipliers().size() && i < 9; i++) {
            final int idx = i;
            m.put(slot++, new Entry(Material.EXPERIENCE_BOTTLE, "倍率: 料理" + (i + 1) + "個", "0.05",
                    () -> String.format("×%.2f", s.getMultipliers().get(idx)),
                    d -> {
                        List<Double> ms = new ArrayList<>(s.getMultipliers());
                        ms.set(idx, Math.max(0, Math.round((ms.get(idx) + d * 0.05) * 100) / 100.0));
                        s.setMultipliers(ms);
                    }));
        }
        return m;
    }

    private static void setTier(Settings s, int idx, java.util.function.UnaryOperator<Settings.Tier> f) {
        List<Settings.Tier> ts = new ArrayList<>(s.getTiers());
        ts.set(idx, f.apply(ts.get(idx)));
        s.setTiers(ts);
    }

    private void fill(Holder h) {
        h.entries.clear();
        h.inventory.clear();
        for (Map.Entry<Integer, Entry> e : entries().entrySet()) {
            h.entries.put(e.getKey(), e.getValue());
            h.inventory.setItem(e.getKey(), icon(e.getValue()));
        }
    }

    private static ItemStack icon(Entry e) {
        ItemStack it = new ItemStack(e.icon());
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(e.name() + ": ", NamedTextColor.WHITE)
                .append(Component.text(e.value().get(), NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                line("左クリック: +" + e.step()),
                line("右クリック: −" + e.step()),
                line("シフトを押しながら: 10倍")));
        it.setItemMeta(meta);
        return it;
    }

    private static Component line(String s) {
        return Component.text(s, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || !p.hasPermission("minecraft-cooks.admin")) return;
        if (e.getClickedInventory() != h.inventory) return;
        Entry entry = h.entries.get(e.getRawSlot());
        if (entry == null) return;
        int dir = e.isRightClick() ? -1 : e.isLeftClick() ? 1 : 0;
        if (dir == 0) return;
        int mult = e.isShiftClick() ? 10 : 1;
        entry.change().accept(dir * mult);
        plugin.saveSettings(plugin.settings());
        fill(h);
        plugin.messages().send(p, "settings.saved");
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder(false) instanceof Holder) e.setCancelled(true);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String secs(int ticks) {
        return (ticks % 20 == 0) ? (ticks / 20) + "秒" : String.format("%.2f秒", ticks / 20.0);
    }

    private static String tieBreakName(TieBreak t) {
        return switch (t) {
            case LAST_ROUND -> "最後のラウンドの得点";
            case FEWER_MISTAKES -> "間違いが少ないほう";
            case SHARE -> "同じ順位のまま";
        };
    }

    private static String equipName(EquipType t) {
        return switch (t) {
            case PAN -> "フライパン";
            case POT_BOIL -> "茹で鍋";
            case POT_STEAM -> "蒸し鍋";
            case POT_STEW -> "煮込み鍋";
            case POT_SET -> "固め鍋";
            case FRYER -> "フライヤー";
            default -> t.key();
        };
    }
}
