package jp.minecraftcooks.core.team;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TeamSplitterTest {
    static List<UUID> players(int n) {
        List<UUID> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(UUID.randomUUID());
        return l;
    }

    @Test
    void split23() {
        TeamSplitter.Split s = TeamSplitter.split(players(23), new Random(1), 5, 8);
        assertEquals(4, s.teams().size());
        s.teams().forEach(t -> assertEquals(5, t.size()));
        assertEquals(3, s.spectators().size());
    }

    @Test
    void split47() {
        TeamSplitter.Split s = TeamSplitter.split(players(47), new Random(1), 5, 8);
        assertEquals(8, s.teams().size());
        assertEquals(7, s.spectators().size());
    }

    @Test
    void split4() {
        TeamSplitter.Split s = TeamSplitter.split(players(4), new Random(1), 5, 8);
        assertEquals(0, s.teams().size());
        assertEquals(4, s.spectators().size());
    }

    @Test
    void noDuplicates() {
        List<UUID> in = players(33);
        TeamSplitter.Split s = TeamSplitter.split(in, new Random(7), 5, 8);
        Set<UUID> all = new HashSet<>(s.spectators());
        s.teams().forEach(all::addAll);
        assertEquals(new HashSet<>(in), all);
        int total = s.spectators().size() + s.teams().stream().mapToInt(List::size).sum();
        assertEquals(33, total);
    }
}
