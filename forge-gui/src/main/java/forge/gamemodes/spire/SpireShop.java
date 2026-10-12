package forge.gamemodes.spire;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import forge.card.CardRarity;
import forge.item.PaperCard;

/**
 * The shop: a few colorless cards that fit most commander decks, bought with gold won in fights.
 */
public final class SpireShop {
    public static final int OFFER_SIZE = 5;

    /** Colorless creatures, equipment and artifacts that work in most decks. */
    public static final List<String> CARD_NAMES = Collections.unmodifiableList(Arrays.asList(
            // creatures
            "Solemn Simulacrum", "Burnished Hart", "Pilgrim's Eye", "Walking Ballista", "Hangarback Walker",
            "Phyrexian Revoker", "Duplicant", "Meteor Golem", "Steel Hellkite", "Myr Battlesphere",
            "Wurmcoil Engine", "Precursor Golem",
            // equipment
            "Swiftfoot Boots", "Lightning Greaves", "Bonesplitter", "Loxodon Warhammer", "Shadowspear",
            "Whispersilk Cloak", "Skullclamp", "Sword of Fire and Ice", "Sword of Feast and Famine", "Batterskull",
            // other artifacts
            "Sol Ring", "Arcane Signet", "Mind Stone", "Thought Vessel", "Commander's Sphere", "Wayfarer's Bauble",
            "Everflowing Chalice", "Sensei's Divining Top", "Mind's Eye", "Ichor Wellspring", "Pristine Talisman",
            "Ratchet Bomb", "Nevinyrral's Disk", "Oblivion Stone", "Spine of Ish Sah", "Sorcerous Spyglass"));

    private SpireShop() {
    }

    public static int price(PaperCard card) {
        CardRarity rarity = card.getRarity();
        if (rarity == CardRarity.MythicRare) {
            return 150;
        }
        if (rarity == CardRarity.Rare) {
            return 100;
        }
        if (rarity == CardRarity.Uncommon) {
            return 75;
        }
        return 50;
    }

    /** Picks {@link #OFFER_SIZE} different cards; names {@code findCard} doesn't know are skipped. */
    public static List<PaperCard> rollOffer(Random rng, Function<String, PaperCard> findCard) {
        List<String> names = new ArrayList<>(CARD_NAMES);
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
