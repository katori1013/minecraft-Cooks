package jp.minecraftcooks.paper.play;

import org.bukkit.entity.Player;

import java.util.Optional;

/** プレイヤーが、いまどのキッチンで遊んでいるか。練習と試合がそれぞれ実装する。 */
public interface KitchenAccess {
    Optional<KitchenRuntime> runtimeFor(Player player);
}
