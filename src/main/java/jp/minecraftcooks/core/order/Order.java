package jp.minecraftcooks.core.order;

import java.util.List;

/** k 番目の注文。dishIds は重複を含む料理IDの並び。 */
public record Order(int k, List<String> dishIds) {
    public Order {
        dishIds = List.copyOf(dishIds);
    }
}
