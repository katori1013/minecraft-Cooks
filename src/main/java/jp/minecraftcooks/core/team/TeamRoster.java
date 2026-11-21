package jp.minecraftcooks.core.team;

import java.util.*;

/** チームの名簿。オンラインかどうかは持たないので、抜けて戻った人も同じチームのまま。 */
public final class TeamRoster {
    public static final List<String> DEFAULT_COLORS =
            List.of("red", "blue", "green", "yellow", "purple", "aqua", "gold", "light_purple");
    public static final List<String> DEFAULT_NAMES =
            List.of("赤チーム", "青チーム", "緑チーム", "黄チーム", "紫チーム", "水色チーム", "橙チーム", "桃チーム");

    public static final class Team {
        private final String id;
        private String name;
        private String color;
        private final LinkedHashSet<UUID> members = new LinkedHashSet<>();
        private String kitchenId;

        Team(String id, String name, String color) {
            this.id = id;
            this.name = name;
            this.color = color;
        }

        public String id() { return id; }
        public String name() { return name; }
        public String color() { return color; }
        public Set<UUID> members() { return Collections.unmodifiableSet(members); }
        public Optional<String> kitchenId() { return Optional.ofNullable(kitchenId); }
    }

    private final Map<String, Team> teams = new LinkedHashMap<>();
    private boolean locked;

    public Team createTeam(String id, String name, String color) {
        checkUnlocked();
        if (teams.containsKey(id)) throw new IllegalArgumentException("すでにあるチーム: " + id);
        Team t = new Team(id, name, color);
        teams.put(id, t);
        return t;
    }

    public Optional<Team> team(String id) {
        return Optional.ofNullable(teams.get(id));
    }

    /** チームに入れる。ほかのチームにいたら外す。 */
    public void addMember(String teamId, UUID player) {
        checkUnlocked();
        Team t = require(teamId);
        for (Team o : teams.values()) o.members.remove(player);
        t.members.add(player);
    }

    public void removeMember(UUID player) {
        checkUnlocked();
        for (Team o : teams.values()) o.members.remove(player);
    }

    public Optional<String> teamOf(UUID player) {
        for (Team t : teams.values()) if (t.members.contains(player)) return Optional.of(t.id);
        return Optional.empty();
    }

    public Set<UUID> members(String teamId) {
        return require(teamId).members();
    }

    public void setName(String teamId, String name) { require(teamId).name = name; }
    public void setColor(String teamId, String color) { require(teamId).color = color; }

    public void assignKitchen(String teamId, String kitchenId) {
        checkUnlocked();
        require(teamId).kitchenId = kitchenId;
    }

    public Optional<String> kitchenOf(String teamId) {
        return require(teamId).kitchenId();
    }

    public Optional<String> teamOfKitchen(String kitchenId) {
        for (Team t : teams.values()) if (kitchenId.equals(t.kitchenId)) return Optional.of(t.id);
        return Optional.empty();
    }

    public void lock() { locked = true; }
    public void unlock() { locked = false; }
    public boolean isLocked() { return locked; }

    public List<String> teamIds() { return List.copyOf(teams.keySet()); }

    public void clear() {
        checkUnlocked();
        teams.clear();
    }

    private Team require(String id) {
        Team t = teams.get(id);
        if (t == null) throw new IllegalArgumentException("ないチーム: " + id);
        return t;
    }

    private void checkUnlocked() {
        if (locked) throw new IllegalStateException("試合中はチームを変えられません");
    }
}
