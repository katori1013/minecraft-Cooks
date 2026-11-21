package jp.minecraftcooks.core.score;

import jp.minecraftcooks.core.Settings;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 注文ごとの得点と、ラウンドの終わりの減点を計算する。 */
public final class Scoring {
    public record Evaluation(int score, int mistakes) {}

    private final Settings settings;

    public Scoring(Settings settings) {
        this.settings = settings;
    }

    public double multiplier(int n) {
        List<Double> m = settings.getMultipliers();
        if (m.isEmpty() || n <= 0) return 1.0;
        return m.get(Math.min(n, m.size()) - 1);
    }

    /** 満点 = 1個あたりの点 × n × 倍率。 */
    public int fullScore(int n) {
        if (n <= 0) return 0;
        return (int) Math.round(settings.getPointsPerDish() * n * multiplier(n));
    }

    /** 間違い = 足りない個数 + 余計・違う料理の個数。得点 = 満点 − 間違い × (満点 ÷ n)。0未満にはしない。 */
    public Evaluation evaluate(List<String> required, List<String> delivered) {
        Map<String, Integer> need = count(required);
        Map<String, Integer> got = count(delivered);
        Set<String> keys = new HashSet<>(need.keySet());
        keys.addAll(got.keySet());
        int mistakes = 0;
        for (String k : keys) {
            mistakes += Math.abs(need.getOrDefault(k, 0) - got.getOrDefault(k, 0));
        }
        int n = required.size();
        int full = fullScore(n);
        int score = n == 0 ? 0 : Math.max(0, full - (int) Math.round(full * mistakes / (double) n));
        return new Evaluation(score, mistakes);
    }

    /** ラウンドの終わりの減点 = 合計点 × 割合 × 間違いの総数(切り捨て)。合計点が0以下なら0。 */
    public int roundPenalty(int roundTotal, int mistakes) {
        if (roundTotal <= 0 || mistakes <= 0) return 0;
        // 0.0025 のような小数は2進数で正確に表せないので、誤差で1点減らないように少し足してから切り捨てる
        return (int) Math.floor(roundTotal * settings.getPenaltyRate() * mistakes + 1e-9);
    }

    private static Map<String, Integer> count(List<String> xs) {
        Map<String, Integer> m = new HashMap<>();
        for (String x : xs) m.merge(x, 1, Integer::sum);
        return m;
    }
}
