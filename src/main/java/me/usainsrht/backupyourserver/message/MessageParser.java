package me.usainsrht.backupyourserver.message;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;
import org.bukkit.configuration.ConfigurationSection;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class MessageParser {

    private MessageParser() {
    }

    public static Message parse(final ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        final Message.Builder builder = Message.builder();

        final List<String> chatMessages = section.getStringList("chat-messages");
        if (!chatMessages.isEmpty()) {
            builder.chatMessages(chatMessages);
        }

        final String actionBar = section.getString("action-bar");
        if (actionBar != null && !actionBar.isBlank()) {
            builder.actionBar(actionBar);
        }

        final List<Sound> sounds = parseSounds(section.getMapList("sounds"));
        if (!sounds.isEmpty()) {
            builder.sounds(sounds);
        }

        final ConfigurationSection titleSection = section.getConfigurationSection("title");
        if (titleSection != null) {
            builder.title(
                    titleSection.getString("title"),
                    titleSection.getString("subtitle"),
                    parseTitleTimes(titleSection.getConfigurationSection("times"))
            );
        }

        final Message message = builder.build();
        return message.isEmpty() ? null : message;
    }

    private static List<Sound> parseSounds(final List<?> rawSounds) {
        final List<Sound> sounds = new ArrayList<>();
        if (rawSounds == null) {
            return sounds;
        }

        for (final Object entry : rawSounds) {
            if (!(entry instanceof java.util.Map<?, ?> map)) {
                continue;
            }
            final Object keyValue = map.get("key");
            if (keyValue == null) {
                continue;
            }
            final double volume = map.get("volume") instanceof Number number ? number.doubleValue() : 1.0D;
            final double pitch = map.get("pitch") instanceof Number number ? number.doubleValue() : 1.0D;
            sounds.add(Sound.sound(Key.key(String.valueOf(keyValue)), Sound.Source.MASTER, (float) volume, (float) pitch));
        }

        return sounds;
    }

    private static Title.Times parseTitleTimes(final ConfigurationSection section) {
        if (section == null) {
            return Title.DEFAULT_TIMES;
        }
        return Title.Times.times(
                Duration.ofMillis(section.getInt("fade-in", 10) * 50L),
                Duration.ofMillis(section.getInt("stay", 70) * 50L),
                Duration.ofMillis(section.getInt("fade-out", 20) * 50L)
        );
    }
}
