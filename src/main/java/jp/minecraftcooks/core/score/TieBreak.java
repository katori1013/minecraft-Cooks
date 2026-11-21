package jp.minecraftcooks.core.score;

/** 同点のときの決め方。 */
public enum TieBreak {
    /** 最後のラウンドの得点が高いほうを上位にする */
    LAST_ROUND,
    /** 間違いが少ないほうを上位にする */
    FEWER_MISTAKES,
    /** 同じ順位のままにする */
    SHARE
}
