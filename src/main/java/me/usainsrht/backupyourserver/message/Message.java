package me.usainsrht.backupyourserver.message;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public final class Message {

    private final @Nullable Collection<String> chatMessages;
    private final @Nullable String actionBar;
    private final @Nullable Collection<Sound> sounds;
    private final @Nullable String titleText;
    private final @Nullable String subtitleText;
    private final @Nullable Title.Times titleTimes;

    public Message(
            final @Nullable Collection<String> chatMessages,
            final @Nullable String actionBar,
            final @Nullable Collection<Sound> sounds,
            final @Nullable String titleText,
            final @Nullable String subtitleText,
            final @Nullable Title.Times titleTimes
    ) {
        this.chatMessages = chatMessages == null ? null : Collections.unmodifiableCollection(chatMessages);
        this.actionBar = actionBar;
        this.sounds = sounds == null ? null : Collections.unmodifiableCollection(sounds);
        this.titleText = titleText;
        this.subtitleText = subtitleText;
        this.titleTimes = titleTimes;
    }

    public @Nullable Collection<String> chatMessages() {
        return chatMessages;
    }

    public @Nullable String actionBar() {
        return actionBar;
    }

    public @Nullable Collection<Sound> sounds() {
        return sounds;
    }

    /**
     * Returns a {@link Title} when title text is configured. Components contain raw MiniMessage strings
     * and are parsed at send time by {@link MessageSender}.
     */
    public @Nullable Title title() {
        if (titleText == null && subtitleText == null) {
            return null;
        }
        return Title.title(
                net.kyori.adventure.text.Component.text(titleText == null ? "" : titleText),
                net.kyori.adventure.text.Component.text(subtitleText == null ? "" : subtitleText),
                titleTimes == null ? Title.DEFAULT_TIMES : titleTimes
        );
    }

    public @Nullable String titleText() {
        return titleText;
    }

    public @Nullable String subtitleText() {
        return subtitleText;
    }

    public @Nullable Title.Times titleTimes() {
        return titleTimes;
    }

    public boolean isEmpty() {
        return (chatMessages == null || chatMessages.isEmpty())
                && actionBar == null
                && (sounds == null || sounds.isEmpty())
                && titleText == null
                && subtitleText == null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Collection<String> chatMessages;
        private String actionBar;
        private Collection<Sound> sounds;
        private String titleText;
        private String subtitleText;
        private Title.Times titleTimes;

        public Builder chatMessages(final Collection<String> chatMessages) {
            this.chatMessages = chatMessages;
            return this;
        }

        public Builder actionBar(final String actionBar) {
            this.actionBar = actionBar;
            return this;
        }

        public Builder sounds(final Collection<Sound> sounds) {
            this.sounds = sounds;
            return this;
        }

        public Builder title(final String titleText, final String subtitleText, final Title.Times titleTimes) {
            this.titleText = titleText;
            this.subtitleText = subtitleText;
            this.titleTimes = titleTimes;
            return this;
        }

        public Message build() {
            return new Message(chatMessages, actionBar, sounds, titleText, subtitleText, titleTimes);
        }
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Message message)) {
            return false;
        }
        return Objects.equals(chatMessages, message.chatMessages)
                && Objects.equals(actionBar, message.actionBar)
                && Objects.equals(sounds, message.sounds)
                && Objects.equals(titleText, message.titleText)
                && Objects.equals(subtitleText, message.subtitleText)
                && Objects.equals(titleTimes, message.titleTimes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chatMessages, actionBar, sounds, titleText, subtitleText, titleTimes);
    }
}
