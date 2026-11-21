package jp.minecraftcooks.core.cook;

import jp.minecraftcooks.core.recipe.RecipeBookTest;
import jp.minecraftcooks.core.recipe.Shop;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PlateStateTest {
    static final Shop CHINESE = RecipeBookTest.bundled().shop("chinese").orElseThrow();

    @Test
    void completesRamenInAnyOrder() {
        PlateState p = new PlateState();
        p.add("veg_cut");
        assertTrue(p.tryComplete(CHINESE).isEmpty());
        p.add("meat_boiled_cut");
        p.add("noodle_boiled");
        assertEquals("ramen", p.tryComplete(CHINESE).orElseThrow().id());
        assertTrue(p.parts().isEmpty());
    }

    @Test
    void undoReturnsLast() {
        PlateState p = new PlateState();
        p.add("veg_cut");
        p.add("noodle_boiled");
        assertEquals(Optional.of("noodle_boiled"), p.undo());
        assertEquals(List.of("veg_cut"), p.parts());
        assertEquals(Optional.of("veg_cut"), p.undo());
        assertEquals(Optional.empty(), p.undo());
    }

    @Test
    void capsAtSix() {
        PlateState p = new PlateState();
        for (int i = 0; i < 6; i++) assertTrue(p.add("ruined"));
        assertFalse(p.add("ruined"));
        assertEquals(6, p.parts().size());
    }

    @Test
    void noMatchKeepsParts() {
        PlateState p = new PlateState();
        p.add("veg_cut");
        p.add("veg_cut");
        assertTrue(p.tryComplete(CHINESE).isEmpty());
        assertEquals(List.of("veg_cut", "veg_cut"), p.parts());
    }
}
