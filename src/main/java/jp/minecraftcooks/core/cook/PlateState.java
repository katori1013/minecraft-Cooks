package jp.minecraftcooks.core.cook;

import jp.minecraftcooks.core.recipe.Dish;
import jp.minecraftcooks.core.recipe.Shop;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 皿(盛り付け台)の状態。 */
public final class PlateState {
    public static final int MAX_PARTS = 6;

    private final List<String> parts = new ArrayList<>();

    public boolean add(String part) {
        if (parts.size() >= MAX_PARTS) return false;
        parts.add(part);
        return true;
    }

    /** 部品が料理とちょうど一致したら、皿を空にして料理を返す。 */
    public Optional<Dish> tryComplete(Shop shop) {
        Optional<Dish> d = shop.plate(parts);
        if (d.isPresent()) parts.clear();
        return d;
    }

    public Optional<String> undo() {
        if (parts.isEmpty()) return Optional.empty();
        return Optional.of(parts.remove(parts.size() - 1));
    }

    public List<String> parts() { return List.copyOf(parts); }

    public void reset() { parts.clear(); }
}
