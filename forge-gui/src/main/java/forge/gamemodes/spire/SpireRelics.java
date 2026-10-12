package forge.gamemodes.spire;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import forge.item.PaperCard;

/**
 * Relics: kept for the whole run and in play from the start of every fight.
 * Spire relics are custom cards that sit in the command zone; imbued relics are real cards
 * that start each fight on the battlefield.
 */
public final class SpireRelics {
    public static final int OFFER_SIZE = 3;

    public static final List<String> SPIRE_RELICS = Collections.unmodifiableList(Arrays.asList(
            "Spire Anchor", "Spire Preparation Bag", "Spire Whetstone", "Spire Vampire Fang"));

    public static final List<String> IMBUED_RELICS = Collections.unmodifiableList(Arrays.asList(
            "Sol Ring", "Mind Stone", "Swiftfoot Boots", "Bonesplitter", "Glorious Anthem", "Phyrexian Arena"));

    private SpireRelics() {
    }

    /** Spire relics go to the command zone; everything else starts on the battlefield. */
    public static boolean startsInCommandZone(PaperCard relic) {
        return SPIRE_RELICS.contains(relic.getName());
    }

    /** Up to {@link #OFFER_SIZE} relics the run doesn't have yet; names {@code findCard} doesn't know are skipped. */
    public static List<PaperCard> rollOffer(Random rng, Collection<PaperCard> owned, Function<String, PaperCard> findCard) {
        List<String> names = new ArrayList<>(SPIRE_RELICS);
        names.addAll(IMBUED_RELICS);
        for (PaperCard card : owned) {
            names.remove(card.getName());
        }
        Collections.shuffle(names, rng);
        List<PaperCard> offer = new ArrayList<>();
        for (String name : names) {
            if (offer.size() >= OFFER_SIZE) {
                break;
            }
            PaperCard card = findCard.apply(name);
            if (card != null) {
                offer.add(card);
            }
        }
        return offer;
    }
}
