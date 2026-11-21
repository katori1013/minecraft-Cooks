package jp.minecraftcooks.core.recipe;

import jp.minecraftcooks.core.EquipType;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class RecipeBookTest {

    static Map<String, Object> bundledYaml() {
        try (InputStream in = RecipeBookTest.class.getClassLoader().getResourceAsStream("recipes.yml")) {
            assertNotNull(in, "recipes.yml が見つからない");
            return new Yaml().load(in);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static RecipeBook bundled() {
        return RecipeBook.parse(bundledYaml());
    }

    static Shop shop(String id) {
        return bundled().shop(id).orElseThrow();
    }

    @Test
    void loadsBundledRecipes() {
        RecipeBook book = bundled();
        assertEquals(List.of("chinese", "french", "japanese"), book.shopIds());
        for (String id : book.shopIds()) {
            assertEquals(3, book.shop(id).orElseThrow().dishes().size(), id);
        }
        assertEquals("meat_raw", book.rawItemFor(EquipType.BOX_MEAT));
        assertEquals("rice_raw", book.rawItemFor(EquipType.BOX_RICE));
        assertEquals("肉", book.itemNames().get("meat_raw"));
        assertEquals("ruined", book.ruinedId());
        assertEquals("burnt", book.burntId());
    }

    @Test
    void everyDishReachableFromBoxes() {
        RecipeBook book = bundled();
        for (String id : book.shopIds()) {
            Shop shop = book.shop(id).orElseThrow();
            Set<String> reach = new HashSet<>();
            for (EquipType t : EquipType.values()) if (t.isBox()) reach.add(book.rawItemFor(t));
            boolean changed = true;
            while (changed) {
                changed = false;
                for (Process p : shop.processes()) {
                    if (reach.containsAll(p.inputs()) && reach.add(p.output())) changed = true;
                }
            }
            for (Dish d : shop.dishes()) {
                assertTrue(reach.containsAll(d.parts()), id + "/" + d.id() + " に届かない部品がある");
            }
        }
    }

    @Test
    void stewDependsOnShop() {
        assertEquals(Optional.of("potofu_stewed"), shop("french").cook(EquipType.POT_STEW, List.of("meat_cut", "veg_cut")));
        assertEquals(Optional.of("nikujaga_stewed"), shop("japanese").cook(EquipType.POT_STEW, List.of("meat_cut", "veg_cut")));
    }

    @Test
    void terrineAcceptsMeatOrFish() {
        Shop f = shop("french");
        assertEquals(Optional.of("terrine_set"), f.cook(EquipType.POT_SET, List.of("fish_cut", "veg_cut")));
        assertEquals(Optional.of("terrine_set"), f.cook(EquipType.POT_SET, List.of("meat_cut", "veg_cut")));
    }

    @Test
    void exactMatchOnly() {
        Shop c = shop("chinese");
        assertEquals(Optional.empty(), c.cook(EquipType.PAN, List.of("veg_cut", "meat_cut")));
        assertEquals(Optional.of("chahan_fried"), c.cook(EquipType.PAN, List.of("rice_raw", "meat_cut", "veg_cut")));
        assertEquals(Optional.empty(), c.cook(EquipType.PAN, List.of("rice_raw", "meat_cut", "veg_cut", "veg_cut")));
    }

    @Test
    void wrongPotIsEmpty() {
        assertEquals(Optional.empty(), shop("chinese").cook(EquipType.POT_BOIL, List.of("veg_cut", "meat_cut")));
    }

    @Test
    void plateMatchesMultiset() {
        Shop c = shop("chinese");
        assertEquals("ramen", c.plate(List.of("veg_cut", "noodle_boiled", "meat_boiled_cut")).orElseThrow().id());
        assertTrue(c.plate(List.of("veg_cut", "noodle_boiled")).isEmpty());
        assertEquals("dish_ramen", c.dish("ramen").orElseThrow().item());
    }

    @Test
    void acceptsForCuttingBoard() {
        Shop j = shop("japanese");
        assertTrue(j.accepts(EquipType.CUTTING_BOARD, "fish_raw"));
        assertTrue(j.accepts(EquipType.CUTTING_BOARD, "fish_cut"));
        assertFalse(j.accepts(EquipType.CUTTING_BOARD, "noodle_raw"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void unknownItemFails() {
        Map<String, Object> y = bundledYaml();
        Map<String, Object> chinese = (Map<String, Object>) ((Map<String, Object>) y.get("shops")).get("chinese");
        List<Map<String, Object>> procs = (List<Map<String, Object>>) chinese.get("processes");
        procs.get(0).put("out", "meat_cutt");
        RecipeException e = assertThrows(RecipeException.class, () -> RecipeBook.parse(y));
        assertTrue(e.getMessage().contains("meat_cutt"), e.getMessage());
        assertTrue(e.getMessage().contains("shops.chinese.processes[0].out"), e.getMessage());
    }

    @SuppressWarnings("unchecked")
    @Test
    void unknownEquipFails() {
        Map<String, Object> y = bundledYaml();
        Map<String, Object> chinese = (Map<String, Object>) ((Map<String, Object>) y.get("shops")).get("chinese");
        List<Map<String, Object>> procs = (List<Map<String, Object>>) chinese.get("processes");
        procs.get(0).put("equip", "oven");
        RecipeException e = assertThrows(RecipeException.class, () -> RecipeBook.parse(y));
        assertTrue(e.getMessage().contains("oven"), e.getMessage());
    }
}
