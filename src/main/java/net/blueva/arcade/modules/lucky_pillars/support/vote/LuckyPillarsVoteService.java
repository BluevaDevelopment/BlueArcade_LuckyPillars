package net.blueva.arcade.modules.lucky_pillars.support.vote;

import net.blueva.arcade.api.ModuleAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.api.ui.ItemAPI;
import net.blueva.arcade.api.ui.LobbyItemDefinition;
import net.blueva.arcade.api.ui.MenuAPI;
import net.blueva.arcade.api.ui.MessageAPI;
import net.blueva.arcade.api.ui.menu.MenuDefinition;
import net.blueva.arcade.modules.lucky_pillars.game.LuckyPillarsGame;
import net.blueva.arcade.modules.lucky_pillars.state.ArenaState;
import net.blueva.arcade.modules.lucky_pillars.state.VoteState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Simplified vote service for Lucky Pillars single modifier voting
 */
public class LuckyPillarsVoteService {

    private static final String VOTE_PERMISSION_BASE = "bluearcade.lucky_pillars.votes";
    public static final String COMMAND = "lucky_pillarsvote";
    public static final String MENU_MODIFIERS = "vote_modifiers";

    private static final Set<String> MODIFIER_OPTIONS = Set.of(
            "none", "elytra", "swap", "speed", "slow_fall",
            "invisibility", "double_health", "one_heart", "unbreakable", "ultra_jump"
    );

    private final ModuleConfigAPI moduleConfig;
    private final MenuAPI<Player, Material> menuAPI;
    private final ItemAPI<Player, ItemStack, Material> itemAPI;
    private final String moduleId;
    private final LuckyPillarsVoteMenuRepository menuRepository;
    private final VoteState waitingVoteState;
    private LuckyPillarsGame game;

    public LuckyPillarsVoteService(ModuleConfigAPI moduleConfig,
                              MenuAPI<Player, Material> menuAPI,
                              ItemAPI<Player, ItemStack, Material> itemAPI,
                              String moduleId) {
        this.moduleConfig = moduleConfig;
        this.menuAPI = menuAPI;
        this.itemAPI = itemAPI;
        this.moduleId = moduleId;
        this.menuRepository = new LuckyPillarsVoteMenuRepository(moduleConfig);
        this.menuRepository.loadMenus();
        registerMenusWithCore();
        this.waitingVoteState = createVoteState();
    }

    /**
     * Register menu opener with the core so OPEN actions can find our menus.
     */
    private void registerMenusWithCore() {
        LuckyPillarsMenuAPI luckyPillarsMenuAPI = new LuckyPillarsMenuAPI(this.menuAPI, this);
        menuAPI.registerModuleMenuAPI("lucky_pillars", luckyPillarsMenuAPI);
    }

    public VoteState createVoteState() {
        String defaultModifier = normalizeOption(
                moduleConfig.getString("votes.defaults.modifier", "none"), 
                MODIFIER_OPTIONS, 
                "none"
        );
        return new VoteState(defaultModifier);
    }

    public VoteState getWaitingVoteState() {
        return waitingVoteState;
    }

    public void setGame(LuckyPillarsGame game) {
        this.game = game;
    }

    public void applyPendingVotes(ArenaState state, List<Player> players) {
        if (state == null || players == null || players.isEmpty()) {
            return;
        }
        VoteState voteState = state.getVoteState();
        if (voteState == null) {
            return;
        }

        for (Player player : players) {
            if (player == null) {
                continue;
            }
            String modifier = waitingVoteState.getPlayerVote(player.getUniqueId());
            if (modifier != null) {
                voteState.castVote(player.getUniqueId(), modifier);
            }
            waitingVoteState.clearPlayerVotes(player.getUniqueId());
        }
        waitingVoteState.clearAll();
    }

    public void registerWaitingItem() {
        if (itemAPI == null || moduleConfig == null) {
            return;
        }

        boolean enabled = moduleConfig.getBoolean("waiting_items.vote_settings.enabled", true);
        if (!enabled) {
            return;
        }

        String materialName = moduleConfig.getString("waiting_items.vote_settings.material", "NAME_TAG");
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            material = Material.NAME_TAG;
        }

        int slot = moduleConfig.getInt("waiting_items.vote_settings.slot", 1);
        String displayName = moduleConfig.getString("waiting_items.vote_settings.display_name");
        List<String> lore = moduleConfig.getStringList("waiting_items.vote_settings.lore");

        LobbyItemDefinition<Material> definition = new LobbyItemDefinition<>(
                "lucky_pillars_vote_settings",
                material,
                slot,
                displayName,
                lore,
                List.of(),
                true
        );

        itemAPI.registerWaitingItem(moduleId, definition);
    }

    public void registerClickHandler(LuckyPillarsGame game) {
        itemAPI.registerClickHandler("lucky_pillars_vote_settings",
                player -> game.handleVoteCommand(player, new String[]{"menu", "modifiers"}));
    }

    public boolean handleVoteCommand(Player player,
                                     GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                     ArenaState state,
                                     String[] args) {
        if (player == null || context == null || state == null) {
            return false;
        }

        GamePhase phase = context.getPhase();
        if (phase == GamePhase.PLAYING || phase == GamePhase.ENDING || phase == GamePhase.FINISHED) {
            sendMessage(context, player, "votes.messages.not_available");
            return true;
        }

        if (args.length == 0) {
            return openMenu(player, state, MENU_MODIFIERS);
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("menu")) {
            return openMenu(player, state, MENU_MODIFIERS);
        }

        if (action.equals("vote")) {
            if (args.length < 3) {
                sendMessage(context, player, "votes.messages.invalid");
                return true;
            }

            String modifier = args[2].toLowerCase(Locale.ROOT);
            
            if (!isModifierValid(modifier)) {
                sendMessage(context, player, "votes.messages.invalid");
                return true;
            }
            if (!hasModifierPermission(player, modifier)) {
                String message = moduleConfig.getStringFrom("language.yml", "votes.messages.no_permission");
                if (message != null) {
                    message = message.replace("{modifier}", getModifierLabel(modifier));
                    context.getMessagesAPI().sendRaw(player, message);
                }
                return true;
            }

            VoteState voteState = state.getVoteState();
            if (voteState == null) {
                return true;
            }

            voteState.castVote(player.getUniqueId(), modifier);
            
            String modifierLabel = getModifierLabel(modifier);
            String message = moduleConfig.getStringFrom("language.yml", "votes.messages.broadcast");
            if (message != null && !message.isBlank()) {
                message = message.replace("{player}", player.getName())
                        .replace("{modifier}", modifierLabel);
                broadcastMessage(context, message);
            }
            return true;
        }

        return openMenu(player, state, MENU_MODIFIERS);
    }

    public boolean handleVoteCommandWithoutContext(Player player, String[] args) {
        if (player == null) {
            return false;
        }

        String[] safeArgs = args != null ? args : new String[0];
        if (safeArgs.length == 0) {
            return openMenuWaiting(player);
        }

        String action = safeArgs[0].toLowerCase(Locale.ROOT);
        if (action.equals("menu")) {
            return openMenuWaiting(player);
        }

        if (action.equals("vote")) {
            if (safeArgs.length < 3) {
                return true;
            }

            String modifier = safeArgs[2].toLowerCase(Locale.ROOT);
            if (!isModifierValid(modifier)) {
                return true;
            }
            if (!hasModifierPermission(player, modifier)) {
                return true;
            }

            waitingVoteState.castVote(player.getUniqueId(), modifier);
            broadcastWaitingVote(player, modifier);
            return openMenuWaiting(player);
        }

        return false;
    }

    public void applyVotes(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                           ArenaState state) {
        if (context == null || state == null) {
            return;
        }

        VoteState voteState = state.getVoteState();
        if (voteState == null) {
            return;
        }

        String modifier = voteState.resolveWinner();
        state.setSelectedModifier(modifier);
    }

    public void broadcastVoteResults(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                     ArenaState state) {
        if (context == null || state == null) {
            return;
        }
        VoteState voteState = state.getVoteState();
        if (voteState == null) {
            return;
        }

        String modifier = voteState.resolveWinner();
        String modifierLabel = getModifierLabel(modifier);
        
        String source = voteState.hasVotes() ? 
                moduleConfig.getStringFrom("language.yml", "votes.messages.selected.sources.popular", "by popular vote") :
                moduleConfig.getStringFrom("language.yml", "votes.messages.selected.sources.default", "by default");
        
        String message = moduleConfig.getStringFrom("language.yml", "votes.messages.selected.modifier");
        if (message != null && !message.isBlank()) {
            message = message.replace("{modifier}", modifierLabel)
                    .replace("{source}", source);
            broadcastMessage(context, message);
        }
    }

    private void broadcastWaitingVote(Player player, String modifier) {
        if (player == null || modifier == null) {
            return;
        }

        String message = moduleConfig.getStringFrom("language.yml", "votes.messages.broadcast");
        if (message == null || message.isBlank()) {
            return;
        }

        message = message.replace("{player}", player.getName())
                .replace("{modifier}", getModifierLabel(modifier));

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getGameContext(player);
        if (context == null) {
            sendWaitingBroadcast(player, message);
            return;
        }

        broadcastMessage(context, message);
    }

    private void sendWaitingBroadcast(Player player, String message) {
        if (player == null || message == null || message.isBlank()) {
            return;
        }

        @SuppressWarnings("unchecked")
        MessageAPI<Player> messagesAPI = (MessageAPI<Player>) ModuleAPI.getMessagesAPI();
        if (messagesAPI != null) {
            messagesAPI.sendRaw(player, message);
            return;
        }

        player.sendMessage(message);
    }

    private GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getGameContext(Player player) {
        if (game == null || player == null) {
            return null;
        }
        return game.getContext(player);
    }

    private boolean openMenu(Player player, ArenaState state, String menuId) {
        VoteState voteState = state != null ? state.getVoteState() : null;
        return openMenu(player, voteState, menuId);
    }

    private boolean openMenuWaiting(Player player) {
        return openMenu(player, waitingVoteState, MENU_MODIFIERS);
    }

    private boolean openMenu(Player player, VoteState voteState, String menuId) {
        if (menuAPI == null || player == null) {
            return false;
        }

        MenuDefinition<Material> menu = menuRepository.getMenu(menuId);
        if (menu == null) {
            return false;
        }

        return menuAPI.openMenu(player, menu, buildPlaceholders(player, voteState));
    }

    private java.util.Map<String, String> buildPlaceholders(Player player, VoteState voteState) {
        java.util.Map<String, String> placeholders = new java.util.HashMap<>();
        for (String option : MODIFIER_OPTIONS) {
            placeholders.put("{votes_modifier_" + option + "}", String.valueOf(voteState != null
                    ? voteState.getVotes(option)
                    : 0));
        }
        placeholders.put("{selected_modifier}", resolveWinningLabel(voteState));
        placeholders.put("{player_vote_modifier}", resolvePlayerVoteLabel(player, voteState));
        return placeholders;
    }

    private String resolveWinningLabel(VoteState voteState) {
        String option = voteState != null ? voteState.resolveWinner() : null;
        return getModifierLabel(option != null ? option : waitingVoteState.resolveWinner());
    }

    private String resolvePlayerVoteLabel(Player player, VoteState voteState) {
        if (player == null || voteState == null) {
            return getModifierLabel(waitingVoteState.resolveWinner());
        }
        String option = voteState.getPlayerVote(player.getUniqueId());
        if (option == null) {
            option = waitingVoteState.resolveWinner();
        }
        return getModifierLabel(option);
    }

    private boolean isModifierValid(String modifier) {
        return modifier != null && MODIFIER_OPTIONS.contains(modifier.toLowerCase(Locale.ROOT));
    }

    private boolean hasModifierPermission(Player player, String modifier) {
        if (player == null || modifier == null) {
            return false;
        }
        String permission = VOTE_PERMISSION_BASE + "." + modifier.toLowerCase(Locale.ROOT);
        return player.hasPermission(permission) || player.hasPermission(VOTE_PERMISSION_BASE + ".*");
    }

    private String getModifierLabel(String modifier) {
        if (modifier == null) {
            return "";
        }
        String label = moduleConfig.getStringFrom("language.yml", "votes.labels.modifiers." + modifier.toLowerCase(Locale.ROOT));
        return label != null ? label : modifier;
    }

    private String normalizeOption(String raw, Set<String> validOptions, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return validOptions.contains(normalized) ? normalized : fallback;
    }

    private void sendMessage(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                            Player player, String messagePath) {
        if (context == null || player == null || messagePath == null) {
            return;
        }
        String message = moduleConfig.getStringFrom("language.yml", messagePath);
        if (message != null && !message.isBlank()) {
            context.getMessagesAPI().sendRaw(player, message);
        }
    }

    private void broadcastMessage(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                 String message) {
        if (context == null || message == null || message.isBlank()) {
            return;
        }
        for (Player player : context.getPlayers()) {
            if (player != null && player.isOnline()) {
                context.getMessagesAPI().sendRaw(player, message);
            }
        }
    }
}
