package forge.screens.spire;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import forge.Forge;
import forge.assets.FSkinFont;
import forge.deck.Deck;
import forge.deck.DeckProxy;
import forge.deck.DeckSection;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.match.HostedMatch;
import forge.gui.GuiBase;
import forge.item.IPaperCard;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.player.GamePlayerUtil;
import forge.screens.LaunchScreen;
import forge.screens.LoadingOverlay;
import forge.screens.home.NewGameMenu;
import forge.toolbox.FLabel;
import forge.toolbox.FOptionPane;
import forge.toolbox.FTextArea;
import forge.toolbox.GuiChoose;
import forge.util.Utils;

/**
 * Commander Spire, day 2: one fight against the Spire Giant Rat using a commander deck.
 * The match is hosted directly (like PuzzleScreen) instead of through Adventure's DuelScene.
 */
public class SpireScreen extends LaunchScreen {
    private static final float PADDING = Utils.scale(10);
    private static final int STARTING_DECK_SIZE = 12;
    private static final int STARTING_LANDS = 3;
    private static final int PLAYER_LIFE = 40;
    private static final int RAT_LIFE = 10;
    private static final String RAT_CARD = "Spire Giant Rat";

    private final FTextArea lblDesc = add(new FTextArea(false,
            "Commander Spire\n\n"
            + "Pick a commander deck. You start with 12 random non-land cards from it, "
            + "3 lands on the battlefield and your commander in the command zone, "
            + "and fight the Giant Rat (10 life)."));

    public SpireScreen() {
        super(null, NewGameMenu.getMenu());
        lblDesc.setFont(FSkinFont.get(12));
        lblDesc.setTextColor(FLabel.getInlineLabelColor());
    }

    @Override
    protected void doLayoutAboveBtnStart(float startY, float width, float height) {
        float y = startY + PADDING;
        lblDesc.setBounds(PADDING, y, width - 2 * PADDING, height - y - PADDING);
    }

    @Override
    protected void startMatch() {
        final List<DeckProxy> decks = new ArrayList<>();
        for (DeckProxy d : DeckProxy.getAllCommanderDecks()) {
            decks.add(d);
        }
        for (DeckProxy d : DeckProxy.getAllCommanderPreconDecks()) {
            decks.add(d);
        }
        Collections.sort(decks, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        GuiChoose.oneOrNone("Choose a commander deck", decks, chosen -> {
            if (chosen == null) {
                return;
            }
            final Deck source = chosen.getDeck();
            if (source == null || source.get(DeckSection.Commander).isEmpty()) {
                FOptionPane.showErrorDialog("That deck has no commander.", "Commander Spire");
                return;
            }
            LoadingOverlay.show("Entering the Spire...", true, () -> hostFight(source));
        });
    }

    private void hostFight(final Deck source) {
        final List<PaperCard> lands = new ArrayList<>();
        final List<PaperCard> pool = new ArrayList<>();
        for (PaperCard card : source.getMain().toFlatList()) {
            (card.getRules().getType().isLand() ? lands : pool).add(card);
        }
        Collections.shuffle(pool);
        final List<PaperCard> hand12 = new ArrayList<>(pool.subList(0, Math.min(STARTING_DECK_SIZE, pool.size())));

        final Deck spireDeck = new Deck("Commander Spire");
        spireDeck.getOrCreate(DeckSection.Commander).addAll(source.get(DeckSection.Commander));
        spireDeck.getOrCreate(DeckSection.Main).add(hand12);

        // The 3 starting lands come from the deck's own lands; they are not part of the 12-card deck.
        final List<IPaperCard> startLands = new ArrayList<>();
        for (int i = 0; i < STARTING_LANDS && !lands.isEmpty(); i++) {
            startLands.add(lands.get(i % lands.size()));
        }

        final PaperCard rat = FModel.getMagicDb().getCommonCards().getUniqueByName(RAT_CARD);
        if (rat == null) {
            FOptionPane.showErrorDialog("Card script '" + RAT_CARD + "' was not found.", "Commander Spire");
            return;
        }

        final Set<GameType> variants = EnumSet.of(GameType.Commander);
        final RegisteredPlayer human = RegisteredPlayer.forVariants(2, variants, spireDeck, null, false, null, null)
                .setPlayer(GamePlayerUtil.getGuiPlayer());
        human.setStartingLife(PLAYER_LIFE);
        human.addExtraCardsOnBattlefield(startLands);

        // The monster has no library: its whole behaviour lives in a command-zone card.
        final RegisteredPlayer monster = new RegisteredPlayer(new Deck("Giant Rat"))
                .setPlayer(GamePlayerUtil.createAiPlayer("Giant Rat", 0));
        monster.setStartingLife(RAT_LIFE);
        monster.setStartingHand(0);
        final List<IPaperCard> ratCards = new ArrayList<>();
        ratCards.add(rat);
        monster.addExtraCardsInCommandZone(ratCards);

        final List<RegisteredPlayer> players = new ArrayList<>();
        players.add(human);
        players.add(monster);

        final GameRules rules = new GameRules(GameType.Commander);
        rules.setGamesPerMatch(1);
        final HostedMatch hostedMatch = GuiBase.getInterface().hostMatch();
        hostedMatch.startMatch(rules, variants, players, human, GuiBase.getInterface().getNewGuiGame());
    }
}
