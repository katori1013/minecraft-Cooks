package jp.minecraftcooks.core.score;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RankingTest {
    static List<String> order(List<Ranking.Ranked> r) {
        return r.stream().map(x -> x.place() + ":" + x.r().teamId()).toList();
    }

    @Test
    void lastRoundBreaksTie() {
        List<TeamResult> in = List.of(
                new TeamResult("a", 500, 100, 0),
                new TeamResult("b", 500, 300, 5),
                new TeamResult("c", 900, 0, 0));
        assertEquals(List.of("1:c", "2:b", "3:a"), order(Ranking.rank(in, TieBreak.LAST_ROUND)));
    }

    @Test
    void fewerMistakesBreaksTie() {
        List<TeamResult> in = List.of(
                new TeamResult("a", 500, 100, 1),
                new TeamResult("b", 500, 300, 5));
        assertEquals(List.of("1:a", "2:b"), order(Ranking.rank(in, TieBreak.FEWER_MISTAKES)));
    }

    @Test
    void shareGivesSamePlace() {
        List<TeamResult> in = List.of(
                new TeamResult("a", 500, 100, 1),
                new TeamResult("b", 500, 300, 5),
                new TeamResult("c", 200, 0, 0));
        List<Ranking.Ranked> r = Ranking.rank(in, TieBreak.SHARE);
        assertEquals(List.of(1, 1, 3), r.stream().map(Ranking.Ranked::place).toList());
        assertEquals("c", r.get(2).r().teamId());
    }

    @Test
    void fullTieSharesEvenWithTieBreak() {
        List<TeamResult> in = List.of(
                new TeamResult("a", 500, 100, 1),
                new TeamResult("b", 500, 100, 1));
        assertEquals(List.of(1, 1), Ranking.rank(in, TieBreak.LAST_ROUND).stream().map(Ranking.Ranked::place).toList());
    }
}
