package de.robingrether.idisguise;

import java.io.File;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Horse.Color;
import org.bukkit.entity.Horse.Style;
import org.bukkit.entity.Ocelot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Horse.Variant;
import org.bukkit.entity.Skeleton.SkeletonType;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import de.robingrether.idisguise.api.DisguiseAPI;
import de.robingrether.idisguise.api.DisguiseEvent;
import de.robingrether.idisguise.api.UndisguiseEvent;
import de.robingrether.idisguise.disguise.ColoredDisguise;
import de.robingrether.idisguise.disguise.CreeperDisguise;
import de.robingrether.idisguise.disguise.Disguise;
import de.robingrether.idisguise.disguise.DisguiseType;
import de.robingrether.idisguise.disguise.DisguiseType.Type;
import de.robingrether.idisguise.disguise.EndermanDisguise;
import de.robingrether.idisguise.disguise.GuardianDisguise;
import de.robingrether.idisguise.disguise.HorseDisguise;
import de.robingrether.idisguise.disguise.HorseDisguise.Armor;
import de.robingrether.idisguise.disguise.RabbitDisguise.RabbitType;
import de.robingrether.idisguise.disguise.MobDisguise;
import de.robingrether.idisguise.disguise.OcelotDisguise;
import de.robingrether.idisguise.disguise.PigDisguise;
import de.robingrether.idisguise.disguise.PlayerDisguise;
import de.robingrether.idisguise.disguise.RabbitDisguise;
import de.robingrether.idisguise.disguise.SizedDisguise;
import de.robingrether.idisguise.disguise.SkeletonDisguise;
import de.robingrether.idisguise.disguise.VillagerDisguise;
import de.robingrether.idisguise.disguise.WolfDisguise;
import de.robingrether.idisguise.disguise.ZombieDisguise;
import de.robingrether.idisguise.io.Configuration;
import de.robingrether.idisguise.io.Metrics.Graph;
import de.robingrether.idisguise.io.Metrics.Plotter;
import de.robingrether.idisguise.io.Metrics;
import de.robingrether.idisguise.io.SLAPI;
import de.robingrether.idisguise.io.UpdateCheck;
import de.robingrether.idisguise.management.DisguiseManager;
import de.robingrether.idisguise.management.DisguiseList;
import de.robingrether.idisguise.management.GhostFactory;
import de.robingrether.idisguise.management.ProfileUtil;
import de.robingrether.idisguise.sound.SoundSystem;
import de.robingrether.util.RandomUtil;
import de.robingrether.util.StringUtil;
import de.robingrether.util.Validate;

public class iDisguise extends JavaPlugin {
	
	public static final File directory = new File("plugins/iDisguise");
	
	public iDisguiseListener listener;
	public Configuration configuration;
	public Metrics metrics;
	
	public void onEnable() {
		checkDirectory();
		listener = new iDisguiseListener(this);
		configuration = new Configuration(directory);
		configuration.loadData();
		configuration.saveData();
		DisguiseManager.setAttribute(0, showOriginalPlayerNames());
		SoundSystem.setEnabled(isSoundSystemEnabled());
		try {
			metrics = new Metrics(this);
			Graph graph1 = metrics.createGraph("Disguise Count");
			graph1.addPlotter(new Plotter("Disguise Count") {
				public int getValue() {
					return DisguiseManager.getOnlineDisguiseCount();
				}
			});
			Graph graph3 = metrics.createGraph("Sound System");
			graph3.addPlotter(new Plotter(isSoundSystemEnabled() ? "enabled" : "disabled") {
				public int getValue() {
					return 1;
				}
			});
			metrics.start();
		} catch(Exception e) {
		}
		if(saveDisguises()) {
			loadData();
		}
		getServer().getPluginManager().registerEvents(listener, this);
		if(isGhostDisguiseEnabled()) {
			GhostFactory.enable(this);
		}
		getServer().getServicesManager().register(DisguiseAPI.class, getAPI(), this, ServicePriority.Normal);
		if(checkForUpdates()) {
			getServer().getScheduler().runTaskLaterAsynchronously(this, new UpdateCheck(getFullName(), getServer().getConsoleSender(), "[iDisguise] " + "An update for iDisguise is available: %s"), 20L);
		}
		System.out.println("[iDisguise] " + String.format("iDisguise v%s enabled!", getVersion()));
	}
	
	public void onDisable() {
		if(isGhostDisguiseEnabled()) {
			GhostFactory.disable();
		}
		getServer().getScheduler().cancelTasks(this);
		if(saveDisguises()) {
			saveData();
		}
		System.out.println("[iDisguise] " + String.format("iDisguise v%s disabled!", getVersion()));
	}
	
	public void onReload() {
		onDisable();
		onEnable();
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		Player player = null;
		if(StringUtil.equalsIgnoreCase(cmd.getName(), "d", "dis", "disguise")) {
			if(sender instanceof Player) {
				player = (Player)sender;
			} else {
				sender.sendMessage(ChatColor.RED + "You have to use '/odisguise' from console.");
				return true;
			}
			if(args.length == 0 || StringUtil.equalsIgnoreCase(args[0], "?", "help")) {
				sender.sendMessage(ChatColor.GREEN + "iDisguise v" + getVersion() + " Help");
				sender.sendMessage(ChatColor.GOLD + "/" + cmd.getName() + " help - Shows this");
				if(player.hasPermission("iDisguise.player")) {
					sender.sendMessage(ChatColor.GOLD + "/" + cmd.getName() + " player <name> - Disguise as a player");
				}
				if(isGhostDisguiseEnabled() && player.hasPermission("iDisguise.player.ghost")) {
					sender.sendMessage(ChatColor.GOLD + "/" + cmd.getName() + " ghost <name> - Disguise as a ghost player");
				}
				if(player.hasPermission("iDisguise.random")) {
					sender.sendMessage(ChatColor.GOLD + "/" + cmd.getName() + " random - Disguise as a random mob");
				}
				sender.sendMessage(ChatColor.GOLD + "/" + cmd.getName() + " status - Shows what you are currently disguised as");
				if(!requirePermissionForUndisguising() || player.hasPermission("iDisguise.undisguise")) {
					sender.sendMessage(ChatColor.GOLD + "/u" + (cmd.getName().equalsIgnoreCase("d") ? "" : "n") + cmd.getName() + " - Undisguise");
				}
				if(player.hasPermission("iDisguise.undisguise.all")) {
					sender.sendMessage(ChatColor.GOLD + "/u" + (cmd.getName().equalsIgnoreCase("d") ? "" : "n") + cmd.getName() + " * - Undisguise everyone");
				}
				if(player.hasPermission("iDisguise.undisguise.others")) {
					sender.sendMessage(ChatColor.GOLD + "/u" + (cmd.getName().equalsIgnoreCase("d") ? "" : "n") + cmd.getName() + " <name> - Undisguise another player");
				}
				sender.sendMessage(ChatColor.GOLD + "/" + cmd.getName() + " [subtype] <mobtype> [subtype] - Disguise as a mob with optional subtypes");
				sender.sendMessage(ChatColor.GRAY + "Mobtypes: bat, blaze, cave_spider, chicken, cow, creeper, ender_dragon, enderman, endermite, ghast, giant, guardian, horse, iron_golem, magma_cube, mushroom_cow, ocelot, pig, pig_zombie, rabbit, sheep, silverfish, skeleton, slime, snowman, spider, squid, villager, witch, witherboss, wolf, zombie");
				if(DisguiseManager.isDisguised(player)) {
					sendSubtypeInformation(player, DisguiseManager.getDisguise(player).getType());
				}
			} else if(StringUtil.equalsIgnoreCase(args[0], "player", "p")) {
				if(args.length == 1) {
					sender.sendMessage(ChatColor.RED + "Wrong usage: " + ChatColor.ITALIC + "/" + cmd.getName() + " " + args[0] + " <name>");
				} else if(!Validate.minecraftUsername(args[1])) {
					sender.sendMessage(ChatColor.RED + "The given username is invalid.");
				} else {
					PlayerDisguise disguise = new PlayerDisguise(args[1], false);
					if(hasPermission(player, disguise)) {
						DisguiseEvent event = new DisguiseEvent(player, disguise);
						getServer().getPluginManager().callEvent(event);
						if(event.isCancelled()) {
							sender.sendMessage(ChatColor.RED + "Some plugin denies you to disguise.");
						} else {
							DisguiseManager.disguiseToAll(player, disguise);
							sender.sendMessage(ChatColor.GOLD + "You disguised as a player called " + ChatColor.ITALIC + args[1]);
						}
					} else {
						sender.sendMessage(ChatColor.RED + "You are not allowed to disguise.");
					}
				}
			} else if(StringUtil.equalsIgnoreCase(args[0], "ghost", "g")) {
				if(args.length == 1) {
					if(DisguiseManager.isDisguised(player) && (DisguiseManager.getDisguise(player) instanceof PlayerDisguise)) {
						PlayerDisguise disguise = new PlayerDisguise(((PlayerDisguise)DisguiseManager.getDisguise(player)).getName(), true);
						if(hasPermission(player, disguise)) {
							DisguiseEvent event = new DisguiseEvent(player, disguise);
							getServer().getPluginManager().callEvent(event);
							if(event.isCancelled()) {
								sender.sendMessage(ChatColor.RED + "Some plugin denies you to disguise.");
							} else {
								DisguiseManager.disguiseToAll(player, disguise);
								sender.sendMessage(ChatColor.GOLD + "You disguised as a ghost called " + ChatColor.ITALIC + disguise.getName());
							}
						} else {
							sender.sendMessage(ChatColor.RED + "You are not allowed to disguise.");
						}
					} else {
						sender.sendMessage(ChatColor.RED + "Wrong usage: " + ChatColor.ITALIC + "/" + cmd.getName() + " " + args[0] + " <name>");
					}
				} else if(!Validate.minecraftUsername(args[1])) {
					sender.sendMessage(ChatColor.RED + "The given username is invalid.");
				} else {
					PlayerDisguise disguise = new PlayerDisguise(args[1], true);
					if(hasPermission(player, disguise)) {
						DisguiseEvent event = new DisguiseEvent(player, disguise);
						getServer().getPluginManager().callEvent(event);
						if(event.isCancelled()) {
							sender.sendMessage(ChatColor.RED + "Some plugin denies you to disguise.");
						} else {
							DisguiseManager.disguiseToAll(player, disguise);
							sender.sendMessage(ChatColor.GOLD + "You disguised as a ghost called " + ChatColor.ITALIC + args[1]);
						}
					} else {
						sender.sendMessage(ChatColor.RED + "You are not allowed to disguise.");
					}
				}
			} else if(StringUtil.equalsIgnoreCase(args[0], "status", "state", "stat", "stats")) {
				if(DisguiseManager.isDisguised(player)) {
					Disguise disguise = DisguiseManager.getDisguise(player);
					if(disguise instanceof PlayerDisguise) {
						sender.sendMessage(ChatColor.GOLD + "You are disguised as a " + (((PlayerDisguise)disguise).isGhost() ? "ghost" : "player") + " called " + ChatColor.ITALIC + ((PlayerDisguise)disguise).getName());
					} else if(disguise instanceof MobDisguise) {
						sender.sendMessage(ChatColor.GOLD + "You are disguised as a " + disguise.getType().name().toLowerCase());
						sender.sendMessage(ChatColor.GRAY + "Your subtypes:");
						sender.sendMessage(ChatColor.GRAY + " Age: " + (((MobDisguise)disguise).isAdult() ? "adult" : "baby"));
						switch(disguise.getType()) {
							case CREEPER:
								sender.sendMessage(ChatColor.GRAY + " Creeper: " + (((CreeperDisguise)disguise).isPowered() ? "powered" : "not-powered"));
								break;
							case ENDERMAN:
								sender.sendMessage(ChatColor.GRAY + " Block in Hand: " + ((EndermanDisguise)disguise).getBlockInHand().name().toLowerCase());
								sender.sendMessage(ChatColor.GRAY + " Data: " + ((EndermanDisguise)disguise).getBlockInHandData());
								break;
							case GUARDIAN:
								sender.sendMessage(ChatColor.GRAY + " Guardian type: " + (((GuardianDisguise)disguise).isElder() ? "elder" : "not-elder"));
								break;
							case HORSE:
								sender.sendMessage(ChatColor.GRAY + " Variant: " + ((HorseDisguise)disguise).getVariant().name().toLowerCase().replace("_horse", "").replace("horse", "normal"));
								sender.sendMessage(ChatColor.GRAY + " Style: " + ((HorseDisguise)disguise).getStyle().name().toLowerCase().replace('_', '-').replaceAll("white$", "white-stripes").replace("none", "no-markings"));
								sender.sendMessage(ChatColor.GRAY + " Color: " + ((HorseDisguise)disguise).getColor().name().toLowerCase().replace('_', '-'));
								sender.sendMessage(ChatColor.GRAY + " Saddle: " + (((HorseDisguise)disguise).isSaddled() ? "saddled" : "not-saddled"));
								sender.sendMessage(ChatColor.GRAY + " Chest: " + (((HorseDisguise)disguise).hasChest() ? "chest" : "no-chest"));
								sender.sendMessage(ChatColor.GRAY + " Armor: " + ((HorseDisguise)disguise).getArmor().name().toLowerCase().replace("none", "no-armor"));
								break;
							case OCELOT:
								sender.sendMessage(ChatColor.GRAY + " Cat type: " + ((OcelotDisguise)disguise).getCatType().name().toLowerCase().replaceAll("_.*", ""));
								break;
							case PIG:
								sender.sendMessage(ChatColor.GRAY + " Saddle: " + (((PigDisguise)disguise).isSaddled() ? "saddled" : "not-saddled"));
								break;
							case RABBIT:
								sender.sendMessage(ChatColor.GRAY + " Rabbit type: " + ((RabbitDisguise)disguise).getRabbitType().name().toLowerCase().replace("_and_", "-").replace("the_killer_bunny", "killer"));
								break;
							case SHEEP:
								sender.sendMessage(ChatColor.GRAY + " Color: " + ((ColoredDisguise)disguise).getColor().name().toLowerCase().replace('_', '-'));
								break;
							case SKELETON:
								sender.sendMessage(ChatColor.GRAY + " Skeleton type: " + (((SkeletonDisguise)disguise).getSkeletonType().equals(SkeletonType.NORMAL) ? "normal" : "wither"));
								break;
							case MAGMA_CUBE:
							case SLIME:
								sender.sendMessage(ChatColor.GRAY + " Size: " + ((SizedDisguise)disguise).getSize() + (((SizedDisguise)disguise).getSize() == 1 ? " (tiny)" : (((SizedDisguise)disguise).getSize() == 2 ? " (normal)" : (((SizedDisguise)disguise).getSize() == 4 ? " (big)" : ""))));
								break;
							case VILLAGER:
								sender.sendMessage(ChatColor.GRAY + " Profession: " + ((VillagerDisguise)disguise).getProfession().name().toLowerCase());
								break;
							case WOLF:
								sender.sendMessage(ChatColor.GRAY + " Collar: " + ((ColoredDisguise)disguise).getColor().name().toLowerCase().replace('_', '-'));
								sender.sendMessage(ChatColor.GRAY + " Tamed: " + (((WolfDisguise)disguise).isTamed() ? "tamed" : "not-tamed"));
								sender.sendMessage(ChatColor.GRAY + " Angry: " + (((WolfDisguise)disguise).isAngry() ? "angry" : "not-angry"));
								break;
							case ZOMBIE:
								sender.sendMessage(ChatColor.GRAY + " Zombie type: " + (((ZombieDisguise)disguise).isVillager() ? "villager" : "normal"));
								break;
							default: break;
						}
					}
				} else {
					sender.sendMessage(ChatColor.GOLD + "You are not disguised.");
				}
			} else if(args[0].equalsIgnoreCase("random")) {
				if(player.hasPermission("iDisguise.random")) {
					DisguiseType type = DisguiseType.random(Type.MOB);
					Disguise disguise;
					switch(type) {
						case CREEPER:
							disguise = new CreeperDisguise(RandomUtil.nextBoolean());
							break;
						case ENDERMAN:
							disguise = new EndermanDisguise(Material.getMaterial(RandomUtil.nextInt(198)), RandomUtil.nextInt(16));
							break;
						case GUARDIAN:
							disguise = new GuardianDisguise(RandomUtil.nextBoolean());
							break;
						case HORSE:
							disguise = new HorseDisguise(RandomUtil.nextBoolean(), RandomUtil.nextEnumValue(Variant.class), RandomUtil.nextEnumValue(Style.class), RandomUtil.nextEnumValue(Color.class), RandomUtil.nextBoolean(), RandomUtil.nextBoolean(), RandomUtil.nextEnumValue(Armor.class));
							break;
						case OCELOT:
							disguise = new OcelotDisguise(RandomUtil.nextEnumValue(Ocelot.Type.class), RandomUtil.nextBoolean());
							break;
						case PIG:
							disguise = new PigDisguise(RandomUtil.nextBoolean(), RandomUtil.nextBoolean());
							break;
						case RABBIT:
							disguise = new RabbitDisguise(RandomUtil.nextBoolean(), RandomUtil.nextEnumValue(RabbitType.class));
							break;
						case SHEEP:
							disguise = new ColoredDisguise(type, RandomUtil.nextBoolean(), RandomUtil.nextEnumValue(DyeColor.class));
							break;
						case SKELETON:
							disguise = new SkeletonDisguise(RandomUtil.nextEnumValue(SkeletonType.class));
							break;
						case MAGMA_CUBE:
						case SLIME:
							disguise = new SizedDisguise(type, RandomUtil.nextInt(1000) + 1);
							break;
						case VILLAGER:
							disguise = new VillagerDisguise(RandomUtil.nextBoolean(), RandomUtil.nextEnumValue(Profession.class));
							break;
						case WOLF:
							disguise = new WolfDisguise(RandomUtil.nextBoolean(), RandomUtil.nextEnumValue(DyeColor.class), RandomUtil.nextBoolean(), RandomUtil.nextBoolean());
							break;
						case ZOMBIE:
							disguise = new ZombieDisguise(RandomUtil.nextBoolean(), RandomUtil.nextBoolean());
							break;
						default:
							disguise = new MobDisguise(type, RandomUtil.nextBoolean());
							break;
					}
					DisguiseEvent event = new DisguiseEvent(player, disguise);
					getServer().getPluginManager().callEvent(event);
					if(event.isCancelled()) {
						sender.sendMessage(ChatColor.RED + "Some plugin denies you to disguise.");
					} else {
						DisguiseManager.disguiseToAll(player, disguise);
						sender.sendMessage(ChatColor.GOLD + "You disguised as a random mob. Type " + ChatColor.ITALIC + "/" + cmd.getName() + " status" + ChatColor.RESET + ChatColor.GOLD + " to get more information.");
					}
				} else {
					sender.sendMessage(ChatColor.RED + "You are not allowed to disguise.");
				}
			} else {
				Disguise disguise = DisguiseManager.isDisguised(player) ? DisguiseManager.getDisguise(player).clone() : null;
				for(String argument : args) { // change mob types
					if(argument.equalsIgnoreCase("bat")) {
						disguise = new MobDisguise(DisguiseType.BAT);
					} else if(argument.equalsIgnoreCase("blaze")) {
						disguise = new MobDisguise(DisguiseType.BLAZE);
					} else if(StringUtil.equalsIgnoreCase(argument, "cave_spider", "cave-spider", "cavespider", "blue_spider", "blue-spider", "bluespider", "cave")) {
						disguise = new MobDisguise(DisguiseType.CAVE_SPIDER);
					} else if(StringUtil.equalsIgnoreCase(argument, "chicken", "chick")) {
						disguise = new MobDisguise(DisguiseType.CHICKEN);
					} else if(StringUtil.equalsIgnoreCase(argument, "cow", "cattle", "ox")) {
						disguise = new MobDisguise(DisguiseType.COW);
					} else if(argument.equalsIgnoreCase("creeper")) {
						disguise = new CreeperDisguise();
					} else if(StringUtil.equalsIgnoreCase(argument, "dragon", "ender_dragon", "ender-dragon", "enderdragon")) {
						disguise = new MobDisguise(DisguiseType.ENDER_DRAGON);
					} else if(StringUtil.equalsIgnoreCase(argument, "enderman", "endermen")) {
						disguise = new EndermanDisguise();
					} else if(StringUtil.equalsIgnoreCase(argument, "endermite", "mite")) {
						disguise = new MobDisguise(DisguiseType.ENDERMITE);
					} else if(argument.equalsIgnoreCase("ghast")) {
						disguise = new MobDisguise(DisguiseType.GHAST);
					} else if(StringUtil.equalsIgnoreCase(argument, "giant", "giant_zombie", "giant-zombie", "giantzombie")) {
						disguise = new MobDisguise(DisguiseType.GIANT);
					} else if(argument.equalsIgnoreCase("guardian")) {
						disguise = new GuardianDisguise();
					} else if(argument.equalsIgnoreCase("horse")) {
						disguise = new HorseDisguise();
					} else if(StringUtil.equalsIgnoreCase(argument, "iron_golem", "iron-golem", "irongolem", "golem")) {
						disguise = new MobDisguise(DisguiseType.IRON_GOLEM);
					} else if(StringUtil.equalsIgnoreCase(argument, "magma_cube", "magma-cube", "magmacube", "magma", "lava_cube", "lava-cube", "lavacube", "lava", "magma_slime", "magma-slime", "magmaslime", "lava_slime", "lava-slime", "lavaslime")) {
						disguise = new SizedDisguise(DisguiseType.MAGMA_CUBE);
					} else if(StringUtil.equalsIgnoreCase(argument, "mushroom_cow", "mushroom-cow", "mushroomcow", "mushroom", "mooshroom")) {
						disguise = new MobDisguise(DisguiseType.MUSHROOM_COW);
					} else if(StringUtil.equalsIgnoreCase(argument, "ocelot", "cat")) {
						disguise = new OcelotDisguise();
					} else if(argument.equalsIgnoreCase("pig")) {
						disguise = new PigDisguise();
					} else if(StringUtil.equalsIgnoreCase(argument, "pig_zombie", "pig-zombie", "pigzombie", "pigman", "zombie_pigman", "zombie-pigman", "zombiepigman")) {
						disguise = new MobDisguise(DisguiseType.PIG_ZOMBIE);
					} else if(argument.equalsIgnoreCase("rabbit")) {
						disguise = new RabbitDisguise();
					} else if(argument.equalsIgnoreCase("sheep")) {
						disguise = new ColoredDisguise(DisguiseType.SHEEP);
					} else if(argument.equalsIgnoreCase("silverfish")) {
						disguise = new MobDisguise(DisguiseType.SILVERFISH);
					} else if(argument.equalsIgnoreCase("skeleton")) {
						disguise = new SkeletonDisguise();
					} else if(StringUtil.equalsIgnoreCase(argument, "slime", "cube")) {
						disguise = new SizedDisguise(DisguiseType.SLIME);
					} else if(StringUtil.equalsIgnoreCase(argument, "snowman", "snow-man", "snow_man", "snow_golem", "snow-golem", "snowgolem")) {
						disguise = new MobDisguise(DisguiseType.SNOWMAN);
					} else if(argument.equalsIgnoreCase("spider")) {
						disguise = new MobDisguise(DisguiseType.SPIDER);
					} else if(argument.equalsIgnoreCase("squid")) {
						disguise = new MobDisguise(DisguiseType.SQUID);
					} else if(argument.equalsIgnoreCase("villager")) {
						disguise = new VillagerDisguise();
					} else if(argument.equalsIgnoreCase("witch")) {
						disguise = new MobDisguise(DisguiseType.WITCH);
					} else if(StringUtil.equalsIgnoreCase(argument, "witherboss", "wither-boss", "wither_boss") || (argument.equalsIgnoreCase("wither") && (disguise == null || disguise.getType() != DisguiseType.SKELETON))) {
						disguise = new MobDisguise(DisguiseType.WITHER);
					} else if(StringUtil.equalsIgnoreCase(argument, "wolf", "dog")) {
						disguise = new WolfDisguise();
					} else if(argument.equalsIgnoreCase("zombie")) {
						disguise = new ZombieDisguise();
					}
				}
				if(disguise instanceof MobDisguise) {
					for(String argument : args) {
						if(StringUtil.equalsIgnoreCase(argument, "adult", "senior")) {
							((MobDisguise)disguise).setAdult(true);
						} else if(StringUtil.equalsIgnoreCase(argument, "baby", "child", "kid", "junior")) {
							((MobDisguise)disguise).setAdult(false);
						}
					}
					if(disguise instanceof ColoredDisguise) {
						for(String argument : args) {
							try {
								DyeColor color = DyeColor.valueOf(argument.replace('-', '_').toUpperCase());
								((ColoredDisguise)disguise).setColor(color);
							} catch(IllegalArgumentException e) {
							}
						}
						if(disguise instanceof WolfDisguise) {
							for(String argument : args) {
								if(StringUtil.equalsIgnoreCase(argument, "tamed", "tame")) {
									((WolfDisguise)disguise).setTamed(true);
								} else if(StringUtil.equalsIgnoreCase(argument, "not-tamed", "not_tamed", "nottamed", "not-tame", "not_tame", "nottame")) {
									((WolfDisguise)disguise).setTamed(false);
								} else if(StringUtil.equalsIgnoreCase(argument, "angry", "aggressive")) {
									((WolfDisguise)disguise).setAngry(true);
								} else if(StringUtil.equalsIgnoreCase(argument, "not-angry", "not_angry", "notangry", "not-aggressive", "not_aggressive", "notaggressive")) {
									((WolfDisguise)disguise).setAngry(false);
								}
							}
						}
					} else if(disguise instanceof CreeperDisguise) {
						for(String argument : args) {
							if(StringUtil.equalsIgnoreCase(argument, "powered", "charged")) {
								((CreeperDisguise)disguise).setPowered(true);
							} else if(StringUtil.equalsIgnoreCase(argument, "normal", "not-powered", "not_powered", "notpowered", "not-charged", "not_charged", "notcharged")) {
								((CreeperDisguise)disguise).setPowered(false);
							}
						}
					} else if(disguise instanceof EndermanDisguise) {
						for(String argument : args) {
							try {
								Material blockInHand = Material.valueOf(argument.replace('-', '_').toUpperCase());
								((EndermanDisguise)disguise).setBlockInHand(blockInHand);
							} catch(IllegalArgumentException e) {
							}
							try {
								int blockInHandData = Integer.valueOf(argument);
								((EndermanDisguise)disguise).setBlockInHandData(blockInHandData);
							} catch(NumberFormatException e) {
							}
						}
					} else if(disguise instanceof GuardianDisguise) {
						for(String argument : args) {
							if(StringUtil.equalsIgnoreCase(argument, "elder", "big")) {
								((GuardianDisguise)disguise).setElder(true);
							} else if(StringUtil.equalsIgnoreCase(argument, "not-elder", "not_elder", "notelder", "normal", "small")) {
								((GuardianDisguise)disguise).setElder(false);
							}
						}
					} else if(disguise instanceof HorseDisguise) {
						for(String argument : args) {
							switch(argument.toLowerCase()) {
								case "donkey":
									((HorseDisguise)disguise).setVariant(Variant.DONKEY);
									break;
								case "normal":
								case "horse":
									((HorseDisguise)disguise).setVariant(Variant.HORSE);
									break;
								case "mule":
									((HorseDisguise)disguise).setVariant(Variant.MULE);
									break;
								case "skeleton":
									((HorseDisguise)disguise).setVariant(Variant.SKELETON_HORSE);
									break;
								case "undead":
								case "zombie":
									((HorseDisguise)disguise).setVariant(Variant.UNDEAD_HORSE);
									break;
								case "black-dots":
								case "blackdots":
								case "black_dots":
									((HorseDisguise)disguise).setStyle(Style.BLACK_DOTS);
									break;
								case "no-markings":
								case "nomarkings":
								case "no_markings":
									((HorseDisguise)disguise).setStyle(Style.NONE);
									break;
								case "white-stripes":
								case "whitestripes":
								case "white_stripes":
									((HorseDisguise)disguise).setStyle(Style.WHITE);
									break;
								case "white-dots":
								case "whitedots":
								case "white_dots":
									((HorseDisguise)disguise).setStyle(Style.WHITE_DOTS);
									break;
								case "whitefield":
									((HorseDisguise)disguise).setStyle(Style.WHITEFIELD);
									break;
								case "black":
									((HorseDisguise)disguise).setColor(Color.BLACK);
									break;
								case "brown":
									((HorseDisguise)disguise).setColor(Color.BROWN);
									break;
								case "chestnut":
									((HorseDisguise)disguise).setColor(Color.CHESTNUT);
									break;
								case "creamy":
								case "cream":
									((HorseDisguise)disguise).setColor(Color.CREAMY);
									break;
								case "dark-brown":
								case "darkbrown":
								case "dark_brown":
									((HorseDisguise)disguise).setColor(Color.DARK_BROWN);
									break;
								case "gray":
								case "grey":
									((HorseDisguise)disguise).setColor(Color.GRAY);
									break;
								case "white":
									((HorseDisguise)disguise).setColor(Color.WHITE);
									break;
								case "saddled":
								case "saddle":
									((HorseDisguise)disguise).setSaddled(true);
									break;
								case "not-saddled":
								case "notsattled":
								case "not_saddled":
								case "no-saddle":
								case "nosaddle":
								case "no_saddle":
									((HorseDisguise)disguise).setSaddled(false);
									break;
								case "chest":
									((HorseDisguise)disguise).setHasChest(true);
									break;
								case "no-chest":
								case "nochest":
								case "no_chest":
									((HorseDisguise)disguise).setHasChest(false);
									break;
								case "no-armor":
								case "noarmor":
								case "no_armor":
									((HorseDisguise)disguise).setArmor(Armor.NONE);
									break;
								case "iron":
									((HorseDisguise)disguise).setArmor(Armor.IRON);
									break;
								case "gold":
									((HorseDisguise)disguise).setArmor(Armor.GOLD);
									break;
								case "diamond":
									((HorseDisguise)disguise).setArmor(Armor.DIAMOND);
									break;
							}
						}
					} else if(disguise instanceof OcelotDisguise) {
						for(String argument : args) {
							switch(argument.toLowerCase()) {
								case "black":
									((OcelotDisguise)disguise).setCatType(Ocelot.Type.BLACK_CAT);
									break;
								case "red":
									((OcelotDisguise)disguise).setCatType(Ocelot.Type.RED_CAT);
									break;
								case "siamese":
									((OcelotDisguise)disguise).setCatType(Ocelot.Type.SIAMESE_CAT);
									break;
								case "wild":
									((OcelotDisguise)disguise).setCatType(Ocelot.Type.WILD_OCELOT);
									break;
							}
						}
					} else if(disguise instanceof PigDisguise) {
						for(String argument : args) {
							if(StringUtil.equalsIgnoreCase(argument, "saddled", "saddle")) {
								((PigDisguise)disguise).setSaddled(true);
							} else if(StringUtil.equalsIgnoreCase(argument, "not-saddled", "notsaddled", "not_saddled", "no-saddle", "nosaddle", "no_saddle")) {
								((PigDisguise)disguise).setSaddled(false);
							}
						}
					} else if(disguise instanceof RabbitDisguise) {
						for(String argument : args) {
							switch(argument.toLowerCase()) {
								case "black":
									((RabbitDisguise)disguise).setRabbitType(RabbitType.BLACK);
									break;
								case "black-white":
								case "blackwhite":
								case "black_white":
								case "blackandwhite":
								case "black-and-white":
								case "black_and_white":
									((RabbitDisguise)disguise).setRabbitType(RabbitType.BLACK_AND_WHITE);
									break;
								case "brown":
									((RabbitDisguise)disguise).setRabbitType(RabbitType.BROWN);
									break;
								case "gold":
									((RabbitDisguise)disguise).setRabbitType(RabbitType.GOLD);
									break;
								case "salt-pepper":
								case "saltpepper":
								case "salt_pepper":
								case "saltandpepper":
								case "salt-and-pepper":
								case "salt_and_pepper":
									((RabbitDisguise)disguise).setRabbitType(RabbitType.SALT_AND_PEPPER);
									break;
								case "killer":
								case "killer-bunny":
								case "killer_bunny":
								case "killerbunny":
								case "thekillerbunny":
								case "the-killer-bunny":
								case "the_killer_bunny":
									((RabbitDisguise)disguise).setRabbitType(RabbitType.THE_KILLER_BUNNY);
									break;
								case "white":
									((RabbitDisguise)disguise).setRabbitType(RabbitType.WHITE);
									break;
							}
						}
					} else if(disguise instanceof SkeletonDisguise) {
						for(String argument : args) {
							if(argument.equalsIgnoreCase("normal")) {
								((SkeletonDisguise)disguise).setSkeletonType(SkeletonType.NORMAL);
							} else if(argument.equalsIgnoreCase("wither")) {
								((SkeletonDisguise)disguise).setSkeletonType(SkeletonType.WITHER);
							}
						}
					} else if(disguise instanceof SizedDisguise) {
						for(String argument : args) {
							if(StringUtil.equalsIgnoreCase(argument, "tiny", "small")) {
								((SizedDisguise)disguise).setSize(1);
							} else if(StringUtil.equalsIgnoreCase(argument, "normal", "medium")) {
								((SizedDisguise)disguise).setSize(2);
							} else if(argument.equalsIgnoreCase("big")) {
								((SizedDisguise)disguise).setSize(4);
							} else {
								try {
									int size = Integer.valueOf(argument);
									if(size > 1000) {
										size = 1000;
									} else if(size < 1) {
										size = 1;
									}
									((SizedDisguise)disguise).setSize(size);
								} catch(NumberFormatException e) {
								}
							}
						}
					} else if(disguise instanceof VillagerDisguise) {
						for(String argument : args) {
							try {
								Profession profession = Profession.valueOf(argument.toUpperCase());
								((VillagerDisguise)disguise).setProfession(profession);
							} catch(IllegalArgumentException e) {
							}
						}
					} else if(disguise instanceof ZombieDisguise) {
						for(String argument : args) {
							if(argument.equalsIgnoreCase("normal")) {
								((ZombieDisguise)disguise).setVillager(false);
							} else if(argument.equalsIgnoreCase("villager")) {
								((ZombieDisguise)disguise).setVillager(true);
							}
						}
					}
				}
				if(disguise == null) {
					sender.sendMessage(ChatColor.RED + "Wrong usage. Type " + ChatColor.ITALIC + "/" + cmd.getName() + " help" + ChatColor.RESET + ChatColor.RED + " for additional information.");
				} else if(!disguise.equals(DisguiseManager.getDisguise(player))) {
					if(hasPermission(player, disguise)) {
						DisguiseEvent event = new DisguiseEvent(player, disguise);
						getServer().getPluginManager().callEvent(event);
						if(event.isCancelled()) {
							sender.sendMessage(ChatColor.RED + "Some plugin denies you to disguise.");
						} else {
							DisguiseManager.disguiseToAll(player, disguise);
							sender.sendMessage(ChatColor.GOLD + "You disguised. Type " + ChatColor.ITALIC + "/" + cmd.getName() + " status" + ChatColor.RESET + ChatColor.RED + " for information about your disguise.");
						}
					} else {
						sender.sendMessage(ChatColor.RED + "You are not allowed to disguise.");
					}
				} else {
					sender.sendMessage(ChatColor.RED + "Wrong usage. Type " + ChatColor.ITALIC + "/" + cmd.getName() + " help" + ChatColor.RESET + ChatColor.RED + " for additional information.");
				}
			}
			return true;
		} else if(StringUtil.equalsIgnoreCase(cmd.getName(), "ud", "undis", "undisguise")) {
			if(sender instanceof Player) {
				player = (Player)sender;
			}
			if(args.length == 0) {
				if(player == null) {
					sender.sendMessage(ChatColor.RED + "You cannot undisguise as console.");
				} else {
					if(DisguiseManager.isDisguised(player)) {
						if(!requirePermissionForUndisguising() || player.hasPermission("iDisguise.undisguise")) {
							UndisguiseEvent event = new UndisguiseEvent(player, DisguiseManager.getDisguise(player), false);
							getServer().getPluginManager().callEvent(event);
							if(!event.isCancelled()) {
								DisguiseManager.undisguiseToAll(player);
								sender.sendMessage(ChatColor.GOLD + "You were undisguised.");
							} else {
								sender.sendMessage(ChatColor.RED + "Some plugin denies you to undisguise.");
							}
						} else {
							sender.sendMessage(ChatColor.RED + "You are not allowed to undisguise.");
						}
					} else {
						sender.sendMessage(ChatColor.RED + "You are not disguised.");
					}
				}
			} else if(args[0].equals("*")) {
				if(player == null || player.hasPermission("iDisguise.undisguise.all")) {
					if(args.length > 1 && args[1].equalsIgnoreCase("ignore")) {
						DisguiseManager.undisguiseAll();
						sender.sendMessage(ChatColor.GOLD + "Undisguised everyone ignoring event cancelling.");
					} else {
						int count = 0;
						int total = DisguiseManager.getDisguiseList().getPlayers().size();
						for(UUID uuid : DisguiseManager.getDisguiseList().getPlayers()) {
							if(Bukkit.getPlayer(uuid) != null) {
								UndisguiseEvent event = new UndisguiseEvent(Bukkit.getPlayer(uuid), DisguiseManager.getDisguise(Bukkit.getPlayer(uuid)), true);
								getServer().getPluginManager().callEvent(event);
								if(!event.isCancelled()) {
									DisguiseManager.undisguiseToAll(Bukkit.getPlayer(uuid));
									count++;
								}
							} else {
								DisguiseManager.getDisguiseList().removeDisguise(uuid);
								count++;
							}
						}
						sender.sendMessage(ChatColor.GOLD + Integer.toString(count) + " of " + total + " disguised players were undisguised.");
					}
				} else {
					sender.sendMessage(ChatColor.RED + "You are not allowed to undisguise everyone.");
				}
			} else {
				sender.sendMessage(ChatColor.RED + "This feature is not available so far."); // add undisguise other players
			}
		} //else if - add /odisguise
		return true;
	}
	
	private void sendSubtypeInformation(CommandSender sender, DisguiseType type) {
		sender.sendMessage(ChatColor.GOLD + "Information about subtypes:");
		sender.sendMessage(ChatColor.GRAY + " Age: adult, baby");
		switch(type) {
			case CREEPER:
				sender.sendMessage(ChatColor.GRAY + " Creeper: powered, not-powered");
				break;
			case ENDERMAN:
				sender.sendMessage(ChatColor.GRAY + " Block in hand: <material-name>");
				sender.sendMessage(ChatColor.GRAY + " Data: <0-255>");
				break;
			case GUARDIAN:
				sender.sendMessage(ChatColor.GRAY + " Guardian type: elder, not-elder");
				break;
			case HORSE:
				sender.sendMessage(ChatColor.GRAY + " Variant: donkey, normal, mule, skeleton, undead");
				sender.sendMessage(ChatColor.GRAY + " Style: black-dots, no-markings, white-stripes, white-dots, whitefield");
				sender.sendMessage(ChatColor.GRAY + " Color: black, brown, chestnut, creamy, dark-brown, gray, white");
				sender.sendMessage(ChatColor.GRAY + " Saddle: saddled, not-saddled");
				sender.sendMessage(ChatColor.GRAY + " Chest: chest, no-chest");
				sender.sendMessage(ChatColor.GRAY + " Armor: no-armor, iron, gold, diamond");
				break;
			case OCELOT:
				sender.sendMessage(ChatColor.GRAY + " Cat type: black, red, siamese, wild");
				break;
			case PIG:
				sender.sendMessage(ChatColor.GRAY + " Saddle: saddled, not-saddled");
				break;
			case RABBIT:
				sender.sendMessage(ChatColor.GRAY + " Rabbit type: black, black-white, brown, gold, salt-pepper, killer, white");
				break;
			case SHEEP:
				sender.sendMessage(ChatColor.GRAY + " Color: black, blue, brown, cyan, gray, green, light-blue, lime, magenta, orange, pink, purple, red, silver, white, yellow");
				break;
			case SKELETON:
				sender.sendMessage(ChatColor.GRAY + " Skeleton type: normal, wither");
				break;
			case MAGMA_CUBE:
			case SLIME:
				sender.sendMessage(ChatColor.GRAY + " Size: tiny, normal, big, <1-1000>");
				break;
			case VILLAGER:
				sender.sendMessage(ChatColor.GRAY + " Profession: blacksmith, butcher, farmer, librarian, priest");
				break;
			case WOLF:
				sender.sendMessage(ChatColor.GRAY + " Collar: black, blue, brown, cyan, gray, green, light-blue, lime, magenta, orange, pink, purple, red, silver, white, yellow");
				sender.sendMessage(ChatColor.GRAY + " Tamed: tamed, not-tamed");
				sender.sendMessage(ChatColor.GRAY + " Angry: angry, not-angry");
				break;
			case ZOMBIE:
				sender.sendMessage(ChatColor.GRAY + " Zombie type: normal, villager");
				break;
			default: break;
		}
	}
	
	private boolean hasPermission(Player player, Disguise disguise) {
		switch(disguise.getType()) {
			case BAT:
				return player.hasPermission("iDisguise.mob.bat");
			case BLAZE:
				return player.hasPermission("iDisguise.mob.blaze");
			case CAVE_SPIDER:
				return player.hasPermission("iDisguise.mob.cave_spider");
			case CHICKEN:
				return player.hasPermission("iDisguise.mob.chicken") && (((MobDisguise)disguise).isAdult() || player.hasPermission("iDisguise.mob.baby"));
			case COW:
				return player.hasPermission("iDisguise.mob.cow") && (((MobDisguise)disguise).isAdult() || player.hasPermission("iDisguise.mob.baby"));
			case CREEPER:
				return player.hasPermission("iDisguise.mob.creeper") && (!((CreeperDisguise)disguise).isPowered() || player.hasPermission("iDisguise.mob.creeper.powered"));
			case ENDER_DRAGON:
				return player.hasPermission("iDisguise.mob.ender_dragon");
			case ENDERMAN:
				return player.hasPermission("iDisguise.mob.enderman") && (((EndermanDisguise)disguise).getBlockInHand().equals(Material.AIR) || player.hasPermission("iDisguise.mob.enderman.block"));
			case ENDERMITE:
				return player.hasPermission("iDisguise.mob.endermite");
			case GHAST:
				return player.hasPermission("iDisguise.mob.ghast");
			case GHOST:
				return player.hasPermission("iDisguise.ghost") && (player.hasPermission("iDisguise.player.*") || player.hasPermission("iDisguise.player." + ((PlayerDisguise)disguise).getName().toLowerCase()));
			case GIANT:
				return player.hasPermission("iDisguise.mob.giant");
			case GUARDIAN:
				return player.hasPermission("iDisguise.mob.guardian") && (!((GuardianDisguise)disguise).isElder() || player.hasPermission("iDisguise.mob.guardian.elder"));
			case HORSE:
				return player.hasPermission("iDisguise.mob.horse") && (((MobDisguise)disguise).isAdult() || player.hasPermission("iDisguise.mob.baby")) && player.hasPermission("iDisguise.mob.horse.variant." + ((HorseDisguise)disguise).getVariant().name().toLowerCase().replace("_horse", "").replace("horse", "normal"));
			case IRON_GOLEM:
				return player.hasPermission("iDisguise.mob.iron_golem");
			case MAGMA_CUBE:
				return player.hasPermission("iDisguise.mob.magma_cube") && (((SizedDisguise)disguise).getSize() < 5 || player.hasPermission("iDisguise.mob.magma_cube.giant"));
			case MUSHROOM_COW:
				return player.hasPermission("iDisguise.mob.mushroom_cow") && (((MobDisguise)disguise).isAdult() || player.hasPermission("iDisguise.mob.baby"));
			case OCELOT:
				return player.hasPermission("iDisguise.mob.ocelot") && (((MobDisguise)disguise).isAdult() || player.hasPermission("iDisguise.mob.baby")) && player.hasPermission("iDisguise.mob.ocelot.type." + ((OcelotDisguise)disguise).getCatType().name().toLowerCase().replaceAll("_.*", ""));
			case PIG:
				return player.hasPermission("iDisguise.mob.pig") && (((MobDisguise)disguise).isAdult() || player.hasPermission("iDisguise.mob.baby")) && (!((PigDisguise)disguise).isSaddled() || player.hasPermission("iDisguise.mob.pig.saddled"));
			case PIG_ZOMBIE:
				return player.hasPermission("iDisguise.mob.pig_zombie") && (((MobDisguise)disguise).isAdult() || player.hasPermission("iDisguise.mob.baby"));
			case PLAYER:
				return player.hasPermission("iDisguise.player.*") || player.hasPermission("iDisguise.player." + ((PlayerDisguise)disguise).getName().toLowerCase());
			case RABBIT:
				return player.hasPermission("iDisguise.mob.rabbit") && (((MobDisguise)disguise).isAdult() || player.hasPermission("iDisguise.mob.baby")) && player.hasPermission("iDisguise.mob.rabbit.type." + ((RabbitDisguise)disguise).getRabbitType().name().toLowerCase().replace("_and_", "-").replace("the_killer_bunny", "killer"));
			case SHEEP:
				return player.hasPermission("iDisguise.mob.sheep") && (((MobDisguise)disguise).isAdult() || player.hasPermission("iDisguise.mob.baby")) && player.hasPermission("iDisguise.mob.sheep.color." + ((ColoredDisguise)disguise).getColor().name().toLowerCase().replace('_', '-'));
			case SILVERFISH:
				return player.hasPermission("iDisguise.mob.silverfish");
			case SKELETON:
				return player.hasPermission("iDisguise.mob.skeleton") && (((SkeletonDisguise)disguise).getSkeletonType().equals(SkeletonType.NORMAL) || player.hasPermission("iDisguise.mob.skeleton.wither"));
			case SLIME:
				return player.hasPermission("iDisguise.mob.slime") && (((SizedDisguise)disguise).getSize() < 5 || player.hasPermission("iDisguise.mob.slime.giant"));
			case SNOWMAN:
				return player.hasPermission("iDisguise.mob.snowman");
			case SPIDER:
				return player.hasPermission("iDisguise.mob.spider");
			case SQUID:
				return player.hasPermission("iDisguise.mob.squid");
			case VILLAGER:
				return player.hasPermission("iDisguise.mob.villager") && (((MobDisguise)disguise).isAdult() || player.hasPermission("iDisguise.mob.baby")) && player.hasPermission("iDisguise.mob.villager.profession." + ((VillagerDisguise)disguise).getProfession().name().toLowerCase());
			case WITCH:
				return player.hasPermission("iDisguise.mob.witch");
			case WITHER:
				return player.hasPermission("iDisguise.mob.witherboss");
			case WOLF:
				return player.hasPermission("iDisguise.mob.wolf") && (((MobDisguise)disguise).isAdult() || player.hasPermission("iDisguise.mob.baby")) && player.hasPermission("iDisguise.mob.wolf.collar." + ((ColoredDisguise)disguise).getColor().name().toLowerCase().replace('_', '-')) && (!((WolfDisguise)disguise).isTamed() || player.hasPermission("iDisguise.mob.wolf.tamed")) && (!((WolfDisguise)disguise).isAngry() || player.hasPermission("iDisguise.mob.wolf.angry"));
			case ZOMBIE:
				return player.hasPermission("iDisguise.mob.zombie") && (((MobDisguise)disguise).isAdult() || player.hasPermission("iDisguise.mob.baby")) && (!((ZombieDisguise)disguise).isVillager() || player.hasPermission("iDisguise.mob.zombie.villager"));
			default:
				return false;
		}
	}
	
	private void checkDirectory() {
		if(!directory.exists()) {
			directory.mkdir();
		}
	}
	
	@SuppressWarnings("unchecked")
	private void loadData() {
		File dataFile = new File(directory, "data.bin");
		File oldDataFile = new File(directory, "disguise.bin");
		if(dataFile.exists()) {
			Object map = SLAPI.load(dataFile);
			if(map instanceof ConcurrentHashMap) {
				DisguiseManager.setDisguiseList(new DisguiseList((ConcurrentHashMap<UUID, Disguise>)map));
			}
		} else if(oldDataFile.exists()) {
			Object oldMap = SLAPI.load(oldDataFile);
			if(oldMap instanceof Map) {
				ConcurrentHashMap<UUID, Disguise> converted = new ConcurrentHashMap<UUID, Disguise>();
				for(Entry<String, Disguise> entry : ((Map<String, Disguise>)oldMap).entrySet()) {
					converted.put(ProfileUtil.getUniqueId(entry.getKey()), entry.getValue());
				}
				DisguiseManager.setDisguiseList(new DisguiseList(converted));
			}
			oldDataFile.delete();
		}
	}
	
	private void saveData() {
		File dataFile = new File(directory, "data.bin");
		SLAPI.save(DisguiseManager.getDisguiseList().getMap(), dataFile);
	}
	
	public DisguiseAPI getAPI() {
		return new DisguiseAPI() {
			public void disguiseToAll(Player player, Disguise disguise) {
				DisguiseManager.disguiseToAll(player, disguise);
			}
			
			public void undisguiseToAll(Player player) {
				DisguiseManager.undisguiseToAll(player);
			}
			
			public void undisguiseAll() {
				DisguiseManager.undisguiseAll();
			}
			
			public boolean isDisguised(Player player) {
				return DisguiseManager.isDisguised(player);
			}
			
			public Disguise getDisguise(Player player) {
				return DisguiseManager.getDisguise(player).clone();
			}
			
			public int getOnlineDisguiseCount() {
				return DisguiseManager.getOnlineDisguiseCount();
			}
			
			public String getLocale() {
				return "enUS";
			}
			
			public String getLocalizedPhrase(String name) {
				throw new UnsupportedOperationException("iDisguise no longer supports different languages.");
			}
			
			public SoundSystem getSoundSystem(DisguiseType type) {
				return SoundSystem.getSoundSystem(type);
			}
			
			public void setSoundSystem(DisguiseType type, SoundSystem soundSystem) {
				SoundSystem.setSoundSystem(type, soundSystem);
			}
		};
	}
	
	public String getVersion() {
		return getDescription().getVersion();
	}
	
	public String getFullName() {
		return "iDisguise v" + getVersion();
	}
	
	public boolean saveDisguises() {
		return configuration.getBoolean("save-disguises");
	}
	
	public boolean canDisguisedPlayersBeDamaged() {
		return configuration.getBoolean("entity-damage-while-disguised");
	}
	
	public boolean undisguisePlayerWhenHitByLiving() {
		return configuration.getBoolean("undisguise-on-hit");
	}
	
	public boolean requirePermissionForUndisguising() {
		return configuration.getBoolean("permission-for-undisguise");
	}
	
	public boolean isDisguisingPermittedInWorld(World world) {
		return isDisguisingPermittedInWorld(world.getName());
	}
	
	public boolean isDisguisingPermittedInWorld(String world) {
		return !configuration.getStringList("prohibited-worlds").contains(world);
	}
	
	public boolean checkForUpdates() {
		return configuration.getBoolean("check-for-updates");
	}
	
	public boolean undisguisePlayerWhenHitByProjectile() {
		return configuration.getBoolean("undisguise-on-projectile-hit");
	}
	
	public boolean undisguisePlayerWhenHitsOtherPlayer() {
		return configuration.getBoolean("undisguise-on-hit-other");
	}
	
	public boolean isSoundSystemEnabled() {
		return configuration.getBoolean("sound-system");
	}
	
	public boolean showOriginalPlayerNames() {
		return configuration.getBoolean("show-name-while-disguised");
	}
	
	public boolean canMobsTargetDisguisedPlayers() {
		return !configuration.getBoolean("no-target-while-disguised");
	}
	
	public boolean isGhostDisguiseEnabled() {
		return configuration.getBoolean("ghost-disguises");
	}
	
}