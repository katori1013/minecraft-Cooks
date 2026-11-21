package jp.minecraftcooks.paper.play;

import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Predicate;

/**
 * 遊んでいる人は、ホットバーの1枠目に1つだけ持てる。
 * インベントリ操作・持ち替え・オフハンドを止め、拾えるのは手が空いているときだけ。ダメージと空腹もなし。
 */
public final class HandLockListener implements Listener {
    private final Predicate<Player> playing;
    private final Predicate<ItemStack> isGameItem;

    public HandLockListener(Predicate<Player> playing, Predicate<ItemStack> isGameItem) {
        this.playing = playing;
        this.isGameItem = isGameItem;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && playing.test(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && playing.test(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (playing.test(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHeld(PlayerItemHeldEvent e) {
        if (playing.test(e.getPlayer()) && e.getNewSlot() != 0) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p) || !playing.test(p)) return;
        e.setCancelled(true);
        Item item = e.getItem();
        ItemStack hand = p.getInventory().getItem(0);
        if (hand != null && !hand.getType().isAir()) return;
        ItemStack stack = item.getItemStack();
        if (!isGameItem.test(stack)) return;
        // 1枠目に確実に入れるため、自分で持たせる
        ItemStack one = stack.clone();
        one.setAmount(1);
        p.getInventory().setItem(0, one);
        if (stack.getAmount() > 1) {
            stack.setAmount(stack.getAmount() - 1);
            item.setItemStack(stack);
        } else {
            item.remove();
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.5f);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && playing.test(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && playing.test(p)) e.setCancelled(true);
    }
}
