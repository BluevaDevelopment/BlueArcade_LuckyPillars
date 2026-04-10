package net.blueva.arcade.modules.lucky_pillars.listener;

import net.blueva.arcade.modules.lucky_pillars.game.LuckyPillarsGame;
import net.blueva.arcade.modules.lucky_pillars.support.vote.LuckyPillarsVoteService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Arrays;
import java.util.Locale;

public class LuckyPillarsVoteListener implements Listener {

    private final LuckyPillarsGame game;

    public LuckyPillarsVoteListener(LuckyPillarsGame game) {
        this.game = game;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVoteCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();

        if (message == null || message.isBlank()) {
            return;
        }

        String normalized = message.trim().toLowerCase(Locale.ROOT);
        String commandPrefix = "/" + LuckyPillarsVoteService.COMMAND;
        if (!normalized.startsWith(commandPrefix)) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        String[] parts = message.substring(1).trim().split("\\s+");
        String[] args = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];

        game.handleVoteCommand(player, args);
    }
}
