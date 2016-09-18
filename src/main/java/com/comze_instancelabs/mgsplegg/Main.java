package com.comze_instancelabs.mgsplegg;

import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BlockIterator;

import com.comze_instancelabs.minigamesapi.Arena;
import com.comze_instancelabs.minigamesapi.ArenaConfigStrings;
import com.comze_instancelabs.minigamesapi.ArenaSetup;
import com.comze_instancelabs.minigamesapi.ArenaState;
import com.comze_instancelabs.minigamesapi.MinigamesAPI;
import com.comze_instancelabs.minigamesapi.PluginInstance;
import com.comze_instancelabs.minigamesapi.config.ArenasConfig;
import com.comze_instancelabs.minigamesapi.config.DefaultConfig;
import com.comze_instancelabs.minigamesapi.config.MessagesConfig;
import com.comze_instancelabs.minigamesapi.config.StatsConfig;
import com.comze_instancelabs.minigamesapi.util.Util;
import com.comze_instancelabs.minigamesapi.util.Validator;

public class Main extends JavaPlugin implements Listener {

	MinigamesAPI api = null;
	PluginInstance pli = null;
	static Main m = null;

	ICommandHandler ic;

	boolean allow_snowball_knockback = true;
	boolean give_snowballs_when_breaking_blocks = true;
	boolean whitelist = true;

	ArrayList<Integer> whitelist_ids = new ArrayList<Integer>();

	public void onEnable() {
		m = this;
		api = MinigamesAPI.getAPI().setupAPI(this, "splegg", IArena.class, new ArenasConfig(this), new MessagesConfig(this), new IClassesConfig(this), new StatsConfig(this, false), new DefaultConfig(this, false), false);
		PluginInstance pinstance = api.pinstances.get(this);
		pinstance.addLoadedArenas(loadArenas(this, pinstance.getArenasConfig()));
		Bukkit.getPluginManager().registerEvents(this, this);
		pinstance.arenaSetup = new IArenaSetup();
		pli = pinstance;
		ic = new ICommandHandler();

		this.getConfig().addDefault("config.allow_snowball_knockback", true);
		this.getConfig().addDefault("config.powerup_spawn_percentage", 10);
		this.getConfig().addDefault("config.shoot_with_shovels", true);
		this.getConfig().addDefault("config.shoot_eggs_instead_of_snowballs", true);
		this.getConfig().addDefault("config.die_below_bedrock_level", 4);
		this.getConfig().addDefault("config.hunger_when_not_breaking_blocks", false);
		this.getConfig().addDefault("config.give_snowballs_when_breaking_blocks", true);
		this.getConfig().addDefault("config.whitelist.enabled", false);
		this.getConfig().addDefault("config.whitelist.ids", "1;5;35");

		this.getConfig().options().copyDefaults(true);
		this.saveConfig();

		if (this.getConfig().isBoolean("config.die_below_bedrock_level"))
		{
			boolean die_below_zero = this.getConfig().getBoolean("config.die_below_bedrock_level");
			if (die_below_zero) {
				pli.getArenaListener().loseY = 100;
			}
		}
		else
		{
			pli.getArenaListener().loseY = this.getConfig().getInt("config.die_below_bedrock_level");
		}

		allow_snowball_knockback = getConfig().getBoolean("config.allow_snowball_knockback");
		give_snowballs_when_breaking_blocks = getConfig().getBoolean("config.give_snowballs_when_breaking_blocks");
		whitelist = getConfig().getBoolean("config.whitelist.enabled");

		for (String str : this.getConfig().getString("config.whitelist.ids").split(";")) {
			if (Util.isNumeric(str)) {
				whitelist_ids.add(Integer.parseInt(str));
			}
		}
	}

	public static ArrayList<Arena> loadArenas(JavaPlugin plugin, ArenasConfig cf) {
		ArrayList<Arena> ret = new ArrayList<Arena>();
		FileConfiguration config = cf.getConfig();
		if (!config.isSet("arenas")) {
			return ret;
		}
		for (String arena : config.getConfigurationSection(ArenaConfigStrings.ARENAS_PREFIX).getKeys(false)) {
			if (Validator.isArenaValid(plugin, arena, cf.getConfig())) {
				ret.add(initArena(arena));
			}
		}
		return ret;
	}

	public static IArena initArena(String arena) {
		IArena a = new IArena(m, arena);
		ArenaSetup s = MinigamesAPI.getAPI().pinstances.get(m).arenaSetup;
		a.init(Util.getSignLocationFromArena(m, arena), Util.getAllSpawns(m, arena), Util.getMainLobby(m), Util.getComponentForArena(m, arena, "lobby"), s.getPlayerCount(m, arena, true), s.getPlayerCount(m, arena, false), s.getArenaVIP(m, arena));
		return a;
	}

	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		ic.handleArgs(this, MinigamesAPI.getAPI().getPermissionGamePrefix("splegg"), "/" + cmd.getName(), sender, args);
		return true;
	}

	@EventHandler
	public void onPlayerDrop(PlayerDropItemEvent event) {
		if (pli.global_players.containsKey(event.getPlayer().getName())) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onEgg(PlayerEggThrowEvent event) {
		if (pli.global_players.containsKey(event.getPlayer().getName())) {
			event.setHatching(false);
		}
	}

	@EventHandler
	public void onEntityDamage(EntityDamageEvent event) {
		if (event.getEntity() instanceof Player) {
			Player p = (Player) event.getEntity();
			if (pli.global_players.containsKey(p.getName())) {
				IArena a = (IArena) pli.global_players.get(p.getName());
				if (a.getArenaState() == ArenaState.INGAME) {
					if (event.getCause() == DamageCause.ENTITY_ATTACK) {
						p.setHealth(20D);
						event.setCancelled(true);
						return;
					} else if (event.getCause() == DamageCause.PROJECTILE) {
						if (!this.allow_snowball_knockback) {
							event.setCancelled(true);
						}
					} else if (event.getCause() == DamageCause.FALL) {
						event.setCancelled(true);
					}
				}
			}
		}
	}

	@EventHandler
	public void onBreak(BlockBreakEvent event) {
		if (pli.global_players.containsKey(event.getPlayer().getName()) && !pli.global_lost.containsKey(event.getPlayer().getName())) {
			IArena a = (IArena) pli.global_players.get(event.getPlayer().getName());
			if (a.getArenaState() == ArenaState.INGAME) {
				if (whitelist) {
					if (whitelist_ids.contains(event.getBlock().getTypeId())) {
						event.setCancelled(true);
						return;
					}
				}
				if (give_snowballs_when_breaking_blocks) {
					event.getPlayer().getInventory().addItem(new ItemStack(Material.SNOW_BALL));
					event.getPlayer().updateInventory();
				}
				if (!a.pp.contains(event.getPlayer().getName())) {
					a.pp.add(event.getPlayer().getName());
					event.getPlayer().setFoodLevel(20);
				}
				a.getSmartReset().addChanged(event.getBlock(), event.getBlock().getType().equals(Material.CHEST));
				event.getBlock().setType(Material.AIR);
			} else {
				event.setCancelled(true);
			}
		}
	}

	@EventHandler
	public void onPlace(BlockPlaceEvent event) {
		if (pli.global_players.containsKey(event.getPlayer().getName())) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onProjectileLand(ProjectileHitEvent event) {
		if (event.getEntity() instanceof Snowball || event.getEntity() instanceof Egg || event.getEntity() instanceof Arrow) {
			if (event.getEntity().getShooter() instanceof Player) {
				Player player = (Player) event.getEntity().getShooter();
				if (player != null) {
					if (pli.global_players.containsKey(player.getName()) && !pli.global_lost.containsKey(player.getName())) {
						IArena a = (IArena) pli.global_players.get(player.getName());
						BlockIterator bi = new BlockIterator(event.getEntity().getWorld(), event.getEntity().getLocation().toVector(), event.getEntity().getVelocity().normalize(), 0.0D, 4);
						Block hit = null;
						while (bi.hasNext()) {
							hit = bi.next();
							if (hit.getTypeId() != 0) {
								break;
							}
						}
						if (hit != null) {
							if (whitelist) {
								if (whitelist_ids.contains(hit.getTypeId())) {
									return;
								}
							}
							a.getSmartReset().addChanged(hit.getLocation().getBlock(), hit.getLocation().getBlock().getType().equals(Material.CHEST));
							if (a.getBoundaries().containsLocWithoutY(hit.getLocation())) {
								hit.setTypeId(0);
								player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1F, 1F);
							}
						}
					}
				}
			}
		}
	}

	@EventHandler
	public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
		if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
			Player p = (Player) event.getEntity();
			Player attacker = (Player) event.getDamager();
			if (pli.global_players.containsKey(p.getName()) && pli.global_players.containsKey(attacker.getName())) {
				event.setCancelled(true);
			}
		}
	}

	@EventHandler
	public void onPlayerPickupItem(PlayerPickupItemEvent event) {
		Player p = event.getPlayer();
		if (pli.global_players.containsKey(p.getName())) {
			IArena a = (IArena) pli.global_players.get(p.getName());
			if (a.getArenaState() == ArenaState.INGAME) {
				if (event.getItem().getItemStack().getType() == Material.POTION) {
					p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 200, 1));
					p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1));
					p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1));
					event.setCancelled(true);
					event.getItem().remove();
				}
				for (Entity e : p.getNearbyEntities(3D, 3D, 3D)) {
					if (e instanceof Chicken) {
						e.remove();
					}
				}
			}
		}
	}

	@EventHandler
	public void onRightclick(PlayerInteractEvent event) {
		if (event.hasItem()) {
			if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
				if (pli.global_players.containsKey(event.getPlayer().getName())) {
					if (this.getConfig().getBoolean("config.shoot_with_shovels")) {
						if (event.getItem().getType() == Material.DIAMOND_SPADE || event.getItem().getType() == Material.IRON_SPADE || event.getItem().getType() == Material.GOLD_SPADE || event.getItem().getType() == Material.STONE_SPADE || event.getItem().getType() == Material.WOOD_SPADE) {
							if (this.getConfig().getBoolean("config.shoot_eggs_instead_of_snowballs")) {
								event.getPlayer().launchProjectile(Egg.class);
							} else {
								event.getPlayer().launchProjectile(Snowball.class);
							}
						}
					}
				}
			}
		}
	}

}
