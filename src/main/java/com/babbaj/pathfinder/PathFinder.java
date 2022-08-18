package com.babbaj.pathfinder;


public class PathFinder {
    public static native long[] pathFind(long seed, boolean fine, boolean raytrace, int x1, int y1, int z1, int x2, int y2, int z2);
}
