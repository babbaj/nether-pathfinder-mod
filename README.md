# Nether Pathfinder Mod
Forge mod that provides a command line interface and renderer for [Nether Pathfinder](https://github.com/babbaj/nether-pathfinder).

https://www.youtube.com/watch?v=ZvwEgLHpX9o

## Usage
Commands are prefixed with `;` and typing just `;` in game will print the complete help text.

Currently this it looks like this:
```
Commands:
help: Print this message

pathfind: Run the pathfinder (prepend '~' for relative coords)
;pathfind <x> <y> <z> <x> <y> <z>
;pathfind <x> <y> <z>
--seed <seed>
--noraytrace  do not simplify the result of the pathfinder
--fine  high resolution but slower pathfinding

addseed: Set the seed for the current server
;addseed <seed>
--ip <String>

cancel: Stop the current pathfinding thread (will still run in the background)

reset: Stop rendering the path
```
By default it will use the seed for 2b2t.

## Building
For windows, tools for building the native code are automatically downloaded by the build script. There is a known weird problem with zig cc's cache causing the build to fail, the recommended solution for that is to uninstall windows.

For linux you need cmake and at least clang 13.

## Performance
Because of a limitation in the standard library shipped with zig cc, there is no multithreading done in the windows builds.

In Linux on a Ryzen 5900X, it pathfinds at about 25,000 blocks/second
