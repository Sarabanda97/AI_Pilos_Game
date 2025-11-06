package be.kuleuven.pylos.player.student;

import be.kuleuven.pylos.game.*;
import be.kuleuven.pylos.player.PylosPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * StudentPlayer – stable negamax over PylosGameSimulator
 * Safe state/color handling + correct undo pairs.
 */
public class StudentPlayer extends PylosPlayer {

    private static final int MAX_DEPTH = 2; // start safe; increase after it’s stable

    /* ========= Entry points ========= */

    @Override
    public void doMove(PylosGameIF game, PylosBoard board) {
        Move best = searchRoot(board, MAX_DEPTH);
        if (best != null) {
            game.moveSphere(best.sphere, best.to);
            return;
        }
        // ultra-safe fallback: try to place reserve somewhere legal; else pass
        PylosSphere r = board.getReserve(this);
        if (r != null) {
            for (PylosLocation l : board.getLocations()) {
                if (r.canMoveTo(l)) { game.moveSphere(r, l); return; }
            }
        }
        game.pass();
    }

    @Override
    public void doRemove(PylosGameIF game, PylosBoard board) {
        // greedy: remove the piece that maximizes our mobility
        PylosSphere pick = null;
        double best = Double.NEGATIVE_INFINITY;
        for (PylosSphere s : board.getSpheres(this)) {
            if (!s.canRemove()) continue;
            double v = mobility(board, this, s);
            if (v > best) { best = v; pick = s; }
        }
        if (pick != null) game.removeSphere(pick);
        else game.pass();
    }

    @Override
    public void doRemoveOrPass(PylosGameIF game, PylosBoard board) {
        // choose remove only if it helps; else pass
        PylosSphere pick = null;
        double base = mobility(board, this, null);
        double bestGain = 0.0;
        for (PylosSphere s : board.getSpheres(this)) {
            if (!s.canRemove()) continue;
            double g = mobility(board, this, s) - base;
            if (g > bestGain) { bestGain = g; pick = s; }
        }
        if (pick != null) game.removeSphere(pick);
        else game.pass();
    }

    /* ========= Search ========= */

    private static final class Move {
        final PylosSphere sphere;
        final PylosLocation to;
        Move(PylosSphere s, PylosLocation t) { this.sphere = s; this.to = t; }
    }

    private Move searchRoot(PylosBoard board, int depth) {
        PylosGameSimulator sim = new PylosGameSimulator(PylosGameState.MOVE, PLAYER_COLOR, board);

        double alpha = -1e18, beta = 1e18;
        double bestVal = -1e18;
        Move best = null;

        // Only our moves at root
        for (Move m : generateMovesFor(board, this)) {
            PylosGameState ps = sim.getState();
            PylosPlayerColor pc = sim.getColor();
            PylosLocation prev = m.sphere.isReserve() ? null : m.sphere.getLocation();

            // Simulate
            sim.moveSphere(m.sphere, m.to);

            // Recurse
            double val = -negamax(sim, depth - 1, -beta, -alpha, board);

            // Undo
            if (prev == null) sim.undoAddSphere(m.sphere, ps, pc);
            else              sim.undoMoveSphere(m.sphere, prev, ps, pc);

            if (val > bestVal) { bestVal = val; best = m; }
            if (val > alpha) alpha = val;
            if (alpha >= beta) break; // prune
        }
        return best;
    }

    private double negamax(PylosGameSimulator sim, int depth, double alpha, double beta, PylosBoard board) {
        // Terminal?
        if (depth <= 0 || sim.getState() == PylosGameState.COMPLETED) {
            // Value from perspective of the side to move; flip to "our" perspective via color
            return signedEval(board, sim.getColor());
        }

        PylosGameState state = sim.getState();
        PylosPlayerColor sideToMove = sim.getColor();

        if (state == PylosGameState.MOVE) {
            // Generate moves for the player who actually moves now
            boolean usToMove = (sideToMove == this.PLAYER_COLOR);
            PylosPlayer who = usToMove ? this : this.OTHER;

            double best = -1e18;
            for (Move m : generateMovesFor(board, who)) {
                // Safety: only push spheres belonging to the player to move
                // (since we built from board.getSpheres(who), this is redundant but harmless)
                PylosGameState ps = sim.getState();
                PylosPlayerColor pc = sim.getColor();
                PylosLocation prev = m.sphere.isReserve() ? null : m.sphere.getLocation();

                sim.moveSphere(m.sphere, m.to);
                double val = -negamax(sim, depth - 1, -beta, -alpha, board);
                if (prev == null) sim.undoAddSphere(m.sphere, ps, pc);
                else              sim.undoMoveSphere(m.sphere, prev, ps, pc);

                if (val > best) best = val;
                if (val > alpha) alpha = val;
                if (alpha >= beta) break;
            }
            return best;
        }

        if (state == PylosGameState.REMOVE_FIRST || state == PylosGameState.REMOVE_SECOND) {
            boolean usToMove = (sideToMove == this.PLAYER_COLOR);
            PylosPlayer who = usToMove ? this : this.OTHER;

            List<PylosSphere> choices = new ArrayList<>();
            for (PylosSphere s : board.getSpheres(who)) {
                if (s.canRemove()) choices.add(s);
            }

            double best = -1e18;

            // Try removals
            for (PylosSphere s : choices) {
                PylosGameState ps = sim.getState();   // capture BEFORE
                PylosPlayerColor pc = sim.getColor(); // capture BEFORE
                PylosLocation from = s.getLocation();

                sim.removeSphere(s);
                double val = -negamax(sim, depth - 1, -beta, -alpha, board);

                // correct undo depending on prior state
                if (ps == PylosGameState.REMOVE_FIRST) sim.undoRemoveFirstSphere(s, from, ps, pc);
                else                                   sim.undoRemoveSecondSphere(s, from, ps, pc);

                if (val > best) best = val;
                if (val > alpha) alpha = val;
                if (alpha >= beta) break;
            }

            // In REMOVE_SECOND you may also PASS
            if (state == PylosGameState.REMOVE_SECOND) {
                PylosGameState ps = sim.getState();
                PylosPlayerColor pc = sim.getColor();
                sim.pass();
                double val = -negamax(sim, depth - 1, -beta, -alpha, board);
                sim.undoPass(ps, pc);
                if (val > best) best = val;
            }

            return best;
        }

        // Any other state (shouldn’t really happen): evaluate
        return signedEval(board, sideToMove);
    }

    /* ========= Move generation + eval ========= */

    private List<Move> generateMovesFor(PylosBoard board, PylosPlayer who) {
        ArrayList<Move> out = new ArrayList<>();

        // 1) add from reserve (if any)
        PylosSphere r = board.getReserve(who);
        if (r != null) {
            for (PylosLocation l : board.getLocations()) {
                // Teacher’s engine uses moveSphere for adding reserve too; legality via canMoveTo
                if (r.canMoveTo(l)) out.add(new Move(r, l));
            }
        }

        // 2) move existing spheres
        for (PylosSphere s : board.getSpheres(who)) {
            if (s.isReserve()) continue; // just in case
            for (PylosLocation l : board.getLocations()) {
                if (s.canMoveTo(l)) out.add(new Move(s, l));
            }
        }
        return out;
    }

    private double signedEval(PylosBoard board, PylosPlayerColor sideToMove) {
        double e = eval(board);
        return (sideToMove == this.PLAYER_COLOR) ? e : -e;
    }

    private double eval(PylosBoard board) {
        // Basic but solid: reserves, height, mobility
        int myRes = 0, opRes = 0, myZ = 0, opZ = 0;
        int myMob = 0, opMob = 0;

        for (PylosSphere s : board.getSpheres(this)) {
            if (s.isReserve()) myRes++;
            else myZ += s.getLocation().Z;
            // approximate mobility (destinations count)
            if (!s.isReserve()) {
                for (PylosLocation l : board.getLocations()) if (s.canMoveTo(l)) myMob++;
            }
        }
        for (PylosSphere s : board.getSpheres(this.OTHER)) {
            if (s.isReserve()) opRes++;
            else opZ += s.getLocation().Z;
            if (!s.isReserve()) {
                for (PylosLocation l : board.getLocations()) if (s.canMoveTo(l)) opMob++;
            }
        }

        // weights are modest; tune later
        return  12.0 * (myRes - opRes)
                +  1.0 * (myZ   - opZ)
                +  0.2 * (myMob - opMob);
    }

    private double mobility(PylosBoard board, PylosPlayer who, PylosSphere pretendRemove) {
        // mobility after (optionally) removing 'pretendRemove'
        double m = 0;
        boolean skipId = (pretendRemove != null);
        int skipHash = skipId ? pretendRemove.hashCode() : 0;

        for (PylosSphere s : board.getSpheres(who)) {
            if (skipId && s.hashCode() == skipHash) continue;
            if (s.isReserve()) continue;
            for (PylosLocation l : board.getLocations()) if (s.canMoveTo(l)) m += 1;
        }
        return m;
    }
}
