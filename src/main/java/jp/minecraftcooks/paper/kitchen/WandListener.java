package jp.minecraftcooks.paper.kitchen;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import jp.minecraftcooks.core.BlockPos;
import jp.minecraftcooks.core.EquipType;
import jp.minecraftcooks.core.kitchen.EquipmentDef;
import jp.minecraftcooks.paper.Messages;
import jp.minecraftcooks.paper.item.CustomItems;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static jp.minecraftcooks.paper.Messages.text;

/** 設備登録ワンド。右クリックで登録、左クリックで解除。範囲モードは左で1点目、右で2点目。 */
public final class WandListener implements Listener {
    private final CustomItems items;
    private final KitchenRegistry registry;
    private final EquipmentEntities entities;
    private final Supplier<Messages> messages;
    private final Predicate<String> kitchenBusy;

    public WandListener(CustomItems items, KitchenRegistry registry, EquipmentEntities entities,
                        Supplier<Messages> messages, Predicate<String> kitchenBusy) {
        this.items = items;
        this.registry = registry;
        this.entities = entities;
        this.messages = messages;
        this.kitchenBusy = kitchenBusy;
    }

    private Optional<CustomItems.WandMode> wandOf(Player p) {
        if (!p.hasPermission("minecraft-cooks.admin")) return Optional.empty();
        return items.wandModeOf(p.getInventory().getItemInMainHand());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Optional<CustomItems.WandMode> wand = wandOf(e.getPlayer());
        if (wand.isEmpty()) return;
        e.setCancelled(true);
        Block b = e.getClickedBlock();
        if (b == null) return;
        BlockPos pos = new BlockPos(b.getX(), b.getY(), b.getZ());
        boolean right = e.getAction() == Action.RIGHT_CLICK_BLOCK;
        boolean left = e.getAction() == Action.LEFT_CLICK_BLOCK;
        if (right) use(e.getPlayer(), wand.get(), pos, true);
        else if (left) use(e.getPlayer(), wand.get(), pos, false);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBreak(BlockBreakEvent e) {
        if (wandOf(e.getPlayer()).isPresent()) e.setCancelled(true);
    }

    /** 登録済みの設備はクリック判定のエンティティに覆われているので、そちらへのクリックも受ける。 */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Optional<CustomItems.WandMode> wand = wandOf(e.getPlayer());
        if (wand.isEmpty()) return;
        Optional<KitchenRegistry.Located> loc = entities.lookup(e.getRightClicked());
        if (loc.isEmpty()) return;
        e.setCancelled(true);
        use(e.getPlayer(), wand.get(), loc.get().def().pos(), true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onAttackEntity(PrePlayerAttackEntityEvent e) {
        Optional<CustomItems.WandMode> wand = wandOf(e.getPlayer());
        if (wand.isEmpty()) return;
        Optional<KitchenRegistry.Located> loc = entities.lookup(e.getAttacked());
        if (loc.isEmpty()) return;
        e.setCancelled(true);
        use(e.getPlayer(), wand.get(), loc.get().def().pos(), false);
    }

    private void use(Player p, CustomItems.WandMode wand, BlockPos pos, boolean right) {
        Messages msg = messages.get();
        Optional<Kitchen> ko = registry.get(wand.kitchenId());
        if (ko.isEmpty()) {
            msg.send(p, "kitchen.not-found", text("id", wand.kitchenId()));
            return;
        }
        Kitchen k = ko.get();
        if (kitchenBusy.test(k.id())) {
            msg.send(p, "kitchen.in-use");
            return;
        }
        if (k.worldName() != null && !k.worldName().equals(p.getWorld().getName())) {
            msg.send(p, "wand.other-world");
            return;
        }
        if ("area".equals(wand.mode())) {
            if (right) {
                k.setCorner2(pos);
                msg.send(p, "wand.corner2", text("pos", pos));
            } else {
                k.setCorner1(pos);
                msg.send(p, "wand.corner1", text("pos", pos));
            }
            registry.save();
            return;
        }
        Optional<KitchenRegistry.Located> here = registry.at(p.getWorld(), pos);
        if (!right) {
            if (here.isEmpty()) {
                msg.send(p, "wand.nothing-here");
                return;
            }
            Kitchen owner = here.get().kitchen();
            if (kitchenBusy.test(owner.id())) {
                msg.send(p, "kitchen.in-use");
                return;
            }
            entities.remove(owner, here.get().def(), true);
            registry.removeEquipment(owner, pos);
            msg.send(p, "wand.unregistered", text("pos", pos));
            return;
        }
        if (here.isPresent()) {
            msg.send(p, "wand.occupied", text("pos", pos), text("id", here.get().kitchen().id()));
            return;
        }
        Optional<EquipType> type = EquipType.byKey(wand.mode());
        if (type.isEmpty()) return;
        int slot = type.get() == EquipType.COUNTER ? wand.slot() : 0;
        if (slot > 0 && k.counters().stream().anyMatch(d -> d.slot() == slot)) {
            msg.send(p, "wand.slot-taken", text("slot", slot));
            return;
        }
        EquipmentDef def = new EquipmentDef(type.get(), slot, pos);
        registry.addEquipment(k, def);
        entities.spawn(k, def);
        msg.send(p, "wand.registered", text("type", type.get().key() + (slot > 0 ? " #" + slot : "")), text("pos", pos));
    }
}
