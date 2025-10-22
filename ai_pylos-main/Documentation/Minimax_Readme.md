# Student – Minimax (Part 1)

## Overview
This player implements a **Minimax search algorithm** for the Pylos board game using the provided `PylosGameSimulator`.  
It explores future moves up to a fixed depth, evaluates the resulting board states with a lightweight heuristic, and chooses the move that maximizes its advantage assuming the opponent plays optimally.

---

## How It Works

### 1. Move Generation
At every turn the player generates all **legal actions**:
- Placing a **reserve sphere** on any legal location.
- **Moving upward** an already placed sphere.

This logic is handled in the method:
```java
generateMoves(PylosBoard board, PylosPlayer player);

```
2. Simulation and Undo
Each possible move is simulated using:

java
Copiar código
sim.moveSphere(...)
After exploring that move recursively, the algorithm undoes the change using the corresponding undo call to restore the simulator’s state:

undoAddSphere(...) if a reserve sphere was placed.

undoMoveSphere(...) if an existing sphere was moved.

undoRemoveFirstSphere(...) or undoRemoveSecondSphere(...) for removal phases.

pass() and undoPass(...) for optional second removals.

This ensures the simulator always returns to the exact previous state before exploring the next move.

3. Recursive Search (Minimax)
The recursive search is implemented with the negamax form of the minimax algorithm, which treats both players symmetrically.

At each level:

The algorithm explores all legal moves for the current player.

For each move, it calls itself recursively with reduced depth.

It inverts the returned score (-value) because what is good for one player is bad for the other.

The recursion stops when either:

The maximum depth is reached, or

The game state is COMPLETED (no more legal moves).

4. Evaluation Function
At leaf nodes, the algorithm evaluates the board with a simple heuristic that rewards:

Having more reserve spheres (flexibility to play).

Having spheres placed on higher layers (progress toward the top).

java
Copiar código
score = 10 * (myReserves - oppReserves)
       + (sumZ(mySpheres) - sumZ(oppSpheres));
This balances short-term mobility and long-term control of the board.

5. Removal Policy
When a removal is required:

The player removes the sphere that maximizes mobility after removal, measured as the number of legal upward moves that remain available.

During the optional second removal phase, the player passes unless removing a sphere is at least neutral (non-negative effect).

This keeps the structure stable and avoids unnecessary weakening of the player’s position.

6. Parameters
Search depth (MAX_DEPTH): default is 3.
Can be increased to 4 if computation time allows.

Simulator: PylosGameSimulator manages move execution and undo operations safely.

Undo logic: each simulated action is paired with its correct undo call to keep the internal state consistent.

7. Results
At depth 3:

Against Student – Random Fit, this player consistently wins the majority of games.

Against Codes – Best Fit, it already wins several games (about 3 out of 10 in testing), proving the heuristic and search are effective.

8. How to Run
Run via GUI
bash
Copiar código
cd ai_pylos-main
mvn -q -DskipTests package
java -jar ./pylos-gui/target/pylos-gui-1.0-SNAPSHOT.jar
Then select:

Player 1: Student – Minimax

Player 2: Codes – Best Fit or Codes – Minimax
Press Start to play.

Run via Command Line (Batch Mode)
If the project includes be.kuleuven.pylos.main.PylosMain, you can execute:

bash
Copiar código
mvn -q -DskipTests -pl pylos-student -am exec:java -D"exec.mainClass=be.kuleuven.pylos.main.PylosMain"
This runs the internal tournament or batch simulation (often 1000 games).

9. Known Limitations and Future Work
Currently no alpha-beta pruning or move ordering beyond basic heuristics.

The evaluation function can be expanded with:

square-completion detection (threats of 3-of-4),

center-control bonuses on low layers,

removal-value estimation.

The optional removal decision could also be integrated directly into the minimax recursion rather than handled greedily.

10. Summary
This implementation completes the requirements for Part 1 – Minimax of the Pylos AI project.
It correctly:

Simulates all legal moves and removals,

Maintains consistent state through precise undo operations,

Evaluates game positions reasonably,

Demonstrates working decision-making logic that can defeat simpler opponents.

Further optimization (alpha-beta, better heuristics, transposition tables) can be added later for Part 2.