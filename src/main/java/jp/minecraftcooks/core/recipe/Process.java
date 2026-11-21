package jp.minecraftcooks.core.recipe;

import jp.minecraftcooks.core.EquipType;

import java.util.List;

/** 1つの工程。equip に inputs をちょうど入れると output になる。 */
public record Process(EquipType equip, List<String> inputs, String output) {
    public Process {
        inputs = List.copyOf(inputs);
    }
}
