package jp.minecraftcooks.core.recipe;

import jp.minecraftcooks.core.EquipType;

import java.util.List;
import java.util.Optional;

/** 料理店。工程と料理は店ごとに決まる。 */
public final class Shop {
    private final String id;
    private final String name;
    private final List<Process> processes;
    private final List<Dish> dishes;

    public Shop(String id, String name, List<Process> processes, List<Dish> dishes) {
        this.id = id;
        this.name = name;
        this.processes = List.copyOf(processes);
        this.dishes = List.copyOf(dishes);
    }

    public String id() { return id; }
    public String name() { return name; }
    public List<Process> processes() { return processes; }
    public List<Dish> dishes() { return dishes; }

    public List<String> dishIds() {
        return dishes.stream().map(Dish::id).toList();
    }

    /** 中身の種類と個数が inputs とちょうど一致する工程の output。 */
    public Optional<String> cook(EquipType equip, List<String> contents) {
        for (Process p : processes) {
            if (p.equip() == equip && Multiset.same(p.inputs(), contents)) return Optional.of(p.output());
        }
        return Optional.empty();
    }

    /** 皿の部品と一致する料理。 */
    public Optional<Dish> plate(List<String> parts) {
        for (Dish d : dishes) {
            if (Multiset.same(d.parts(), parts)) return Optional.of(d);
        }
        return Optional.empty();
    }

    /** その設備のどれかの工程の入力に item が含まれるか。 */
    public boolean accepts(EquipType equip, String item) {
        for (Process p : processes) {
            if (p.equip() == equip && p.inputs().contains(item)) return true;
        }
        return false;
    }

    public Optional<Dish> dish(String dishId) {
        return dishes.stream().filter(d -> d.id().equals(dishId)).findFirst();
    }

    /** 盛り付け済みのアイテムから料理を探す。 */
    public Optional<Dish> dishByItem(String item) {
        return dishes.stream().filter(d -> d.item().equals(item)).findFirst();
    }
}
