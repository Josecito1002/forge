package forge.ai;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CounterType;
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
    public void onlyTheTopLandOfThePileCanBePlayed() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        addCardToZone("Commander Spire Rules", p, ZoneType.Command);
        addCardToZone("Forest", p, ZoneType.Sideboard);
        addCardToZone("Island", p, ZoneType.Sideboard);
        game.getAction().checkStateEffects(true);

        Card top = p.getCardsIn(ZoneType.Sideboard).get(0);
        Card below = p.getCardsIn(ZoneType.Sideboard).get(1);
        AssertJUnit.assertTrue(p.getCardsActivatableInExternalZones(true).contains(top));
        AssertJUnit.assertFalse(top.getAllPossibleAbilities(p, true).isEmpty());
        AssertJUnit.assertFalse(p.getCardsActivatableInExternalZones(true).contains(below));
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
        addCardToZone("Spire Giant Rat", rat, ZoneType.Battlefield);
        for (int i = 0; i < 5; i++) {
            addCardToZone("Island", p, ZoneType.Hand);
        }

        int[] expectedLife = {35, 30, 30, 27, 20};
        for (int expected : expectedLife) {
            do {
                playUntilNextTurn(game);
            } while (!game.isGameOver() && !game.getPhaseHandler().isPlayerTurn(rat));
            playUntilPhase(game, PhaseType.MAIN1);
            AssertJUnit.assertEquals(expected, p.getLife());
        }
        AssertJUnit.assertFalse(rat.hasLost());
    }

    @Test
    public void basicCardDealsTwoToTheMonster() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Player rat = game.getPlayers().get(0);
        rat.setLife(10, null);
        Card basic = addCardToZone("Spire Basic Card", p, ZoneType.Hand);

        SpellAbility sa = basic.getFirstSpellAbility().getAdditionalAbilityList("Choices").get(0);
        sa.setActivatingPlayer(p);
        sa.getTargets().add(rat);
        AbilityUtils.resolve(sa);

        AssertJUnit.assertEquals(8, rat.getLife());
    }

    @Test
    public void basicCardBlocksFiveDamageUntilYourNextTurn() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Player skel = game.getPlayers().get(0);
        p.setLife(40, null);
        fillLibrary(p, 20);
        Card rules = addCardToZone("Commander Spire Rules", p, ZoneType.Command);
        addCardToZone("Spire Skeleton Guardian", skel, ZoneType.Battlefield);
        Card basic = addCardToZone("Spire Basic Card", p, ZoneType.Hand);

        // the block mode takes no target: it always goes on you
        SpellAbility sa = basic.getFirstSpellAbility().getAdditionalAbilityList("Choices").get(1);
        AssertJUnit.assertFalse(sa.usesTargeting());
        sa.setActivatingPlayer(p);
        AbilityUtils.resolve(sa);
        AssertJUnit.assertEquals(5, rules.getCounters(CounterType.getType("BLOCK")));

        // it survives your end step and soaks 5 of the monster's 7 damage on its turn
        goToMonsterMain1(game, skel);
        AssertJUnit.assertEquals(38, p.getLife());
        AssertJUnit.assertEquals(0, rules.getCounters(CounterType.getType("BLOCK")));

        // unused block goes away at the beginning of your upkeep
        rules.addCounterInternal(CounterType.getType("BLOCK"), 5, p, false, null, null);
        do {
            playUntilNextTurn(game);
        } while (!game.isGameOver() && !game.getPhaseHandler().isPlayerTurn(p));
        playUntilPhase(game, PhaseType.MAIN1);
        AssertJUnit.assertEquals(0, rules.getCounters(CounterType.getType("BLOCK")));
    }

    @Test
    public void skeletonGuardianBlocksDamageUntilTheEndOfYourTurn() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Player skel = game.getPlayers().get(0);
        skel.setLife(14, null);
        Card guardian = addCardToZone("Spire Skeleton Guardian", skel, ZoneType.Battlefield);
        guardian.addCounterInternal(CounterType.getType("BLOCK"), 6, skel, false, null, null);

        Card axe = addCardToZone("Lava Axe", p, ZoneType.Hand);
        SpellAbility hit = axe.getFirstSpellAbility();
        hit.setActivatingPlayer(p);
        hit.getTargets().add(skel);
        AbilityUtils.resolve(hit);
        AssertJUnit.assertEquals(14, skel.getLife());
        AssertJUnit.assertEquals(1, guardian.getCounters(CounterType.getType("BLOCK")));

        Card axe2 = addCardToZone("Lava Axe", p, ZoneType.Hand);
        SpellAbility hit2 = axe2.getFirstSpellAbility();
        hit2.setActivatingPlayer(p);
        hit2.getTargets().add(skel);
        AbilityUtils.resolve(hit2);
        AssertJUnit.assertEquals(10, skel.getLife());
        AssertJUnit.assertEquals(0, guardian.getCounters(CounterType.getType("BLOCK")));
    }

    @Test
    public void skeletonGuardianFollowsItsCycle() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Player skel = game.getPlayers().get(0);
        p.setLife(40, null);
        fillLibrary(p, 20);
        Card guardian = addCardToZone("Spire Skeleton Guardian", skel, ZoneType.Battlefield);
        for (int i = 0; i < 5; i++) {
            addCardToZone("Island", p, ZoneType.Hand);
        }

        int[] expectedLife = {33, 33, 28, 21, 14};
        int[] expectedBlock = {0, 10, 5, 0, 0};
        for (int i = 0; i < expectedLife.length; i++) {
            do {
                playUntilNextTurn(game);
            } while (!game.isGameOver() && !game.getPhaseHandler().isPlayerTurn(skel));
            playUntilPhase(game, PhaseType.MAIN1);
            AssertJUnit.assertEquals(expectedLife[i], p.getLife());
            AssertJUnit.assertEquals(expectedBlock[i], guardian.getCounters(CounterType.getType("BLOCK")));
        }
    }

    @Test
    public void ratKingEnragesInPhaseTwo() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Player king = game.getPlayers().get(0);
        p.setLife(40, null);
        king.setLife(40, null);
        fillLibrary(p, 20);
        addCardToZone("Spire Rat King", king, ZoneType.Battlefield);
        for (int i = 0; i < 5; i++) {
            addCardToZone("Island", p, ZoneType.Hand);
        }

        // phase 1: rats, then 9 damage
        goToMonsterMain1(game, king);
        AssertJUnit.assertEquals(2, countCardsWithName(game, "Rat Token"));
        goToMonsterMain1(game, king);
        int afterHit = p.getLife();
        AssertJUnit.assertTrue("rats may attack too, but the 9 damage landed", afterHit <= 31);

        // phase 2: block 15, and the enraged 5 damage in the same upkeep
        king.setLife(20, null);
        goToMonsterMain1(game, king);
        AssertJUnit.assertTrue(p.getLife() <= afterHit - 5);
        AssertJUnit.assertEquals(15, game.getCardsIn(ZoneType.Battlefield).stream()
                .filter(c -> c.getName().equals("Spire Rat King")).findFirst().get()
                .getCounters(CounterType.getType("BLOCK")));
    }

    @Test
    public void monsterIsACreatureWhoseToughnessIsItsLife() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Player skel = game.getPlayers().get(0);
        skel.setLife(28, null);
        Card guardian = addCardToZone("Spire Skeleton Guardian", skel, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        AssertJUnit.assertEquals(28, guardian.getNetToughness());

        // damage aimed at the creature hits the monster's life
        Card bolt = addCardToZone("Lightning Bolt", p, ZoneType.Hand);
        SpellAbility sa = bolt.getFirstSpellAbility();
        sa.setActivatingPlayer(p);
        sa.getTargets().add(guardian);
        AbilityUtils.resolve(sa);
        AssertJUnit.assertEquals(25, skel.getLife());
        AssertJUnit.assertEquals(0, guardian.getDamage());

        // block counters still soak damage aimed at the creature
        guardian.addCounterInternal(CounterType.getType("BLOCK"), 2, skel, false, null, null);
        Card bolt2 = addCardToZone("Lightning Bolt", p, ZoneType.Hand);
        SpellAbility sa2 = bolt2.getFirstSpellAbility();
        sa2.setActivatingPlayer(p);
        sa2.getTargets().add(guardian);
        AbilityUtils.resolve(sa2);
        AssertJUnit.assertEquals(24, skel.getLife());
    }

    @Test
    public void removalOnTheMonsterCostsItTenLife() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Player rat = game.getPlayers().get(0);
        rat.setLife(20, null);
        Card monster = addCardToZone("Spire Giant Rat", rat, ZoneType.Battlefield);

        Card path = addCardToZone("Path to Exile", p, ZoneType.Hand);
        SpellAbility sa = path.getFirstSpellAbility();
        sa.setActivatingPlayer(p);
        sa.getTargets().add(monster);
        AbilityUtils.resolve(sa);

        AssertJUnit.assertEquals(10, rat.getLife());
        AssertJUnit.assertTrue(monster.isInZone(ZoneType.Battlefield));
    }

    @Test
    public void spireRelicsWorkFromTheCommandZone() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Player skel = game.getPlayers().get(0);
        p.setLife(40, null);
        fillLibrary(p, 20);
        Card rules = addCardToZone("Commander Spire Rules", p, ZoneType.Command);
        addCardToZone("Spire Anchor", p, ZoneType.Command);
        addCardToZone("Spire Whetstone", p, ZoneType.Command);
        addCardToZone("Spire Skeleton Guardian", skel, ZoneType.Battlefield);
        Card bear = addCardToZone("Grizzly Bears", p, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        AssertJUnit.assertEquals(3, bear.getNetPower());

        // the anchor's block 10, from the end of your first turn, soaks the guardian's 7 damage
        if (p.getTurn() == 0) {
            p.incrementTurn(); // the test game starts mid-turn, without a turn begin
        }
        playUntilPhase(game, PhaseType.CLEANUP);
        AssertJUnit.assertEquals(10, rules.getCounters(CounterType.getType("BLOCK")));
        goToMonsterMain1(game, skel);
        AssertJUnit.assertEquals(40, p.getLife());
    }

    private void goToMonsterMain1(Game game, Player monster) {
        do {
            playUntilNextTurn(game);
        } while (!game.isGameOver() && !game.getPhaseHandler().isPlayerTurn(monster));
        playUntilPhase(game, PhaseType.MAIN1);
    }
}
