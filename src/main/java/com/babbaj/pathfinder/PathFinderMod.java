package com.babbaj.pathfinder;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import dev.babbaj.pathfinder.NetherPathfinder;
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
    public static final String MODID = "netherpathfinder";
    public static final String NAME = "Nether Pathfinder";
    public static final String VERSION = "1.1";

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
        if (NetherPathfinder.isThisSystemSupported()) {
            MinecraftForge.EVENT_BUS.register(new ExamplePathfinderControl(readSeedsFromDisk().orElse(new HashMap<>())));
        } else {
            logger.fatal("This system isn't supported lol");
        }
    }
}
