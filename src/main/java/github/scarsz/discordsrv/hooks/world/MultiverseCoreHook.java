/*
 * DiscordSRV - https://github.com/DiscordSRV/DiscordSRV
 *
 * Copyright (C) 2016 - 2024 Austin "Scarsz" Shapiro
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package github.scarsz.discordsrv.hooks.world;

import com.onarandombox.MultiverseCore.api.MultiverseWorld;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.hooks.PluginHook;
import github.scarsz.discordsrv.util.PluginUtil;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * @deprecated Kept for backwards compatibility
 */
@Deprecated
@SuppressWarnings("unused")
public class MultiverseCoreHook implements PluginHook {

    /**
     * @deprecated Use {@link DiscordSRV#getWorldAlias(String)} instead
     */
    @Deprecated
    public static String getWorldAlias(String world) {
        try {
            if (!PluginUtil.pluginHookIsEnabled("Multiverse-Core")) return world;

            com.onarandombox.MultiverseCore.MultiverseCore multiversePlugin = (com.onarandombox.MultiverseCore.MultiverseCore) Bukkit.getPluginManager().getPlugin("Multiverse-Core");
            if (multiversePlugin != null) {
                MultiverseWorld multiverseWorld = multiversePlugin.getMVWorldManager().getMVWorld(world);
                if (multiverseWorld != null) {
                    String alias = multiverseWorld.getAlias();
                    return StringUtils.isNotBlank(alias) ? alias : world;
                }
            }
        } catch (Exception e) {
            DiscordSRV.error(e);
        }
        return world;
    }

    @Override
    public Plugin getPlugin() {
        return PluginUtil.getPlugin("Multiverse-Core");
    }

}
