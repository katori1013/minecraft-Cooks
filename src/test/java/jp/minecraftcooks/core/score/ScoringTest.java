package jp.minecraftcooks.core.score;

import jp.minecraftcooks.core.Settings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScoringTest {
    final Scoring s = new Scoring(new Settings());

    @Test
    void fullScores() {
        assertEquals(100, s.fullScore(1));
        assertEquals(220, s.fullScore(2));
        assertEquals(360, s.fullScore(3));
        assertEquals(520, s.fullScore(4));
        assertEquals(650, s.fullScore(5)); // 倍率の数を超えたら最後の倍率
    }

    @Test
    void specExample() {
        Scoring.Evaluation e = s.evaluate(List.of("ramen", "ramen", "chahan"), List.of("ramen", "shumai"));
        assertEquals(3, e.mistakes());
        assertEquals(0, e.score());
    }

    @Test
    void perfect() {
        Scoring.Evaluation e = s.evaluate(List.of("ramen", "chahan"), List.of("chahan", "ramen"));
        assertEquals(0, e.mistakes());
        assertEquals(220, e.score());
    }

    @Test
    void shortDeliveryCountsMissing() {
        Scoring.Evaluation e = s.evaluate(List.of("ramen", "ramen"), List.of("ramen"));
        assertEquals(1, e.mistakes());
        assertEquals(110, e.score());
    }

    @Test
    void onlyWrongDishes() {
        Scoring.Evaluation e = s.evaluate(List.of("ramen"), List.of("soba", "soba"));
        assertEquals(3, e.mistakes());
        assertEquals(0, e.score());
    }

    @Test
    void overDelivery() {
        Scoring.Evaluation e = s.evaluate(List.of("ramen"), List.of("ramen", "ramen"));
        assertEquals(1, e.mistakes());
        assertEquals(0, e.score());
    }

    @Test
    void penalty() {
        assertEquals(75, s.roundPenalty(10000, 3));
        assertEquals(0, s.roundPenalty(-50, 3));
        assertEquals(0, s.roundPenalty(0, 3));
        assertEquals(0, s.roundPenalty(10000, 0));
    }
}
