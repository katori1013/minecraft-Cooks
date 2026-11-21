package jp.minecraftcooks.core.order;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DeliverySlotTest {
    @Test
    void undoLast() {
        DeliverySlot s = new DeliverySlot(new Order(0, List.of("ramen")));
        assertTrue(s.isEmpty());
        s.add("ramen");
        s.add("soba");
        assertEquals(Optional.of("soba"), s.undoLast());
        assertEquals(List.of("ramen"), s.delivered());
        s.undoLast();
        assertEquals(Optional.empty(), s.undoLast());
    }

    @Test
    void nextClears() {
        DeliverySlot s = new DeliverySlot(new Order(0, List.of("ramen")));
        s.add("ramen");
        s.next(new Order(3, List.of("chahan")));
        assertTrue(s.isEmpty());
        assertEquals(3, s.order().k());
    }
}
