package jp.minecraftcooks.core.kitchen;

import jp.minecraftcooks.core.BlockPos;
import jp.minecraftcooks.core.EquipType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KitchenLayoutTest {
    static final List<EquipmentDef> SRC = List.of(
            new EquipmentDef(EquipType.POT_BOIL, 0, new BlockPos(3, 65, 4)),
            new EquipmentDef(EquipType.COUNTER, 2, new BlockPos(10, 64, 10)));

    @Test
    void shiftsByMinCorner() {
        List<EquipmentDef> out = KitchenLayout.cloneShifted(SRC,
                new BlockPos(0, 64, 0), new BlockPos(10, 70, 10),
                new BlockPos(100, 64, 0), new BlockPos(110, 70, 10));
        assertEquals(new BlockPos(103, 65, 4), out.get(0).pos());
        assertEquals(EquipType.POT_BOIL, out.get(0).type());
        assertEquals(new EquipmentDef(EquipType.COUNTER, 2, new BlockPos(110, 64, 10)), out.get(1));
    }

    @Test
    void cornerOrderIrrelevant() {
        List<EquipmentDef> a = KitchenLayout.cloneShifted(SRC,
                new BlockPos(0, 64, 0), new BlockPos(10, 70, 10),
                new BlockPos(100, 64, 0), new BlockPos(110, 70, 10));
        List<EquipmentDef> b = KitchenLayout.cloneShifted(SRC,
                new BlockPos(10, 70, 10), new BlockPos(0, 64, 0),
                new BlockPos(110, 70, 10), new BlockPos(100, 64, 0));
        assertEquals(a, b);
    }

    @Test
    void sizeCheck() {
        assertTrue(KitchenLayout.sameSize(new BlockPos(0, 0, 0), new BlockPos(5, 5, 5), new BlockPos(15, 5, 15), new BlockPos(10, 0, 10)));
        assertFalse(KitchenLayout.sameSize(new BlockPos(0, 0, 0), new BlockPos(5, 5, 5), new BlockPos(10, 0, 10), new BlockPos(16, 5, 15)));
    }
}
