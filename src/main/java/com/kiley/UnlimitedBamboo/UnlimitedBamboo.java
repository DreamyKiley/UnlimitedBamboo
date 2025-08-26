/*
 * UnlimitedBamboo
 * Author: Kiley W.
 * Description: A Bukkit/Spigot plugin that allows admins to control bamboo growth
 * License: MIT
 */
package com.kiley.unlimitedbamboo;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bamboo;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class UnlimitedBamboo extends JavaPlugin implements Listener {

    private int maxHeight;
    private int growthInterval;
    private double growthChance;

    @Override
    public void onEnable() {
        // Load config
        saveDefaultConfig();
        maxHeight = getConfig().getInt("max-height", 32); // Vanilla: 16, IGNORE IF BUILDING
        growthInterval = getConfig().getInt("growth-interval-ticks", 4096); // Vanilla: 3.4mins
        growthChance = getConfig().getDouble("growth-chance", 0.33); // Vanilla: 33%

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("UnlimitedBamboo enabled! Max height: " + maxHeight
                + ", growth interval: " + growthInterval
                + " ticks, growth chance: " + growthChance);

        // Schedule gradual growth task (optional, for global natural growth)
        new BukkitRunnable() {
            @Override
            public void run() {
                growAllBamboo();
            }
        }.runTaskTimer(this, 0L, growthInterval);
    }

    @EventHandler
    public void onBambooGrow(BlockGrowEvent event) {
        // Only handle bamboo blocks
        if (!(event.getBlock().getBlockData() instanceof Bamboo)) return;

        Block block = event.getBlock();

        // Count current bamboo height
        int height = 1;
        Block below = block.getRelative(0, -1, 0);
        while (below.getType() == Material.BAMBOO) {
            height++;
            below = below.getRelative(0, -1, 0);
        }

        // Cancel vanilla growth to prevent jumps
        event.setCancelled(true);

        // Only grow one block if under maxHeight
        if (height < maxHeight) {
            Block above = block.getRelative(0, 1, 0);
            if (above.getType() == Material.AIR) {
                Bamboo bambooData = (Bamboo) Bukkit.createBlockData(Material.BAMBOO);
                bambooData.setLeaves(Bamboo.Leaves.NONE);
                bambooData.setStage(0);
                above.setBlockData(bambooData, false);
            }
        }
    }

    private void growAllBamboo() {
        Bukkit.getWorlds().forEach(world -> {
            for (var chunk : world.getLoadedChunks()) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < world.getMaxHeight(); y++) {
                            Block block = chunk.getBlock(x, y, z);

                            // Only process bamboo occasionally
                            if (block.getType() == Material.BAMBOO && Math.random() < growthChance) {
                                growOneBlock(block);
                            }
                        }
                    }
                }
            }
        });
    }

    private void growOneBlock(Block baseBlock) {
        var world = baseBlock.getWorld();
        int x = baseBlock.getX();
        int y = baseBlock.getY();
        int z = baseBlock.getZ();

        // Count current bamboo stack height
        int height = 1;
        Block below = baseBlock.getRelative(0, -1, 0);
        while (below.getType() == Material.BAMBOO) {
            height++;
            below = below.getRelative(0, -1, 0);
        }

        // Stop if already at max height
        if (height >= maxHeight) return;

        // Grow one block above current top
        Block above = baseBlock.getRelative(0, 1, 0);
        if (above.getType() == Material.AIR) {
            Bamboo bambooData = (Bamboo) Bukkit.createBlockData(Material.BAMBOO);
            bambooData.setLeaves(Bamboo.Leaves.NONE);
            bambooData.setStage(0);
            above.setBlockData(bambooData, false);
        }
    }
}
