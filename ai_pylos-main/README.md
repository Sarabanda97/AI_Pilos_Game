# StudentPlayer — Pylos AI (Submission Notes)

**Authors:** <Gonçalo Sarabanda + Catarina LimaS>  
**Course:** KU Leuven — Artificial Intelligence (Pylos assignment)  
**Submitted file:** `be/kuleuven/pylos/player/student/StudentPlayer.java` (single file; helpers are **nested**; explicit **no‑arg** constructor)

---

## 1) What we submit
- **One Java file only**: `StudentPlayer.java` in package `be.kuleuven.pylos.player.student`.
- **No‑argument constructor**; all tunables are **hardcoded** in the class.
- Any `Collections.shuffle(...)` uses `getRandom()` for deterministic behavior.
- Tested with JVM assertions enabled (`-ea`).

---

## 2) Approach (high level)
We implement a **fast alpha–beta** search with a **transposition table** (exact/upper/lower bounds) and **PV move ordering**. Move ordering features a strong bias for **immediate square creation** (form‑and‑remove).

The **evaluation** concentrates on Pylos‑specific factors:
- **Completed squares** and **3‑of‑4 threats** (structure first),
- **Elevation** (unlocking build potential),
- **Reserves** balance,
- **Light mobility** (tie‑breaker).

To reduce draws, we include a **small “contempt” bias** and a slightly more proactive **remove‑over‑pass** decision in tight spots.

---

## 3) Measured results (on our machine)
Environment: Windows 10, OpenJDK 23.0.1, IntelliJ 2024.3; `-ea` enabled.

- **vs Minimax 2 (100 games):** ~58% Student / 29% Minimax2 / 13% Draw
- **vs Minimax 4 (100 games):** ~42% Student / 56% Minimax4 / 2% Draw
- **Speed:** ~0.02 s per game (≫ 1 game/second guideline)

These reflect a very fast, competitive player that prioritizes Pylos structure.

---

## 4) Design choices & tiny anti‑draw tuning
- **Search:** alpha–beta + TT (exact/upper/lower), PV ordering at root.
- **Ordering:** strong bump for **immediate square formation**; lifts to higher Z get a bonus; mild center preference.
- **Evaluation weights (hardcoded):** reserves **16.0**, squares **14.0**, threats **7.0**, elevation **1.2**, mobility **0.15**.
- **Draw discouragement:** small **CONTEMPT = 0.25** in `signedEval(...)` and a slightly looser **remove‑over‑pass** threshold.
- All of this preserves speed and fits the **single‑file** constraint.

---

## 5) How to run
Use the provided battle UI:

```bash
java -ea -classpath "<.../pylos-student/target/classes>;<.../pylos-core/target/classes>;...<deps>" be.kuleuven.pylos.main.PylosMain
```
Select **Student** vs **Minimax2/Minimax4** and run 100 games.

