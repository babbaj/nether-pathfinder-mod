package com.babbaj.pathfinder;

import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class PathFinder {
    public final ConcurrentLinkedQueue<List<BlockPos>> resultQueue;
    public final CompletableFuture<Boolean> future;
    public final AtomicBoolean cancelled;

    public PathFinder(ConcurrentLinkedQueue<List<BlockPos>> resultQueue, CompletableFuture<Boolean> future, AtomicBoolean cancelled) {
        this.resultQueue = resultQueue;
        this.future = future;
        this.cancelled = cancelled;
    }
}
