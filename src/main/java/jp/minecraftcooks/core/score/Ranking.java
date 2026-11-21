package jp.minecraftcooks.core.score;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 合計点の高い順に並べる。同点は TieBreak で比べ、それでも同じなら同じ順位にする(1位,1位,3位)。 */
public final class Ranking {
    public record Ranked(int place, TeamResult r) {}

    private Ranking() {}

    public static List<Ranked> rank(List<TeamResult> results, TieBreak tieBreak) {
        Comparator<TeamResult> cmp = Comparator.comparingInt(TeamResult::total).reversed();
        Comparator<TeamResult> tie = switch (tieBreak) {
            case LAST_ROUND -> Comparator.comparingInt(TeamResult::lastRound).reversed();
            case FEWER_MISTAKES -> Comparator.comparingInt(TeamResult::mistakes);
            case SHARE -> (a, b) -> 0;
        };
        Comparator<TeamResult> full = cmp.thenComparing(tie);
        List<TeamResult> sorted = new ArrayList<>(results);
        sorted.sort(full.thenComparing(TeamResult::teamId));
        List<Ranked> out = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            int place = (i > 0 && full.compare(sorted.get(i - 1), sorted.get(i)) == 0)
                    ? out.get(i - 1).place() : i + 1;
            out.add(new Ranked(place, sorted.get(i)));
        }
        return out;
    }
}
