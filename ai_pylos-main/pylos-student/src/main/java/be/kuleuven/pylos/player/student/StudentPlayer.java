package be.kuleuven.pylos.player.student;

import be.kuleuven.pylos.game.*;
import be.kuleuven.pylos.player.PylosPlayer;
import java.util.ArrayList;
import java.util.List;

public class StudentPlayer extends PylosPlayer {

    private static final int MAX_DEPTH = 3; // começa por 3; depois podes subir

    @Override
    public void doMove(PylosGameIF game, PylosBoard board) {
        Move best = searchBestMove(board, MAX_DEPTH);
        if (best == null) {
            // fallback: coloca a reserve em qualquer sítio legal
            PylosSphere r = board.getReserve(this);
            if (r != null) {
                for (PylosLocation loc : board.getLocations()) if (r.canMoveTo(loc)) { game.moveSphere(r, loc); return; }
            }
            return;
        }
        game.moveSphere(best.sphere, best.to);
    }

    @Override
    public void doRemove(PylosGameIF game, PylosBoard board) {
        PylosSphere best = null; double bestScore = Double.NEGATIVE_INFINITY;
        for (PylosSphere s : board.getSpheres(this)) if (s.canRemove()) {
            double score = mobilityAfterRemoval(board, s);
            if (score > bestScore) { bestScore = score; best = s; }
        }
        if (best != null) game.removeSphere(best); else game.pass();
    }

    @Override
    public void doRemoveOrPass(PylosGameIF game, PylosBoard board) {
        PylosSphere pick = null; double bestGain = -1e9;
        for (PylosSphere s : board.getSpheres(this)) if (s.canRemove()) {
            double g = mobilityAfterRemoval(board, s);
            if (g > bestGain) { bestGain = g; pick = s; }
        }
        if (pick != null && bestGain >= 0) game.removeSphere(pick); else game.pass();
    }

    /* ===== search ===== */

    private static class Move { PylosSphere sphere; PylosLocation to; Move(PylosSphere s,PylosLocation l){sphere=s;to=l;} }

    private Move searchBestMove(PylosBoard board, int maxDepth) {
        PylosGameSimulator sim = new PylosGameSimulator(PylosGameState.MOVE, PLAYER_COLOR, board);
        double alpha = -1e9, beta = 1e9;
        Move best = null;

        for (Move m : generateMoves(board, this)) {
            PylosGameState ps = sim.getState(); PylosPlayerColor pc = sim.getColor();
            PylosLocation prev = m.sphere.isReserve()? null : m.sphere.getLocation();
            sim.moveSphere(m.sphere, m.to);

            double val = -negamax(sim, maxDepth-1, -beta, -alpha, false, board);

            if (prev == null) sim.undoAddSphere(m.sphere, ps, pc);
            else              sim.undoMoveSphere(m.sphere, prev, ps, pc);

            if (val > alpha) { alpha = val; best = m; }
        }
        return best;
    }

    private double negamax(PylosGameSimulator sim, int depth, double alpha, double beta, boolean myTurn, PylosBoard board) {
        if (depth == 0 || sim.getState() == PylosGameState.COMPLETED) return eval(board) * (myTurn?1:-1);

        PylosGameState state = sim.getState();

        if (state == PylosGameState.MOVE) {
            double best = -1e9;
            PylosPlayer who = (sim.getColor()==PLAYER_COLOR)? this : OTHER;
            for (Move m : generateMoves(board, who)) {
                PylosGameState ps = sim.getState(); PylosPlayerColor pc = sim.getColor();
                PylosLocation prev = m.sphere.isReserve()? null : m.sphere.getLocation();
                sim.moveSphere(m.sphere, m.to);

                double val = -negamax(sim, depth-1, -beta, -alpha, !myTurn, board);

                if (prev == null) sim.undoAddSphere(m.sphere, ps, pc);
                else              sim.undoMoveSphere(m.sphere, prev, ps, pc);

                if (val > best) best = val;
                if (val > alpha) alpha = val;
                if (alpha >= beta) break; // poda
            }
            return best;
        }

        if (state == PylosGameState.REMOVE_FIRST || state == PylosGameState.REMOVE_SECOND) {
            ArrayList<PylosSphere> rem = new ArrayList<>();
            PylosPlayer who = (sim.getColor()==PLAYER_COLOR)? this : OTHER;
            for (PylosSphere s : board.getSpheres(who)) if (s.canRemove()) rem.add(s);

            double best = -1e9;
            for (PylosSphere s : rem) {
                PylosGameState ps = sim.getState(); PylosPlayerColor pc = sim.getColor();
                PylosLocation from = s.getLocation();
                sim.removeSphere(s);
                double val = -negamax(sim, depth-1, -beta, -alpha, !myTurn, board);
                if (ps == PylosGameState.REMOVE_FIRST) sim.undoRemoveFirstSphere(s, from, ps, pc);
                else                                   sim.undoRemoveSecondSphere(s, from, ps, pc);
                if (val > best) best = val;
                if (val > alpha) alpha = val;
                if (alpha >= beta) break;
            }
            if (state == PylosGameState.REMOVE_SECOND) {
                PylosGameState ps = sim.getState(); PylosPlayerColor pc = sim.getColor();
                sim.pass();
                double val = -negamax(sim, depth-1, -beta, -alpha, !myTurn, board);
                sim.undoPass(ps, pc);
                if (val > best) best = val;
            }
            return best;
        }

        return eval(board) * (myTurn?1:-1);
    }

    /* ===== helpers ===== */

    private List<Move> generateMoves(PylosBoard board, PylosPlayer player) {
        ArrayList<Move> moves = new ArrayList<>();
        PylosSphere r = board.getReserve(player);
        if (r != null) for (PylosLocation l : board.getLocations()) if (r.canMoveTo(l)) moves.add(new Move(r, l));
        for (PylosSphere s : board.getSpheres(player)) if (!s.isReserve())
            for (PylosLocation l : board.getLocations()) if (s.canMoveTo(l)) moves.add(new Move(s, l));
        return moves;
    }

    private double mobilityAfterRemoval(PylosBoard board, PylosSphere toRemove) {
        double score = 0;
        for (PylosSphere s : board.getSpheres(this)) if (!s.isReserve())
            for (PylosLocation l : board.getLocations()) if (s.canMoveTo(l)) score += 1;
        if (toRemove != null && toRemove.getLocation()!=null) score += (3 - toRemove.getLocation().Z) * 0.1;
        return score;
    }

    private double eval(PylosBoard board) {
        int myRes=0, opRes=0, myZ=0, opZ=0;
        for (PylosSphere s : board.getSpheres(this)) { if (s.isReserve()) myRes++; else myZ += s.getLocation().Z; }
        for (PylosSphere s : board.getSpheres(this.OTHER)) { if (s.isReserve()) opRes++; else opZ += s.getLocation().Z; }
        return (myRes - opRes)*10 + (myZ - opZ);
    }
}
