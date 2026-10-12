package forge.gamemodes.spire;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;

/**
 * State of one Commander Spire run, carried between fights: life, the growing deck, flipped cards and the pool.
 * The source commander deck is split into commander, lands and pool (everything else, shuffled).
 */
public class SpireRun {
    public static final int MAX_LIFE = 40;
    public static final int STARTING_DECK_SIZE = 12;
    public static final int FLIPPED_COUNT = 4;
    public static final int REWARD_REVEAL = 3;
    public static final int STARTING_LANDS = 3;

    private final String deckName;
    private final List<PaperCard> commanders = new ArrayList<>();
    private final List<PaperCard> lands = new ArrayList<>();
    private final LinkedList<PaperCard> pool = new LinkedList<>();
    private final List<PaperCard> deck = new ArrayList<>();
    private final List<PaperCard> flipped = new ArrayList<>();
    private final List<PaperCard> pendingReward = new ArrayList<>();
    private int life = MAX_LIFE;
    private int fightsWon;
    private boolean mulliganUsed;
    private boolean over;

    /** What a single fight needs: the deck to register and the lands that start on the battlefield. */
    public static class Fight {
        public final Deck deck;
        public final List<PaperCard> startingLands;

        private Fight(Deck deck, List<PaperCard> startingLands) {
            this.deck = deck;
            this.startingLands = startingLands;
        }
    }

    private SpireRun(String deckName) {
        this.deckName = deckName;
    }

    public static SpireRun start(Deck source, Random rng) {
        SpireRun run = new SpireRun(source.getName());
        run.commanders.addAll(source.get(DeckSection.Commander).toFlatList());
        for (PaperCard card : source.getMain().toFlatList()) {
            if (card.getRules().getType().isLand()) {
                run.lands.add(card);
            } else {
                run.pool.add(card);
            }
        }
        Collections.shuffle(run.pool, rng);
        while (run.deck.size() < STARTING_DECK_SIZE && !run.pool.isEmpty()) {
            run.deck.add(run.pool.removeFirst());
        }
        return run;
    }

    public String getDeckName() { return deckName; }
    public List<PaperCard> getCommanders() { return Collections.unmodifiableList(commanders); }
    public List<PaperCard> getLands() { return Collections.unmodifiableList(lands); }
    public List<PaperCard> getPool() { return Collections.unmodifiableList(pool); }
    /** Cards in the deck with their normal side up. */
    public List<PaperCard> getDeck() { return Collections.unmodifiableList(deck); }
    /** Cards in the deck that are flipped: in a fight each one is a basic card instead. */
    public List<PaperCard> getFlipped() { return Collections.unmodifiableList(flipped); }
    public int getDeckSize() { return deck.size() + flipped.size(); }
    public int getLife() { return life; }
    public int getFightsWon() { return fightsWon; }
    public boolean isOver() { return over; }
    public boolean canMulligan() { return !mulliganUsed && !pool.isEmpty(); }
    public boolean hasPendingReward() { return !pendingReward.isEmpty(); }
    public List<PaperCard> getPendingReward() { return Collections.unmodifiableList(pendingReward); }

    /** The one mulligan: swap a card for the top card of the pool. The card goes to the bottom of the pool. */
    public void mulligan(PaperCard out) {
        if (!canMulligan() || !deck.remove(out)) {
            return;
        }
        deck.add(pool.removeFirst());
        pool.addLast(out);
        mulliganUsed = true;
    }

    public void flip(Collection<PaperCard> cards) {
        for (PaperCard card : cards) {
            if (deck.remove(card)) {
                flipped.add(card);
            }
        }
    }

    public void unflip(PaperCard card) {
        if (flipped.remove(card)) {
            deck.add(card);
        }
    }

    /**
     * Builds the deck for the next fight: commander, normal cards plus one basic card per flipped card,
     * and the land pile in the sideboard. 3 random lands start on the battlefield.
     */
    public Fight prepareFight(PaperCard basicCard, Random rng) {
        Deck fightDeck = new Deck("Commander Spire");
        fightDeck.getOrCreate(DeckSection.Commander).add(commanders);
        fightDeck.getOrCreate(DeckSection.Main).add(deck);
        if (!flipped.isEmpty()) {
            fightDeck.getMain().add(basicCard, flipped.size());
        }
        List<PaperCard> pile = new ArrayList<>(lands);
        Collections.shuffle(pile, rng);
        List<PaperCard> startingLands = new ArrayList<>();
        while (startingLands.size() < STARTING_LANDS && !pile.isEmpty()) {
            startingLands.add(pile.remove(pile.size() - 1));
        }
        fightDeck.getOrCreate(DeckSection.Sideboard).add(pile);
        return new Fight(fightDeck, startingLands);
    }

    /** A won fight: life carries over (up to 40) and 3 pool cards are revealed as the reward. */
    public void recordWin(int lifeLeft) {
        life = Math.max(1, Math.min(MAX_LIFE, lifeLeft));
        fightsWon++;
        takeReward(null); // an unclaimed earlier reward goes back to the pool
        for (int i = 0; i < REWARD_REVEAL && !pool.isEmpty(); i++) {
            pendingReward.add(pool.removeFirst());
        }
    }

    public void recordLoss() {
        life = 0;
        over = true;
    }

    /** Take one of the revealed cards (or none, with null). The rest go to the bottom of the pool. */
    public void takeReward(PaperCard chosen) {
        for (PaperCard card : pendingReward) {
            if (card == chosen) {
                deck.add(card);
                chosen = null;
            } else {
                pool.addLast(card);
            }
        }
        pendingReward.clear();
    }

    // Saving: one "key=value" or "[section]" per line, cards as "name|set".

    public List<String> save() {
        List<String> lines = new ArrayList<>();
        lines.add("name=" + deckName);
        lines.add("life=" + life);
        lines.add("won=" + fightsWon);
        lines.add("mulligan=" + mulliganUsed);
        lines.add("over=" + over);
        saveCards(lines, "commander", commanders);
        saveCards(lines, "lands", lands);
        saveCards(lines, "deck", deck);
        saveCards(lines, "flipped", flipped);
        saveCards(lines, "pool", pool);
        saveCards(lines, "reward", pendingReward);
        return lines;
    }

    private static void saveCards(List<String> lines, String section, List<PaperCard> cards) {
        lines.add("[" + section + "]");
        for (PaperCard card : cards) {
            lines.add(card.getName() + "|" + card.getEdition());
        }
    }

    /** Restores a saved run; cards are looked up with {@code findCard} ("name|set"), unknown cards are dropped. */
    public static SpireRun load(List<String> lines, Function<String, PaperCard> findCard) {
        String name = "";
        int life = MAX_LIFE, won = 0;
        boolean mulligan = false, over = false;
        List<PaperCard> target = null;
        SpireRun run = null;
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[")) {
                if (run == null) {
                    run = new SpireRun(name);
                }
                switch (line) {
                    case "[commander]": target = run.commanders; break;
                    case "[lands]": target = run.lands; break;
                    case "[deck]": target = run.deck; break;
                    case "[flipped]": target = run.flipped; break;
                    case "[pool]": target = run.pool; break;
                    case "[reward]": target = run.pendingReward; break;
                    default: target = null; break;
                }
            } else if (target != null) {
                PaperCard card = findCard.apply(line);
                if (card != null) {
                    target.add(card);
                }
            } else if (run == null) {
                int eq = line.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String key = line.substring(0, eq), value = line.substring(eq + 1);
                switch (key) {
                    case "name": name = value; break;
                    case "life": life = Integer.parseInt(value); break;
                    case "won": won = Integer.parseInt(value); break;
                    case "mulligan": mulligan = Boolean.parseBoolean(value); break;
                    case "over": over = Boolean.parseBoolean(value); break;
                    default: break;
                }
            }
        }
        if (run == null || run.commanders.isEmpty()) {
            return null;
        }
        run.life = life;
        run.fightsWon = won;
        run.mulliganUsed = mulligan;
        run.over = over;
        return run;
    }
}
