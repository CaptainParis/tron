# Tron Plugin

A Minecraft plugin that recreates the classic Tron Light Cycle game. Players ride pigs and leave glass trails behind them while trying not to crash into anything.

## What it does

You ride a pig that moves forward automatically. You steer with WASD and try not to hit the glass trails that spawn behind you and other players. Last player alive wins. Pretty simple concept but the implementation gets interesting.

## How it works

The core idea is pretty straightforward - disable pig AI, make them move forward constantly, and spawn glass blocks behind them. But there's a bunch of technical stuff that makes it actually work well:

### Pig Movement
The pigs have their AI completely disabled and use teleport based movement. They move forward constantly and players steer them with mouse movements. There's a boost system that multiplies speed temporarily.

### Trail System
Uses real glass blocks (not packets) for 100% reliable collision detection. Trails are 2 blocks tall and use different colored glass for each player. The system includes:
- Bresenham line algorithm to prevent gaps in trails
- Smart diagonal corner placement for glass panes
- Automatic glass pane connections for proper visual lines

### Collision Detection
Checks for collisions every tick using spatial partitioning for performance. Has a 1-second grace period at game start so players don't immediately crash into their own trail.

### Arena Management
Creates a void world with a black concrete floor and barrier walls. World border slowly shrinks to force players together.

- **GameManager**: Handles game state, countdown, player elimination
- **PlayerManager**: Player data, statistics, queue management
- **TrailManager**: Trail generation, glass pane connections, collision detection
- **ArenaManager**: World creation, arena setup, world border
- **OptimizationManager**: Performance monitoring and optimization systems

### Key Classes

**TrailManager** is probably the most interesting - it handles:
- Bresenham line algorithm for gap-free trail generation
- Glass pane connection logic for proper visual lines
- Spatial partitioning for fast collision detection
- Corner block placement for diagonal movements

**PigRideListener** handles the pig movement:
- Captures player input (yaw)
- Applies speed to pigs based on look direction
- Prevents dismounting during games
- Real-time collision checking

**GameManager** coordinates everything:
- Queue system and game state management
- Countdown and game start/end logic
- Player elimination and winner detection
- World border management

## Interesting Technical Bits

### Glass Pane Trails
Glass panes don't naturally connect on diagonals, so there's logic to detect diagonal movement and place corner blocks to bridge gaps. The system is conservative to avoid creating thick trails - it only places one corner block per diagonal movement and only when it would connect exactly 2 adjacent panes.

### Collision Detection
Uses chunk-based spatial partitioning instead of checking every trail block. Trail blocks are indexed by chunk coordinates for O(1) lookups instead of O(n) iteration.

### Pig Movement
Pigs have their AI completely disabled and use velocity-based movement. The system captures player look direction and applies velocity in that direction every tick. Boost multiplies the velocity temporarily.

### Performance Optimizations
- Object pooling for Location objects
- Lock-free data structures for concurrent access
- Packet batching for network optimization
- Spatial indexing for collision detection
- Real blocks instead of packet spoofing for reliability

## Commands

Basic stuff:
- `/tron join` - Join the queue
- `/tron leave` - Leave the queue
- `/start` - Force start a game (admin)
- `/endgame` - Force end current game (admin)
- `/opt status` - Check performance stats (admin)

## Building

```bash
mvn clean package
```

JAR ends up in `target/`. Needs Java 21+ and Paper/Spigot 1.21.4+.

## Project Structure

```
src/main/java/Paris/
├── Tron.java                    # Main plugin class
├── managers/
│   ├── GameManager.java         # Game state and flow
│   ├── PlayerManager.java       # Player data and queue
│   ├── TrailManager.java        # Trail generation and collision
│   └── ArenaManager.java        # World and arena management
├── listeners/
│   ├── GameListeners.java       # Game events
│   └── PigRideListener.java     # Pig movement and collision
└── optimization/                # Performance stuff
    ├── lockfree/               # Lock-free data structures
    ├── pooling/                # Object pooling
    └── integration/            # Optimized implementations
```

## Dependencies

- Paper API 1.21.4+
- ProtocolLib (for packet stuff)
- SQLite JDBC (for player stats)

## License

MIT License - do whatever you want with it.
