package jp.minecraftcooks.core;

import jp.minecraftcooks.core.score.TieBreak;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {

    @Test
    void defaultsRoundTrip() {
        Settings a = new Settings();
        a.setRoundSeconds(123);
        a.setTieBreak(TieBreak.SHARE);
        a.setCookTime(EquipType.PAN, new Settings.CookTime(11, 22));
        a.getTiers().set(1, new Settings.Tier(7, 2, 3));
        Settings b = Settings.fromMap(a.toMap());
        assertEquals(a.toMap(), b.toMap());
        assertEquals(123, b.getRoundSeconds());
        assertEquals(TieBreak.SHARE, b.getTieBreak());
        assertEquals(new Settings.CookTime(11, 22), b.cookTime(EquipType.PAN));
        assertEquals(new Settings.Tier(7, 2, 3), b.getTiers().get(1));
    }

    @Test
    void missingKeysUseDefaults() {
        Settings s = Settings.fromMap(new HashMap<>());
        assertEquals(300, s.getRoundSeconds());
        assertEquals(30, s.getBreakSeconds());
        assertEquals(5, s.getCountdownSeconds());
        assertEquals(3, s.getOrderSlots());
        assertEquals(5, s.getChopCount());
        assertEquals(4, s.getMaxCookerItems());
        assertEquals(100, s.getPointsPerDish());
        assertEquals(0.0025, s.getPenaltyRate(), 1e-12);
        assertEquals(TieBreak.LAST_ROUND, s.getTieBreak());
        assertEquals(60, s.getSubmitConfirmTicks());
        assertEquals(new Settings.CookTime(160, 160), s.cookTime(EquipType.PAN));
        assertEquals(new Settings.CookTime(200, 200), s.cookTime(EquipType.POT_BOIL));
        assertEquals(new Settings.CookTime(200, 200), s.cookTime(EquipType.POT_STEAM));
        assertEquals(new Settings.CookTime(240, 240), s.cookTime(EquipType.POT_STEW));
        assertEquals(new Settings.CookTime(240, 240), s.cookTime(EquipType.POT_SET));
        assertEquals(new Settings.CookTime(160, 160), s.cookTime(EquipType.FRYER));
        assertEquals(java.util.List.of(1.0, 1.1, 1.2, 1.3), s.getMultipliers());
        assertEquals(new Settings().toMap(), s.toMap());
    }

    @Test
    void partialMapKeepsOtherDefaults() {
        Map<String, Object> m = new HashMap<>();
        m.put("roundSeconds", 60);
        Settings s = Settings.fromMap(m);
        assertEquals(60, s.getRoundSeconds());
        assertEquals(30, s.getBreakSeconds());
    }

    @Test
    void tierForK() {
        Settings s = new Settings();
        assertEquals(new Settings.Tier(0, 1, 2), s.tierFor(0));
        assertEquals(new Settings.Tier(0, 1, 2), s.tierFor(5));
        assertEquals(new Settings.Tier(6, 1, 3), s.tierFor(6));
        assertEquals(new Settings.Tier(14, 2, 4), s.tierFor(20));
    }

    @Test
    void equipKeys() {
        assertEquals("pot_boil", EquipType.POT_BOIL.key());
        assertEquals(EquipType.BOX_RICE, EquipType.byKey("box_rice").orElseThrow());
        assertTrue(EquipType.byKey("nope").isEmpty());
        assertTrue(EquipType.FRYER.isCooker());
        assertFalse(EquipType.CUTTING_BOARD.isCooker());
    }
}
