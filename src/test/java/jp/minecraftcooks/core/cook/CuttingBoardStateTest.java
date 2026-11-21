package jp.minecraftcooks.core.cook;

import jp.minecraftcooks.core.recipe.RecipeBookTest;
import jp.minecraftcooks.core.recipe.Shop;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CuttingBoardStateTest {
    static Shop shop(String id) {
        return RecipeBookTest.bundled().shop(id).orElseThrow();
    }

    @Test
    void fiveChopsCut() {
        Shop c = shop("chinese");
        CuttingBoardState b = new CuttingBoardState();
        assertTrue(b.place("meat_raw"));
        for (int i = 0; i < 4; i++) assertEquals(Optional.empty(), b.chop(c, 5));
        assertFalse(b.isDone());
        assertEquals(Optional.of("meat_cut"), b.chop(c, 5));
        assertTrue(b.isDone());
        assertEquals(Optional.of("meat_cut"), b.take());
        assertEquals(Optional.empty(), b.take());
    }

    @Test
    void fishCutTwiceForTempura() {
        Shop j = shop("japanese");
        CuttingBoardState b = new CuttingBoardState();
        b.place("fish_raw");
        for (int i = 0; i < 4; i++) b.chop(j, 5);
        assertEquals(Optional.of("fish_cut"), b.chop(j, 5));
        assertTrue(b.canChop(j));
        for (int i = 0; i < 4; i++) b.chop(j, 5);
        assertEquals(Optional.of("fish_cut_fine"), b.chop(j, 5));
        assertFalse(b.canChop(j));
        assertEquals(Optional.of("fish_cut_fine"), b.take());
    }

    @Test
    void placeOnlyWhenEmpty() {
        CuttingBoardState b = new CuttingBoardState();
        assertTrue(b.place("veg_raw"));
        assertFalse(b.place("meat_raw"));
    }

    @Test
    void progressCounts() {
        CuttingBoardState b = new CuttingBoardState();
        b.place("veg_raw");
        b.chop(shop("chinese"), 5);
        b.chop(shop("chinese"), 5);
        assertEquals(0.4, b.progress(), 1e-9);
    }
}
