package be.kuleuven.pylos.player.student;

import be.kuleuven.pylos.game.*;
import be.kuleuven.pylos.player.PylosPlayer;

import java.util.*;

/**
 * StudentPlayer – square-first ordering + square-aware evaluation + tactical extension.
 * Alpha-beta + TT (exact/upper/lower) + PV move ordering. No getPlayer()/getBoard() usage.
 */
public class StudentPlayer extends PylosPlayer {

    // Small bias to avoid comfy zero-eval lines
    private static final double CONTEMPT = 0.25;

    public StudentPlayer() { /* tunables are hardcoded below */ }


    /* ================= Transposition table ================= */
    private static final int TT_SIZE = 1 << 20; // ~1M
    private static final long TT_MASK = TT_SIZE - 1;
    private static final byte TT_EXACT = 0, TT_LOWER = 1, TT_UPPER = 2;

    private static final class TTEntry {
        long key; double value; int depth; byte flag;
        PylosSphere s; PylosLocation to;
    }
    private final TTEntry[] TT = new TTEntry[TT_SIZE];

    private long hashBoard(PylosBoard b, PylosPlayerColor side, PylosGameState st) {
        long h = 1469598103934665603L; // FNV-1a offset
        for (PylosSphere s : b.getSpheres(this)) {
            if (s.isReserve()) continue;
            PylosLocation L = s.getLocation();
            long code = 0x9E + 7*L.Z + 11*L.X + 13*L.Y;
            h ^= code; h *= 1099511628211L;
        }
        for (PylosSphere s : b.getSpheres(this.OTHER)) {
            if (s.isReserve()) continue;
            PylosLocation L = s.getLocation();
            long code = 0xA5 + 17*L.Z + 19*L.X + 23*L.Y;
            h ^= code; h *= 1099511628211L;
        }
        h ^= (long)side.ordinal() * 0x9E3779B97F4A7C15L;
        h ^= (long)st.ordinal()   * 0xBF58476D1CE4E5B9L;
        return h;
    }
    private TTEntry ttProbe(long key, int depth, double alpha, double beta) {
        TTEntry e = TT[(int)(key & TT_MASK)];
        if (e != null && e.key == key && e.depth >= depth) {
            if (e.flag == TT_EXACT) return e;
            if (e.flag == TT_LOWER && e.value >= beta) return e;
            if (e.flag == TT_UPPER && e.value <= alpha) return e;
        }
        return null;
    }
    private void ttStore(long key, int depth, double val, double alphaOrig, double betaOrig, byte flag, Move best) {
        int idx = (int)(key & TT_MASK);
        TTEntry e = TT[idx];
        if (e == null || e.depth <= depth) TT[idx] = e = new TTEntry();
        e.key = key; e.depth = depth; e.value = val; e.flag = flag;
        e.s = (best == null) ? null : best.sphere;
        e.to = (best == null) ? null : best.to;
    }

    /* ================= Search settings ================= */
    private static final int MAX_DEPTH = 4 ;
    private static final double INF = 1e18;

    /* ================= Entrypoints ================= */
    @Override
    public void doMove(PylosGameIF game, PylosBoard board) {
        Move best = searchRoot(board, MAX_DEPTH);
        if (best != null) { game.moveSphere(best.sphere, best.to); return; }
        // rare fallback
        PylosSphere r = board.getReserve(this);
        if (r != null) {
            for (PylosLocation l : board.getLocations()) if (r.canMoveTo(l)) { game.moveSphere(r, l); return; }
        }
        game.pass();
    }

    @Override
    public void doRemove(PylosGameIF game, PylosBoard board) {
        PylosSphere pick = null;
        double best = -INF;
        for (PylosSphere s : board.getSpheres(this)) {
            if (!s.canRemove()) continue;
            double val = mobility(board, this, s) - 0.2 * s.getLocation().Z;
            if (val > best) { best = val; pick = s; }
        }
        if (pick != null) game.removeSphere(pick); else game.pass();
    }

    @Override
    public void doRemoveOrPass(PylosGameIF game, PylosBoard board) {
        double base = mobility(board, this, null);
        PylosSphere pick = null;
        double bestGain = -0.1; // slightly negative to prefer removing over passing when close
        for (PylosSphere s : board.getSpheres(this)) {
            if (!s.canRemove()) continue;
            double gain = mobility(board, this, s) - base - 0.2 * s.getLocation().Z;
            if (gain > bestGain) { bestGain = gain; pick = s; }
        }
        if (pick != null) game.removeSphere(pick); else game.pass();
    }

    /* ================= Core search ================= */
    private static final class Move {
        final PylosSphere sphere; final PylosLocation to;
        Move(PylosSphere s, PylosLocation t) { sphere = s; to = t; }
    }
    private static final class ScoredMove {
        final Move m; final double score;
        ScoredMove(Move m, double s) { this.m = m; this.score = s; }
    }

    private Move searchRoot(PylosBoard board, int depth) {
        PylosGameSimulator sim = new PylosGameSimulator(PylosGameState.MOVE, PLAYER_COLOR, board);

        double alpha = -INF, beta = INF, bestVal = -INF;
        Move best = null;

        // Try PV move from TT first
        long key = hashBoard(board, sim.getColor(), sim.getState());
        TTEntry pv = ttProbe(key, depth, alpha, beta);

        List<Move> moves = generateOrderedMoves(sim, board, this);
        if (pv != null && pv.s != null && pv.to != null) {
            // move PV to front if it’s legal in current node
            for (int i = 0; i < moves.size(); i++) {
                Move m = moves.get(i);
                if (m.sphere == pv.s && m.to == pv.to) {
                    if (i != 0) { moves.remove(i); moves.add(0, m); }
                    break;
                }
            }
        }

        for (Move m : moves) {
            PylosGameState ps = sim.getState(); PylosPlayerColor pc = sim.getColor();
            PylosLocation from = m.sphere.isReserve() ? null : m.sphere.getLocation();
            sim.moveSphere(m.sphere, m.to);

            int nextDepth = depth - 1;
            if (sim.getState() == PylosGameState.REMOVE_FIRST) nextDepth = Math.max(nextDepth + 1, 0);

            double val = -negamax(sim, nextDepth, -beta, -alpha, board);

            if (from == null) sim.undoAddSphere(m.sphere, ps, pc);
            else              sim.undoMoveSphere(m.sphere, from, ps, pc);

            if (val > bestVal) { bestVal = val; best = m; alpha = Math.max(alpha, val); }
            if (alpha >= beta) break;
        }
        return best;
    }

    private double negamax(PylosGameSimulator sim, int depth, double alpha, double beta, PylosBoard board) {
        if (depth <= 0 || sim.getState() == PylosGameState.COMPLETED) {
            return signedEval(board, sim.getColor());
        }

        double alphaOrig = alpha;
        long key = hashBoard(board, sim.getColor(), sim.getState());
        TTEntry hit = ttProbe(key, depth, alpha, beta);
        if (hit != null) return hit.value;

        PylosGameState state = sim.getState();
        PylosPlayerColor side = sim.getColor();
        PylosPlayer who = (side == this.PLAYER_COLOR) ? this : this.OTHER;

        double best = -INF;
        byte flag = TT_UPPER; // assume fail-high/low until we improve alpha
        Move bestMoveForTT = null;

        if (state == PylosGameState.MOVE) {
            List<Move> moves = generateOrderedMoves(sim, board, who);

            // PV move first if TT has one
            TTEntry pv = TT[(int)(key & TT_MASK)];
            if (pv != null && pv.key == key && pv.s != null && pv.to != null) {
                for (int i = 0; i < moves.size(); i++) {
                    Move m = moves.get(i);
                    if (m.sphere == pv.s && m.to == pv.to) {
                        if (i != 0) { moves.remove(i); moves.add(0, m); }
                        break;
                    }
                }
            }

            if (moves.isEmpty()) return signedEval(board, sim.getColor()); // <- FIXED (was evaluate)

            for (Move m : moves) {
                PylosGameState ps = sim.getState(); PylosPlayerColor pc = sim.getColor();
                PylosLocation from = m.sphere.isReserve() ? null : m.sphere.getLocation();

                sim.moveSphere(m.sphere, m.to);

                int nextDepth = depth - 1;
                if (sim.getState() == PylosGameState.REMOVE_FIRST) nextDepth = Math.max(nextDepth + 1, 0);

                double val = -negamax(sim, nextDepth, -beta, -alpha, board);

                if (from == null) sim.undoAddSphere(m.sphere, ps, pc);
                else              sim.undoMoveSphere(m.sphere, from, ps, pc);

                if (val > best) { best = val; bestMoveForTT = m; }
                if (val > alpha) { alpha = val; flag = TT_EXACT; }
                if (alpha >= beta) { flag = TT_LOWER; break; }
            }
        } else {
            // REMOVE_FIRST / REMOVE_SECOND
            List<PylosSphere> choices = new ArrayList<>();
            for (PylosSphere s : board.getSpheres(who)) if (s.canRemove()) choices.add(s);
            // remove higher-Z first (keeps base intact)
            choices.sort(Comparator.comparingInt((PylosSphere s) -> s.getLocation().Z).reversed());

            for (PylosSphere s : choices) {
                PylosGameState ps = sim.getState(); PylosPlayerColor pc = sim.getColor();
                PylosLocation from = s.getLocation();

                sim.removeSphere(s);
                double val = -negamax(sim, depth - 1, -beta, -alpha, board);

                if (ps == PylosGameState.REMOVE_FIRST) sim.undoRemoveFirstSphere(s, from, ps, pc);
                else                                   sim.undoRemoveSecondSphere(s, from, ps, pc);

                if (val > best) { best = val; bestMoveForTT = null; }
                if (val > alpha) { alpha = val; flag = TT_EXACT; }
                if (alpha >= beta) { flag = TT_LOWER; break; }
            }

            if (state == PylosGameState.REMOVE_SECOND) {
                // also consider PASS
                PylosGameState ps = sim.getState(); PylosPlayerColor pc = sim.getColor();
                sim.pass();
                double val = -negamax(sim, depth - 1, -beta, -alpha, board);
                sim.undoPass(ps, pc);
                if (val > best) { best = val; }
                if (val > alpha) { alpha = val; flag = TT_EXACT; }
            }
        }

        // finalize TT flag (consistent with alphaOrig/beta)
        byte storeFlag = flag;
        if (best <= alphaOrig) storeFlag = TT_UPPER;     // fail-low
        else if (best >= beta) storeFlag = TT_LOWER;     // fail-high
        else storeFlag = TT_EXACT;

        ttStore(key, depth, best, alphaOrig, beta, storeFlag, bestMoveForTT);
        return best;
    }

    /* ================= Move ordering ================= */
    private List<Move> generateOrderedMoves(PylosGameSimulator sim, PylosBoard board, PylosPlayer who) {
        List<Move> raw = new ArrayList<>();

        // reserve placements (ours only)
        PylosSphere r = board.getReserve(who);
        if (r != null) for (PylosLocation l : board.getLocations()) if (r.canMoveTo(l)) raw.add(new Move(r, l));

        // lifts/moves (ours only)
        for (PylosSphere s : board.getSpheres(who)) {
            if (s.isReserve()) continue;
            for (PylosLocation l : board.getLocations()) if (s.canMoveTo(l)) raw.add(new Move(s, l));
        }

        // probe ordering: squares >> lifts >> center
        List<ScoredMove> scored = new ArrayList<>(raw.size());
        for (Move m : raw) {
            double score = 0.0;

            // lift bonus
            if (!m.sphere.isReserve()) {
                if (m.to.Z > m.sphere.getLocation().Z) score += 6.0;
            } else if (m.to.Z > 0) {
                score += 4.0;
            }

            score += centerBonus(m.to);

            // light probe to see if it forms a square
            PylosGameState ps = sim.getState(); PylosPlayerColor pc = sim.getColor();
            PylosLocation from = m.sphere.isReserve() ? null : m.sphere.getLocation();

            sim.moveSphere(m.sphere, m.to);
            if (sim.getState() == PylosGameState.REMOVE_FIRST) score += 200.20;
            if (from == null) sim.undoAddSphere(m.sphere, ps, pc);
            else              sim.undoMoveSphere(m.sphere, from, ps, pc);

            scored.add(new ScoredMove(m, score));
        }

        scored.sort((a, b) -> Double.compare(b.score, a.score));
        List<Move> ordered = new ArrayList<>(scored.size());
        for (ScoredMove sm : scored) ordered.add(sm.m);
        return ordered;
    }

    private double centerBonus(PylosLocation l) {
        int n = 4 - l.Z;
        double cx = (n - 1) / 2.0, cy = (n - 1) / 2.0;
        double dx = l.X - cx, dy = l.Y - cy;
        double d2 = dx*dx + dy*dy;
        double edge = (l.X == 0 || l.Y == 0 || l.X == n-1 || l.Y == n-1) ? -0.5 : 0.0;
        return -0.6 * d2 + edge + (l.Z == 0 ? 0.7 : 0.25);
    }

    /* ================= Evaluation ================= */
    private double signedEval(PylosBoard board, PylosPlayerColor sideToMove) {
        double e = eval(board);
        return (sideToMove == this.PLAYER_COLOR) ? (e + CONTEMPT) : (-e - CONTEMPT);
    }

    private double eval(PylosBoard board) {
        int myRes=0, opRes=0, myZ=0, opZ=0, myMob=0, opMob=0;

        boolean[][][] occMy = new boolean[4][][], occOp = new boolean[4][][], occAny = new boolean[4][][];
        for (int z=0; z<4; z++) { int n=4-z; occMy[z]=new boolean[n][n]; occOp[z]=new boolean[n][n]; occAny[z]=new boolean[n][n]; }

        for (PylosSphere s : board.getSpheres(this)) {
            if (s.isReserve()) { myRes++; continue; }
            PylosLocation L = s.getLocation(); myZ += L.Z;
            occMy[L.Z][L.X][L.Y] = true; occAny[L.Z][L.X][L.Y] = true;
            for (PylosLocation t : board.getLocations()) if (s.canMoveTo(t)) myMob++;
        }
        for (PylosSphere s : board.getSpheres(this.OTHER)) {
            if (s.isReserve()) { opRes++; continue; }
            PylosLocation L = s.getLocation(); opZ += L.Z;
            occOp[L.Z][L.X][L.Y] = true; occAny[L.Z][L.X][L.Y] = true;
            for (PylosLocation t : board.getLocations()) if (s.canMoveTo(t)) opMob++;
        }

        int mySquares = countSquares(occMy);
        int opSquares = countSquares(occOp);
        int myThreats = countThreats3of4(occMy, occAny);
        int opThreats = countThreats3of4(occOp, occAny);

        return  16.0 * (myRes     - opRes)   // reserve advantage (lower -> place earlier)
                + 14.0 * (mySquares - opSquares)
                +   7.0 * (myThreats - opThreats)
                +   1.2 * (myZ       - opZ)
                +   0.15* (myMob     - opMob);
    }

    private int countSquares(boolean[][][] occ) {
        int tot = 0;
        for (int z=0; z<4; z++) {
            int n=4-z; if (n<2) continue;
            for (int x=0; x<n-1; x++) for (int y=0; y<n-1; y++)
                if (occ[z][x][y] && occ[z][x+1][y] && occ[z][x][y+1] && occ[z][x+1][y+1]) tot++;
        }
        return tot;
    }
    private int countThreats3of4(boolean[][][] mine, boolean[][][] any) {
        int t=0;
        for (int z=0; z<4; z++) {
            int n=4-z; if (n<2) continue;
            for (int x=0; x<n-1; x++) for (int y=0; y<n-1; y++) {
                int cnt=0;
                if (mine[z][x][y]) cnt++; if (mine[z][x+1][y]) cnt++;
                if (mine[z][x][y+1]) cnt++; if (mine[z][x+1][y+1]) cnt++;
                if (cnt==3) {
                    if (!mine[z][x][y]     && !any[z][x][y])     t++;
                    if (!mine[z][x+1][y]   && !any[z][x+1][y])   t++;
                    if (!mine[z][x][y+1]   && !any[z][x][y+1])   t++;
                    if (!mine[z][x+1][y+1] && !any[z][x+1][y+1]) t++;
                }
            }
        }
        return t;
    }

    /* ================= Utilities ================= */
    private double mobility(PylosBoard board, PylosPlayer who, PylosSphere pretendRemove) {
        int skip = pretendRemove == null ? 0 : System.identityHashCode(pretendRemove);
        double m = 0;
        for (PylosSphere s : board.getSpheres(who)) {
            if (pretendRemove != null && System.identityHashCode(s) == skip) continue;
            if (s.isReserve()) continue;
            for (PylosLocation l : board.getLocations()) if (s.canMoveTo(l)) m += 1;
        }
        return m;
    }
}
