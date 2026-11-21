package jp.minecraftcooks.core.team;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TeamRosterTest {
    @Test
    void rejoinKeepsTeam() {
        TeamRoster r = new TeamRoster();
        r.createTeam("team-1", "赤チーム", "red");
        UUID p = UUID.randomUUID();
        r.addMember("team-1", p);
        r.assignKitchen("team-1", "kitchen-1");
        r.lock();
        // 抜けて戻っても、名簿はオンラインかどうかを持たないので同じ値を返す
        assertEquals(Optional.of("team-1"), r.teamOf(p));
        assertEquals(Optional.of("kitchen-1"), r.kitchenOf("team-1"));
    }

    @Test
    void lockedRejectsChanges() {
        TeamRoster r = new TeamRoster();
        r.createTeam("team-1", "赤チーム", "red");
        UUID p = UUID.randomUUID();
        r.addMember("team-1", p);
        r.lock();
        assertThrows(IllegalStateException.class, () -> r.addMember("team-1", UUID.randomUUID()));
        assertThrows(IllegalStateException.class, () -> r.removeMember(p));
        r.unlock();
        r.removeMember(p);
        assertTrue(r.teamOf(p).isEmpty());
    }

    @Test
    void addMovesBetweenTeams() {
        TeamRoster r = new TeamRoster();
        r.createTeam("team-1", "赤チーム", "red");
        r.createTeam("team-2", "青チーム", "blue");
        UUID p = UUID.randomUUID();
        r.addMember("team-1", p);
        r.addMember("team-2", p);
        assertEquals(Optional.of("team-2"), r.teamOf(p));
        assertFalse(r.members("team-1").contains(p));
    }

    @Test
    void defaultsAndClear() {
        assertEquals("red", TeamRoster.DEFAULT_COLORS.get(0));
        assertEquals("light_purple", TeamRoster.DEFAULT_COLORS.get(7));
        assertEquals("赤チーム", TeamRoster.DEFAULT_NAMES.get(0));
        TeamRoster r = new TeamRoster();
        r.createTeam("team-1", "赤チーム", "red");
        r.clear();
        assertTrue(r.teamIds().isEmpty());
    }
}
