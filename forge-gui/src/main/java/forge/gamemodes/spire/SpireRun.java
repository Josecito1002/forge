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
    public static final int BOSS_REWARD_REVEAL = 5;
    public static final int CAMPFIRE_HEAL = 12;
    public static final int STARTING_GOLD = 50;
    public static final int FIGHT_GOLD = 30;

    private final String deckName;
    private final List<PaperCard> commanders = new ArrayList<>();
    private final List<PaperCard> lands = new ArrayList<>();
    private final LinkedList<PaperCard> pool = new LinkedList<>();
    private final List<PaperCard> deck = new ArrayList<>();
    private final List<PaperCard> flipped = new ArrayList<>();
    private final List<PaperCard> pendingReward = new ArrayList<>();
    private final List<PaperCard> shopOffer = new ArrayList<>();
    private int life = MAX_LIFE;
    private int gold = STARTING_GOLD;
    private int fightsWon;
    private boolean mulliganUsed;
    private boolean over;
    private boolean victory;
    private SpireMap map;
    private int row = -1;
    private int column = -1;
    /** Whether the current node (fight won or campfire used) is finished. */
    private boolean nodeDone = true;

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
        run.map = SpireMap.generate(rng);
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
    public int getGold() { return gold; }
    public int getFightsWon() { return fightsWon; }
    public boolean isOver() { return over; }
    /** The boss is beaten: the run ends in victory once its reward is chosen. */
    public boolean isVictory() { return victory; }
    public SpireMap getMap() { return map; }
    public boolean isNodeDone() { return nodeDone; }

    /** The node the player stands on, or null before the first row. */
    public SpireMap.Node getCurrentNode() {
        return row < 0 ? null : map.getNode(row, column);
    }

    public List<SpireMap.Node> getNextChoices() {
        return nodeDone && !over && !victory ? map.getNextChoices(getCurrentNode()) : Collections.emptyList();
    }

    public void moveTo(SpireMap.Node node) {
        if (!getNextChoices().contains(node)) {
            return;
        }
        row = node.row;
        column = node.column;
        nodeDone = false;
    }

    /** The fight waiting on the current node, or null when there is none. */
    public SpireMap.Monster getPendingFight() {
        SpireMap.Node node = getCurrentNode();
        return node != null && !nodeDone && node.monster != null ? node.monster : null;
    }

    public boolean isAtCampfire() {
        SpireMap.Node node = getCurrentNode();
        return node != null && !nodeDone && node.type == SpireMap.NodeType.CAMPFIRE;
    }

    public boolean isAtShop() {
        SpireMap.Node node = getCurrentNode();
        return node != null && !nodeDone && node.type == SpireMap.NodeType.SHOP;
    }

    public List<PaperCard> getShopOffer() { return Collections.unmodifiableList(shopOffer); }

    /** Stocks the shop the first time the player opens it; the offer is kept until they leave. */
    public void stockShop(List<PaperCard> offer) {
        if (isAtShop() && shopOffer.isEmpty()) {
            shopOffer.addAll(offer);
        }
    }

    /** Buys a card from the shop into the deck; false when it isn't offered or costs more than the gold left. */
    public boolean buy(PaperCard card) {
        int price = SpireShop.price(card);
        if (!isAtShop() || !shopOffer.contains(card) || price > gold) {
            return false;
        }
        gold -= price;
        shopOffer.remove(card);
        deck.add(card);
        return true;
    }

    public void leaveShop() {
        if (isAtShop()) {
            shopOffer.clear();
            nodeDone = true;
        }
    }

    /** Campfire: heal 12 (up to 40). */
    public void rest() {
        if (isAtCampfire()) {
            life = Math.min(MAX_LIFE, life + CAMPFIRE_HEAL);
            nodeDone = true;
        }
    }

    /** Campfire: flip one normal card. */
    public void flipAtCampfire(PaperCard card) {
        if (isAtCampfire() && deck.contains(card)) {
            flip(Collections.singletonList(card));
            nodeDone = true;
        }
    }

    /** Campfire: turn one flipped card back to its normal side. */
    public void unflipAtCampfire(PaperCard card) {
        if (isAtCampfire() && flipped.contains(card)) {
            unflip(card);
            nodeDone = true;
        }
    }
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

    /**
     * A won fight: life carries over (up to 40), 30 gold, and 3 pool cards are revealed as the reward.
     * The boss reveals 5, heals fully and wins the run.
     */
    public void recordWin(int lifeLeft) {
        SpireMap.Node node = getCurrentNode();
        boolean boss = node != null && node.type == SpireMap.NodeType.BOSS;
        life = boss ? MAX_LIFE : Math.max(1, Math.min(MAX_LIFE, lifeLeft));
        fightsWon++;
        gold += boss ? 0 : FIGHT_GOLD;
        nodeDone = true;
        victory |= boss;
        takeReward(null); // an unclaimed earlier reward goes back to the pool
        int reveal = boss ? BOSS_REWARD_REVEAL : REWARD_REVEAL;
        for (int i = 0; i < reveal && !pool.isEmpty(); i++) {
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
        lines.add("gold=" + gold);
        lines.add("won=" + fightsWon);
        lines.add("mulligan=" + mulliganUsed);
        lines.add("over=" + over);
        lines.add("victory=" + victory);
        lines.add("row=" + row);
        lines.add("column=" + column);
        lines.add("nodeDone=" + nodeDone);
        saveCards(lines, "commander", commanders);
        saveCards(lines, "lands", lands);
        saveCards(lines, "deck", deck);
        saveCards(lines, "flipped", flipped);
        saveCards(lines, "pool", pool);
        saveCards(lines, "reward", pendingReward);
        saveCards(lines, "shop", shopOffer);
        lines.add("[map]");
        lines.addAll(map.save());
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
        int life = MAX_LIFE, won = 0, gold = STARTING_GOLD;
        boolean mulligan = false, over = false, victory = false, nodeDone = true;
        int row = -1, column = -1;
        List<String> mapLines = null;
        boolean inMap = false;
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
                inMap = line.equals("[map]");
                if (inMap) {
                    mapLines = new ArrayList<>();
                }
                switch (line) {
                    case "[commander]": target = run.commanders; break;
                    case "[lands]": target = run.lands; break;
                    case "[deck]": target = run.deck; break;
                    case "[flipped]": target = run.flipped; break;
                    case "[pool]": target = run.pool; break;
                    case "[reward]": target = run.pendingReward; break;
                    case "[shop]": target = run.shopOffer; break;
                    default: target = null; break;
                }
            } else if (inMap) {
                mapLines.add(line);
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
                    case "gold": gold = Integer.parseInt(value); break;
                    case "won": won = Integer.parseInt(value); break;
                    case "mulligan": mulligan = Boolean.parseBoolean(value); break;
                    case "over": over = Boolean.parseBoolean(value); break;
                    case "victory": victory = Boolean.parseBoolean(value); break;
                    case "row": row = Integer.parseInt(value); break;
                    case "column": column = Integer.parseInt(value); break;
                    case "nodeDone": nodeDone = Boolean.parseBoolean(value); break;
                    default: break;
                }
            }
        }
        if (run == null || run.commanders.isEmpty() || mapLines == null || mapLines.isEmpty()) {
            return null;
        }
        run.map = SpireMap.load(mapLines);
        run.victory = victory;
        run.row = row;
        run.column = column;
        run.nodeDone = nodeDone;
        run.life = life;
        run.gold = gold;
        run.fightsWon = won;
        run.mulliganUsed = mulligan;
        run.over = over;
        return run;
    }
}
