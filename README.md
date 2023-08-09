**This repo has been deprecated in favor of baritone and will not be updated past forge 1.12**

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

thisway: Pathfind n blocks in the current direction
;thisway <distance>
--seed <seed>
--noraytrace  do not simplify the result of the pathfinder

addseed: Set the seed for the current server
;addseed <seed>
--ip <String>

cancel: Stop the current pathfinding thread

reset: Stop rendering the path
```
By default it will use the seed for 2b2t.
