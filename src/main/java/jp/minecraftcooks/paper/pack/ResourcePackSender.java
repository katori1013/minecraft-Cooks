package jp.minecraftcooks.paper.pack;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

/** 参加したプレイヤーにリソースパックを送る。サーバー本体のパックの上に重ねて送れる。 */
public final class ResourcePackSender implements Listener {
    private final Plugin plugin;
    private final String url;
    private final boolean required;
    private final String prompt;
    private final UUID packId;
    private volatile byte[] hash;

    public ResourcePackSender(Plugin plugin, String url, String sha1Hex, boolean required, String prompt) {
        this.plugin = plugin;
        this.url = url == null ? "" : url.trim();
        this.required = required;
        this.prompt = prompt;
        this.packId = UUID.nameUUIDFromBytes(("minecraft-cooks:" + this.url).getBytes(StandardCharsets.UTF_8));
        if (sha1Hex != null && !sha1Hex.isBlank()) {
            try {
                this.hash = HexFormat.of().parseHex(sha1Hex.trim());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("resource-pack.sha1 が16進数ではありません。ダウンロードして計算します");
            }
        }
    }

    public void start() {
        if (url.isEmpty()) {
            plugin.getLogger().info("resource-pack.url が空なので、リソースパックは送りません");
            return;
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (hash == null) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::computeHash);
        }
    }

    private void computeHash() {
        try {
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(15)).build();
            HttpResponse<byte[]> res = client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) {
                plugin.getLogger().warning("リソースパックのダウンロードに失敗しました(HTTP " + res.statusCode() + ")");
                return;
            }
            byte[] h = MessageDigest.getInstance("SHA-1").digest(res.body());
            hash = h;
            plugin.getLogger().info("リソースパックの SHA-1: " + HexFormat.of().formatHex(h));
        } catch (Exception e) {
            plugin.getLogger().warning("リソースパックの SHA-1 を計算できませんでした: " + e.getMessage());
        }
    }

    public void send(Player player) {
        if (url.isEmpty()) return;
        player.addResourcePack(packId, url, hash, prompt, required);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) send(p);
        }, 20L);
    }
}
