/*
 * UnlimitedBamboo
 * Author: Kiley W.
 * Description: A Bukkit/Spigot plugin that allows admins to control bamboo growth
 * License: MIT
 */
package com.kiley.unlimitedbamboo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bamboo;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public final class UnlimitedBamboo extends JavaPlugin implements Listener {

    private int maxHeight;
    private int growthInterval;
    private double growthChance;

    private final Set<Location> bambooLocations = new HashSet<>();
    private final Queue<Location> growthQueue = new LinkedList<>();
    private static final int BLOCKS_PER_TICK = 50; // adjust for performance

    @Override
    public void onEnable() {
        saveDefaultConfig();
        maxHeight = getConfig().getInt("max-height", 32);
        growthInterval = getConfig().getInt("growth-interval-ticks", 4096);
        growthChance = getConfig().getDouble("growth-chance", 0.33);

        getServer().getPluginManager().registerEvents(this, this);

        // Tack existing bamboo in loaded chunks
        Bukkit.getWorlds().forEach(world -> {
            for (var chunk : world.getLoadedChunks()) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < world.getMaxHeight(); y++) {
                            Block block = chunk.getBlock(x, y, z);
                            if (block.getType() == Material.BAMBOO) {
                                bambooLocations.add(block.getLocation());
                            }
                        }
                    }
                }
            }
        });

        getLogger().info("UnlimitedBamboo enabled! Max height: " + maxHeight
                + ", growth interval: " + growthInterval
                + " ticks, growth chance: " + growthChance);

        // Schedule gradual growth task
        new BukkitRunnable() {
            @Override
            public void run() {
                scheduleGrowth();
            }
        }.runTaskTimer(this, 0L, growthInterval);

        // Tick-spread processing task (runs every tick)
        new BukkitRunnable() {
            @Override
            public void run() {
                processGrowthQueue();
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    @EventHandler
    public void onBambooGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.BAMBOO) return;

        bambooLocations.add(block.getLocation());
        event.setCancelled(true);
        enqueueGrowth(block.getLocation());
    }

    @EventHandler
    public void onBambooPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() == Material.BAMBOO) {
            bambooLocations.add(event.getBlockPlaced().getLocation());
        }
    }

    @EventHandler
    public void onBambooBreak(BlockBreakEvent event) {
        bambooLocations.remove(event.getBlock().getLocation());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        bambooLocations.removeIf(loc -> loc.getChunk().equals(event.getChunk()));
    }

    // Randomly queue bamboo growth
    private void scheduleGrowth() {
        for (Location loc : bambooLocations) {
            if (Math.random() < growthChance) {
                enqueueGrowth(loc);
            }
        }
    }

    private void enqueueGrowth(Location loc) {
        growthQueue.add(loc);
    }

    // Limit growth per tick to prevent lag
    private void processGrowthQueue() {
        int count = 0;
        while (count < BLOCKS_PER_TICK && !growthQueue.isEmpty()) {
            Location loc = growthQueue.poll();
            if (loc == null) continue;

            Block block = loc.getWorld().getBlockAt(loc);
            if (block.getType() == Material.BAMBOO) {
                growOneBlockFromTop(loc);
            }
            count++;
        }
    }

    private void growOneBlockFromTop(Location baseLoc) {
        Block block = baseLoc.getWorld().getBlockAt(baseLoc);

        // Find top of this bamboo stack
        Block top = block;
        while (top.getRelative(0, 1, 0).getType() == Material.BAMBOO) {
            top = top.getRelative(0, 1, 0);
        }

        // Count current bamboo height
        int height = 1;
        Block below = top.getRelative(0, -1, 0);
        while (below.getType() == Material.BAMBOO) {
            height++;
            below = below.getRelative(0, -1, 0);
        }

        if (height >= maxHeight) return;

        Block above = top.getRelative(0, 1, 0);
        if (above.getType() == Material.AIR) {
            Bamboo bambooData = (Bamboo) Bukkit.createBlockData(Material.BAMBOO);
            bambooData.setLeaves(Bamboo.Leaves.NONE);
            bambooData.setStage(0);
            above.setBlockData(bambooData, false);
            bambooLocations.add(above.getLocation());
        }
    }
}
