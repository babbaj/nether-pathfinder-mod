package com.babbaj.pathfinder;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ExamplePathfinderControl {

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, Long> seeds;
    private PathRenderer renderer;
    private Future<?> pathFuture;

    private final Map<String, Function<OptionParser, ICommand>> commands = ImmutableMap.<String, Function<OptionParser, ICommand>>builder()
        .put("help", Help::new)
        .put("pathfind", PathFind::new)
        .put("addseed", AddSeed::new)
        .put("cancel", Cancel::new)
        .put("reset", Reset::new)
        .build();

    public ExamplePathfinderControl(Map<String, Long> seeds) {
        this.seeds = seeds;
    }

    // TODO: add description function
    interface ICommand extends BiConsumer<List<String>, OptionSet> {
        @Override
        void accept(List<String> args, OptionSet options);
    }

    private long getSeedFomOption(String arg) {
        try {
            return Long.parseLong(arg);
        } catch (NumberFormatException ex) {
            Long seed = seeds.get(arg);
            if (seed != null) {
                return seed;
            } else {
                throw new IllegalArgumentException("No server with name " + arg);
            }
        }
    }

    // might want this to only return ip
    private static String getServerName() {
        return Optional.ofNullable(Minecraft.getMinecraft().getCurrentServerData())
            .map(server -> !Strings.isNullOrEmpty(server.serverIP) ? server.serverIP : server.serverName)
            .filter(str -> !str.isEmpty())
            .orElse("localhost");
            //.orElseThrow(() -> new IllegalStateException("Failed to get ip or server name"));
    }

    private int parseCoord(String arg, int player) throws NumberFormatException {
        if (arg.startsWith("~")) {
            return arg.length() == 1 ? player : (player + Integer.parseInt(arg.substring(1)));
        } else {
            return Integer.parseInt(arg);
        }
    }

    private BlockPos parsePosition(String x, String y, String z) {
        final Entity player = Minecraft.getMinecraft().player;
        return new BlockPos(parseCoord(x, (int)player.posX), parseCoord(y, (int)player.posY), parseCoord(z, (int)player.posZ));
    }

    private Tuple<BlockPos, BlockPos> parseCoords(List<String> args) throws IllegalArgumentException {
        if (args.size() == 6) {
            return new Tuple<>(
                parsePosition(args.get(0), args.get(1), args.get(2)),
                parsePosition(args.get(3), args.get(4), args.get(5))
            );
        } else if (args.size() == 3) {
            final Entity player = Minecraft.getMinecraft().player;
            return new Tuple<>(
                new BlockPos((int)player.posX, (int)player.posY, (int)player.posZ),
                parsePosition(args.get(0), args.get(1), args.get(2))
            );
        } else {
            throw new IllegalArgumentException("Invalid number of arguments(" + args.size() + "), expected 3 or 6");
        }
    }

    private void registerRenderer(List<BlockPos> path) {
        if (this.renderer != null) {
            disableRenderer();
            this.renderer.deleteBuffer();
        }
        this.renderer = new PathRenderer(path);
        MinecraftForge.EVENT_BUS.register(this.renderer);
    }

    private void disableRenderer() {
        if (this.renderer != null) {
            MinecraftForge.EVENT_BUS.unregister(this.renderer);
        }
    }

    private void resetRenderer() {
        if (this.renderer != null) {
            MinecraftForge.EVENT_BUS.unregister(this.renderer);
            this.renderer.deleteBuffer();
            this.renderer = null;
        }
    }

    private static void sendMessage(String str) {
        Minecraft.getMinecraft().player.sendMessage(new TextComponentString(str));
    }

    private static void addToChatHistory(String msg) {
        Minecraft.getMinecraft().ingameGUI.getChatGUI().addToSentMessages(msg);
    }

    static void checkY(int y) {
        if (y <= 0 || y > 127) {
            throw new IllegalArgumentException("Y level not in valid range");
        }
    }

    private class PathFind implements ICommand {
        private final OptionSpec<String> seedOption;

        PathFind(OptionParser parser) {
            this.seedOption = parser.accepts("seed").withRequiredArg();
            parser.accepts("fine", "high resolution but slower pathfinding");
        }

        @Override
        public void accept(List<String> args, OptionSet options) {
            final Minecraft mc = Minecraft.getMinecraft();

            Tuple<BlockPos, BlockPos> coords = parseCoords(args);
            final long seed;
            if (options.has(seedOption)) {
                seed = getSeedFomOption(options.valueOf(seedOption));
            } else {
                final String ip = getServerName();
                final Long seedObj = seeds.get(ip);
                if (seedObj != null) {
                    seed = seedObj;
                } else {
                    throw new IllegalArgumentException("No seed for server \"" + ip + "\"");
                }
            }

            final BlockPos a = coords.getFirst();
            final BlockPos b = coords.getSecond();
            checkY(a.getY());
            checkY(b.getY());

            if (pathFuture != null) {
                pathFuture.cancel(true);
                pathFuture = null;
                sendMessage("Canceled existing pathfinder");
            }
            resetRenderer();

            pathFuture = executor.submit(() -> {
                final long t1 = System.currentTimeMillis();
                final long[] longs = PathFinder.pathFind(seed, options.has("fine"), a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
                // TODO: native code should check the interrupt flag and throw InterruptedException
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                final long t2 = System.currentTimeMillis();
                final List<BlockPos> path =  Arrays.stream(longs).mapToObj(BlockPos::fromLong).collect(Collectors.toList());

                mc.addScheduledTask(() -> {
                    registerRenderer(path);
                    pathFuture = null;
                    sendMessage(String.format("Found path in %.2f seconds", (t2 - t1) / 1000.0));
                });
            });
        }
    }

    private class Help implements ICommand {
        public Help(OptionParser parser) {}

        @Override
        public void accept(List<String> strings, OptionSet optionSet) {
            // TODO: print descriptions and options
            sendMessage("Commands:");
            commands.forEach((name, fn) -> {
                sendMessage(name);
            });
        }
    }

    private class AddSeed implements ICommand {
        private final OptionSpec<String> ipOption;
        public AddSeed(OptionParser parser) {
            this.ipOption = parser.accepts("ip").withRequiredArg();
        }

        @Override
        public void accept(List<String> args, OptionSet options) {
            if (args.size() != 1) throw new IllegalArgumentException("Expected 1 argument");
            final long seed = Long.parseLong(args.get(0));
            final String ip;
            if (options.has(ipOption)) {
                ip = options.valueOf(ipOption);
            } else {
                ip = getServerName();
            }
            seeds.put(ip, seed);
            sendMessage("Set seed for " + ip);
            PathFinderMod.writeSeedsToDisk(seeds);
        }
    }

    private class Cancel implements ICommand {
        public Cancel(OptionParser parser) {}

        @Override
        public void accept(List<String> args, OptionSet options) {
            if (pathFuture != null) {
                pathFuture.cancel(true);
                pathFuture = null;
                sendMessage("Canceled pathfinder");
            } else {
                sendMessage("No pathfinder runing");
            }
        }
    }

    private class Reset implements ICommand {
        public Reset(OptionParser parser) {}

        @Override
        public void accept(List<String> args, OptionSet options) {
            resetRenderer();
        }
    }

    @SubscribeEvent
    public void onChat(ClientChatEvent event) {
        final String msg = event.getOriginalMessage();

        if (msg.startsWith(";")) { // TODO customizable char
            event.setCanceled(true);
            addToChatHistory(msg); // forge is dumb
            final String cmd = msg.substring(1);
            final String[] args0 = cmd.split(" +");

            if (args0.length > 0) {
                Function<OptionParser, ICommand> command = commands.get(args0[0].toLowerCase());
                if (command != null) {
                    final String[] args = Arrays.copyOfRange(args0, 1, args0.length);
                    final OptionParser parser = new OptionParser();
                    parser.allowsUnrecognizedOptions();
                    try {
                        final ICommand consumer = command.apply(parser);
                        final OptionSet opts = parser.parse(args);
                        consumer.accept((List<String>) opts.nonOptionArguments(), opts);
                    } catch (Exception ex) {
                        // input error
                        sendMessage(ex.toString());
                        // print stacktrace in case it's a bug
                        ex.printStackTrace();
                    }
                } else {
                    sendMessage("Invalid command");
                }
            }
        }
    }
}
