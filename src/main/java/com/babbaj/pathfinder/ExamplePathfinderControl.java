package com.babbaj.pathfinder;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import dev.babbaj.pathfinder.NetherPathfinder;
import dev.babbaj.pathfinder.PathSegment;
import joptsimple.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ExamplePathfinderControl {

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, Long> seeds;
    private final List<PathRenderer> renderList = new ArrayList<>();
    private PathFinder pathFinder;

    private final Map<String, Function<OptionParser, ICommand>> commands = ImmutableMap.<String, Function<OptionParser, ICommand>>builder()
        .put("help", Help::new)
        .put("pathfind", PathFind::new)
        .put("thisway", Thisway::new)
        .put("addseed", AddSeed::new)
        .put("cancel", Cancel::new)
        .put("reset", Reset::new)
        .build();

    public ExamplePathfinderControl(Map<String, Long> seeds) {
        this.seeds = seeds;
        this.seeds.putIfAbsent("connect.2b2t.org", 146008555100680L);
        this.seeds.putIfAbsent("2b2t.org", 146008555100680L);
    }

    // TODO: add description function
    interface ICommand extends BiConsumer<List<String>, OptionSet> {
        @Override
        void accept(List<String> args, OptionSet options);

        String description();
        List<String> usage();
        default List<String> optionHelp() { return Collections.emptyList(); };
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

    private void cancelExistingPathfinder() {
        if (pathFinder != null) {
            pathFinder.cancelled.set(true);
            pathFinder = null;
        }
    }

    private void resetRenderer() {
        for (PathRenderer segment : this.renderList) {
            segment.deleteBuffer();
        }
        this.renderList.clear();
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

    private static void acceptPathfindFlags(OptionParser parser) {
        parser.accepts("seed").withRequiredArg();
        parser.accepts("noraytrace");
    }

    private static List<String> pathfindHelp() {
        return Arrays.asList(
                "--seed <seed>",
                "--noraytrace  do not simplify the result of the pathfinder"
        );
    }

    private long getSeed(OptionSet options) {
        final long seed;
        if (options.has("seed")) {
           return getSeedFomOption((String) options.valueOf("seed"));
        } else {
            final String ip = getServerName();
            final Long seedObj = seeds.get(ip);
            if (seedObj != null) {
                return seedObj;
            } else {
                sendMessage("No seed for server \"" + ip + "\", defaulting to 2b2t");
                return 146008555100680L;
            }
        }
    }

    private void startPathFinder(final OptionSet options, final BlockPos startIn, final BlockPos end) {
        checkY(startIn.getY());
        checkY(end.getY());
        final long seed = getSeed(options);

        if (this.pathFinder != null) {
            if (!this.pathFinder.future.isDone()) {
                this.pathFinder.cancelled.set(true);
                sendMessage("Canceled existing path finder");
            }
            pathFinder = null;
        }
        resetRenderer();

        final long ctx = NetherPathfinder.newContext(seed);
        ConcurrentLinkedQueue<List<BlockPos>> queue = new ConcurrentLinkedQueue<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            final long t1 = System.currentTimeMillis();
            BlockPos start = startIn;
            PathSegment segment;
            do {
                segment = NetherPathfinder.pathFind(ctx, start.getX(), start.getY(), start.getZ(), end.getX(), end.getY(), end.getZ(), true, 10000);
                if (cancelled.get()) {
                    return false;
                }
                if (segment == null) {
                    Minecraft.getMinecraft().addScheduledTask(() -> sendMessage("path finder returned null"));
                    return false;
                }
                long[] refined = !options.has("noraytrace") ? NetherPathfinder.refinePath(ctx, segment.packed) : segment.packed;
                final List<BlockPos> path = Arrays.stream(refined).mapToObj(BlockPos::fromLong).collect(Collectors.toList());
                queue.add(path);
                start = BlockPos.fromLong(segment.packed[segment.packed.length - 1]);
            } while (!segment.finished);
            final long t2 = System.currentTimeMillis();
            Minecraft.getMinecraft().addScheduledTask(() -> sendMessage(String.format("Found path in %.2f seconds", (t2 - t1) / 1000.0)));
            return true;
        }).exceptionally(ex -> {
            ex.printStackTrace();
            return false;
        })
        .whenCompleteAsync((res, ex) -> NetherPathfinder.freeContext(ctx));

        this.pathFinder = new PathFinder(queue, future, cancelled);
    }

    private class PathFind implements ICommand {
        PathFind(OptionParser parser) {
            acceptPathfindFlags(parser);
        }

        @Override
        public String description() {
            return "Run the pathfinder (prepend '~' for relative coords)";
        }

        @Override
        public List<String> usage() {
            return Arrays.asList(
                    "<x> <y> <z> <x> <y> <z>",
                    "<x> <y> <z>"
            );
        }

        @Override
        public List<String> optionHelp() {
            return pathfindHelp();
        }

        @Override
        public void accept(List<String> args, OptionSet options) {
            Tuple<BlockPos, BlockPos> coords = parseCoords(args);

            final BlockPos a = coords.getFirst();
            final BlockPos b = coords.getSecond();
            startPathFinder(options, a, b);
        }
    }

    private class Thisway implements ICommand {
        public Thisway(OptionParser parser) {
            acceptPathfindFlags(parser);
        }
        @Override
        public void accept(List<String> args, OptionSet options) {
            if (args.size() != 1) {
                throw new IllegalArgumentException("Expected 1 argument");
            }
            int distance = Integer.parseInt(args.get(0));
            Entity player = Minecraft.getMinecraft().player;

            float theta = (float) Math.toRadians(player.rotationYaw);
            double x = player.posX - MathHelper.sin(theta) * distance;
            double z = player.posZ + MathHelper.cos(theta) * distance;
            startPathFinder(options, player.getPosition(), new BlockPos(x, 64, z));
        }

        @Override
        public String description() {
            return "Pathfind n blocks in the current direction";
        }

        @Override
        public List<String> usage() {
            return Collections.singletonList("<distance>");
        }
        @Override
        public List<String> optionHelp() {
            return pathfindHelp();
        }
    }

    private class Help implements ICommand {
        public Help(OptionParser parser) {}

        @Override
        public String description() {
            return "Print this message";
        }

        @Override
        public List<String> usage() {
            return Collections.emptyList();
        }

        @Override
        public void accept(List<String> strings, OptionSet optionSet) {
            printHelp();
        }
    }

    private class AddSeed implements ICommand {
        private final OptionSpec<String> ipOption;
        public AddSeed(OptionParser parser) {
            this.ipOption = parser.accepts("ip").withRequiredArg();
        }

        @Override
        public String description() {
            return "Set the seed for the current server";
        }

        @Override
        public List<String> usage() {
            return Collections.singletonList("<seed>");
        }

        @Override
        public List<String> optionHelp() {
            return Arrays.asList(
                "--ip <String>"
            );
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
        public String description() {
            return "Stop the current pathfinding thread (will still run in the background)";
        }

        @Override
        public List<String> usage() {
            return Collections.emptyList();
        }

        @Override
        public void accept(List<String> args, OptionSet options) {
            if (pathFinder != null) {
                pathFinder.cancelled.set(true);
                pathFinder = null;
                sendMessage("Canceled pathfinder");
            } else {
                sendMessage("No pathfinder runing");
            }
        }
    }

    private class Reset implements ICommand {
        public Reset(OptionParser parser) {}

        @Override
        public String description() {
            return "Stop rendering the path";
        }

        @Override
        public List<String> usage() {
            return Collections.emptyList();
        }

        @Override
        public void accept(List<String> args, OptionSet options) {
            cancelExistingPathfinder();
            resetRenderer();
        }
    }

    void printHelp() {
        sendMessage("Commands:");
        commands.forEach((cmd, fn) -> {
            final OptionParser parser = new OptionParser();
            final ICommand icmd = fn.apply(parser);
            sendMessage(cmd + ": " + icmd.description());
            for (String usage : icmd.usage()) {
                sendMessage(";" + cmd + " " + usage);
            }
            for (String line : icmd.optionHelp()) {
                sendMessage(line);
            }
            sendMessage("");
        });
    }

    @SubscribeEvent
    public void onChat(ClientChatEvent event) {
        final String msg = event.getOriginalMessage();

        if (msg.startsWith(";")) { // TODO customizable char
            event.setCanceled(true);
            addToChatHistory(msg); // forge is dumb
            if (msg.length() > 1) {
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
            } else {
                printHelp();
            }
        }
    }

    private static boolean isInNether() {
        return Minecraft.getMinecraft().player.dimension == -1;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (this.pathFinder != null) {
            boolean success = this.pathFinder.future.getNow(true);
            if (!success) {
                this.pathFinder = null;
                this.renderList.clear();
            } else {
                List<BlockPos> segment = this.pathFinder.resultQueue.poll();
                if (segment != null) {
                    this.renderList.add(new PathRenderer(segment));
                }
            }
        }
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent event) {
        if (!isInNether()) return;

        if (!this.renderList.isEmpty()) {
            PathRenderer.preRender();
            GlStateManager.glLineWidth(1.f);
            for (PathRenderer segment : this.renderList) {
                segment.drawLine(event.getPartialTicks());
            }
            PathRenderer.postRender();
        }
    }
}
