package jp.minecraftcooks.core.team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** 参加者をランダムに teamSize 人ずつに分ける。余った人と、maxTeams を超えたぶんは観戦に回す。 */
public final class TeamSplitter {
    public record Split(List<List<UUID>> teams, List<UUID> spectators) {}

    private TeamSplitter() {}

    public static Split split(List<UUID> players, Random rnd, int teamSize, int maxTeams) {
        List<UUID> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled, rnd);
        int teamCount = Math.min(shuffled.size() / teamSize, maxTeams);
        List<List<UUID>> teams = new ArrayList<>();
        for (int i = 0; i < teamCount; i++) {
            teams.add(List.copyOf(shuffled.subList(i * teamSize, (i + 1) * teamSize)));
        }
        List<UUID> spectators = List.copyOf(shuffled.subList(teamCount * teamSize, shuffled.size()));
        return new Split(List.copyOf(teams), spectators);
    }
}
