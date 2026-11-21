package jp.minecraftcooks.paper.kitchen;

import jp.minecraftcooks.core.BlockPos;
import jp.minecraftcooks.core.EquipType;
import jp.minecraftcooks.core.kitchen.EquipmentDef;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/** キッチンの一覧。変えるたびに kitchens.yml に保存する。 */
public final class KitchenRegistry {
    public record Located(Kitchen kitchen, EquipmentDef def) {}

    private final File file;
    private final Logger log;
    private final Map<String, Kitchen> kitchens = new LinkedHashMap<>();

    public KitchenRegistry(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    public void load() {
        kitchens.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("kitchens");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Kitchen k = new Kitchen(id, s.getString("name", id), s.getString("world"));
            if (s.isString("corner1")) k.setCorner1(BlockPos.parse(s.getString("corner1")));
            if (s.isString("corner2")) k.setCorner2(BlockPos.parse(s.getString("corner2")));
            ConfigurationSection sp = s.getConfigurationSection("spawn");
            if (sp != null) {
                k.setSpawn(new Location(null, sp.getDouble("x"), sp.getDouble("y"), sp.getDouble("z"),
                        (float) sp.getDouble("yaw"), (float) sp.getDouble("pitch")));
            }
            for (Map<?, ?> m : s.getMapList("equipment")) {
                Optional<EquipType> t = EquipType.byKey(String.valueOf(m.get("type")));
                if (t.isEmpty()) {
                    log.warning("kitchens.yml: " + id + " の設備の種類 '" + m.get("type") + "' が読めないので飛ばします");
                    continue;
                }
                int slot = m.get("slot") instanceof Number n ? n.intValue() : 0;
                k.add(new EquipmentDef(t.get(), slot, BlockPos.parse(String.valueOf(m.get("pos")))));
            }
            kitchens.put(id, k);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Kitchen k : kitchens.values()) {
            String b = "kitchens." + k.id() + ".";
            y.set(b + "name", k.displayName());
            y.set(b + "world", k.worldName());
            if (k.corner1() != null) y.set(b + "corner1", k.corner1().toString());
            if (k.corner2() != null) y.set(b + "corner2", k.corner2().toString());
            Optional.ofNullable(k.spawnRaw()).ifPresent(l -> {
                y.set(b + "spawn.x", l.getX());
                y.set(b + "spawn.y", l.getY());
                y.set(b + "spawn.z", l.getZ());
                y.set(b + "spawn.yaw", (double) l.getYaw());
                y.set(b + "spawn.pitch", (double) l.getPitch());
            });
            List<Map<String, Object>> eq = new ArrayList<>();
            for (EquipmentDef d : k.equipment()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", d.type().key());
                m.put("slot", d.slot());
                m.put("pos", d.pos().toString());
                eq.add(m);
            }
            y.set(b + "equipment", eq);
        }
        try {
            y.save(file);
        } catch (IOException e) {
            log.warning("kitchens.yml を保存できませんでした: " + e.getMessage());
        }
    }

    public Kitchen create(String id, String name, World world) {
        if (kitchens.containsKey(id)) throw new IllegalArgumentException("すでにあるキッチン: " + id);
        Kitchen k = new Kitchen(id, name, world.getName());
        kitchens.put(id, k);
        save();
        return k;
    }

    public void remove(String id) {
        kitchens.remove(id);
        save();
    }

    public Optional<Kitchen> get(String id) {
        return Optional.ofNullable(kitchens.get(id));
    }

    public Collection<Kitchen> all() {
        return Collections.unmodifiableCollection(kitchens.values());
    }

    public Optional<Located> at(World world, BlockPos pos) {
        for (Kitchen k : kitchens.values()) {
            if (!world.getName().equals(k.worldName())) continue;
            Optional<EquipmentDef> d = k.at(pos);
            if (d.isPresent()) return Optional.of(new Located(k, d.get()));
        }
        return Optional.empty();
    }

    public void addEquipment(Kitchen k, EquipmentDef def) {
        k.add(def);
        save();
    }

    public boolean removeEquipment(Kitchen k, BlockPos pos) {
        boolean r = k.remove(pos);
        if (r) save();
        return r;
    }

    public void clearEquipment(Kitchen k) {
        k.clearEquipment();
        save();
    }
}
