package jp.minecraftcooks.core.kitchen;

import jp.minecraftcooks.core.BlockPos;

import java.util.List;

/** キッチンの複製の計算。 */
public final class KitchenLayout {
    private KitchenLayout() {}

    /** 範囲の小さいほうの角どうしのずれぶん、設備をずらす。 */
    public static List<EquipmentDef> cloneShifted(List<EquipmentDef> src, BlockPos fromCorner1, BlockPos fromCorner2,
                                                  BlockPos toCorner1, BlockPos toCorner2) {
        BlockPos shift = BlockPos.min(toCorner1, toCorner2).minus(BlockPos.min(fromCorner1, fromCorner2));
        return src.stream().map(d -> new EquipmentDef(d.type(), d.slot(), d.pos().plus(shift))).toList();
    }

    /** 2つの範囲の大きさが同じか。 */
    public static boolean sameSize(BlockPos a1, BlockPos a2, BlockPos b1, BlockPos b2) {
        BlockPos sa = BlockPos.max(a1, a2).minus(BlockPos.min(a1, a2));
        BlockPos sb = BlockPos.max(b1, b2).minus(BlockPos.min(b1, b2));
        return sa.equals(sb);
    }
}
