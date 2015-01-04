package com.instancedev.minigamesapibungee;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

public class Main extends JavaPlugin implements PluginMessageListener, Listener {

	BungeeServer serv;

	public void onEnable() {
		this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
		this.getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", this);

		Bukkit.getPluginManager().registerEvents(this, this);

		init();

		System.out.println("Initializing BungeeServer");
		serv = new BungeeServer(this);
	}

	public void onDisable() {
		try {
			serv.server.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		// /join <game> <arena> <server> [player]
		if (args.length > 2) {
			String game = args[0];
			String arena = args[1];
			String server = args[2];

			Player p = null;

			if (sender instanceof Player) {
				p = (Player) sender;
			}
			if (args.length > 3) {
				p = Bukkit.getPlayer(args[3]);
			}
			if (p == null) {
				return true;
			}

			ByteArrayDataOutput out = ByteStreams.newDataOutput();
			try {
				out.writeUTF("Forward");
				out.writeUTF("ALL");
				out.writeUTF("MinigamesLibBack");

				ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
				DataOutputStream msgout = new DataOutputStream(msgbytes);
				String info = game + ":" + arena + ":" + p.getName();
				System.out.println(info);
				msgout.writeUTF(info);

				out.writeShort(msgbytes.toByteArray().length);
				out.write(msgbytes.toByteArray());

				Bukkit.getServer().sendPluginMessage(this, "BungeeCord", out.toByteArray());
			} catch (Exception e) {
				e.printStackTrace();
			}
			connectToServer(this, p.getName(), server);
		} else {
			sender.sendMessage(ChatColor.GRAY + "Usage: /join <game> <arena> <server> [player]");
			sender.sendMessage(ChatColor.GRAY + "[player] is optional.");
		}
		return true;
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		if (!channel.equals("BungeeCord")) {
			return;
		}
		ByteArrayDataInput in = ByteStreams.newDataInput(message);
		String subchannel = in.readUTF();
		if (subchannel.equals("MinigamesLib")) {
			short len = in.readShort();
			byte[] msgbytes = new byte[len];
			in.readFully(msgbytes);

			DataInputStream msgin = new DataInputStream(new ByteArrayInputStream(msgbytes));
			try {
				final String signData = msgin.readUTF();
				final String plugin_ = signData.split(":")[0];
				final String arena = signData.split(":")[1];
				final String arenastate = signData.split(":")[2];
				final int count = Integer.parseInt(signData.split(":")[3]);
				final int maxcount = Integer.parseInt(signData.split(":")[4]);
				// System.out.println(plugin_ + " -> " + arena);
				Bukkit.getScheduler().runTaskLater(this, new Runnable() {
					public void run() {
						// updateSign(plugin_, arena, arenastate, count, maxcount);
					}
				}, 10L);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void connectToServer(JavaPlugin plugin, String player, String server) {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(stream);
		try {
			out.writeUTF("Connect");
			out.writeUTF(server);
		} catch (IOException e) {
			e.printStackTrace();
		}
		Bukkit.getPlayer(player).sendPluginMessage(plugin, "BungeeCord", stream.toByteArray());
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		updateAllServerSigns();
	}

	@EventHandler
	public void onBreak(BlockBreakEvent event) {
		if (event.getBlock().getType() == Material.SIGN_POST || event.getBlock().getType() == Material.WALL_SIGN) {
			if (getConfig().isSet("arenas.")) {
				for (String mg_key : getConfig().getConfigurationSection("arenas.").getKeys(false)) {
					for (String arena_key : getConfig().getConfigurationSection("arenas." + mg_key + ".").getKeys(false)) {
						Location l = new Location(Bukkit.getWorld(getConfig().getString("arenas." + mg_key + "." + arena_key + ".world")), getConfig().getInt("arenas." + mg_key + "." + arena_key + ".loc.x"), getConfig().getInt("arenas." + mg_key + "." + arena_key + ".loc.y"), getConfig().getInt("arenas." + mg_key + "." + arena_key + ".loc.z"));
						System.out.println(l);
						if (l.distance(event.getBlock().getLocation()) < 1) {
							// getConfig().set("arenas." + mg_key + "." + arena_key + ".server", null);
							getConfig().set("arenas." + mg_key + "." + arena_key, null);
							saveConfig();
							return;
						}
					}
				}
			}
		}
	}

	@EventHandler
	public void onSignUse(PlayerInteractEvent event) {
		if (event.hasBlock()) {
			if (event.getClickedBlock().getType() == Material.SIGN_POST || event.getClickedBlock().getType() == Material.WALL_SIGN) {
				if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
					return;
				}
				final Sign s = (Sign) event.getClickedBlock().getState();
				String server = getServerBySignLocation(s.getLocation());
				if (server != null && server != "") {
					try {
						ByteArrayDataOutput out = ByteStreams.newDataOutput();
						try {
							out.writeUTF("Forward");
							out.writeUTF("ALL");
							out.writeUTF("MinigamesLibBack");

							ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
							DataOutputStream msgout = new DataOutputStream(msgbytes);
							String info = getInfoBySignLocation(s.getLocation()) + ":" + event.getPlayer().getName();
							System.out.println(info);
							msgout.writeUTF(info);

							out.writeShort(msgbytes.toByteArray().length);
							out.write(msgbytes.toByteArray());

							Bukkit.getServer().sendPluginMessage(this, "BungeeCord", out.toByteArray());
						} catch (Exception e) {
							e.printStackTrace();
						}
					} catch (Exception e) {
						System.out.println("Error occurred while sending first sign request: " + e.getMessage() + " - Invalid server/minigame/arena?");
					}
					connectToServer(this, event.getPlayer().getName(), server);
				}
			}
		}

	}

	@EventHandler
	public void onSignChange(SignChangeEvent event) {
		Player p = event.getPlayer();
		if (event.getLine(0).toLowerCase().equalsIgnoreCase("mglib")) {
			if (event.getPlayer().hasPermission("mgapi.sign") || event.getPlayer().isOp()) {
				if (!event.getLine(1).equalsIgnoreCase("") && !event.getLine(2).equalsIgnoreCase("") && !event.getLine(3).equalsIgnoreCase("")) {
					String mg = event.getLine(1);
					String arena = event.getLine(2);
					String server = event.getLine(3);

					getConfig().set("arenas." + mg + "." + arena + ".server", server);
					getConfig().set("arenas." + mg + "." + arena + ".world", p.getWorld().getName());
					getConfig().set("arenas." + mg + "." + arena + ".loc.x", event.getBlock().getLocation().getBlockX());
					getConfig().set("arenas." + mg + "." + arena + ".loc.y", event.getBlock().getLocation().getBlockY());
					getConfig().set("arenas." + mg + "." + arena + ".loc.z", event.getBlock().getLocation().getBlockZ());
					saveConfig();

					p.sendMessage(ChatColor.GREEN + "Successfully set sign.");

					updateSign(mg, arena, "JOIN", event);

					requestServerSign(mg, arena);

				}
			}
		}
	}

	public String getServerBySignLocation(Location sign) {
		if (getConfig().isSet("arenas.")) {
			for (String mg_key : getConfig().getConfigurationSection("arenas.").getKeys(false)) {
				for (String arena_key : getConfig().getConfigurationSection("arenas." + mg_key + ".").getKeys(false)) {
					Location l = new Location(Bukkit.getWorld(getConfig().getString("arenas." + mg_key + "." + arena_key + ".world")), getConfig().getInt("arenas." + mg_key + "." + arena_key + ".loc.x"), getConfig().getInt("arenas." + mg_key + "." + arena_key + ".loc.y"), getConfig().getInt("arenas." + mg_key + "." + arena_key + ".loc.z"));
					if (l.distance(sign) < 1) {
						return getConfig().getString("arenas." + mg_key + "." + arena_key + ".server");
					}
				}
			}
		}
		return "";
	}

	public String getInfoBySignLocation(Location sign) {
		if (getConfig().isSet("arenas.")) {
			for (String mg_key : getConfig().getConfigurationSection("arenas.").getKeys(false)) {
				for (String arena_key : getConfig().getConfigurationSection("arenas." + mg_key + ".").getKeys(false)) {
					Location l = new Location(Bukkit.getWorld(getConfig().getString("arenas." + mg_key + "." + arena_key + ".world")), getConfig().getInt("arenas." + mg_key + "." + arena_key + ".loc.x"), getConfig().getInt("arenas." + mg_key + "." + arena_key + ".loc.y"), getConfig().getInt("arenas." + mg_key + "." + arena_key + ".loc.z"));
					if (l.distance(sign) < 1) {
						return mg_key + ":" + arena_key;
					}
				}
			}
		}
		return "";
	}

	public static String squares = Character.toString((char) 0x25A0);

	public static char[] squares_mid = new char[10];
	public static char[] squares_full = new char[10];
	public static char[] squares_medium = new char[10];
	public static char[] squares_light = new char[10];

	public void init() {
		Arrays.fill(squares_mid, (char) 0x25A0);
		Arrays.fill(squares_full, (char) 0x2588);
		Arrays.fill(squares_medium, (char) 0x2592);
		Arrays.fill(squares_light, (char) 0x2591);
		for (int i = 0; i < 10; i++) {
			squares += Character.toString((char) 0x25A0);
		}

		String[] states = new String[] { "JOIN", "STARTING", "INGAME", "RESTARTING" };
		ChatColor[] col = new ChatColor[] { ChatColor.GREEN, ChatColor.GREEN, ChatColor.RED, ChatColor.GOLD };
		int c = 0;
		for (String state : states) {
			this.getConfig().addDefault("signs." + state.toLowerCase() + ".0", col[c] + "<minigame>");
			this.getConfig().addDefault("signs." + state.toLowerCase() + ".1", col[c] + "<arena>");
			this.getConfig().addDefault("signs." + state.toLowerCase() + ".2", col[c] + "<count>/<maxcount>");
			this.getConfig().addDefault("signs." + state.toLowerCase() + ".3", col[c] + "");
			c++;
		}

		this.getConfig().options().copyDefaults(true);
		this.saveConfig();
	}

	public Sign getSignFromArena(String mg, String arena) {
		if (!getConfig().isSet("arenas." + mg + "." + arena + ".world")) {
			return null;
		}
		Location b_ = new Location(Bukkit.getServer().getWorld(getConfig().getString("arenas." + mg + "." + arena + ".world")), getConfig().getInt("arenas." + mg + "." + arena + ".loc.x"), getConfig().getInt("arenas." + mg + "." + arena + ".loc.y"), getConfig().getInt("arenas." + mg + "." + arena + ".loc.z"));
		if (b_ != null) {
			if (b_.getWorld() != null) {
				if (b_.getBlock().getState() != null) {
					BlockState bs = b_.getBlock().getState();
					Sign s_ = null;
					if (bs instanceof Sign) {
						s_ = (Sign) bs;
					}
					return s_;
				}
			}
		}
		return null;
	}

	public void updateSign(String mg, String arenaname, String arenastate, int count, int maxcount) {
		Sign s = getSignFromArena(mg, arenaname);
		arenastate = arenastate.toLowerCase();
		if (s != null) {
			s.getBlock().getChunk().load();
			s.setLine(0, getConfig().getString("signs." + arenastate + ".0").replaceAll("&", "§").replace("<count>", Integer.toString(count)).replace("<maxcount>", Integer.toString(maxcount)).replace("<arena>", arenaname).replace("<minigame>", mg).replace("[]", new String(squares_mid)).replace("[1]", new String(squares_full).replace("[2]", new String(squares_medium)).replace("[3]", new String(squares_light))));
			s.setLine(1, getConfig().getString("signs." + arenastate + ".1").replaceAll("&", "§").replace("<count>", Integer.toString(count)).replace("<maxcount>", Integer.toString(maxcount)).replace("<arena>", arenaname).replace("<minigame>", mg).replace("[]", new String(squares_mid)).replace("[1]", new String(squares_full).replace("[2]", new String(squares_medium)).replace("[3]", new String(squares_light))));
			s.setLine(2, getConfig().getString("signs." + arenastate + ".2").replaceAll("&", "§").replace("<count>", Integer.toString(count)).replace("<maxcount>", Integer.toString(maxcount)).replace("<arena>", arenaname).replace("<minigame>", mg).replace("[]", new String(squares_mid)).replace("[1]", new String(squares_full).replace("[2]", new String(squares_medium)).replace("[3]", new String(squares_light))));
			s.setLine(3, getConfig().getString("signs." + arenastate + ".3").replaceAll("&", "§").replace("<count>", Integer.toString(count)).replace("<maxcount>", Integer.toString(maxcount)).replace("<arena>", arenaname).replace("<minigame>", mg).replace("[]", new String(squares_mid)).replace("[1]", new String(squares_full).replace("[2]", new String(squares_medium)).replace("[3]", new String(squares_light))));
			s.update();
		}
	}

	public void updateSign(String mg, String arenaname, String arenastate, SignChangeEvent event) {
		int count = 0;
		int maxcount = 10;
		arenastate = arenastate.toLowerCase();
		event.setLine(0, getConfig().getString("signs." + arenastate + ".0").replaceAll("&", "§").replace("<count>", Integer.toString(count)).replace("<maxcount>", Integer.toString(maxcount)).replace("<arena>", arenaname).replace("<minigame>", mg).replace("[]", new String(squares_mid)).replace("[1]", new String(squares_full).replace("[2]", new String(squares_medium)).replace("[3]", new String(squares_light))));
		event.setLine(1, getConfig().getString("signs." + arenastate + ".1").replaceAll("&", "§").replace("<count>", Integer.toString(count)).replace("<maxcount>", Integer.toString(maxcount)).replace("<arena>", arenaname).replace("<minigame>", mg).replace("[]", new String(squares_mid)).replace("[1]", new String(squares_full).replace("[2]", new String(squares_medium)).replace("[3]", new String(squares_light))));
		event.setLine(2, getConfig().getString("signs." + arenastate + ".2").replaceAll("&", "§").replace("<count>", Integer.toString(count)).replace("<maxcount>", Integer.toString(maxcount)).replace("<arena>", arenaname).replace("<minigame>", mg).replace("[]", new String(squares_mid)).replace("[1]", new String(squares_full).replace("[2]", new String(squares_medium)).replace("[3]", new String(squares_light))));
		event.setLine(3, getConfig().getString("signs." + arenastate + ".3").replaceAll("&", "§").replace("<count>", Integer.toString(count)).replace("<maxcount>", Integer.toString(maxcount)).replace("<arena>", arenaname).replace("<minigame>", mg).replace("[]", new String(squares_mid)).replace("[1]", new String(squares_full).replace("[2]", new String(squares_medium)).replace("[3]", new String(squares_light))));
	}

	public void updateAllServerSigns() {
		if (getConfig().isSet("arenas.")) {
			for (String mg_key : getConfig().getConfigurationSection("arenas.").getKeys(false)) {
				for (String arena_key : getConfig().getConfigurationSection("arenas." + mg_key + ".").getKeys(false)) {
					this.requestServerSign(mg_key, arena_key);
				}
			}
		}
	}

	public void requestServerSign(String mg_key, String arena_key) {
		try {
			ByteArrayDataOutput out = ByteStreams.newDataOutput();
			try {
				out.writeUTF("Forward");
				out.writeUTF("ALL");
				out.writeUTF("MinigamesLibRequest");

				ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
				DataOutputStream msgout = new DataOutputStream(msgbytes);
				msgout.writeUTF(mg_key + ":" + arena_key);

				out.writeShort(msgbytes.toByteArray().length);
				out.write(msgbytes.toByteArray());

				Bukkit.getServer().sendPluginMessage(this, "BungeeCord", out.toByteArray());

				// TODO if no answer after 2 seconds, server empty!

			} catch (Exception e) {
				e.printStackTrace();
			}
		} catch (Exception e) {
			System.out.println("Error occurred while sending extra sign request: " + e.getMessage());
		}
	}
}
