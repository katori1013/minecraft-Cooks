package jp.minecraftcooks.paper.play;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.*;

/** /cg practice: 試合の外で、ひとりでキッチンを試す。 */
public final class PracticeSessions implements KitchenAccess {
    private final Map<UUID, KitchenRuntime> byPlayer = new HashMap<>();
    private final Map<UUID, GameMode> previousMode = new HashMap<>();

    @Override
    public Optional<KitchenRuntime> runtimeFor(Player player) {
        return Optional.ofNullable(byPlayer.get(player.getUniqueId()));
    }

    public boolean isBusy(String kitchenId) {
        return byPlayer.values().stream().anyMatch(r -> r.kitchen().id().equals(kitchenId));
    }

    public Optional<KitchenRuntime> runtimeOfKitchen(String kitchenId) {
        return byPlayer.values().stream().filter(r -> r.kitchen().id().equals(kitchenId)).findFirst();
    }

    public void start(Player p, KitchenRuntime rt) {
        stop(p);
        previousMode.put(p.getUniqueId(), p.getGameMode());
        p.getInventory().clear();
        p.getInventory().setHeldItemSlot(0);
        p.setGameMode(GameMode.ADVENTURE);
        rt.setActive(true);
        byPlayer.put(p.getUniqueId(), rt);
    }

    public boolean stop(Player p) {
        KitchenRuntime rt = byPlayer.remove(p.getUniqueId());
        if (rt == null) return false;
        rt.setActive(false);
        rt.reset();
        rt.clearDisplays();
        p.getInventory().clear();
        GameMode prev = previousMode.remove(p.getUniqueId());
        if (prev != null) p.setGameMode(prev);
        return true;
    }

    public void stopAll(Collection<? extends Player> online) {
        for (Player p : online) stop(p);
        byPlayer.clear();
    }

    public void tick() {
        for (KitchenRuntime rt : byPlayer.values()) rt.tick();
    }
}
