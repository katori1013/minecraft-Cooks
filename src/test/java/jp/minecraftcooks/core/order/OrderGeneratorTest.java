package jp.minecraftcooks.core.order;

import jp.minecraftcooks.core.Settings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderGeneratorTest {
    static final List<String> DISHES = List.of("ramen", "shumai", "chahan");

    @Test
    void sameSeedSameOrder() {
        OrderGenerator a = new OrderGenerator(42L, DISHES, new Settings());
        OrderGenerator b = new OrderGenerator(42L, DISHES, new Settings());
        assertEquals(a.orderAt(7), b.orderAt(7));
        assertEquals(7, a.orderAt(7).k());
    }

    @Test
    void countsFollowTiers() {
        OrderGenerator g = new OrderGenerator(1234L, DISHES, new Settings());
        for (int k = 0; k <= 5; k++) {
            int n = g.orderAt(k).dishIds().size();
            assertTrue(n >= 1 && n <= 2, "k=" + k + " n=" + n);
        }
        for (int k = 14; k <= 100; k++) {
            int n = g.orderAt(k).dishIds().size();
            assertTrue(n >= 2 && n <= 4, "k=" + k + " n=" + n);
        }
    }

    @Test
    void onlyShopDishes() {
        OrderGenerator g = new OrderGenerator(99L, DISHES, new Settings());
        for (int k = 0; k < 50; k++) assertTrue(DISHES.containsAll(g.orderAt(k).dishIds()));
    }
}
