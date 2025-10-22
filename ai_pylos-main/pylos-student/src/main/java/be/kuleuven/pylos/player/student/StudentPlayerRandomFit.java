package be.kuleuven.pylos.player.student;

import be.kuleuven.pylos.game.PylosBoard;
import be.kuleuven.pylos.game.PylosGameIF;
import be.kuleuven.pylos.game.PylosLocation;
import be.kuleuven.pylos.game.PylosSphere;
import be.kuleuven.pylos.player.PylosPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Fill in the warm-up player: random legal actions. */
public class StudentPlayerRandomFit extends PylosPlayer {

    @Override
    public void doMove(PylosGameIF game, PylosBoard board) {
        // choose randomly between placing a reserve or moving a used sphere upward
        List<PylosSphere> candidates = new ArrayList<>();
        PylosSphere reserve = board.getReserve(this);
        if (reserve != null) candidates.add(reserve);
        for (PylosSphere s : board.getSpheres(this)) if (!s.isReserve()) candidates.add(s);

        Random rnd = getRandom();
        while (!candidates.isEmpty()) {
            PylosSphere s = candidates.remove(rnd.nextInt(candidates.size()));
            List<PylosLocation> locs = new ArrayList<>();
            for (PylosLocation l : board.getLocations()) if (s.canMoveTo(l)) locs.add(l);
            if (!locs.isEmpty()) {
                PylosLocation to = locs.get(rnd.nextInt(locs.size()));
                game.moveSphere(s, to);
                return;
            }
        }
        // no legal move -> do nothing (game likely finished)
    }

    @Override
    public void doRemove(PylosGameIF game, PylosBoard board) {
        List<PylosSphere> list = new ArrayList<>();
        for (PylosSphere s : board.getSpheres(this)) if (s.canRemove()) list.add(s);
        if (!list.isEmpty()) game.removeSphere(list.get(getRandom().nextInt(list.size())));
    }

    @Override
    public void doRemoveOrPass(PylosGameIF game, PylosBoard board) {
        // always pass for the simple version
        game.pass();
    }
}
