package jp.minecraftcooks.core.order;

import jp.minecraftcooks.core.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 乱数の種と k だけで注文を決める。種が同じなら、どのキッチンでも k 番目の注文は同じ。 */
public final class OrderGenerator {
    private final long seed;
    private final List<String> dishIds;
    private final Settings settings;

    public OrderGenerator(long seed, List<String> dishIds, Settings settings) {
        if (dishIds.isEmpty()) throw new IllegalArgumentException("料理がありません");
        this.seed = seed;
        this.dishIds = List.copyOf(dishIds);
        this.settings = settings;
    }

    public Order orderAt(int k) {
        Random rnd = new Random(seed ^ (k * 0x9E3779B97F4A7C15L));
        Settings.Tier tier = settings.tierFor(k);
        int min = Math.max(1, tier.minDishes());
        int max = Math.max(min, tier.maxDishes());
        int n = min + rnd.nextInt(max - min + 1);
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(dishIds.get(rnd.nextInt(dishIds.size())));
        return new Order(k, out);
    }
}
