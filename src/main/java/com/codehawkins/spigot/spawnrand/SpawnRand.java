package com.codehawkins.spigot.spawnrand;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;

public final class SpawnRand extends JavaPlugin implements Listener {
    private File playerDataFile;
    private FileConfiguration playerData;

    private final FileConfiguration srConfig = getConfig();
    int centerX = srConfig.getInt("Spawnrand.centerX");
    int centerZ = srConfig.getInt("Spawnrand.centerZ");
    int minDistance = srConfig.getInt("Spawnrand.minDistance");
    int maxDistance = srConfig.getInt("Spawnrand.maxDistance");

    private World theWorld;
    private Location theLocation;

    @Override
    public void onEnable() {
        // Register events:
        getServer().getPluginManager().registerEvents(this, this);

        // Init Configs if necessary:
        this.saveDefaultConfig();
        createPlayerData();
    }

    @EventHandler
    public void onPlayerSpawnLocationEvent(PlayerSpawnLocationEvent event) throws IOException {
        theWorld = event.getSpawnLocation().getWorld();
        if (validateConfig()) {
            Player thePlayer = event.getPlayer();
            String playerUUID = thePlayer.getUniqueId().toString();
            if (playerData.isConfigurationSection(playerUUID)) {
                theLocation = new Location(theWorld
                        , playerData.getInt(playerUUID + ".x")
                        , playerData.getInt(playerUUID + ".y")
                        , playerData.getInt(playerUUID + ".z"));
            } else {
                do { randomizeLocation(); } while (!locationIsSafe());
                savePlayerData(thePlayer);
            }
            event.setSpawnLocation(theLocation);
        } else {
            System.out.print("Error: Config is invalid in Spawnrand.onPlayerSpawnLocationEvent(...)");
        }
    }

    // public FileConfiguration getPlayerData() {
    //     return this.playerData;
    // }

    private void createPlayerData() {
        // https://www.spigotmc.org/wiki/config-files/
        playerDataFile = new File(getDataFolder(), "playerData.yml");
        if (!playerDataFile.exists()) {
            playerDataFile.getParentFile().mkdirs();
            saveResource("playerData.yml", false);
        }

        playerData = new YamlConfiguration();
        try {
            playerData.load(playerDataFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }

    private void savePlayerData(Player thePlayer) throws IOException {
        String playerUUID = thePlayer.getUniqueId().toString();
        playerData.createSection(playerUUID);
        playerData.set(playerUUID + ".name", thePlayer.getName());
        playerData.set(playerUUID + ".standingOn",
                theWorld.getBlockAt(
                        theLocation.getBlockX(),
                        theLocation.getBlockY() - 1,
                        theLocation.getBlockZ()).getType().toString());
        playerData.set(playerUUID + ".x", theLocation.getBlockX());
        playerData.set(playerUUID + ".y", theLocation.getBlockY());
        playerData.set(playerUUID + ".z", theLocation.getBlockZ());
        playerData.save(playerDataFile);
    }

    private void updateConfig() {
        srConfig.set("Spawnrand.maxDistance", maxDistance);
        srConfig.set("Spawnrand.minDistance", minDistance);
        srConfig.set("Spawnrand.centerX", centerX);
        srConfig.set("Spawnrand.centerZ", centerZ);
        saveConfig();
    }

    private void randomizeLocation() {
        // Get a point within distance spec.
        int x; int z; double distance;
        do {
            x = randBetween(-maxDistance, maxDistance);
            z = randBetween(-maxDistance, maxDistance);
            distance = Math.sqrt(Math.pow(x, 2) + Math.pow(z, 2));
        } while (distance <= minDistance || distance >= maxDistance);

        // Apply the center offset.
        x += centerX;
        z += centerZ;
        theLocation = new Location(theWorld, x, theWorld.getHighestBlockAt(x, z).getY() + 1, z);
    }

    private boolean locationIsSafe() {
        Block blockUnder = theWorld.getBlockAt(theLocation.getBlockX(), theLocation.getBlockY() - 1, theLocation.getBlockZ());
        Block blockHead = theWorld.getBlockAt(theLocation.getBlockX(), theLocation.getBlockY() + 1, theLocation.getBlockZ());
        List<String> dangerBlock = getConfig().getStringList("dangerBlock");

        return !dangerBlock.contains(blockUnder.getType().toString())
                && blockUnder.getType().isSolid()
                && !theLocation.getBlock().getType().isSolid()      // Apparently can be glass, etc.
                && !blockHead.getType().isSolid();
    }

    private int randBetween(int min, int max) {
        // Inclusive of both min and max!
        SecureRandom random = new SecureRandom();
        int range = max - min + 1;
        return random.nextInt(range) + min;
    }

    private boolean validateConfig() {
        boolean isModified = false;

        int worldRadius = (int) (theWorld.getWorldBorder().getSize() / 2);
        int larger = Math.max(centerX, centerZ);

        if (worldRadius < 500) {
            return false;
        }

        if (maxDistance < 100) {
            isModified = true;
            maxDistance = 100;
        }

        if (minDistance > Math.round(maxDistance * 0.80)) {
            isModified = true;
            minDistance = (int) Math.round(maxDistance * 0.80);
        }

        if (maxDistance + larger > worldRadius) {
            isModified = true;
            int scaleDown = worldRadius / (maxDistance + larger);
            maxDistance = (int) Math.floor(maxDistance * scaleDown);
            centerX = (int) Math.floor(centerX * scaleDown);
            centerZ = (int) Math.floor(centerZ * scaleDown);
        }

        if (maxDistance > worldRadius) {
            isModified = true;
            maxDistance = worldRadius;
        }

        if (isModified) {
            updateConfig();
        }
        return true;
    }
}