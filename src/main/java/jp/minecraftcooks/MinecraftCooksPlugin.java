package jp.minecraftcooks;

import jp.minecraftcooks.core.Settings;
import jp.minecraftcooks.core.recipe.RecipeBook;
import jp.minecraftcooks.core.recipe.RecipeException;
import jp.minecraftcooks.paper.Messages;
import jp.minecraftcooks.paper.command.CgCommand;
import jp.minecraftcooks.paper.item.CustomItems;
import jp.minecraftcooks.paper.kitchen.EquipmentEntities;
import jp.minecraftcooks.paper.kitchen.Kitchen;
import jp.minecraftcooks.paper.kitchen.KitchenRegistry;
import jp.minecraftcooks.paper.kitchen.WandListener;
import jp.minecraftcooks.paper.match.MatchManager;
import jp.minecraftcooks.paper.pack.ResourcePackSender;
import jp.minecraftcooks.paper.play.HandLockListener;
import jp.minecraftcooks.paper.play.InteractionListener;
import jp.minecraftcooks.paper.play.KitchenRuntime;
import jp.minecraftcooks.paper.play.PracticeSessions;
import jp.minecraftcooks.core.order.OrderGenerator;
import jp.minecraftcooks.core.recipe.Shop;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class MinecraftCooksPlugin extends JavaPlugin implements Listener {
    private Settings settings;
    private RecipeBook recipes;
    private Messages messages;
    private CustomItems items;
    private KitchenRegistry kitchens;
    private EquipmentEntities entities;
    private PracticeSessions practice;
    private MatchManager match;
    private jp.minecraftcooks.paper.gui.SettingsGui settingsGui;

    @Override
    public void onDisable() {
        if (match != null) match.shutdown();
        if (practice != null) practice.stopAll(getServer().getOnlinePlayers());
        if (entities != null) entities.despawnEverything();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        copyIfMissing("recipes.yml");
        copyIfMissing("messages.yml");
        try {
            recipes = loadRecipes();
        } catch (RecipeException e) {
            getLogger().severe("recipes.yml を読み込めません: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        messages = loadMessages();
        settings = loadSettings();
        items = new CustomItems(recipes);

        kitchens = new KitchenRegistry(new File(getDataFolder(), "kitchens.yml"), getLogger());
        kitchens.load();
        entities = new EquipmentEntities(items, kitchens);
        getServer().getPluginManager().registerEvents(entities, this);
        entities.spawnEverything();
        getServer().getPluginManager().registerEvents(
                new WandListener(items, kitchens, entities, this::messages, this::isKitchenBusy), this);

        practice = new PracticeSessions();
        match = new MatchManager(this);
        getServer().getPluginManager().registerEvents(match, this);
        getServer().getPluginManager().registerEvents(new InteractionListener(entities, items, this::runtimeFor), this);
        getServer().getPluginManager().registerEvents(
                new HandLockListener(p -> runtimeFor(p).isPresent(), s -> items.idOf(s).isPresent()), this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::tick, 1L, 1L);

        settingsGui = new jp.minecraftcooks.paper.gui.SettingsGui(this);
        getServer().getPluginManager().registerEvents(settingsGui, this);

        CgCommand cg = new CgCommand(this);
        var command = getCommand("cg");
        if (command != null) {
            command.setExecutor(cg);
            command.setTabCompleter(cg);
        }

        new ResourcePackSender(this,
                getConfig().getString("resource-pack.url", ""),
                getConfig().getString("resource-pack.sha1", ""),
                getConfig().getBoolean("resource-pack.required", true),
                getConfig().getString("resource-pack.prompt", "")).start();

        getLogger().info("Minecraft Cooks 有効化");
    }

    private void copyIfMissing(String name) {
        if (!new File(getDataFolder(), name).exists()) saveResource(name, false);
    }

    /** データフォルダの recipes.yml を読む。誤りがあれば RecipeException。 */
    public RecipeBook loadRecipes() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "recipes.yml"));
        return RecipeBook.parse(toPlainMap(y.getValues(false)));
    }

    /** データフォルダの messages.yml を読む。足りないキーは jar の中のものを使う。 */
    public Messages loadMessages() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
        InputStream in = getResource("messages.yml");
        if (in != null) {
            y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
        return new Messages(y);
    }

    private Settings loadSettings() {
        File f = new File(getDataFolder(), "settings.yml");
        if (!f.exists()) {
            Settings s = new Settings();
            saveSettings(s);
            return s;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        return Settings.fromMap(toPlainMap(y.getValues(false)));
    }

    public void saveSettings(Settings s) {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String, Object> e : s.toMap().entrySet()) y.set(e.getKey(), e.getValue());
        try {
            y.save(new File(getDataFolder(), "settings.yml"));
        } catch (IOException e) {
            getLogger().warning("settings.yml を保存できませんでした: " + e.getMessage());
        }
    }

    /** Bukkit の ConfigurationSection を、ただの Map と List に直す。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toPlainMap(Map<String, Object> values) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> e : values.entrySet()) out.put(e.getKey(), plain(e.getValue()));
        return out;
    }

    private static Object plain(Object o) {
        if (o instanceof org.bukkit.configuration.ConfigurationSection cs) return toPlainMap(cs.getValues(false));
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) out.put(String.valueOf(e.getKey()), plain(e.getValue()));
            return out;
        }
        if (o instanceof java.util.List<?> l) {
            java.util.List<Object> out = new java.util.ArrayList<>();
            for (Object x : l) out.add(plain(x));
            return out;
        }
        return o;
    }

    public Settings settings() { return settings; }
    public RecipeBook recipes() { return recipes; }
    public Messages messages() { return messages; }
    public CustomItems items() { return items; }
    public KitchenRegistry kitchens() { return kitchens; }
    public EquipmentEntities entities() { return entities; }

    /** 練習や試合で使っているキッチンか。使っている間は登録を変えられない。 */
    public boolean isKitchenBusy(String kitchenId) {
        return practice.isBusy(kitchenId) || match.isBusy(kitchenId);
    }

    /** キッチンの設備の中身を空にする。 */
    public void resetKitchen(Kitchen k) {
        // 試合中は設備の中身だけを空にする(得点と注文の順番は消さない)
        for (String t : match.roster().teamIds()) {
            java.util.Optional<KitchenRuntime> m = match.runtimeOfTeam(t).filter(r -> r.kitchen() == k);
            if (m.isPresent() && match.isRunning()) {
                m.get().clearEquipment();
                return;
            }
        }
        practice.runtimeOfKitchen(k.id()).ifPresentOrElse(KitchenRuntime::reset, () -> entities.spawnAll(k));
    }

    /** recipes.yml と messages.yml を読み直す。失敗したら前の内容のままにして、理由を返す(成功なら null)。 */
    public String reloadData() {
        RecipeBook next;
        try {
            next = loadRecipes();
        } catch (RecipeException e) {
            return e.getMessage();
        } catch (RuntimeException e) {
            return "recipes.yml: " + e.getMessage();
        }
        recipes = next;
        items.setBook(next);
        messages = loadMessages();
        return null;
    }

    private void tick() {
        practice.tick();
        match.tick();
    }

    /** そのプレイヤーが、いま遊んでいるキッチン(試合が優先、なければ練習)。 */
    public java.util.Optional<KitchenRuntime> runtimeFor(Player p) {
        return match.runtimeFor(p).or(() -> practice.runtimeFor(p));
    }

    public PracticeSessions practice() { return practice; }
    public MatchManager match() { return match; }
    public jp.minecraftcooks.paper.gui.SettingsGui settingsGui() { return settingsGui; }

    /** 1ラウンドぶんのキッチンの状態を作る。設定はこの時点の値を写して使う(途中で変えても次から効く)。 */
    public KitchenRuntime newRuntime(Kitchen k, Shop shop, long seed, java.util.function.Supplier<java.util.Collection<Player>> members) {
        Settings snap = Settings.fromMap(settings.toMap());
        return new KitchenRuntime(k, shop, snap, new OrderGenerator(seed, shop.dishIds(), snap), recipes, items,
                entities, this::messages, members);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        practice.stop(e.getPlayer());
    }
}
