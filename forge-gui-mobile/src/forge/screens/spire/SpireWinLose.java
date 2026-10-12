package forge.screens.spire;

import forge.game.GameView;
import forge.gui.FThreads;
import forge.screens.match.winlose.ControlWinLose;
import forge.screens.match.winlose.ViewWinLose;

/** The win/lose screen of a Spire fight: the reward is picked right there, and the fight can't be replayed. */
public class SpireWinLose extends ControlWinLose {
    public SpireWinLose(final ViewWinLose view, final GameView game) {
        super(view, game);
        // "Start New Match" would replay the fight outside the run
        view.getBtnRestart().setEnabled(false);
    }

    @Override
    public void showRewards() {
        // after the screen is up, so the reward list opens on top of it
        FThreads.invokeInEdtLater(SpireScreen::onFightScreenShown);
    }
}
