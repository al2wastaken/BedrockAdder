package dev.bedrockadder.paper;

import dev.bedrockadder.common.convert.ConversionContext;
import dev.bedrockadder.common.java.JavaPack;
import dev.bedrockadder.paper.deploy.Deployer;
import dev.bedrockadder.paper.deploy.FolderDeployer;
import dev.bedrockadder.paper.deploy.HttpDeployer;
import dev.bedrockadder.paper.deploy.LocalGeyserDeployer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.logging.Level;

public final class BedrockAdderPlugin extends JavaPlugin {

    private Settings settings;
    private ConversionService conversion;
    private FurnitureMirror furniture;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (getConfig().getString("sync.http.token", "").isEmpty()) {
            byte[] token = new byte[24];
            new SecureRandom().nextBytes(token);
            getConfig().set("sync.http.token", HexFormat.of().formatHex(token));
            saveConfig();
        }
        settings = Settings.load(getConfig());

        Deployer deployer;
        try {
            deployer = createDeployer();
        } catch (IOException | RuntimeException e) {
            getLogger().log(Level.SEVERE, "Could not set up sync type " + settings.syncType() + "; bundles are only written locally", e);
            deployer = offDeployer();
        }
        conversion = new ConversionService(this, deployer,
                new ConversionContext.Options(settings.iconSize(), settings.fonts(), settings.fontCellUnits(), settings.guiOffsetX()),
                settings.packName());
        conversion.deployExisting();
        getLogger().info("Bedrock bundles go to: " + deployer.describe());

        if (settings.furniture()) {
            BedrockPlayers bedrock = BedrockPlayers.detect(getServer());
            if (bedrock == null) {
                getLogger().warning("Furniture for Bedrock needs Floodgate or Geyser-Spigot on this server; it is disabled.");
            } else {
                furniture = new FurnitureMirror(this, bedrock, settings.furnitureYawOffset(), settings.furnitureYOffset());
                furniture.start();
                getLogger().info("Showing ItemsAdder furniture to Bedrock players (detected via " + bedrock.source() + ")");
            }
        }

        getServer().getPluginManager().registerEvents(new ItemsAdderListener(this), this);
        PluginCommand command = getCommand("bedrockadder");
        if (command != null) {
            BedrockAdderCommand executor = new BedrockAdderCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    @Override
    public void onDisable() {
        if (furniture != null) {
            furniture.stop();
        }
        if (conversion != null) {
            conversion.shutdown();
        }
    }

    private Deployer createDeployer() throws IOException {
        Plugin geyser = getServer().getPluginManager().getPlugin("Geyser-Spigot");
        Settings.SyncType type = settings.syncType();
        if (type == Settings.SyncType.AUTO) {
            type = geyser != null ? Settings.SyncType.LOCAL : Settings.SyncType.OFF;
        }
        return switch (type) {
            case LOCAL -> {
                if (geyser == null) {
                    throw new IllegalStateException("sync.type is LOCAL but Geyser-Spigot is not installed");
                }
                LocalGeyserDeployer local = new LocalGeyserDeployer(geyser.getDataFolder().toPath(), getLogger());
                local.checkGeyserConfig();
                installExtension(local);
                yield local;
            }
            case FOLDER -> {
                if (settings.syncFolder().isBlank()) {
                    throw new IllegalStateException("sync.type is FOLDER but sync.folder is empty");
                }
                yield new FolderDeployer(getServer().getWorldContainer().toPath().resolve(settings.syncFolder()).normalize());
            }
            case HTTP -> new HttpDeployer(settings.httpBind(), settings.httpPort(), settings.httpToken());
            default -> offDeployer();
        };
    }

    private void installExtension(LocalGeyserDeployer local) {
        try (InputStream jar = getResource(LocalGeyserDeployer.EXTENSION_JAR)) {
            if (jar == null) {
                getLogger().warning("This build does not contain the Geyser extension; install it manually.");
            } else if (local.installExtension(jar)) {
                getLogger().warning("Installed/updated the BedrockAdder Geyser extension. Restart the server to load it.");
            }
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Could not install the Geyser extension", e);
        }
    }

    private Deployer offDeployer() {
        Path folder = getDataFolder().toPath().resolve("bundle");
        return new Deployer() {
            @Override
            public String describe() {
                return folder + " only (sync is off)";
            }

            @Override
            public void deploy(Path bundleDirectory) {
            }
        };
    }

    /** Converts from ItemsAdder's generated pack on disk (used by the command and as a startup fallback). */
    void convertFromDisk(boolean force, CommandSender sender) {
        Plugin itemsAdder = getServer().getPluginManager().getPlugin("ItemsAdder");
        Path zip = itemsAdder == null ? null : itemsAdder.getDataFolder().toPath().resolve("output").resolve("generated.zip");
        if (zip == null || !Files.isRegularFile(zip)) {
            reply(sender, "ItemsAdder has not generated a resource pack yet. Run /iazip.");
            return;
        }
        Map<String, byte[]> files;
        try {
            files = JavaPack.readZip(zip);
        } catch (IOException | RuntimeException e) {
            // ItemsAdder's zip protection can make the archive unreadable on purpose.
            reply(sender, "Could not read " + zip + " (" + e.getMessage() + "). Run /iazip so BedrockAdder receives the pack directly.");
            return;
        }
        conversion.convert(files, force, sender);
    }

    private void reply(CommandSender sender, String message) {
        getLogger().warning(message);
        if (sender != null && !(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            sender.sendMessage("[BedrockAdder] " + message);
        }
    }

    Settings settings() {
        return settings;
    }

    ConversionService conversion() {
        return conversion;
    }
}
