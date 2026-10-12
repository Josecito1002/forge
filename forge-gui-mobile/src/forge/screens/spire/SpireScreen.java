package forge.screens.spire;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.badlogic.gdx.utils.Align;

import forge.assets.FSkinFont;
import forge.deck.Deck;
import forge.deck.DeckProxy;
import forge.deck.DeckSection;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.match.HostedMatch;
import forge.gamemodes.spire.SpireMap;
import forge.gamemodes.spire.SpireRun;
import forge.gui.GuiBase;
import forge.item.IPaperCard;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;
import forge.model.FModel;
import forge.player.GamePlayerUtil;
import forge.screens.LaunchScreen;
import forge.screens.LoadingOverlay;
import forge.screens.home.NewGameMenu;
import forge.toolbox.FButton;
import forge.toolbox.FLabel;
import forge.toolbox.FOptionPane;
import forge.toolbox.FTextArea;
import forge.toolbox.GuiChoose;
import forge.util.FileUtil;
import forge.util.Utils;

/**
 * Commander Spire: a run of fights with a commander deck that grows with rewards between fights.
 * Each match is hosted directly (like PuzzleScreen) instead of through Adventure's DuelScene.
 * The run is saved after every step so it survives closing the app.
 */
public class SpireScreen extends LaunchScreen {
    private static final float PADDING = Utils.scale(10);
    private static final int HAND_SIZE = 5;
    private static final String RULES_CARD = "Commander Spire Rules";
    private static final String BASIC_CARD = "Spire Basic Card";
    private static final String TITLE = "Commander Spire";
    private static final String SAVE_FILE = ForgeConstants.USER_DIR + "commander_spire_run.txt";

    private static final String INTRO =
            "Commander Spire\n\n"
            + "Pick a commander deck. Your run starts with 12 random non-land cards from it "
            + "(one mulligan: swap a card for the top of the pool) and you flip 4 of them: "
            + "in fights a flipped card is a Spire Basic Card ({1}: 2 damage to a monster, "
            + "or prevent the next 3 damage to a player).\n\n"
            + "Each fight starts with 3 lands on the battlefield and the rest in a separate pile, "
            + "your commander in the command zone, and you draw up to 5 each turn. "
            + "Life (40 max) carries over between fights. After each win, take 1 of 3 cards from your pool, or none.\n\n"
            + "Climb the act's map: fights, campfires (heal 12, or flip / unflip a card) and the Rat King at the top. "
            + "Beat him to win the run.";

    private static SpireRun run;
    private static boolean loaded;

    private final Random rng = new Random();
    private final FTextArea lblDesc = add(new FTextArea(false, INTRO));
    private final SpireStatsBar statsBar = add(new SpireStatsBar());
    private final SpireMapView mapView = add(new SpireMapView(this::onNodeTapped));
    private final FLabel lblHint = add(new FLabel.Builder().font(FSkinFont.get(13)).align(Align.center).build());
    private final FButton btnDeck = add(new FButton("View deck", e -> showDeck()));
    private final FButton btnAbandon = add(new FButton("Abandon run", e -> abandonRun()));

    public SpireScreen() {
        super(null, NewGameMenu.getMenu());
        lblDesc.setFont(FSkinFont.get(12));
        lblDesc.setTextColor(FLabel.getInlineLabelColor());
    }

    @Override
    public void onActivate() {
        super.onActivate();
        if (!loaded) {
            loaded = true;
            run = loadRun();
        }
        if (run != null && run.isVictory() && !run.hasPendingReward()) {
            showVictory();
        } else if (run != null && run.isOver()) {
            final int won = run.getFightsWon();
            clearRun();
            FOptionPane.showMessageDialog("You fell in the Spire after winning " + won + " fight(s). The run is over.", TITLE);
        } else if (run != null && run.hasPendingReward()) {
            chooseReward();
        }
        refresh();
    }

    private void refresh() {
        final boolean inRun = run != null;
        lblDesc.setVisible(!inRun);
        statsBar.setVisible(inRun);
        mapView.setVisible(inRun);
        lblHint.setVisible(inRun);
        btnDeck.setVisible(inRun);
        btnAbandon.setVisible(inRun);
        // in a run the map is the only way forward: tap a node, no Start button
        btnStart.setVisible(!inRun);
        statsBar.setRun(run);
        if (inRun) {
            final SpireMap.Monster fight = run.getPendingFight();
            if (fight != null) {
                lblHint.setText("Tap your node to fight the " + fight.label + ".");
            } else if (run.isAtCampfire()) {
                lblHint.setText("Tap the campfire to rest.");
            } else {
                lblHint.setText("Tap a glowing node to choose your path.");
            }
        }
        revalidate();
        mapView.setRun(run);
    }

    /** Tapping a glowing node moves there; tapping the current node continues what waits on it. */
    private void onNodeTapped(final SpireMap.Node node) {
        if (run == null) {
            return;
        }
        if (run.getNextChoices().contains(node)) {
            run.moveTo(node);
            saveRun();
            refresh();
            advance();
        } else if (node == run.getCurrentNode() && !run.isNodeDone()) {
            advance();
        }
    }

    private void showDeck() {
        final List<String> lines = new ArrayList<>();
        for (PaperCard card : run.getCommanders()) {
            lines.add(card.getName() + " (commander)");
        }
        for (PaperCard card : run.getDeck()) {
            lines.add(card.getName());
        }
        for (PaperCard card : run.getFlipped()) {
            lines.add(card.getName() + " (flipped: Spire Basic Card)");
        }
        lines.add(run.getLands().size() + " lands in the land pile");
        // reveal() passes a null callback, which crashes when the list is closed
        GuiChoose.getChoices("Your deck", -1, -1, lines, ignored -> { });
    }

    @Override
    protected void doLayoutAboveBtnStart(float startY, float width, float height) {
        float y = startY + PADDING;
        float w = width - 2 * PADDING;
        if (run == null) {
            lblDesc.setBounds(PADDING, y, w, height - y - PADDING);
            return;
        }
        float statsHeight = SpireStatsBar.getPreferredHeight();
        statsBar.setBounds(PADDING, y, w, statsHeight);
        y += statsHeight + PADDING;

        float buttonHeight = Utils.AVG_FINGER_HEIGHT * 0.8f;
        // the hidden Start button's space goes to the map
        float bottom = height + btnStart.getHeight() + PADDING;
        float half = (w - PADDING) / 2;
        btnDeck.setBounds(PADDING, bottom - buttonHeight, half, buttonHeight);
        btnAbandon.setBounds(PADDING * 2 + half, bottom - buttonHeight, half, buttonHeight);
        bottom -= buttonHeight + PADDING;

        float hintHeight = lblHint.getAutoSizeBounds().height;
        lblHint.setBounds(PADDING, bottom - hintHeight, w, hintHeight);
        bottom -= hintHeight + PADDING / 2;

        mapView.setBounds(PADDING, y, w, bottom - y);
    }

    @Override
    protected void startMatch() {
        if (run != null) {
            advance();
            return;
        }
        // Your own decks first (Commander decks, and Constructed decks that have a commander), then the precons.
        final List<DeckProxy> own = new ArrayList<>();
        for (DeckProxy d : DeckProxy.getAllCommanderDecks()) {
            own.add(d);
        }
        for (DeckProxy d : DeckProxy.getAllConstructedDecks(deck -> !deck.get(DeckSection.Commander).isEmpty())) {
            own.add(d);
        }
        own.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        final List<DeckProxy> precons = new ArrayList<>();
        for (DeckProxy d : DeckProxy.getAllCommanderPreconDecks()) {
            precons.add(d);
        }
        precons.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        final Map<String, DeckProxy> byLabel = new LinkedHashMap<>();
        for (DeckProxy d : own) {
            byLabel.put("[Mine] " + d.getName(), d);
        }
        for (DeckProxy d : precons) {
            byLabel.putIfAbsent(d.toString(), d);
        }
        final String title = own.isEmpty()
                ? "Choose a commander deck (none of yours found: make one in Deck Manager > Commander)"
                : "Choose a commander deck ([Mine] = your decks)";

        GuiChoose.oneOrNone(title, new ArrayList<>(byLabel.keySet()), label -> {
            final DeckProxy chosen = label == null ? null : byLabel.get(label);
            if (chosen == null) {
                return;
            }
            final Deck source = chosen.getDeck();
            if (source == null || source.get(DeckSection.Commander).isEmpty()) {
                FOptionPane.showErrorDialog("That deck has no commander.", TITLE);
                return;
            }
            setUpRun(SpireRun.start(source, rng));
        });
    }

    /** Mulligan, then flip 4 cards; the run only becomes current once both choices are made. */
    private void setUpRun(final SpireRun newRun) {
        GuiChoose.oneOrNone("Mulligan: swap one card for the top of your pool? (Cancel to keep all)",
                new ArrayList<>(newRun.getDeck()), out -> {
            if (out != null) {
                newRun.mulligan(out);
            }
            final List<PaperCard> cards = new ArrayList<>(newRun.getDeck());
            final int count = Math.min(SpireRun.FLIPPED_COUNT, cards.size());
            GuiChoose.many("Flip " + count + " cards", "Flipped cards are Spire Basic Cards in fights",
                    count, count, cards, null, flipped -> {
                newRun.flip(flipped);
                run = newRun;
                saveRun();
                refresh();
                advance();
            });
        });
    }

    /** The next step of the run: the pending fight, the campfire, or picking the next node on the map. */
    private void advance() {
        final SpireMap.Monster fight = run.getPendingFight();
        if (fight != null) {
            LoadingOverlay.show("Climbing the Spire...", true, () -> hostFight(fight));
            return;
        }
        if (run.isAtCampfire()) {
            useCampfire();
            return;
        }
        // otherwise the player picks the next node by tapping it on the map
        refresh();
    }

    private void useCampfire() {
        final String rest = "Rest: heal " + SpireRun.CAMPFIRE_HEAL + " (life " + run.getLife() + " -> "
                + Math.min(SpireRun.MAX_LIFE, run.getLife() + SpireRun.CAMPFIRE_HEAL) + ")";
        final String flip = "Flip a card (it becomes a Spire Basic Card)";
        final String unflip = "Turn a flipped card back to its normal side";
        final List<String> options = new ArrayList<>();
        options.add(rest);
        if (!run.getDeck().isEmpty()) {
            options.add(flip);
        }
        if (!run.getFlipped().isEmpty()) {
            options.add(unflip);
        }
        GuiChoose.oneOrNone("Campfire", options, option -> {
            if (option == null) {
                return;
            }
            if (option.equals(rest)) {
                run.rest();
                campfireDone();
            } else if (option.equals(flip)) {
                GuiChoose.oneOrNone("Flip which card?", new ArrayList<>(run.getDeck()), card -> {
                    if (card == null) {
                        useCampfire();
                        return;
                    }
                    run.flipAtCampfire(card);
                    campfireDone();
                });
            } else {
                GuiChoose.oneOrNone("Turn which card back?", new ArrayList<>(run.getFlipped()), card -> {
                    if (card == null) {
                        useCampfire();
                        return;
                    }
                    run.unflipAtCampfire(card);
                    campfireDone();
                });
            }
        });
    }

    private void campfireDone() {
        saveRun();
        refresh();
    }

    private void chooseReward() {
        final String title = run.isVictory()
                ? "The Rat King falls! Take one card for your deck (Cancel to take none)"
                : "Victory! Take one card for your deck (Cancel to take none)";
        GuiChoose.oneOrNone(title, new ArrayList<>(run.getPendingReward()), chosen -> {
            run.takeReward(chosen);
            saveRun();
            if (run.isVictory()) {
                showVictory();
            }
            refresh();
        });
    }

    private void showVictory() {
        final int won = run.getFightsWon();
        clearRun();
        refresh();
        FOptionPane.showMessageDialog("You beat the Rat King and cleared the act after " + won + " fight(s). The run is won!", TITLE);
    }

    private void abandonRun() {
        FOptionPane.showConfirmDialog("Abandon this run? It can't be resumed.", TITLE, false, yes -> {
            if (yes) {
                clearRun();
                refresh();
            }
        });
    }

    private void hostFight(final SpireMap.Monster enemy) {
        SpireCards.ensureLoaded();
        final PaperCard monsterCard = FModel.getMagicDb().getCommonCards().getUniqueByName(enemy.cardName);
        final PaperCard spireRules = FModel.getMagicDb().getCommonCards().getUniqueByName(RULES_CARD);
        final PaperCard basic = FModel.getMagicDb().getCommonCards().getUniqueByName(BASIC_CARD);
        if (monsterCard == null || spireRules == null || basic == null) {
            final List<String> missing = new ArrayList<>();
            if (monsterCard == null) {
                missing.add(enemy.cardName);
            }
            if (spireRules == null) {
                missing.add(RULES_CARD);
            }
            if (basic == null) {
                missing.add(BASIC_CARD);
            }
            FOptionPane.showErrorDialog("Commander Spire card scripts were not found: " + String.join(", ", missing)
                    + (SpireCards.getError() == null ? "" : "\n" + SpireCards.getError()), TITLE);
            return;
        }
        final SpireRun current = run;
        final SpireRun.Fight fight = current.prepareFight(basic, rng);

        // without a player name set the result screen reads "Human won!" and ": 1 ... : 0"
        if (GamePlayerUtil.getGuiPlayer().getName() == null || GamePlayerUtil.getGuiPlayer().getName().trim().isEmpty()) {
            GamePlayerUtil.getGuiPlayer().setName("You");
        }
        final Set<GameType> variants = EnumSet.of(GameType.Commander);
        final RegisteredPlayer human = RegisteredPlayer.forVariants(2, variants, fight.deck, null, false, null, null)
                .setPlayer(GamePlayerUtil.getGuiPlayer());
        human.setStartingLife(current.getLife());
        human.setStartingHand(HAND_SIZE);
        human.addExtraCardsOnBattlefield(new ArrayList<IPaperCard>(fight.startingLands));
        final List<IPaperCard> rulesCards = new ArrayList<>();
        rulesCards.add(spireRules);
        human.addExtraCardsInCommandZone(rulesCards);

        // The monster has no library: it is one creature on its battlefield whose toughness is its life.
        final RegisteredPlayer monster = new RegisteredPlayer(new Deck(enemy.label))
                .setPlayer(GamePlayerUtil.createAiPlayer(enemy.label, 0));
        monster.setStartingLife(enemy.life);
        monster.setStartingHand(0);
        final List<IPaperCard> monsterCards = new ArrayList<>();
        monsterCards.add(monsterCard);
        monster.addExtraCardsOnBattlefield(monsterCards);

        final List<RegisteredPlayer> players = new ArrayList<>();
        players.add(human);
        players.add(monster);

        final GameRules rules = new GameRules(GameType.Commander);
        rules.setGamesPerMatch(1);
        rules.setMulligansAllowed(false);
        rules.setLandPileInSideboard(true);
        final HostedMatch hostedMatch = GuiBase.getInterface().hostMatch();
        // Runs on the game thread when the fight ends; the reward is offered back on this screen.
        hostedMatch.setEndGameHook(() -> recordResult(current, hostedMatch.getGame()));
        hostedMatch.startMatch(rules, variants, players, human, GuiBase.getInterface().getNewGuiGame());
    }

    private static void recordResult(final SpireRun current, final Game game) {
        if (game == null || current != run) {
            return;
        }
        if (game.getOutcome() != null && game.getOutcome().isWinner(GamePlayerUtil.getGuiPlayer())) {
            int life = 0;
            for (Player p : game.getRegisteredPlayers()) {
                if (p.getLobbyPlayer() == GamePlayerUtil.getGuiPlayer()) {
                    life = p.getLife();
                }
            }
            current.recordWin(life);
        } else {
            current.recordLoss();
        }
        saveRun();
    }

    private static SpireRun loadRun() {
        if (!FileUtil.doesFileExist(SAVE_FILE)) {
            return null;
        }
        try {
            return SpireRun.load(FileUtil.readFile(SAVE_FILE), request -> {
                PaperCard card = FModel.getMagicDb().getCommonCards().getCard(request);
                if (card == null) {
                    card = FModel.getMagicDb().getCommonCards().getUniqueByName(request.split("\\|")[0]);
                }
                return card;
            });
        } catch (RuntimeException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void saveRun() {
        if (run != null) {
            FileUtil.writeFile(SAVE_FILE, run.save());
        }
    }

    private static void clearRun() {
        run = null;
        FileUtil.deleteFile(SAVE_FILE);
    }
}
