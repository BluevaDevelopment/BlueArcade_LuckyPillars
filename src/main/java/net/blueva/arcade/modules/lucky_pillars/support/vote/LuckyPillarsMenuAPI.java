package net.blueva.arcade.modules.lucky_pillars.support.vote;

import net.blueva.arcade.api.ui.MenuAPI;
import net.blueva.arcade.api.ui.ModuleActionHandler;
import net.blueva.arcade.api.ui.menu.DynamicMenuDefinition;
import net.blueva.arcade.api.ui.menu.MenuDefinition;
import net.blueva.arcade.api.ui.menu.MenuEntry;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * Wrapper around MenuAPI that adds support for opening LuckyPillars vote menus by ID.
 */
public class LuckyPillarsMenuAPI implements MenuAPI<Player, Material> {

    private final MenuAPI<Player, Material> delegate;
    private final LuckyPillarsVoteService voteService;

    public LuckyPillarsMenuAPI(MenuAPI<Player, Material> delegate, LuckyPillarsVoteService voteService) {
        this.delegate = delegate;
        this.voteService = voteService;
    }

    @Override
    public boolean openMenu(Player player, MenuDefinition<Material> menu, Map<String, String> placeholders) {
        return delegate.openMenu(player, menu, placeholders);
    }

    @Override
    public boolean openDynamicMenu(Player player, DynamicMenuDefinition<Material> menu, List<MenuEntry<Material>> entries, int page, Map<String, String> placeholders) {
        return delegate.openDynamicMenu(player, menu, entries, page, placeholders);
    }

    @Override
    public boolean isBedrockPlayer(Player player) {
        return delegate.isBedrockPlayer(player);
    }

    @Override
    public void registerModuleActionHandler(String moduleId, ModuleActionHandler<Player> handler) {
        delegate.registerModuleActionHandler(moduleId, handler);
    }

    @Override
    public void unregisterModuleActionHandler(String moduleId) {
        delegate.unregisterModuleActionHandler(moduleId);
    }

    @Override
    public boolean openMenuById(Player player, String menuId) {
        // For Lucky Pillars, we only have one menu: modifiers
        return voteService.handleVoteCommandWithoutContext(player, new String[]{"menu", "modifiers"});
    }
}
