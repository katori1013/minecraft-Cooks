package jp.minecraftcooks.paper.kitchen;

import jp.minecraftcooks.core.BlockPos;
import jp.minecraftcooks.core.EquipType;
import jp.minecraftcooks.core.kitchen.EquipmentDef;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** 運営が建てて登録したキッチン1つ。 */
public final class Kitchen {
    private final String id;
    private String displayName;
    private String worldName;
    private BlockPos corner1;
    private BlockPos corner2;
    private Location spawn;
    private final List<EquipmentDef> equipment = new ArrayList<>();

    public Kitchen(String id, String displayName, String worldName) {
        this.id = id;
        this.displayName = displayName;
        this.worldName = worldName;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public void setDisplayName(String n) { displayName = n; }
    public String worldName() { return worldName; }
    public void setWorldName(String w) { worldName = w; }
    public World world() { return worldName == null ? null : Bukkit.getWorld(worldName); }
    public BlockPos corner1() { return corner1; }
    public BlockPos corner2() { return corner2; }
    public void setCorner1(BlockPos p) { corner1 = p; }
    public void setCorner2(BlockPos p) { corner2 = p; }
    public boolean hasArea() { return corner1 != null && corner2 != null; }

    /** スポーン地点。ワールドが読み込まれていなければ empty。 */
    public Optional<Location> spawn() {
        if (spawn == null) return Optional.empty();
        World w = world();
        if (w == null) return Optional.empty();
        Location l = spawn.clone();
        l.setWorld(w);
        return Optional.of(l);
    }

    /** 保存用。ワールドは入っていない。 */
    public Location spawnRaw() { return spawn == null ? null : spawn.clone(); }

    public void setSpawn(Location l) { spawn = l == null ? null : l.clone(); }

    public List<EquipmentDef> equipment() { return List.copyOf(equipment); }

    public Optional<EquipmentDef> at(BlockPos pos) {
        return equipment.stream().filter(d -> d.pos().equals(pos)).findFirst();
    }

    /** 納品場所を番号順に。 */
    public List<EquipmentDef> counters() {
        return equipment.stream().filter(d -> d.type() == EquipType.COUNTER)
                .sorted(Comparator.comparingInt(EquipmentDef::slot)).toList();
    }

    void add(EquipmentDef d) { equipment.add(d); }
    boolean remove(BlockPos pos) { return equipment.removeIf(d -> d.pos().equals(pos)); }
    void clearEquipment() { equipment.clear(); }
}
