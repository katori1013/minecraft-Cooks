package jp.minecraftcooks.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

/** messages.yml の文章(MiniMessage 形式)。<prefix> は prefix キーの文章に置き換わる。 */
public final class Messages {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final YamlConfiguration yaml;

    public Messages(YamlConfiguration yaml) {
        this.yaml = yaml;
    }

    public Component get(String key, TagResolver... args) {
        String raw = yaml.getString(key);
        if (raw == null) return Component.text("[" + key + "]");
        TagResolver prefix = Placeholder.parsed("prefix", yaml.getString("prefix", ""));
        return MM.deserialize(raw, TagResolver.resolver(TagResolver.resolver(args), prefix));
    }

    public void send(CommandSender to, String key, TagResolver... args) {
        to.sendMessage(get(key, args));
    }

    /** プレイヤーの入力など、タグとして読ませたくない文字を入れるとき。 */
    public static TagResolver text(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    public static TagResolver comp(String name, Component value) {
        return Placeholder.component(name, value);
    }
}
