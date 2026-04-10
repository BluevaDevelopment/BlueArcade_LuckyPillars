package net.blueva.arcade.modules.lucky_pillars.state;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simplified vote state for Lucky Pillars single modifier voting
 */
public class VoteState {

    private final Map<String, Integer> modifierVotes = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerVotes = new ConcurrentHashMap<>();
    private final String defaultModifier;

    public VoteState(String defaultModifier) {
        this.defaultModifier = defaultModifier != null ? defaultModifier : "none";
    }

    public void castVote(UUID playerId, String modifier) {
        if (playerId == null || modifier == null) {
            return;
        }

        String previous = playerVotes.put(playerId, modifier);

        if (previous != null && previous.equals(modifier)) {
            return;
        }

        if (previous != null) {
            modifierVotes.computeIfPresent(previous, (key, value) -> Math.max(0, value - 1));
        }
        modifierVotes.merge(modifier, 1, Integer::sum);
    }

    public int getVotes(String modifier) {
        if (modifier == null) {
            return 0;
        }
        return modifierVotes.getOrDefault(modifier, 0);
    }

    public String getPlayerVote(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return playerVotes.get(playerId);
    }

    public void clearPlayerVotes(UUID playerId) {
        if (playerId == null) {
            return;
        }
        String modifier = playerVotes.remove(playerId);
        if (modifier == null) {
            return;
        }
        modifierVotes.computeIfPresent(modifier, (key, value) -> {
            int nextValue = value - 1;
            return nextValue > 0 ? nextValue : null;
        });
    }

    public void clearAll() {
        playerVotes.clear();
        modifierVotes.clear();
    }

    public String resolveWinner() {
        if (modifierVotes.isEmpty()) {
            return defaultModifier;
        }

        int maxVotes = -1;
        String winningModifier = null;
        boolean tie = false;
        for (Map.Entry<String, Integer> entry : modifierVotes.entrySet()) {
            int count = entry.getValue();
            if (count > maxVotes) {
                maxVotes = count;
                winningModifier = entry.getKey();
                tie = false;
            } else if (count == maxVotes) {
                tie = true;
            }
        }

        if (winningModifier == null) {
            return defaultModifier;
        }

        if (tie) {
            return defaultModifier;
        }

        return winningModifier;
    }

    public boolean hasVotes() {
        return !modifierVotes.isEmpty() && modifierVotes.values().stream().anyMatch(count -> count > 0);
    }
}
