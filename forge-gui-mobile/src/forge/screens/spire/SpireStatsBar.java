package forge.screens.spire;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import forge.Graphics;
import forge.assets.FSkinFont;
import forge.assets.FSkinImage;
import forge.gamemodes.spire.SpireRun;
import forge.toolbox.FDisplayObject;
import forge.util.Utils;

/** The run at a glance: a life bar, then deck, pool and wins with their icons. */
public class SpireStatsBar extends FDisplayObject {
    private static final float PAD = Utils.scale(6);
    private static final FSkinFont FONT = FSkinFont.get(13);
    private static final FSkinFont TITLE_FONT = FSkinFont.get(15);
    private static final Color BACKDROP = new Color(0.07f, 0.06f, 0.10f, 0.85f);
    private static final Color BAR_BACK = new Color(0.25f, 0.08f, 0.08f, 1f);
    private static final Color BAR_LIFE = new Color(0.80f, 0.15f, 0.15f, 1f);
    private static final Color TEXT = new Color(0.95f, 0.93f, 0.88f, 1f);
    private static final Color SUBTLE = new Color(0.75f, 0.72f, 0.68f, 1f);

    private SpireRun run;

    public void setRun(SpireRun run0) {
        run = run0;
    }

    public static float getPreferredHeight() {
        return TITLE_FONT.getLineHeight() + FONT.getLineHeight() * 2 + PAD * 5;
    }

    @Override
    public void draw(Graphics g) {
        if (run == null) {
            return;
        }
        float w = getWidth();
        g.fillRoundRect(BACKDROP, 0, 0, w, getHeight(), Utils.scale(8));

        float y = PAD;
        g.drawText(run.getDeckName(), TITLE_FONT, TEXT, PAD, y, w - 2 * PAD, TITLE_FONT.getLineHeight(), false, Align.left, false);
        y += TITLE_FONT.getLineHeight() + PAD;

        // life bar
        float icon = FONT.getLineHeight() * 1.2f;
        g.drawImage(FSkinImage.QUEST_LIFE, PAD, y, icon, icon);
        float barX = PAD * 2 + icon;
        float barW = w - barX - PAD;
        g.fillRoundRect(BAR_BACK, barX, y, barW, icon, icon / 3);
        float fill = barW * run.getLife() / (float) SpireRun.MAX_LIFE;
        if (fill > 0) {
            g.fillRoundRect(BAR_LIFE, barX, y, fill, icon, icon / 3);
        }
        g.drawOutlinedText(run.getLife() + " / " + SpireRun.MAX_LIFE, FONT, Color.WHITE, Color.BLACK,
                barX, y, barW, icon, false, Align.center, true);
        y += icon + PAD;

        // deck, pool, wins
        float cell = (w - PAD) / 3;
        drawStat(g, FSkinImage.DECKLIST, run.getDeckSize() + " (" + run.getFlipped().size() + " flipped)", PAD, y, cell, icon);
        drawStat(g, FSkinImage.LIBRARY, run.getPool().size() + " in pool", PAD + cell, y, cell, icon);
        drawStat(g, FSkinImage.STAR_FILLED, run.getFightsWon() + " won", PAD + cell * 2, y, cell, icon);
    }

    private static void drawStat(Graphics g, FSkinImage image, String text, float x, float y, float w, float icon) {
        g.drawImage(image, x, y, icon, icon);
        g.drawText(text, FONT, SUBTLE, x + icon + PAD, y, w - icon - PAD * 2, icon, false, Align.left, true);
    }
}
