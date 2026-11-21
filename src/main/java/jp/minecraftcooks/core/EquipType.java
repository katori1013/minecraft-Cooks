package jp.minecraftcooks.core;

import java.util.Locale;
import java.util.Optional;

/** 設備の種類。key は設定ファイルやモデル名で使う小文字のスネークケース。 */
public enum EquipType {
    BOX_MEAT, BOX_FISH, BOX_VEG, BOX_NOODLE, BOX_RICE,
    CUTTING_BOARD,
    PAN, POT_BOIL, POT_STEAM, POT_STEW, POT_SET, FRYER,
    PLATE, COUNTER, TRASH;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<EquipType> byKey(String key) {
        if (key == null) return Optional.empty();
        for (EquipType t : values()) {
            if (t.key().equalsIgnoreCase(key)) return Optional.of(t);
        }
        return Optional.empty();
    }

    /** 自動で調理する設備(フライパン、鍋4種、フライヤー)か。 */
    public boolean isCooker() {
        return switch (this) {
            case PAN, POT_BOIL, POT_STEAM, POT_STEW, POT_SET, FRYER -> true;
            default -> false;
        };
    }

    /** 食材箱か。 */
    public boolean isBox() {
        return name().startsWith("BOX_");
    }
}
