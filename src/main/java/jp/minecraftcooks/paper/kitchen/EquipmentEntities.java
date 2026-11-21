package jp.minecraftcooks.paper.kitchen;

import jp.minecraftcooks.core.BlockPos;
import jp.minecraftcooks.core.EquipType;
import jp.minecraftcooks.core.kitchen.EquipmentDef;
import jp.minecraftcooks.paper.item.CustomItems;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

/**
 * 設備1つを、バリアブロック + 見た目(ItemDisplay) + クリック判定(Interaction)
 * + 文字(TextDisplay) + 中身の表示(小さい ItemDisplay)で作る。
 */
public final class EquipmentEntities implements Listener {
    public static final String TAG = "minecraft-cooks_equip";
    public static final NamespacedKey EQUIP_KEY = new NamespacedKey(CustomItems.NAMESPACE, "equip");

    private record Parts(UUID model, UUID interaction, UUID label, UUID contents) {
        List<UUID> all() { return List.of(model, interaction, label, contents); }
    }

    private final CustomItems items;
    private final KitchenRegistry registry;
    private final Map<String, Parts> parts = new HashMap<>();
    private final Set<UUID> known = new HashSet<>();

    public EquipmentEntities(CustomItems items, KitchenRegistry registry) {
        this.items = items;
        this.registry = registry;
    }

    private static String key(Kitchen k, BlockPos pos) {
        return k.id() + "|" + pos;
    }

    /** 起動時: 設備のあるチャンクを読み込み、残っている古いエンティティを消してから作り直す。 */
    public void spawnEverything() {
        for (Kitchen k : registry.all()) {
            World w = k.world();
            if (w == null) continue;
            for (EquipmentDef d : k.equipment()) {
                Chunk c = w.getChunkAt(d.pos().x() >> 4, d.pos().z() >> 4);
                removeOrphans(c.getEntities());
            }
            spawnAll(k);
        }
    }

    public void spawnAll(Kitchen k) {
        for (EquipmentDef d : k.equipment()) spawn(k, d);
    }

    public void removeAll(Kitchen k) {
        for (EquipmentDef d : k.equipment()) remove(k, d, true);
    }

    /** サーバー終了時。ブロックはそのままにして、エンティティだけ消す。 */
    public void despawnEverything() {
        for (Kitchen k : registry.all()) {
            for (EquipmentDef d : k.equipment()) remove(k, d, false);
        }
    }

    public void spawn(Kitchen k, EquipmentDef d) {
        World w = k.world();
        if (w == null) return;
        remove(k, d, false);
        BlockPos p = d.pos();
        w.getBlockAt(p.x(), p.y(), p.z()).setType(Material.BARRIER, false);
        Location center = new Location(w, p.x() + 0.5, p.y() + 0.5, p.z() + 0.5);

        String modelId = "equip_" + d.type().key();
        ItemDisplay model = w.spawn(center, ItemDisplay.class, e -> {
            tag(e);
            e.setItemStack(items.create(modelId));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        });

        String pdc = k.id() + "|" + p;
        Interaction hit = w.spawn(new Location(w, p.x() + 0.5, p.y() - 0.005, p.z() + 0.5), Interaction.class, e -> {
            tag(e);
            e.setInteractionWidth(1.01f);
            e.setInteractionHeight(1.01f);
            e.setResponsive(true);
            e.getPersistentDataContainer().set(EQUIP_KEY, PersistentDataType.STRING, pdc);
        });

        double labelY = d.type() == EquipType.COUNTER ? 1.45 : 1.35;
        TextDisplay label = w.spawn(new Location(w, p.x() + 0.5, p.y() + labelY, p.z() + 0.5), TextDisplay.class, e -> {
            tag(e);
            e.setBillboard(Display.Billboard.CENTER);
            e.setAlignment(TextDisplay.TextAlignment.CENTER);
            e.setLineWidth(200);
            e.text(Component.empty());
        });

        ItemDisplay contents = w.spawn(new Location(w, p.x() + 0.5, p.y() + 1.2, p.z() + 0.5), ItemDisplay.class, e -> {
            tag(e);
            e.setBillboard(Display.Billboard.VERTICAL);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(0.4f, 0.4f, 0.4f), new AxisAngle4f()));
        });

        Parts ps = new Parts(model.getUniqueId(), hit.getUniqueId(), label.getUniqueId(), contents.getUniqueId());
        parts.put(key(k, p), ps);
        known.addAll(ps.all());
    }

    /** エンティティを消す。clearBlock が true なら、バリアブロックも空気に戻す。 */
    public void remove(Kitchen k, EquipmentDef d, boolean clearBlock) {
        Parts ps = parts.remove(key(k, d.pos()));
        if (ps != null) {
            for (UUID id : ps.all()) {
                known.remove(id);
                Entity e = Bukkit.getEntity(id);
                if (e != null) e.remove();
            }
        }
        if (clearBlock) {
            World w = k.world();
            if (w != null) {
                BlockPos p = d.pos();
                if (w.getBlockAt(p.x(), p.y(), p.z()).getType() == Material.BARRIER) {
                    w.getBlockAt(p.x(), p.y(), p.z()).setType(Material.AIR, false);
                }
            }
        }
    }

    private static void tag(Entity e) {
        e.addScoreboardTag(TAG);
        e.setPersistent(true);
        e.setSilent(true);
        e.setInvulnerable(true);
    }

    private void removeOrphans(Entity[] entities) {
        for (Entity e : entities) {
            if (e.getScoreboardTags().contains(TAG) && !known.contains(e.getUniqueId())) e.remove();
        }
    }

    /** チャンクが読み込まれたとき、作った覚えのない設備エンティティ(前回の残り)を消す。 */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        removeOrphans(e.getEntities().toArray(new Entity[0]));
    }

    /** クリック判定のエンティティから、どのキッチンのどの設備かを調べる。 */
    public Optional<KitchenRegistry.Located> lookup(Entity entity) {
        if (!(entity instanceof Interaction)) return Optional.empty();
        String raw = entity.getPersistentDataContainer().get(EQUIP_KEY, PersistentDataType.STRING);
        if (raw == null) return Optional.empty();
        int bar = raw.indexOf('|');
        if (bar < 0) return Optional.empty();
        BlockPos pos;
        try {
            pos = BlockPos.parse(raw.substring(bar + 1));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        return registry.get(raw.substring(0, bar))
                .flatMap(k -> k.at(pos).map(d -> new KitchenRegistry.Located(k, d)));
    }

    public Optional<TextDisplay> label(Kitchen k, EquipmentDef d) {
        Parts ps = parts.get(key(k, d.pos()));
        return ps == null ? Optional.empty() : Optional.ofNullable(Bukkit.getEntity(ps.label()))
                .filter(TextDisplay.class::isInstance).map(TextDisplay.class::cast);
    }

    public Optional<ItemDisplay> contentsDisplay(Kitchen k, EquipmentDef d) {
        Parts ps = parts.get(key(k, d.pos()));
        return ps == null ? Optional.empty() : Optional.ofNullable(Bukkit.getEntity(ps.contents()))
                .filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast);
    }

    public void setLabel(Kitchen k, EquipmentDef d, Component text) {
        label(k, d).ifPresent(t -> {
            if (!text.equals(t.text())) t.text(text);
        });
    }

    /** 設備の上に浮かべる中身。null なら消す。 */
    public void setContents(Kitchen k, EquipmentDef d, String itemId) {
        contentsDisplay(k, d).ifPresent(e -> {
            ItemStack cur = e.getItemStack();
            String curId = items.idOf(cur).orElse(null);
            if (Objects.equals(curId, itemId)) return;
            e.setItemStack(itemId == null ? null : items.create(itemId));
        });
    }
}
