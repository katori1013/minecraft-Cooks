package jp.minecraftcooks.core.recipe;

import java.util.List;

/** 料理。parts を皿に載せると、item(盛り付け済みのアイテム)になる。 */
public record Dish(String id, String name, String item, List<String> parts) {
    public Dish {
        parts = List.copyOf(parts);
    }
}
