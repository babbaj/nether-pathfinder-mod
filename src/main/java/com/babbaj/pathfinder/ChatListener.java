package com.babbaj.pathfinder;

import com.google.common.base.Strings;
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
import java.util.stream.Collectors;

public class ChatListener {

    private final Map<String, Long> seeds;
    private PathRenderer renderer;

    public ChatListener(Map<String, Long> seeds) {
        this.seeds = seeds;
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

    private static Optional<String> getServerName() {
        return Optional.ofNullable(Minecraft.getMinecraft().getCurrentServerData())
            .map(server -> !Strings.isNullOrEmpty(server.serverIP) ? server.serverIP : server.serverName);
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

    private static List<String> nonOptionArgs(String[] args) {
        List<String> out = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("-")) break;
            out.add(arg);
        }
        return out;
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
        if (this.renderer != null) MinecraftForge.EVENT_BUS.unregister(this.renderer);

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
            this.renderer = null;
        }
    }

    @SubscribeEvent
    public void onChat(ClientChatEvent event) {
        final String msg = event.getOriginalMessage();

        if (msg.startsWith(";")) { // TODO custom char
            final String cmd = msg.substring(1);

            final String[] args0 = cmd.split(" +");
            // TODO: more commands
            if (args0.length > 0 && args0[0].equalsIgnoreCase("pathfind")) {
                event.setCanceled(true);

                final String[] rawArgs = Arrays.copyOfRange(args0, 1, args0.length);
                final OptionParser parser = new OptionParser();
                final OptionSpec<String> seedOption = parser.accepts("seed").withRequiredArg();
                final OptionSet options = parser.parse(rawArgs);

                final List<String> args = nonOptionArgs(rawArgs);
                try {
                    Tuple<BlockPos, BlockPos> coords = parseCoords(args);
                    final long seed;
                    if (options.has(seedOption)) {
                        seed = getSeedFomOption(options.valueOf(seedOption));
                    } else {
                        final String ip = getServerName().orElseThrow(() -> new IllegalStateException("failed to get ip"));
                        final Long seedObj = seeds.get(ip);
                        if (seedObj != null) {
                            seed = seedObj;
                        } else {
                            throw new IllegalArgumentException("No seed for server \"" + ip + "\"");
                        }
                    }

                    // TODO: do this async
                    final BlockPos a = coords.getFirst();
                    final BlockPos b = coords.getSecond();
                    final long[] longs = PathFinder.pathFind(seed, a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
                    final List<BlockPos> path = Arrays.stream(longs).filter(l -> l != 0).mapToObj(BlockPos::fromLong).collect(Collectors.toList());
                    this.registerRenderer(path);
                } catch (Exception ex) {
                    Minecraft.getMinecraft().player.sendMessage(new TextComponentString(ex.toString()));
                    ex.printStackTrace();
                    // input error
                }
            }
        }
    }
}