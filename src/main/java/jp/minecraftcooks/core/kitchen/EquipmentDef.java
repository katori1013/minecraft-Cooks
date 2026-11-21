package jp.minecraftcooks.core.kitchen;

import jp.minecraftcooks.core.BlockPos;
import jp.minecraftcooks.core.EquipType;

/** キッチンに登録した設備1つ。slot は納品場所の番号(1〜)で、それ以外は0。 */
public record EquipmentDef(EquipType type, int slot, BlockPos pos) {}
