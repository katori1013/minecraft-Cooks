package jp.minecraftcooks.core.cook;

import jp.minecraftcooks.core.EquipType;
import jp.minecraftcooks.core.Settings;
import jp.minecraftcooks.core.recipe.RecipeBook;
import jp.minecraftcooks.core.recipe.RecipeBookTest;
import jp.minecraftcooks.core.recipe.Shop;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CookerStateTest {
    static final RecipeBook BOOK = RecipeBookTest.bundled();
    static final Shop CHINESE = BOOK.shop("chinese").orElseThrow();
    static final Settings.CookTime BOIL = new Settings.CookTime(200, 200);

    static void run(CookerState c, int n) {
        for (int i = 0; i < n; i++) c.tick(CHINESE, BOIL, "ruined", "burnt");
    }

    @Test
    void cooksThenBurns() {
        CookerState c = new CookerState(EquipType.POT_BOIL);
        assertTrue(c.add("noodle_raw", 4));
        run(c, 199);
        assertEquals(CookerState.Phase.COOKING, c.phase());
        run(c, 1);
        assertEquals(CookerState.Phase.DONE, c.phase());
        assertEquals(Optional.of("noodle_boiled"), c.result());
        run(c, 199);
        assertEquals(CookerState.Phase.DONE, c.phase());
        run(c, 1);
        assertEquals(CookerState.Phase.BURNT, c.phase());
        assertEquals(Optional.of("burnt"), c.takeOut());
        assertEquals(CookerState.Phase.EMPTY, c.phase());
    }

    @Test
    void wrongMixRuins() {
        CookerState c = new CookerState(EquipType.POT_BOIL);
        c.add("veg_cut", 4);
        c.add("meat_cut", 4);
        run(c, 200);
        assertEquals(CookerState.Phase.RUINED, c.phase());
        run(c, 1000);
        assertEquals(CookerState.Phase.RUINED, c.phase());
        assertEquals(Optional.of("ruined"), c.takeOut());
    }

    @Test
    void addResetsTimer() {
        CookerState c = new CookerState(EquipType.PAN);
        c.add("veg_cut", 4);
        c.add("meat_cut", 4);
        run(c, 150);
        c.add("rice_raw", 4);
        run(c, 199);
        assertEquals(CookerState.Phase.COOKING, c.phase());
        run(c, 1);
        assertEquals(Optional.of("chahan_fried"), c.result());
    }

    @Test
    void takeOutWhileCookingReturnsLast() {
        CookerState c = new CookerState(EquipType.PAN);
        c.add("veg_cut", 4);
        c.add("meat_cut", 4);
        run(c, 100);
        assertEquals(Optional.of("meat_cut"), c.takeOut());
        assertEquals(List.of("veg_cut"), c.contents());
        assertEquals(0.0, c.progress());
        assertEquals(Optional.of("veg_cut"), c.takeOut());
        assertEquals(CookerState.Phase.EMPTY, c.phase());
        assertEquals(Optional.empty(), c.takeOut());
    }

    @Test
    void noTickNoProgress() {
        CookerState c = new CookerState(EquipType.POT_BOIL);
        c.add("noodle_raw", 4);
        assertEquals(0.0, c.progress());
        assertEquals(CookerState.Phase.COOKING, c.phase());
        run(c, 100);
        assertEquals(0.5, c.progress(), 1e-9);
    }

    @Test
    void cannotAddToDone() {
        CookerState c = new CookerState(EquipType.POT_BOIL);
        c.add("noodle_raw", 4);
        run(c, 200);
        assertFalse(c.add("noodle_raw", 4));
    }

    @Test
    void capacityLimit() {
        CookerState c = new CookerState(EquipType.POT_BOIL);
        for (int i = 0; i < 4; i++) assertTrue(c.add("veg_cut", 4));
        assertFalse(c.add("veg_cut", 4));
    }
}
