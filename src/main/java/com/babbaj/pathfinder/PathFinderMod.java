package com.babbaj.pathfinder;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Objects;

@Mod(modid = PathFinderMod.MODID, name = PathFinderMod.NAME, version = PathFinderMod.VERSION)
public class PathFinderMod {
    static {
        try {
            final InputStream libraryStream = PathFinder.class.getClassLoader().getResourceAsStream("native/libnether_pathfinder.dll");
            Objects.requireNonNull(libraryStream, "Failed to find pathfinder library");
            final Path tempFile = Files.createTempFile("libnether_pathfinder_temp", ".dll");
            System.out.println("Created temp file at " + tempFile.toAbsolutePath().toString());
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


    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        logger = event.getModLog();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event)
    {
        // TODO: maintain list of seeds
        MinecraftForge.EVENT_BUS.register(new ExamplePathfinderControl(new HashMap<>()));
    }
}
