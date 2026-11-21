package jp.minecraftcooks.core.cook;

import jp.minecraftcooks.core.EquipType;
import jp.minecraftcooks.core.Settings;
import jp.minecraftcooks.core.recipe.Shop;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 自動で調理する設備(フライパン、鍋、フライヤー)の状態。tick() を呼んだぶんだけ進む。 */
public final class CookerState {
    public enum Phase { EMPTY, COOKING, DONE, RUINED, BURNT }

    private final EquipType type;
    private final List<String> contents = new ArrayList<>();
    private Phase phase = Phase.EMPTY;
    private int elapsed;
    private int lastTotal = 1;
    private String result;

    public CookerState(EquipType type) {
        if (!type.isCooker()) throw new IllegalArgumentException("調理する設備ではありません: " + type);
        this.type = type;
    }

    public EquipType type() { return type; }
    public Phase phase() { return phase; }
    public List<String> contents() { return List.copyOf(contents); }
    public Optional<String> result() { return Optional.ofNullable(result); }

    /** 食材を1つ入れる。完成・失敗・焦げのとき、または満杯のときは入らない。入れるとタイマーは最初に戻る。 */
    public boolean add(String item, int maxItems) {
        if (phase == Phase.DONE || phase == Phase.RUINED || phase == Phase.BURNT) return false;
        if (contents.size() >= maxItems) return false;
        contents.add(item);
        phase = Phase.COOKING;
        elapsed = 0;
        return true;
    }

    public void tick(Shop shop, Settings.CookTime t, String ruinedId, String burntId) {
        switch (phase) {
            case COOKING -> {
                lastTotal = Math.max(1, t.cookTicks());
                elapsed++;
                if (elapsed >= t.cookTicks()) {
                    Optional<String> out = shop.cook(type, contents);
                    result = out.orElse(ruinedId);
                    phase = out.isPresent() ? Phase.DONE : Phase.RUINED;
                    contents.clear();
                    elapsed = 0;
                }
            }
            case DONE -> {
                lastTotal = Math.max(1, t.burnTicks());
                elapsed++;
                if (elapsed >= t.burnTicks()) {
                    result = burntId;
                    phase = Phase.BURNT;
                    elapsed = 0;
                }
            }
            default -> { }
        }
    }

    /** 完成品(失敗・焦げを含む)を取り出す。調理中なら最後に入れた1つが戻り、タイマーは最初に戻る。 */
    public Optional<String> takeOut() {
        switch (phase) {
            case DONE, RUINED, BURNT -> {
                String r = result;
                reset();
                return Optional.of(r);
            }
            case COOKING -> {
                String last = contents.remove(contents.size() - 1);
                elapsed = 0;
                if (contents.isEmpty()) phase = Phase.EMPTY;
                return Optional.of(last);
            }
            default -> {
                return Optional.empty();
            }
        }
    }

    /** 調理中は完成までの割合、完成後は 1。 */
    public double progress() {
        return switch (phase) {
            case COOKING -> Math.min(1.0, elapsed / (double) lastTotal);
            case DONE, RUINED, BURNT -> 1.0;
            default -> 0.0;
        };
    }

    /** 完成してから焦げるまでの割合(完成していなければ 0)。 */
    public double burnProgress() {
        return phase == Phase.DONE ? Math.min(1.0, elapsed / (double) lastTotal) : 0.0;
    }

    public void reset() {
        contents.clear();
        phase = Phase.EMPTY;
        elapsed = 0;
        result = null;
    }
}
