package forge.screens.spire;

import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import forge.card.CardDb;
import forge.card.CardEdition;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.item.PaperCard;
import forge.model.FModel;

/**
 * Makes sure the Commander Spire card scripts are in the card database.
 * On desktop they come from res/cardsfolder. On Android the res folder is downloaded from the
 * official Forge servers and lacks them, so the APK carries copies in assets/spire_cards
 * (see forge-gui-android/pom.xml) and they are added here, like Adventure adds its custom cards.
 */
public final class SpireCards {
    private static final String ASSET_DIR = "spire_cards/";
    private static final String[] FILES = {
        "c/commander_spire_rules.txt",
        "s/spire_basic_card.txt",
        "s/spire_giant_rat.txt",
        "s/spire_skeleton_guardian.txt",
        "s/spire_rat_king.txt",
    };

    private static boolean checked;

    private SpireCards() {
    }

    public static synchronized void ensureLoaded() {
        if (checked) {
            return;
        }
        checked = true;
        final CardDb db = FModel.getMagicDb().getCommonCards();
        final CardRules.Reader reader = new CardRules.Reader();
        for (String file : FILES) {
            final FileHandle handle = Gdx.files.internal(ASSET_DIR + file);
            if (!handle.exists()) {
                continue;
            }
            final List<String> lines = Arrays.asList(handle.readString("UTF-8").split("\\r?\\n"));
            reader.reset();
            final String baseName = file.substring(file.lastIndexOf('/') + 1, file.length() - ".txt".length());
            final CardRules rules = reader.readCard(lines, baseName);
            if (db.getUniqueByName(rules.getName()) != null) {
                continue;
            }
            rules.setCustom();
            db.addCard(new PaperCard(rules, CardEdition.UNKNOWN_CODE, CardRarity.Special));
        }
    }
}
