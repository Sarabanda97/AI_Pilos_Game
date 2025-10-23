#  Student – Minimax 

##  Overview
This project implements a **Minimax-based AI player** for the board game **Pylos**, built on top of the provided `PylosGameSimulator`.  
The algorithm explores possible move sequences up to a fixed depth, evaluates each board state with a strategic heuristic, and selects the move that **maximizes the AI’s advantage** assuming the opponent plays optimally.

>  **Goal (Part 1)**  
> Implement a fully working **Minimax search** with:
> - Correct simulation and undo mechanics
> - A consistent and meaningful evaluation function

---

## Core Idea
**Minimax** is a recursive search algorithm used in two-player games.  
It assumes:
- The **maximizing player** (our AI) tries to achieve the **highest score**.
- The **minimizing player** (the opponent) responds with their **best counter-move**.

This process forms a **game tree** of alternating turns.  
By exploring this tree, the AI chooses the move that guarantees the best achievable outcome against an optimal opponent.

---

## Algorithm Details

### 1 Move Generation
On every turn, the player lists all **legal actions**:
- **Placing a reserve sphere** on a valid location.
- **Moving an existing sphere upward**, if legally supported.

```java
generateMoves(PylosBoard board, PylosPlayer player)
This method collects all valid moves for a given player and returns them as (sphere, location) pairs.
```

### 2 Simulation and Undo
Each move is tested virtually using the PylosGameSimulator.
This simulator supports both applying and undoing moves, allowing the algorithm to explore hypothetical futures safely.

Example Simulation Cycle
```java
Copiar código
sim.moveSphere(sphere, location);
double value = -negamax(sim, depth - 1, -beta, -alpha);
sim.undoMoveSphere(sphere, previousLocation, prevState, prevColor);
```
Undo Operations

```java
undoAddSphere(...)         // For reserve placements
undoMoveSphere(...)        // For upward moves
undoRemoveFirstSphere(...) // For first removal
undoRemoveSecondSphere(...)// For optional second removal
undoPass(...)              // For optional passes
```

Each recursive branch leaves the simulator in a consistent state, ensuring no side effects between moves.

### 3 Recursive Search (Negamax Form)
The algorithm uses the Negamax variant of Minimax, simplifying code by merging both player perspectives.

#### Process
1. For each possible move:

   - Simulate the move.
   - Recursively evaluate the resulting state. 
   - Negate the returned value (since what benefits one player harms the other).

2. Keep the move with the maximum score.

#### Stopping Conditions
- Maximum depth reached (MAX_DEPTH = 3)
- The simulator reports COMPLETED game state
- This recursion allows the AI to look ahead several turns and anticipate the opponent’s best responses.

### 4 Evaluation Function
When the depth limit is reached, the algorithm uses a heuristic evaluation to estimate the advantage of each player.

#### Heuristic Considerations
- Reserves → More spheres left = more flexibility
- Height → Higher spheres = stronger positional control

```java
score = 10 * (myReserves - oppReserves)
      + (sumZ(mySpheres) - sumZ(oppSpheres));
```

#### Interpretation
| Component | Description                        | Weight |
| --------- | ---------------------------------- | ------ |
| Reserves  | Prioritizes keeping spare spheres  | ×10    |
| Height    | Encourages control of upper layers | ×1     |


A higher score favors the AI.
This heuristic is intentionally simple but effective for early gameplay phases.

### 5 Removal Logic
After completing a square, the player may remove one or two of their spheres.
- First removal: Choose the sphere that preserves future mobility.

- Second removal (optional): Perform only if beneficial; otherwise pass.

This removal logic operates outside the recursive search for efficiency, but follows the same heuristic principles.

### 6 Parameters

| Parameter    | Meaning                                         | Default              |
| ------------ | ----------------------------------------------- | -------------------- |
| `MAX_DEPTH`  | Maximum recursion depth                         | `3`                  |
| `Simulator`  | Runs virtual moves and supports undo operations | `PylosGameSimulator` |
| `Evaluation` | Simple heuristic based on reserves and height   | *(see above)*        |

You may increase MAX_DEPTH to 4 if computation time allows — depth 3 already provides strong results.

Performance Summary

| Opponent                 | Average Result                | Notes                                               |
| ------------------------ | ----------------------------- | --------------------------------------------------- |
| **Student – Random Fit** | AI wins the majority of games | Confirms correct Minimax logic                      |
| **Codes – Best Fit**     | ~30% win rate (3/10 tests)    | Demonstrates strategic play and a working heuristic |

The observed results confirm correct simulation behavior, recursive evaluation, and turn-based decision-making.

How to Run

GUI Mode

```bash
cd ai_pylos-main
mvn -q -DskipTests package
java -jar ./pylos-gui/target/pylos-gui-1.0-SNAPSHOT.jar
```

In the GUI:

1. Select Student – Minimax as Player 1.
2. Select Codes – Best Fit (or Codes – Minimax) as Player 2.
3. Press Start to watch them play.

Command-Line Mode (Batch / Tournament)
If the repository includes be.kuleuven.pylos.main.PylosMain, you can run a full round-robin tournament:

```bash
mvn -q -DskipTests -pl pylos-student -am exec:java \
    -D"exec.mainClass=be.kuleuven.pylos.main.PylosMain"
```

This will execute all registered players against each other for a large number of rounds (typically 1000).

Limitations and Future Improvements:

-  No alpha-beta pruning
→ Currently evaluates all branches; adding pruning will allow deeper searches.

- Basic heuristic — can be improved with:
  - Square-completion threat detection (3-of-4 pattern)
  - Center control prioritization on lower layers 
  - Smarter removal evaluation integrated into recursion

Future versions will likely incorporate alpha-beta pruning, advanced heuristics, and move ordering to achieve higher efficiency and competitiveness.
