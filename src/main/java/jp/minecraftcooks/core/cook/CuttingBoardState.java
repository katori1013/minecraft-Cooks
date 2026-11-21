package jp.minecraftcooks.core.cook;

import jp.minecraftcooks.core.EquipType;
import jp.minecraftcooks.core.recipe.Shop;

import java.util.List;
import java.util.Optional;

/** まな板の状態。置ける食材かどうかは、呼び出し側が shop.accepts(CUTTING_BOARD, item) で確かめる。 */
public final class CuttingBoardState {
    private String item;
    private int chops;
    private int lastCount = 1;
    private boolean done;

    public boolean place(String item) {
        if (this.item != null) return false;
        this.item = item;
        chops = 0;
        done = false;
        return true;
    }

    public Optional<String> item() { return Optional.ofNullable(item); }

    /** まだ切れる(切る工程がある)か。 */
    public boolean canChop(Shop shop) {
        return item != null && shop.cook(EquipType.CUTTING_BOARD, List.of(item)).isPresent();
    }

    /** 1回切る。切り終えたら、切ったあとの識別子を返して置き換える。 */
    public Optional<String> chop(Shop shop, int chopCount) {
        if (item == null) return Optional.empty();
        Optional<String> out = shop.cook(EquipType.CUTTING_BOARD, List.of(item));
        if (out.isEmpty()) return Optional.empty();
        lastCount = Math.max(1, chopCount);
        chops++;
        if (chops >= chopCount) {
            item = out.get();
            chops = 0;
            done = true;
            return out;
        }
        return Optional.empty();
    }

    public Optional<String> take() {
        Optional<String> r = Optional.ofNullable(item);
        reset();
        return r;
    }

    /** 1回以上切り終えた品が載っているか。 */
    public boolean isDone() {
        return item != null && done && chops == 0;
    }

    public double progress() {
        return item == null ? 0.0 : Math.min(1.0, chops / (double) lastCount);
    }

    public void reset() {
        item = null;
        chops = 0;
        done = false;
    }
}
