package me.usainsrht.backupyourserver.message;

import io.github.miniplaceholders.api.MiniPlaceholders;
import me.usainsrht.backupyourserver.placeholder.BackupPlaceholders;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;

public final class MessageSender {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private MessageSender() {
    }

    public static void send(final CommandSender sender, final Message message, final TagResolver... extraResolvers) {
        if (message == null || message.isEmpty()) {
            return;
        }

        final Audience audience = sender instanceof Player player ? player : sender;
        final TagResolver resolver = buildResolver(sender, extraResolvers);

        final Collection<String> chatMessages = message.chatMessages();
        if (chatMessages != null) {
            for (final String line : chatMessages) {
                audience.sendMessage(deserialize(line, audience, resolver));
            }
        }

        if (message.actionBar() != null) {
            audience.sendActionBar(deserialize(message.actionBar(), audience, resolver));
        }

        if (message.sounds() != null && sender instanceof Player player) {
            for (final net.kyori.adventure.sound.Sound sound : message.sounds()) {
                player.playSound(sound);
            }
        }

        if ((message.titleText() != null || message.subtitleText() != null) && sender instanceof Player player) {
            final Component title = message.titleText() == null
                    ? Component.empty()
                    : deserialize(message.titleText(), audience, resolver);
            final Component subtitle = message.subtitleText() == null
                    ? Component.empty()
                    : deserialize(message.subtitleText(), audience, resolver);
            final Title.Times times = message.titleTimes() == null ? Title.DEFAULT_TIMES : message.titleTimes();
            player.showTitle(Title.title(title, subtitle, times));
        }
    }

    public static void broadcast(final Collection<? extends Player> players, final Message message, final TagResolver... extraResolvers) {
        for (final Player player : players) {
            send(player, message, extraResolvers);
        }
    }

    private static TagResolver buildResolver(final CommandSender sender, final TagResolver... extraResolvers) {
        final TagResolver.Builder builder = TagResolver.builder()
                .resolvers(BackupPlaceholders.resolver());

        if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders")) {
            if (sender instanceof Player) {
                builder.resolvers(MiniPlaceholders.audiencePlaceholders());
            } else {
                builder.resolvers(MiniPlaceholders.globalPlaceholders());
            }
        }

        if (extraResolvers != null) {
            builder.resolvers(extraResolvers);
        }

        return builder.build();
    }

    private static Component deserialize(final String input, final Audience audience, final TagResolver resolver) {
        if (audience instanceof Player player) {
            return MINI_MESSAGE.deserialize(input, player, resolver);
        }
        return MINI_MESSAGE.deserialize(input, resolver);
    }
}
