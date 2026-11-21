package jp.minecraftcooks.paper.play;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import jp.minecraftcooks.paper.item.CustomItems;
import jp.minecraftcooks.paper.kitchen.EquipmentEntities;
import jp.minecraftcooks.paper.kitchen.KitchenRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;

/** 設備のクリック判定エンティティへの右クリック・左クリックを、そのキッチンの状態に渡す。 */
public final class InteractionListener implements Listener {
    private final EquipmentEntities entities;
    private final CustomItems items;
    private final KitchenAccess access;

    public InteractionListener(EquipmentEntities entities, CustomItems items, KitchenAccess access) {
        this.entities = entities;
        this.items = items;
        this.access = access;
    }

    private boolean holdingWand(Player p) {
        return items.wandModeOf(p.getInventory().getItemInMainHand()).isPresent();
    }

    private Optional<Target> target(Player p, org.bukkit.entity.Entity clicked) {
        Optional<KitchenRegistry.Located> loc = entities.lookup(clicked);
        if (loc.isEmpty()) return Optional.empty();
        Optional<KitchenRuntime> rt = access.runtimeFor(p);
        // 自分のキッチン以外の設備は反応しない
        if (rt.isEmpty() || rt.get().kitchen() != loc.get().kitchen()) return Optional.of(new Target(loc.get(), null));
        return Optional.of(new Target(loc.get(), rt.get()));
    }

    private record Target(KitchenRegistry.Located loc, KitchenRuntime runtime) {}

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRight(PlayerInteractEntityEvent e) {
        Optional<KitchenRegistry.Located> isEquip = entities.lookup(e.getRightClicked());
        if (isEquip.isEmpty()) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (holdingWand(p)) return;
        target(p, e.getRightClicked()).filter(t -> t.runtime() != null)
                .ifPresent(t -> t.runtime().rightClick(p, t.loc().def(), p.isSneaking()));
    }

    /** Interaction への右クリックでは、この「位置つき」のイベントも来る。重ならないように止めるだけにする。 */
    @EventHandler
    public void onRightAt(PlayerInteractAtEntityEvent e) {
        if (entities.lookup(e.getRightClicked()).isPresent()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onLeft(PrePlayerAttackEntityEvent e) {
        Optional<KitchenRegistry.Located> isEquip = entities.lookup(e.getAttacked());
        if (isEquip.isEmpty()) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (holdingWand(p)) return;
        target(p, e.getAttacked()).filter(t -> t.runtime() != null)
                .ifPresent(t -> t.runtime().leftClick(p, t.loc().def()));
    }
}
