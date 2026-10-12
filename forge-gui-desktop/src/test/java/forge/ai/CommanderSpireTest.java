package forge.ai;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * Commander Spire mode: the player's rules card (draw up to 5, reshuffle graveyard, land pile in the sideboard)
 * and the Spire Giant Rat monster card.
 */
public class CommanderSpireTest extends AITest {

    private void goToOwnMain1(Game game, Player p) {
        do {
            playUntilNextTurn(game);
        } while (!game.isGameOver() && !game.getPhaseHandler().isPlayerTurn(p));
        playUntilPhase(game, PhaseType.MAIN1);
    }

    @Test
    public void drawsUpToFiveInsteadOfNormalDraw() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Player opp = game.getPlayers().get(0);
        fillLibrary(opp, 10);
        fillLibrary(p, 10);
        addCardToZone("Commander Spire Rules", p, ZoneType.Command);
        addCardToZone("Runeclaw Bear", p, ZoneType.Hand);
        addCardToZone("Runeclaw Bear", p, ZoneType.Hand);

        goToOwnMain1(game, p);

        AssertJUnit.assertEquals(5, p.getCardsIn(ZoneType.Hand).size());
        AssertJUnit.assertEquals(7, p.getCardsIn(ZoneType.Library).size());
    }

    @Test
    public void emptyLibraryReshufflesGraveyardAndNeverLoses() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Player opp = game.getPlayers().get(0);
        fillLibrary(opp, 10);
        addCardToZone("Commander Spire Rules", p, ZoneType.Command);
        for (int i = 0; i < 3; i++) {
            addCardToZone("Runeclaw Bear", p, ZoneType.Graveyard);
        }

        goToOwnMain1(game, p);

        AssertJUnit.assertFalse(p.hasLost());
        AssertJUnit.assertEquals(3, p.getCardsIn(ZoneType.Hand).size());
        AssertJUnit.assertEquals(0, p.getCardsIn(ZoneType.Graveyard).size());

        // Nothing left anywhere: still no loss.
        p.getZone(ZoneType.Hand).removeAllCards(true);
        goToOwnMain1(game, p);
        AssertJUnit.assertFalse(p.hasLost());
    }

    @Test
    public void landsCanBePlayedFromThePile() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        addCardToZone("Commander Spire Rules", p, ZoneType.Command);
        Card forest = addCardToZone("Forest", p, ZoneType.Sideboard);
        game.getAction().checkStateEffects(true);

        AssertJUnit.assertTrue(p.getCardsActivatableInExternalZones(true).contains(forest));
        AssertJUnit.assertFalse(forest.getAllPossibleAbilities(p, true).isEmpty());
    }

    @Test
    public void librarySearchFindsLandsInThePile() {
        Game game = initAndCreateGame();
        game.getRules().setLandPileInSideboard(true);
        Player p = game.getPlayers().get(1);
        addCardToZone("Forest", p, ZoneType.Sideboard);
        fillLibrary(p, 5);
        Card growth = addCardToZone("Rampant Growth", p, ZoneType.Hand);

        SpellAbility sa = growth.getFirstSpellAbility();
        sa.setActivatingPlayer(p);
        AbilityUtils.resolve(sa);

        AssertJUnit.assertEquals(1, countCardsWithName(game, "Forest"));
        AssertJUnit.assertEquals(0, p.getCardsIn(ZoneType.Sideboard).size());
    }

    @Test
    public void giantRatFollowsItsCycle() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Player rat = game.getPlayers().get(0);
        p.setLife(40, null);
        fillLibrary(p, 20);
        addCardToZone("Spire Giant Rat", rat, ZoneType.Command);
        for (int i = 0; i < 5; i++) {
            addCardToZone("Island", p, ZoneType.Hand);
        }

        int[] expectedLife = {36, 32, 32, 30, 25};
        for (int expected : expectedLife) {
            do {
                playUntilNextTurn(game);
            } while (!game.isGameOver() && !game.getPhaseHandler().isPlayerTurn(rat));
            playUntilPhase(game, PhaseType.MAIN1);
            AssertJUnit.assertEquals(expected, p.getLife());
        }
        AssertJUnit.assertFalse(rat.hasLost());
    }
}
