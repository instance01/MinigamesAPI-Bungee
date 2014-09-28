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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

public class Main extends JavaPlugin implements PluginMessageListener, Listener {

	public void onEnable() {
		this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
		this.getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", this);

		Bukkit.getPluginManager().registerEvents(this, this);

		init();
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {
		System.out.println(channel);
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
				String signData = msgin.readUTF();
				String plugin_ = signData.split(":")[0];
				String arena = signData.split(":")[1];
				String arenastate = signData.split(":")[2];
				int count = Integer.parseInt(signData.split(":")[3]);
				int maxcount = Integer.parseInt(signData.split(":")[4]);
				System.out.println(plugin_ + " -> " + arena);
				this.updateSign(plugin_, arena, arenastate, count, maxcount);
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
	public void onSignUse(PlayerInteractEvent event) {
		if (event.hasBlock()) {
			if (event.getClickedBlock().getType() == Material.SIGN_POST || event.getClickedBlock().getType() == Material.WALL_SIGN) {
				if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
					return;
				}
				final Sign s = (Sign) event.getClickedBlock().getState();
				String server = getServerBySignLocation(s.getLocation());
				if (server != null && server != "") {
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

					updateSign(arena, "JOIN", event);

				}
			}
		}
	}

	public String getServerBySignLocation(Location sign) {
		if (getConfig().isSet("arenas.")) {
			for (String mg_key : getConfig().getConfigurationSection("arenas.").getKeys(false)) {
				for (String arena_key : getConfig().getConfigurationSection("arenas." + mg_key + ".").getKeys(false)) {
					Location l = new Location(Bukkit.getWorld(getConfig().getString("arenas." + mg_key + "." + arena_key + ".world")), getConfig().getInt("arenas." + mg_key + "." + arena_key + ".loc.x"), getConfig().getInt("arenas." + mg_key + "." + arena_key + ".loc.y"), getConfig().getInt("arenas." + mg_key + "." + arena_key + ".loc.z"));
					System.out.println(l);
					if (l.distance(sign) < 1) {
						return getConfig().getString("arenas." + mg_key + "." + arena_key + ".server");
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
		for (String state : states) {
			this.getConfig().addDefault("signs." + state.toLowerCase() + ".0", "[]");
			this.getConfig().addDefault("signs." + state.toLowerCase() + ".1", "<arena>");
			this.getConfig().addDefault("signs." + state.toLowerCase() + ".2", "<count>/<maxcount>");
			this.getConfig().addDefault("signs." + state.toLowerCase() + ".3", "[]");
		}

		this.getConfig().options().copyDefaults(true);
		this.saveConfig();
	}

	public Sign getSignFromArena(String mg, String arena) {
		if (!getConfig().isSet("arenas." + mg + "." + arena + ".sign.world")) {
			return null;
		}
		Location b_ = new Location(Bukkit.getServer().getWorld(getConfig().getString("arenas." + mg + "." + arena + ".sign.world")), getConfig().getInt("arenas." + mg + "." + arena + ".sign.loc.x"), getConfig().getInt("arenas." + mg + "." + arena + ".sign.loc.y"), getConfig().getInt("arenas." + mg + "." + arena + ".sign.loc.z"));
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
		if (s != null) {
			s.setLine(0, getConfig().getString("signs." + arenastate + ".0").replaceAll("&", "§").replace("<count>", Integer.toString(count)).replace("<maxcount>", Integer.toString(maxcount)).replace("<arena>", arenaname).replace("[]", new String(squares_mid)).replace("[1]", new String(squares_full).replace("[2]", new String(squares_medium)).replace("[3]", new String(squares_light))));
			s.setLine(1, getConfig().getString("signs." + arenastate + ".1").replaceAll("&", "§").replace("<count>", Integer.toString(count)).replace("<maxcount>", Integer.toString(maxcount)).replace("<arena>", arenaname).replace("[]", new String(squares_mid)).replace("[1]", new String(squares_full).replace("[2]", new String(squares_medium)).replace("[3]", new String(squares_light))));
			s.setLine(2, getConfig().getString("signs." + arenastate + ".2").replaceAll("&", "§").replace("<count>", Integer.toString(count)).replace("<maxcount>", Integer.toString(maxcount)).replace("<arena>", arenaname).replace("[]", new String(squares_mid)).replace("[1]", new String(squares_full).replace("[2]", new String(squares_medium)).replace("[3]", new String(squares_light))));
			s.setLine(3, getConfig().getString("signs." + arenastate + ".3").replaceAll("&", "§").replace("<count>", Integer.toString(count)).replace("<maxcount>", Integer.toString(maxcount)).replace("<arena>", arenaname).replace("[]", new String(squares_mid)).replace("[1]", new String(squares_full).replace("[2]", new String(squares_medium)).replace("[3]", new String(squares_light))));
			s.update();
		}
	}

	public void updateSign(String arenaname, String arenastate, SignChangeEvent event) {
		int count = 0;
		int maxcount = 10;
		event.setLine(0, getConfig().getString("signs." + arenastate + ".0").replaceAll("&", "§").replace("<count>", Integer.toString(count)).replace("<maxcount>", Integer.toString(maxcount)).replace("<arena>", arenaname).replace("[]", new String(squares_mid)).replace("[1]", new String(squares_full).replace("[2]", new String(squares_medium)).replace("[3]", new String(squares_light))));
		event.setLine(1, getConfig().getString("signs." + arenastate + ".1").replaceAll("&", "§").replace("<count>", Integer.toString(count)).replace("<maxcount>", Integer.toString(maxcount)).replace("<arena>", arenaname).replace("[]", new String(squares_mid)).replace("[1]", new String(squares_full).replace("[2]", new String(squares_medium)).replace("[3]", new String(squares_light))));
		event.setLine(2, getConfig().getString("signs." + arenastate + ".2").replaceAll("&", "§").replace("<count>", Integer.toString(count)).replace("<maxcount>", Integer.toString(maxcount)).replace("<arena>", arenaname).replace("[]", new String(squares_mid)).replace("[1]", new String(squares_full).replace("[2]", new String(squares_medium)).replace("[3]", new String(squares_light))));
		event.setLine(3, getConfig().getString("signs." + arenastate + ".3").replaceAll("&", "§").replace("<count>", Integer.toString(count)).replace("<maxcount>", Integer.toString(maxcount)).replace("<arena>", arenaname).replace("[]", new String(squares_mid)).replace("[1]", new String(squares_full).replace("[2]", new String(squares_medium)).replace("[3]", new String(squares_light))));
	}
}
