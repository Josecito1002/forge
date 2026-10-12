package forge.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.gamemodes.spire.SpireMap;
import forge.gamemodes.spire.SpireRun;
import forge.item.PaperCard;
import forge.model.FModel;

/**
 * Commander Spire run state: deck split, mulligan, flipped cards, rewards and saving.
 */
public class SpireRunTest extends AITest {
    private static final String[] NON_LANDS = {
        "Runeclaw Bear", "Grizzly Bears", "Llanowar Elves", "Rampant Growth", "Cultivate", "Giant Growth",
        "Elvish Mystic", "Fyndhorn Elves", "Kodama's Reach", "Harmonize", "Craterhoof Behemoth", "Eternal Witness",
        "Wood Elves", "Sakura-Tribe Elder", "Beast Within", "Krosan Grip", "Regrowth", "Nature's Lore",
        "Three Visits", "Farseek"
    };

    private static PaperCard card(String name) {
        return FModel.getMagicDb().getCommonCards().getCard(name);
    }

    private static Deck sourceDeck() {
        Deck deck = new Deck("Test Commander");
        deck.getOrCreate(DeckSection.Commander).add(card("Omnath, Locus of Mana"));
        for (String name : NON_LANDS) {
            deck.getMain().add(card(name));
        }
        deck.getMain().add(card("Forest"), 6);
        return deck;
    }

    @Test
    public void startSplitsTheDeck() {
        SpireRun run = SpireRun.start(sourceDeck(), new Random(1));
        AssertJUnit.assertEquals(1, run.getCommanders().size());
        AssertJUnit.assertEquals(6, run.getLands().size());
        AssertJUnit.assertEquals(SpireRun.STARTING_DECK_SIZE, run.getDeck().size());
        AssertJUnit.assertEquals(NON_LANDS.length - SpireRun.STARTING_DECK_SIZE, run.getPool().size());
        AssertJUnit.assertEquals(SpireRun.MAX_LIFE, run.getLife());
    }

    @Test
    public void mulliganSwapsOneCardOnce() {
        SpireRun run = SpireRun.start(sourceDeck(), new Random(2));
        PaperCard out = run.getDeck().get(0);
        PaperCard top = run.getPool().get(0);
        run.mulligan(out);
        AssertJUnit.assertTrue(run.getDeck().contains(top));
        AssertJUnit.assertFalse(run.getDeck().contains(out));
        AssertJUnit.assertEquals(out, run.getPool().get(run.getPool().size() - 1));
        AssertJUnit.assertFalse(run.canMulligan());
    }

    @Test
    public void flippedCardsBecomeBasicCardsInAFight() {
        SpireRun run = SpireRun.start(sourceDeck(), new Random(3));
        run.flip(new ArrayList<>(run.getDeck().subList(0, SpireRun.FLIPPED_COUNT)));
        PaperCard basic = card("Spire Basic Card");
        AssertJUnit.assertNotNull(basic);

        SpireRun.Fight fight = run.prepareFight(basic, new Random(4));
        AssertJUnit.assertEquals(SpireRun.STARTING_DECK_SIZE, fight.deck.getMain().countAll());
        AssertJUnit.assertEquals(SpireRun.FLIPPED_COUNT, fight.deck.getMain().count(basic));
        AssertJUnit.assertEquals(SpireRun.STARTING_LANDS, fight.startingLands.size());
        AssertJUnit.assertEquals(6 - SpireRun.STARTING_LANDS, fight.deck.get(DeckSection.Sideboard).countAll());
        AssertJUnit.assertEquals(1, fight.deck.get(DeckSection.Commander).countAll());
    }

    @Test
    public void winRevealsRewardAndCarriesLife() {
        SpireRun run = SpireRun.start(sourceDeck(), new Random(5));
        int poolBefore = run.getPool().size();
        run.recordWin(55);
        AssertJUnit.assertEquals(SpireRun.MAX_LIFE, run.getLife());
        run.recordWin(23);
        AssertJUnit.assertEquals(23, run.getLife());
        AssertJUnit.assertEquals(2, run.getFightsWon());

        List<PaperCard> reward = new ArrayList<>(run.getPendingReward());
        AssertJUnit.assertEquals(SpireRun.REWARD_REVEAL, reward.size());
        run.takeReward(reward.get(1));
        AssertJUnit.assertFalse(run.hasPendingReward());
        AssertJUnit.assertTrue(run.getDeck().contains(reward.get(1)));
        AssertJUnit.assertEquals(SpireRun.STARTING_DECK_SIZE + 1, run.getDeckSize());
        AssertJUnit.assertEquals(poolBefore - 1, run.getPool().size());
        // the other two went to the bottom of the pool
        List<PaperCard> pool = run.getPool();
        AssertJUnit.assertEquals(reward.get(2), pool.get(pool.size() - 1));
        AssertJUnit.assertEquals(reward.get(0), pool.get(pool.size() - 2));
    }

    @Test
    public void takingNoRewardReturnsAllToThePool() {
        SpireRun run = SpireRun.start(sourceDeck(), new Random(6));
        int poolBefore = run.getPool().size();
        run.recordWin(30);
        run.takeReward(null);
        AssertJUnit.assertEquals(SpireRun.STARTING_DECK_SIZE, run.getDeckSize());
        AssertJUnit.assertEquals(poolBefore, run.getPool().size());
    }

    @Test
    public void lossEndsTheRun() {
        SpireRun run = SpireRun.start(sourceDeck(), new Random(7));
        run.recordLoss();
        AssertJUnit.assertTrue(run.isOver());
        AssertJUnit.assertEquals(0, run.getLife());
    }

    @Test
    public void saveAndLoadKeepEverything() {
        SpireRun run = SpireRun.start(sourceDeck(), new Random(8));
        run.mulligan(run.getDeck().get(3));
        run.flip(new ArrayList<>(run.getDeck().subList(0, SpireRun.FLIPPED_COUNT)));
        run.recordWin(31);

        SpireRun loaded = SpireRun.load(run.save(), request -> FModel.getMagicDb().getCommonCards().getCard(request));
        AssertJUnit.assertNotNull(loaded);
        AssertJUnit.assertEquals(run.getDeckName(), loaded.getDeckName());
        AssertJUnit.assertEquals(31, loaded.getLife());
        AssertJUnit.assertEquals(1, loaded.getFightsWon());
        AssertJUnit.assertFalse(loaded.canMulligan());
        AssertJUnit.assertEquals(run.getCommanders(), loaded.getCommanders());
        AssertJUnit.assertEquals(run.getLands(), loaded.getLands());
        AssertJUnit.assertEquals(run.getDeck(), loaded.getDeck());
        AssertJUnit.assertEquals(run.getFlipped(), loaded.getFlipped());
        AssertJUnit.assertEquals(run.getPool(), loaded.getPool());
        AssertJUnit.assertEquals(run.getPendingReward(), loaded.getPendingReward());
    }

    @Test
    public void mapHasFightsThenCampfiresThenTheBoss() {
        SpireMap map = SpireMap.generate(new Random(9));
        AssertJUnit.assertEquals(SpireMap.ROWS + 2, map.getRowCount());
        for (SpireMap.Node node : map.getRow(0)) {
            AssertJUnit.assertEquals(SpireMap.NodeType.FIGHT, node.type);
            AssertJUnit.assertNotNull(node.monster);
        }
        for (SpireMap.Node node : map.getRow(SpireMap.ROWS)) {
            AssertJUnit.assertEquals(SpireMap.NodeType.CAMPFIRE, node.type);
        }
        List<SpireMap.Node> top = map.getRow(SpireMap.ROWS + 1);
        AssertJUnit.assertEquals(1, top.size());
        AssertJUnit.assertEquals(SpireMap.Monster.RAT_KING, top.get(0).monster);

        AssertJUnit.assertEquals(SpireMap.WIDTH, map.getNextChoices(null).size());
        AssertJUnit.assertEquals(2, map.getNextChoices(map.getNode(0, 0)).size());
        AssertJUnit.assertEquals(3, map.getNextChoices(map.getNode(0, 1)).size());
        AssertJUnit.assertEquals(1, map.getNextChoices(map.getNode(SpireMap.ROWS, 0)).size());
    }

    @Test
    public void climbingTheMapToTheBossWinsTheRun() {
        SpireRun run = SpireRun.start(sourceDeck(), new Random(10));
        AssertJUnit.assertNull(run.getCurrentNode());
        int campfires = 0;
        while (!run.isVictory()) {
            List<SpireMap.Node> choices = run.getNextChoices();
            AssertJUnit.assertFalse(choices.isEmpty());
            run.moveTo(choices.get(0));
            if (run.isAtCampfire()) {
                campfires++;
                run.rest();
            } else {
                AssertJUnit.assertNotNull(run.getPendingFight());
                run.recordWin(20);
                if (!run.isVictory()) {
                    run.takeReward(null);
                }
            }
            AssertJUnit.assertTrue(run.isNodeDone());
        }
        AssertJUnit.assertTrue(campfires >= 1);
        AssertJUnit.assertEquals(SpireRun.MAX_LIFE, run.getLife());
        AssertJUnit.assertEquals(SpireRun.BOSS_REWARD_REVEAL, run.getPendingReward().size());
        AssertJUnit.assertTrue(run.getNextChoices().isEmpty());
    }

    @Test
    public void campfireHealsFlipsOrUnflips() {
        SpireRun run = SpireRun.start(sourceDeck(), new Random(11));
        // walk up to the campfire row
        while (run.getCurrentNode() == null || run.getCurrentNode().row < SpireMap.ROWS) {
            run.moveTo(run.getNextChoices().get(0));
            if (run.isAtCampfire()) {
                run.rest();
            } else {
                run.recordWin(10);
                run.takeReward(null);
            }
        }
        // the last step above was a rest at the campfire row: 10 + 12
        AssertJUnit.assertEquals(22, run.getLife());

        SpireRun other = SpireRun.load(run.save(), request -> FModel.getMagicDb().getCommonCards().getCard(request));
        AssertJUnit.assertEquals(run.getCurrentNode().row, other.getCurrentNode().row);
        AssertJUnit.assertEquals(run.getCurrentNode().column, other.getCurrentNode().column);
        AssertJUnit.assertTrue(other.isNodeDone());
        AssertJUnit.assertEquals(run.getMap().save(), other.getMap().save());
    }

    @Test
    public void campfireFlipAndUnflip() {
        SpireRun run = SpireRun.start(sourceDeck(), new Random(12));
        PaperCard card = run.getDeck().get(0);
        // find a path with a campfire in rows 1..ROWS
        while (!run.isAtCampfire()) {
            run.moveTo(run.getNextChoices().get(0));
            if (run.getPendingFight() != null) {
                run.recordWin(30);
                run.takeReward(null);
            }
        }
        run.flipAtCampfire(card);
        AssertJUnit.assertTrue(run.getFlipped().contains(card));
        AssertJUnit.assertTrue(run.isNodeDone());
        // a used campfire does nothing more
        run.unflipAtCampfire(card);
        AssertJUnit.assertTrue(run.getFlipped().contains(card));
    }

    @Test
    public void customCardsAddedAtRuntimeAreFoundByName() {
        // Android adds the Spire scripts at runtime (SpireCards); a fresh name stands in for a missing card.
        List<String> script = java.util.Arrays.asList(
                "Name:Spire Runtime Test Card", "ManaCost:1", "Types:Instant",
                "A:SP$ DealDamage | ValidTgts$ Any | NumDmg$ 1 | SpellDescription$ Deal 1 damage to any target.",
                "Oracle:Deal 1 damage to any target.");
        forge.card.CardRules rules = new forge.card.CardRules.Reader().readCard(script, "spire_runtime_test_card");
        forge.card.CardDb db = FModel.getMagicDb().getCommonCards();
        AssertJUnit.assertNull(db.getUniqueByName("Spire Runtime Test Card"));
        db.addCustomCard(rules, forge.card.CardEdition.UNKNOWN_CODE, forge.card.CardRarity.Special);
        PaperCard found = db.getUniqueByName("Spire Runtime Test Card");
        AssertJUnit.assertNotNull(found);
        AssertJUnit.assertNotNull(db.getCard("Spire Runtime Test Card"));
        // and it can be put into a game
        forge.game.Game game = initAndCreateGame();
        forge.game.card.Card c = addCardToZone("Spire Runtime Test Card", game.getPlayers().get(0), forge.game.zone.ZoneType.Hand);
        AssertJUnit.assertEquals("Spire Runtime Test Card", c.getName());
    }
}
