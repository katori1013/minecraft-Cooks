package jp.minecraftcooks.paper.item;

import jp.minecraftcooks.core.recipe.RecipeBook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 紙をベースにした独自アイテム。種類は見た目の名前ではなく PDC の minecraft-cooks:id で判別する。 */
public final class CustomItems {
    public static final String NAMESPACE = "minecraft-cooks";
    public static final NamespacedKey ID_KEY = new NamespacedKey(NAMESPACE, "id");
    public static final NamespacedKey WAND_KEY = new NamespacedKey(NAMESPACE, "wand");

    /**
     * バニラの見た目をそのまま借りるアイテム(識別子 → バニラのアイテムモデル)。
     * ベースは紙のままなので、本物の牛肉などとは混ざらない。ここにないものは minecraft-cooks:<識別子> を使う。
     */
    private static final Map<String, String> VANILLA_MODELS = Map.of(
            "meat_raw", "beef",
            "fish_raw", "cod",
            "meat_grilled", "cooked_beef",
            "fish_grilled", "cooked_cod",
            "burnt", "charcoal",
            "wand", "blaze_rod");

    private static NamespacedKey modelFor(String id) {
        String vanilla = VANILLA_MODELS.get(id);
        return vanilla != null ? NamespacedKey.minecraft(vanilla) : new NamespacedKey(NAMESPACE, id);
    }

    /** ワンドの用途。mode は設備の key か "area"。slot は納品場所の番号(それ以外は0)。 */
    public record WandMode(String kitchenId, String mode, int slot) {}

    private volatile RecipeBook book;

    public CustomItems(RecipeBook book) {
        this.book = book;
    }

    public void setBook(RecipeBook book) {
        this.book = book;
    }

    public ItemStack create(String id) {
        ItemStack stack = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(modelFor(id));
        meta.displayName(Component.text(book.nameOf(id), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(ID_KEY, PersistentDataType.STRING, id);
        stack.setItemMeta(meta);
        return stack;
    }

    public Optional<String> idOf(ItemStack stack) {
        if (stack == null || stack.getType() != Material.PAPER || !stack.hasItemMeta()) return Optional.empty();
        return Optional.ofNullable(stack.getItemMeta().getPersistentDataContainer().get(ID_KEY, PersistentDataType.STRING));
    }

    public ItemStack wand(String kitchenId, String mode, int slot) {
        ItemStack stack = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(modelFor("wand"));
        String label = "area".equals(mode) ? "範囲" : mode + (slot > 0 ? " #" + slot : "");
        meta.displayName(Component.text("設備登録ワンド: " + kitchenId + " / " + label, NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("右クリック: 登録(範囲は2点目)", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("左クリック: 解除(範囲は1点目)", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(WAND_KEY, PersistentDataType.STRING, kitchenId + "|" + mode + "|" + slot);
        stack.setItemMeta(meta);
        return stack;
    }

    public Optional<WandMode> wandModeOf(ItemStack stack) {
        if (stack == null || stack.getType() != Material.PAPER || !stack.hasItemMeta()) return Optional.empty();
        String raw = stack.getItemMeta().getPersistentDataContainer().get(WAND_KEY, PersistentDataType.STRING);
        if (raw == null) return Optional.empty();
        String[] p = raw.split("\\|");
        if (p.length != 3) return Optional.empty();
        try {
            return Optional.of(new WandMode(p[0], p[1], Integer.parseInt(p[2])));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
