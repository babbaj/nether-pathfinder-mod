package com.babbaj.pathfinder;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Mod(modid = PathFinderMod.MODID, name = PathFinderMod.NAME, version = PathFinderMod.VERSION)
public class PathFinderMod {
    static {
        try {
            final String library = "native/" + System.mapLibraryName("nether_pathfinder");
            final InputStream libraryStream = PathFinder.class.getClassLoader().getResourceAsStream(library);
            Objects.requireNonNull(libraryStream, "Failed to find pathfinder library (" + library + ")");
            final String tempName = System.mapLibraryName("nether_pathfinder_temp");
            final String[] split = tempName.split("\\.");
            final Path tempFile = Files.createTempFile(split[0], split[1]);
            System.out.println("Created temp file at " + tempFile.toAbsolutePath());
            try {
                Files.copy(libraryStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
                System.load(tempFile.toAbsolutePath().toString());
            } finally {
                try {
                    Files.delete(tempFile);
                } catch (IOException ex) {
                    System.out.println("trolled");
                }
                tempFile.toFile().delete();
                tempFile.toFile().deleteOnExit();
            }

            System.out.println("Loaded shared library");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static final String MODID = "netherpathfinder";
    public static final String NAME = "Nether Pathfinder";
    public static final String VERSION = "1.0";

    private static Logger logger;
    public static final Path SEEDS_PATH = Paths.get("pathfinder_seeds.json");

    public static void writeSeedsToDisk(Map<String, Long> seeds) {
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(seeds);
        try {
            Files.write(SEEDS_PATH, json.getBytes());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static Optional<Map<String, Long>> readSeedsFromDisk() {
        if (!Files.isReadable(SEEDS_PATH)) return Optional.empty();

        try {
            JsonReader reader = new JsonReader(Files.newBufferedReader(SEEDS_PATH));
            return Optional.of(
                new Gson().fromJson(reader, new TypeToken<Map<String, Long>>() {}.getType())
            );
        } catch (IOException | JsonParseException ex) {
            ex.printStackTrace();
            return Optional.empty();
        }
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        logger = event.getModLog();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event)
    {
        // TODO: maintain list of seeds
        MinecraftForge.EVENT_BUS.register(new ExamplePathfinderControl(readSeedsFromDisk().orElse(new HashMap<>())));
    }
}
