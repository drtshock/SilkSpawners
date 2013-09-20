package de.dustplanet.silkspawners.listeners;

import net.minecraft.server.v1_6_R3.Entity;
import net.minecraft.server.v1_6_R3.EntityTypes;
import net.minecraft.server.v1_6_R3.World;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_6_R3.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;

import de.dustplanet.silkspawners.SilkSpawners;
import de.dustplanet.silkspawners.events.SilkSpawnersSpawnerChangeEvent;
import de.dustplanet.util.SilkUtil;

/**
 * To show a chat message that a player is holding a mob spawner and it's type
 * 
 * @author (former) mushroomhostage
 * @author xGhOsTkiLLeRx
 */

public class SilkSpawnersPlayerListener implements Listener {
    private SilkSpawners plugin;
    private SilkUtil su;

    public SilkSpawnersPlayerListener(SilkSpawners instance, SilkUtil util) {
	plugin = instance;
	su = util;
    }

    @EventHandler
    public void onPlayerHoldItem(PlayerItemHeldEvent event) {
	// Check if we should notify the player. The second condition is the
	// permission and that the slot isn't null and the item is a mob spawner
	if (plugin.config.getBoolean("notifyOnHold")
		&& plugin.hasPermission((Player) event.getPlayer(), "silkspawners.info")
		&& event.getPlayer().getInventory().getItem(event.getNewSlot()) != null
		&& event.getPlayer().getInventory().getItem(event.getNewSlot()).getType().equals(Material.MOB_SPAWNER)) {

	    // Get ID
	    short entityID = su.getStoredSpawnerItemEntityID(event.getPlayer().getInventory().getItem(event.getNewSlot()));
	    // Check for unkown/invalid ID
	    if (entityID == 0 || !su.knownEids.contains(entityID)) {
		entityID = su.defaultEntityID;
	    }
	    // Get the name from the entityID
	    String spawnerName = su.getCreatureName(entityID);
	    Player player = event.getPlayer();
	    su.notify(player, spawnerName, entityID);
	}
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
	if (!event.hasItem() || !event.hasBlock()) {
	    return;
	}
	ItemStack item = event.getItem();
	Block block = event.getClickedBlock();
	Player player = event.getPlayer();
	// If we use a spawn egg
	if (item != null && item.getType() == su.SPAWN_EGG) {
	    // Get the entityID
	    short entityID = item.getDurability();
	    // Clicked spawner with monster egg to change type
	    if (event.getAction() == Action.LEFT_CLICK_BLOCK && block != null && block.getType() == Material.MOB_SPAWNER) {
		// WorldGuard region protection
		if (!su.canBuildHere(player, block.getLocation())) {
		    return;
		}

		// Mob
		String mobName = su.getCreatureName(entityID).toLowerCase().replace(" ", "");

		if (!plugin.hasPermission(player, "silkspawners.changetypewithegg." + mobName)
			&& !plugin.hasPermission(player, "silkspawners.changetypewithegg.*")) {
		    player.sendMessage(ChatColor.translateAlternateColorCodes('\u0026', plugin.localization .getString("noPermissionChangingWithEggs")));
		    return;
		}

		// Call the event and maybe change things!
		SilkSpawnersSpawnerChangeEvent changeEvent = new SilkSpawnersSpawnerChangeEvent(player, block, entityID, su.getSpawnerEntityID(block));
		plugin.getServer().getPluginManager().callEvent(changeEvent);
		// See if we need to stop
		if (changeEvent.isCancelled()) {
		    return;
		}
		// Get the new ID (might be changed)
		entityID = changeEvent.getEntityID();

		su.setSpawnerType(block, entityID, player, ChatColor.translateAlternateColorCodes('\u0026', plugin.localization.getString("changingDeniedWorldGuard")));
		player.sendMessage(ChatColor.translateAlternateColorCodes('\u0026', plugin.localization.getString("changedSpawner")).replace("%creature%", su.getCreatureName(entityID)));

		// Consume egg
		if (plugin.config.getBoolean("consumeEgg", true)) {
		    su.reduceEggs(player);
		}
		// Normal spawning
	    } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
		if (plugin.config.getBoolean("spawnEggToSpawner", false)) {
		    Block targetBlock = block.getRelative(BlockFace.UP);
		    // Check if block above is air
		    if (targetBlock.getType() == Material.AIR) {
			targetBlock.setType(Material.MOB_SPAWNER);
			su.setSpawnerEntityID(targetBlock, entityID);
			// Prevent mob spawning
			// Should we consume the egg?
			if (plugin.config.getBoolean("consumeEgg", true)) {
			    su.reduceEggs(player);
			}
		    } else {
			player.sendMessage(ChatColor.translateAlternateColorCodes('\u0026', plugin.localization.getString("noSpawnerHere")));
		    }
		    event.setCancelled(true);
		}
		// Disabled by default, since it is dangerous
		else if (plugin.config.getBoolean("spawnEggOverride", false)) {
		    // Name
		    String mobID = su.eid2MobID.get(entityID);
		    // Are we allowed to spawn?
		    boolean allowed = plugin.config.getBoolean("spawnEggOverrideSpawnDefault", true);
		    if (mobID != null) {
			allowed = plugin.mobs.getBoolean("creatures." + mobID
				+ ".enableSpawnEggOverrideAllowSpawn", allowed);
		    }
		    // Deny spawning
		    if (!allowed) {
			player.sendMessage(ChatColor.translateAlternateColorCodes('\u0026',
				plugin.localization
				.getString("spawningDenied")
				.replace("%ID%", Short.toString(entityID)))
				.replace("%creature%", su.getCreatureName(entityID)));
			event.setCancelled(true);
			return;
		    }
		    // Bukkit doesn't allow us to spawn wither or dragons and so
		    // on. NMS here we go!
		    // https://github.com/Bukkit/CraftBukkit/blob/master/src/main/java/net/minecraft/server/ItemMonsterEgg.java#L23

		    // Notify
		    plugin.informPlayer(player, ChatColor.translateAlternateColorCodes('\u0026',
			    plugin.localization.getString("spawning")
			    .replace("%ID%", Short.toString(entityID)))
			    .replace("%creature%", su.getCreatureName(entityID)));

		    // We can spawn using the direct method from EntityTypes
		    // https://github.com/Bukkit/mc-dev/blob/master/net/minecraft/server/EntityTypes.java#L67
		    World world = ((CraftWorld) player.getWorld()).getHandle();
		    Entity entity = EntityTypes.a(entityID, world);
		    // Should actually never happen since the method above
		    // contains a null check, too
		    if (entity == null) {
			plugin.getLogger().warning("Failed to spawn, falling through. You should report this (entity == null)!");
			return;
		    }

		    // Spawn on top of targeted block
		    Location location = block.getLocation().add(0, 1, 0);
		    double x = location.getX(), y = location.getY(), z = location.getZ();

		    // Random facing
		    entity.setPositionRotation(x, y, z, world.random.nextFloat() * 360.0f, 0.0f);
		    // We need to add the entity to the world, reason is of
		    // course a spawn egg so that other events can handle this
		    world.addEntity(entity, SpawnReason.SPAWNER_EGG);

		    su.reduceEggs(player);

		    // Prevent normal spawning
		    event.setCancelled(true);
		}
	    }
	}
    }

    // Color the pickup spawner
    @EventHandler
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
	if (event.getItem().getItemStack().getType() == Material.MOB_SPAWNER) {
	    if (!su.coloredNames) {
		return;
	    }
	    ItemStack item = event.getItem().getItemStack();
	    ItemStack itemNew = su.newSpawnerItem(item.getDurability(), plugin.localization.getString("spawnerName"));
	    event.getItem().setItemStack(itemNew);
	}
    }
}