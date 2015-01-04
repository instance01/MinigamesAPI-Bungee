package com.instancedev.minigamesapibungee;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class BungeeServer {

	Main plugin;
	ServerSocket server;

	public BungeeServer(Main p) {
		this.plugin = p;
		new Thread(new Runnable() {
			public void run() {
				start();
			}
		}).start();
	}

	public void start() {
		int port = 13380;
		while (!available(port) && port < 13400) {
			port++;
		}
		Bukkit.getLogger().info("Port > " + port);
		try {
			server = new ServerSocket(port);
			while (true) {
				final Socket clientSocket = server.accept();
				new Thread(new Runnable() {
					public void run() {
						try {
							PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
							BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
							String t = "";
							while ((t = in.readLine()) != null) {
								if (t.startsWith("sign:")) {
									System.out.println("Client: " + t);
									String signData = t;
									final String plugin_ = signData.split(":")[1];
									final String arena = signData.split(":")[2];
									final String arenastate = signData.split(":")[3];
									final int count = Integer.parseInt(signData.split(":")[4]);
									final int maxcount = Integer.parseInt(signData.split(":")[5]);
									System.out.println("! " + plugin_ + " -> " + arena);
									Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
										public void run() {
											plugin.updateSign(plugin_, arena, arenastate, count, maxcount);
										}
									}, 2L);
								}
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}).start();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// http://stackoverflow.com/questions/434718/sockets-discover-port-availability-using-java
	/**
	 * Checks to see if a specific port is available.
	 *
	 * @param port
	 *            the port to check for availability
	 */
	public static boolean available(int port) {
		ServerSocket ss = null;
		DatagramSocket ds = null;
		try {
			ss = new ServerSocket(port);
			ss.setReuseAddress(true);
			ds = new DatagramSocket(port);
			ds.setReuseAddress(true);
			return true;
		} catch (IOException e) {
		} finally {
			if (ds != null) {
				ds.close();
			}

			if (ss != null) {
				try {
					ss.close();
				} catch (IOException e) {
					/* should not be thrown */
				}
			}
		}

		return false;
	}
}
