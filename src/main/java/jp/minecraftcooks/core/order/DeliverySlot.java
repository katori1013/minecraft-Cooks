package jp.minecraftcooks.core.order;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 納品場所1つぶん。いまの注文と、入れた料理を持つ。 */
public final class DeliverySlot {
    private Order order;
    private final List<String> delivered = new ArrayList<>();

    public DeliverySlot(Order order) {
        this.order = order;
    }

    public Order order() { return order; }

    public void add(String dishId) { delivered.add(dishId); }

    public Optional<String> undoLast() {
        if (delivered.isEmpty()) return Optional.empty();
        return Optional.of(delivered.remove(delivered.size() - 1));
    }

    public List<String> delivered() { return List.copyOf(delivered); }

    public boolean isEmpty() { return delivered.isEmpty(); }

    /** 中身を空にして、注文を差し替える。 */
    public void next(Order o) {
        delivered.clear();
        order = o;
    }
}
