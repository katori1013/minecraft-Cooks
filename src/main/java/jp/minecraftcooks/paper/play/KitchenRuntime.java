package jp.minecraftcooks.paper.play;

import jp.minecraftcooks.core.BlockPos;
import jp.minecraftcooks.core.EquipType;
import jp.minecraftcooks.core.Settings;
import jp.minecraftcooks.core.cook.CookerState;
import jp.minecraftcooks.core.cook.CuttingBoardState;
import jp.minecraftcooks.core.cook.PlateState;
import jp.minecraftcooks.core.kitchen.EquipmentDef;
import jp.minecraftcooks.core.order.DeliverySlot;
import jp.minecraftcooks.core.order.OrderGenerator;
import jp.minecraftcooks.core.recipe.Dish;
import jp.minecraftcooks.core.recipe.RecipeBook;
import jp.minecraftcooks.core.recipe.Shop;
import jp.minecraftcooks.core.score.Scoring;
import jp.minecraftcooks.paper.Messages;
import jp.minecraftcooks.paper.item.CustomItems;
import jp.minecraftcooks.paper.kitchen.EquipmentEntities;
import jp.minecraftcooks.paper.kitchen.Kitchen;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Supplier;

import static jp.minecraftcooks.paper.Messages.comp;
import static jp.minecraftcooks.paper.Messages.text;

/** 1つのキッチンの、1ラウンドぶんの状態。active が false の間は、クリックにも tick にも反応しない。 */
public final class KitchenRuntime {
    private final Kitchen kitchen;
    private final Shop shop;
    private final Settings settings;
    private final OrderGenerator orders;
    private final RecipeBook book;
    private final CustomItems items;
    private final EquipmentEntities entities;
    private final Supplier<Messages> messages;
    private final Supplier<Collection<Player>> members;
    private final Scoring scoring;

    private final Map<BlockPos, CookerState> cookers = new HashMap<>();
    private final Map<BlockPos, CuttingBoardState> boards = new HashMap<>();
    private final Map<BlockPos, PlateState> plates = new HashMap<>();
    /** 納品場所の番号 → 枠。 */
    private final SortedMap<Integer, DeliverySlot> slots = new TreeMap<>();
    private final Map<UUID, int[]> pendingSubmit = new HashMap<>();

    private int nextK;
    private int roundScore;
    private int roundMistakes;
    private int submitted;
    private boolean active;
    private int tickCount;

    public KitchenRuntime(Kitchen kitchen, Shop shop, Settings settings, OrderGenerator orders, RecipeBook book,
                          CustomItems items, EquipmentEntities entities, Supplier<Messages> messages,
                          Supplier<Collection<Player>> members) {
        this.kitchen = kitchen;
        this.shop = shop;
        this.settings = settings;
        this.orders = orders;
        this.book = book;
        this.items = items;
        this.entities = entities;
        this.messages = messages;
        this.members = members;
        this.scoring = new Scoring(settings);
        reset();
    }

    public Kitchen kitchen() { return kitchen; }
    public Shop shop() { return shop; }
    public boolean isActive() { return active; }
    public void setActive(boolean a) { active = a; }
    public int roundScore() { return roundScore; }
    public int roundMistakes() { return roundMistakes; }
    public int submittedCount() { return submitted; }
    public int roundPenalty() { return scoring.roundPenalty(roundScore, roundMistakes); }

    /** 全設備を空にして、注文を k=0 から出し直す。 */
    public void reset() {
        cookers.clear();
        boards.clear();
        plates.clear();
        slots.clear();
        pendingSubmit.clear();
        for (EquipmentDef d : kitchen.equipment()) {
            if (d.type().isCooker()) cookers.put(d.pos(), new CookerState(d.type()));
            else if (d.type() == EquipType.CUTTING_BOARD) boards.put(d.pos(), new CuttingBoardState());
            else if (d.type() == EquipType.PLATE) plates.put(d.pos(), new PlateState());
        }
        nextK = 0;
        for (EquipmentDef c : kitchen.counters()) {
            if (c.slot() >= 1 && c.slot() <= settings.getOrderSlots() && !slots.containsKey(c.slot())) {
                slots.put(c.slot(), new DeliverySlot(orders.orderAt(nextK++)));
            }
        }
        roundScore = 0;
        roundMistakes = 0;
        submitted = 0;
        refreshAll();
    }

    /** 設備の中身だけを空にする。得点・注文・納品場所の中身はそのまま(試合中の kitchen reset 用)。 */
    public void clearEquipment() {
        for (CookerState c : cookers.values()) c.reset();
        for (CuttingBoardState b : boards.values()) b.reset();
        for (PlateState p : plates.values()) p.reset();
        refreshAll();
    }

    /** 設備の表示を消す(試合の後など)。 */
    public void clearDisplays() {
        for (EquipmentDef d : kitchen.equipment()) {
            entities.setLabel(kitchen, d, Component.empty());
            entities.setContents(kitchen, d, null);
        }
    }

    public void tick() {
        if (!active) return;
        for (Map.Entry<BlockPos, CookerState> e : cookers.entrySet()) {
            CookerState c = e.getValue();
            CookerState.Phase before = c.phase();
            c.tick(shop, settings.cookTime(c.type()), book.ruinedId(), book.burntId());
            if (c.phase() != before) refreshAt(e.getKey());
        }
        if (++tickCount % 4 == 0) {
            for (BlockPos p : cookers.keySet()) refreshAt(p);
        }
    }

    // ---------- 手 ----------

    private Optional<String> hand(Player p) {
        return items.idOf(p.getInventory().getItem(0));
    }

    private boolean handEmpty(Player p) {
        ItemStack s = p.getInventory().getItem(0);
        return s == null || s.getType().isAir();
    }

    /** 失敗した料理と焦げは、ゴミ箱にしか入らない。 */
    private boolean isFailure(String id) {
        return id.equals(book.ruinedId()) || id.equals(book.burntId());
    }

    private void setHand(Player p, String id) {
        p.getInventory().setItem(0, id == null ? null : items.create(id));
    }

    private void bar(Player p, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... args) {
        p.sendActionBar(messages.get().get(key, args));
    }

    // ---------- クリック ----------

    public void rightClick(Player p, EquipmentDef d, boolean sneaking) {
        if (!active) return;
        EquipType t = d.type();
        if (t.isBox()) {
            if (!handEmpty(p)) {
                bar(p, "play.hand-full");
                return;
            }
            setHand(p, book.rawItemFor(t));
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
        } else if (t == EquipType.CUTTING_BOARD) {
            board(p, d, sneaking);
        } else if (t.isCooker()) {
            cooker(p, d);
        } else if (t == EquipType.PLATE) {
            plateRight(p, d);
        } else if (t == EquipType.COUNTER) {
            counterRight(p, d, sneaking);
        } else if (t == EquipType.TRASH) {
            Optional<String> h = hand(p);
            if (handEmpty(p)) {
                bar(p, "play.hand-empty");
                return;
            }
            setHand(p, null);
            bar(p, "play.trashed", text("item", h.map(book::nameOf).orElse("?")));
            p.playSound(p.getLocation(), Sound.BLOCK_COMPOSTER_FILL, 0.6f, 1f);
        }
        refreshAt(d.pos());
    }

    public void leftClick(Player p, EquipmentDef d) {
        if (!active) return;
        if (d.type() == EquipType.PLATE) {
            PlateState plate = plates.get(d.pos());
            if (plate == null) return;
            if (!handEmpty(p)) {
                bar(p, "play.hand-full");
                return;
            }
            plate.undo().ifPresent(part -> setHand(p, part));
        } else if (d.type() == EquipType.COUNTER) {
            counterLeft(p, d);
        }
        refreshAt(d.pos());
    }

    private void board(Player p, EquipmentDef d, boolean sneaking) {
        CuttingBoardState b = boards.get(d.pos());
        if (b == null) return;
        if (!handEmpty(p)) {
            Optional<String> h = hand(p);
            if (b.item().isPresent()) {
                bar(p, "play.board-busy");
            } else if (h.isPresent() && shop.accepts(EquipType.CUTTING_BOARD, h.get())) {
                b.place(h.get());
                setHand(p, null);
            } else {
                bar(p, "play.cannot-place");
            }
            return;
        }
        if (b.item().isEmpty()) return;
        if (!sneaking && b.canChop(shop)) {
            Optional<String> done = b.chop(shop, settings.getChopCount());
            p.playSound(p.getLocation(), done.isPresent() ? Sound.BLOCK_WOOD_BREAK : Sound.BLOCK_WOOD_HIT, 0.7f, 1.2f);
            return;
        }
        b.take().ifPresent(it -> setHand(p, it));
    }

    private void cooker(Player p, EquipmentDef d) {
        CookerState c = cookers.get(d.pos());
        if (c == null) return;
        if (!handEmpty(p)) {
            Optional<String> h = hand(p);
            if (h.isEmpty() || isFailure(h.get())) {
                bar(p, "play.cannot-place");
                return;
            }
            CookerState.Phase ph = c.phase();
            if (ph == CookerState.Phase.DONE || ph == CookerState.Phase.RUINED || ph == CookerState.Phase.BURNT) {
                bar(p, "play.cooker-done");
            } else if (c.add(h.get(), settings.getMaxCookerItems())) {
                setHand(p, null);
                p.playSound(p.getLocation(), Sound.BLOCK_LANTERN_PLACE, 0.6f, 1f);
            } else {
                bar(p, "play.cooker-full");
            }
            return;
        }
        c.takeOut().ifPresent(it -> setHand(p, it));
    }

    private void plateRight(Player p, EquipmentDef d) {
        PlateState plate = plates.get(d.pos());
        if (plate == null) return;
        if (handEmpty(p)) {
            if (plate.parts().isEmpty()) {
                bar(p, "play.plate-empty");
            } else {
                messages.get().send(p, "play.plate-contents", text("list", names(plate.parts())));
            }
            return;
        }
        Optional<String> h = hand(p);
        if (h.isEmpty() || isFailure(h.get())) {
            bar(p, "play.cannot-place");
            return;
        }
        if (!plate.add(h.get())) {
            bar(p, "play.plate-full");
            return;
        }
        setHand(p, null);
        Optional<Dish> dish = plate.tryComplete(shop);
        if (dish.isPresent()) {
            setHand(p, dish.get().item());
            bar(p, "play.plated", text("dish", dish.get().name()));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
        }
    }

    private void counterRight(Player p, EquipmentDef d, boolean sneaking) {
        DeliverySlot slot = slots.get(d.slot());
        if (slot == null) return;
        pendingSubmit.remove(p.getUniqueId());
        if (handEmpty(p)) {
            if (sneaking) slot.undoLast().flatMap(shop::dish).ifPresent(dish -> setHand(p, dish.item()));
            return;
        }
        Optional<Dish> dish = hand(p).flatMap(shop::dishByItem);
        if (dish.isEmpty()) {
            bar(p, "play.not-a-dish");
            return;
        }
        slot.add(dish.get().id());
        setHand(p, null);
        p.playSound(p.getLocation(), Sound.BLOCK_WOOD_PLACE, 0.7f, 1.3f);
    }

    private void counterLeft(Player p, EquipmentDef d) {
        DeliverySlot slot = slots.get(d.slot());
        if (slot == null) return;
        if (slot.isEmpty()) {
            bar(p, "play.counter-empty");
            return;
        }
        int now = Bukkit.getCurrentTick();
        int[] pend = pendingSubmit.get(p.getUniqueId());
        if (pend != null && pend[0] == d.slot() && now - pend[1] <= settings.getSubmitConfirmTicks()) {
            pendingSubmit.remove(p.getUniqueId());
            submit(slot);
        } else {
            pendingSubmit.put(p.getUniqueId(), new int[]{d.slot(), now});
            bar(p, "play.submit-confirm", text("k", slot.order().k() + 1));
        }
    }

    private void submit(DeliverySlot slot) {
        Scoring.Evaluation ev = scoring.evaluate(slot.order().dishIds(), slot.delivered());
        int shownK = slot.order().k() + 1;
        roundScore += ev.score();
        roundMistakes += ev.mistakes();
        submitted++;
        slot.next(orders.orderAt(nextK++));
        // ほかの人が送信待ちにしていた枠は、注文が変わったので取り消す
        pendingSubmit.clear();
        for (Player m : members.get()) {
            m.sendActionBar(messages.get().get("play.submitted", text("k", shownK),
                    text("score", ev.score()), text("mistakes", ev.mistakes())));
            m.playSound(m.getLocation(), ev.mistakes() == 0 ? Sound.ENTITY_PLAYER_LEVELUP : Sound.BLOCK_NOTE_BLOCK_BASS,
                    0.6f, 1.2f);
        }
    }

    private String names(List<String> ids) {
        StringJoiner j = new StringJoiner("、");
        for (String id : ids) j.add(book.nameOf(id));
        return j.toString();
    }

    // ---------- 表示 ----------

    public void refreshAll() {
        for (EquipmentDef d : kitchen.equipment()) refresh(d);
    }

    private void refreshAt(BlockPos pos) {
        kitchen.at(pos).ifPresent(this::refresh);
    }

    private void refresh(EquipmentDef d) {
        Messages msg = messages.get();
        Component label = Component.empty();
        String contents = null;
        if (d.type().isCooker()) {
            CookerState c = cookers.get(d.pos());
            if (c != null) {
                switch (c.phase()) {
                    case COOKING -> {
                        label = progressBar(c.progress(), NamedTextColor.YELLOW);
                        List<String> cs = c.contents();
                        contents = cs.isEmpty() ? null : cs.get(cs.size() - 1);
                    }
                    case DONE -> {
                        label = c.burnProgress() >= 0.5
                                ? msg.get("label.burning").appendNewline().append(progressBar(c.burnProgress(), NamedTextColor.RED))
                                : msg.get("label.done");
                        contents = c.result().orElse(null);
                    }
                    case RUINED -> {
                        label = msg.get("label.ruined");
                        contents = c.result().orElse(null);
                    }
                    case BURNT -> {
                        label = msg.get("label.burnt");
                        contents = c.result().orElse(null);
                    }
                    default -> { }
                }
            }
        } else if (d.type() == EquipType.CUTTING_BOARD) {
            CuttingBoardState b = boards.get(d.pos());
            if (b != null && b.item().isPresent()) {
                contents = b.item().get();
                if (b.progress() > 0) label = progressBar(b.progress(), NamedTextColor.AQUA);
                else if (b.isDone()) label = msg.get("label.done");
            }
        } else if (d.type() == EquipType.PLATE) {
            PlateState pl = plates.get(d.pos());
            if (pl != null && !pl.parts().isEmpty()) {
                List<String> ps = pl.parts();
                contents = ps.get(ps.size() - 1);
                label = Component.text(names(ps) + " (" + ps.size() + "/" + PlateState.MAX_PARTS + ")", NamedTextColor.WHITE);
            }
        } else if (d.type() == EquipType.COUNTER) {
            label = counterLabel(d);
        }
        entities.setLabel(kitchen, d, label);
        entities.setContents(kitchen, d, contents);
    }

    private Component counterLabel(EquipmentDef d) {
        Messages msg = messages.get();
        DeliverySlot slot = slots.get(d.slot());
        if (slot == null) return msg.get("label.counter-slot", text("slot", d.slot()));
        Component out = msg.get("label.order-title", text("k", slot.order().k() + 1));
        Map<String, Integer> need = new LinkedHashMap<>();
        for (String id : slot.order().dishIds()) need.merge(id, 1, Integer::sum);
        Map<String, Integer> have = new LinkedHashMap<>();
        for (String id : slot.delivered()) have.merge(id, 1, Integer::sum);
        for (Map.Entry<String, Integer> e : need.entrySet()) {
            int h = have.getOrDefault(e.getKey(), 0);
            String countKey = h == e.getValue() ? "label.count-ok" : h < e.getValue() ? "label.count-short" : "label.count-over";
            Component count = msg.get(countKey, text("have", h), text("need", e.getValue()));
            out = out.appendNewline().append(msg.get("label.order-line", text("dish", dishName(e.getKey())), comp("count", count)));
        }
        for (Map.Entry<String, Integer> e : have.entrySet()) {
            if (need.containsKey(e.getKey())) continue;
            out = out.appendNewline().append(msg.get("label.order-extra", text("dish", dishName(e.getKey())),
                    text("count", e.getValue() + "/0")));
        }
        return out;
    }

    private String dishName(String dishId) {
        return shop.dish(dishId).map(Dish::name).orElse(dishId);
    }

    private static Component progressBar(double ratio, NamedTextColor color) {
        int cells = 10;
        int filled = (int) Math.round(Math.max(0, Math.min(1, ratio)) * cells);
        return Component.text("■".repeat(filled), color)
                .append(Component.text("□".repeat(cells - filled), NamedTextColor.DARK_GRAY));
    }

    /** 納品場所ごとの注文(表示とテスト用)。 */
    public SortedMap<Integer, DeliverySlot> slots() {
        return Collections.unmodifiableSortedMap(slots);
    }
}
