package forge.screens.spire;

import java.util.List;
import java.util.function.Consumer;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import forge.Graphics;
import forge.assets.FSkinFont;
import forge.assets.FSkinImage;
import forge.gamemodes.spire.SpireMap;
import forge.gamemodes.spire.SpireRun;
import forge.toolbox.FDisplayObject;
import forge.toolbox.FScrollPane;
import forge.util.Utils;

/**
 * The act map drawn as a tower of nodes, boss on top. Paths join each node to the nodes it can reach.
 * The current node has a white ring and the nodes you can move to glow gold; tapping one picks it.
 */
public class SpireMapView extends FScrollPane {
    private static final float ROW_HEIGHT = Utils.scale(78);
    private static final float NODE_RADIUS = Utils.scale(19);
    private static final float BOSS_RADIUS = Utils.scale(28);
    private static final float LINE = Utils.scale(2);
    private static final FSkinFont LABEL_FONT = FSkinFont.get(10);
    private static final FSkinFont HP_FONT = FSkinFont.get(9);

    private static final Color BACKDROP = new Color(0.07f, 0.06f, 0.10f, 0.85f);
    private static final Color FIGHT = new Color(0.62f, 0.18f, 0.16f, 1f);
    private static final Color ELITE_FIGHT = new Color(0.45f, 0.20f, 0.45f, 1f);
    private static final Color CAMPFIRE = new Color(0.85f, 0.50f, 0.12f, 1f);
    private static final Color BOSS = new Color(0.40f, 0.10f, 0.45f, 1f);
    private static final Color PATH = new Color(0.55f, 0.55f, 0.60f, 0.35f);
    private static final Color PATH_NEXT = new Color(1f, 0.82f, 0.30f, 0.9f);
    private static final Color GLOW = new Color(1f, 0.82f, 0.30f, 1f);
    private static final Color GLOW_FAINT = new Color(1f, 0.82f, 0.30f, 0.25f);
    private static final Color DIM = new Color(0f, 0f, 0f, 0.55f);
    private static final Color LABEL = new Color(0.92f, 0.90f, 0.86f, 1f);
    private static final Color HP = new Color(1f, 0.55f, 0.50f, 1f);

    private final MapCanvas canvas = add(new MapCanvas());
    private final Consumer<SpireMap.Node> onNodeTapped;
    private SpireRun run;

    public SpireMapView(Consumer<SpireMap.Node> onNodeTapped) {
        this.onNodeTapped = onNodeTapped;
    }

    public void setRun(SpireRun run0) {
        run = run0;
        revalidate();
        scrollToCurrentRow();
    }

    /** Keeps the rows around the player in view: row 1 at the bottom, the boss at the top. */
    public void scrollToCurrentRow() {
        if (run == null) {
            return;
        }
        SpireMap.Node current = run.getCurrentNode();
        int row = current == null ? 0 : current.row;
        float y = canvas.rowCenterY(row) - getHeight() * 0.6f;
        setScrollTop(Math.max(0, Math.min(getMaxScrollTop(), y)));
    }

    @Override
    protected ScrollBounds layoutAndGetScrollBounds(float visibleWidth, float visibleHeight) {
        int rows = run == null ? 0 : run.getMap().getRowCount();
        float height = Math.max(visibleHeight, rows * ROW_HEIGHT + ROW_HEIGHT * 0.5f);
        canvas.setBounds(0, 0, visibleWidth, height);
        return new ScrollBounds(visibleWidth, height);
    }

    @Override
    protected void drawBackground(Graphics g) {
        g.fillRoundRect(BACKDROP, 0, 0, getWidth(), getHeight(), Utils.scale(8));
    }

    private class MapCanvas extends FDisplayObject {
        float rowCenterY(int row) {
            // row 0 at the bottom
            return getHeight() - ROW_HEIGHT * 0.75f - row * ROW_HEIGHT;
        }

        float columnX(SpireMap.Node node) {
            float colWidth = getWidth() / SpireMap.WIDTH;
            return colWidth * node.column + colWidth / 2;
        }

        float radius(SpireMap.Node node) {
            return node.type == SpireMap.NodeType.BOSS ? BOSS_RADIUS : NODE_RADIUS;
        }

        @Override
        public void draw(Graphics g) {
            if (run == null) {
                return;
            }
            SpireMap map = run.getMap();
            SpireMap.Node current = run.getCurrentNode();
            List<SpireMap.Node> next = run.getNextChoices();

            // paths first, so the nodes sit on top of them
            for (int r = 0; r < map.getRowCount() - 1; r++) {
                for (SpireMap.Node from : map.getRow(r)) {
                    for (SpireMap.Node to : map.getNextChoices(from)) {
                        boolean highlighted = from == current && next.contains(to);
                        g.drawLine(highlighted ? LINE * 2 : LINE, highlighted ? PATH_NEXT : PATH,
                                columnX(from), rowCenterY(from.row), columnX(to), rowCenterY(to.row));
                    }
                }
            }

            for (int r = 0; r < map.getRowCount(); r++) {
                for (SpireMap.Node node : map.getRow(r)) {
                    // nodes at or below the player's row that it can't stand on any more are dimmed
                    boolean behind = current != null && node != current && r <= current.row;
                    drawNode(g, node, node == current, next.contains(node), behind);
                }
            }
        }

        private void drawNode(Graphics g, SpireMap.Node node, boolean isCurrent, boolean reachable, boolean behind) {
            float x = columnX(node);
            float y = rowCenterY(node.row);
            float rad = radius(node);

            if (reachable) {
                g.fillCircle(GLOW_FAINT, x, y, rad * 1.45f);
                g.drawCircle(LINE * 1.5f, GLOW, x, y, rad + LINE * 2);
            }
            g.fillCircle(nodeColor(node), x, y, rad);
            float icon = rad * 1.2f;
            g.drawImage(nodeIcon(node), x - icon / 2, y - icon / 2, icon, icon);
            if (isCurrent) {
                g.drawCircle(LINE * 2, Color.WHITE, x, y, rad + LINE * 2);
            }
            if (behind) {
                g.fillCircle(DIM, x, y, rad);
            }

            String label = node.monster == null ? "Campfire" : node.monster.label;
            float labelWidth = getWidth() / SpireMap.WIDTH;
            float labelY = y + rad + Utils.scale(1);
            g.drawOutlinedText(label, LABEL_FONT, LABEL, Color.BLACK, x - labelWidth / 2, labelY,
                    labelWidth, LABEL_FONT.getLineHeight(), false, Align.center, false);
            if (node.monster != null) {
                g.drawOutlinedText(node.monster.life + " HP", HP_FONT, HP, Color.BLACK, x - labelWidth / 2,
                        labelY + LABEL_FONT.getLineHeight(), labelWidth, HP_FONT.getLineHeight(), false, Align.center, false);
            }
        }

        private Color nodeColor(SpireMap.Node node) {
            switch (node.type) {
                case BOSS: return BOSS;
                case CAMPFIRE: return CAMPFIRE;
                default: return node.monster == SpireMap.Monster.SKELETON_GUARDIAN ? ELITE_FIGHT : FIGHT;
            }
        }

        private FSkinImage nodeIcon(SpireMap.Node node) {
            switch (node.type) {
                case BOSS: return FSkinImage.QUEST_BIG_AXE;
                case CAMPFIRE: return FSkinImage.QUEST_BIG_BREW;
                default: return node.monster == SpireMap.Monster.SKELETON_GUARDIAN
                        ? FSkinImage.QUEST_BIG_SHIELD : FSkinImage.QUEST_BIG_SWORD;
            }
        }

        @Override
        public boolean tap(float x, float y, int count) {
            if (run == null) {
                return false;
            }
            SpireMap map = run.getMap();
            for (int r = 0; r < map.getRowCount(); r++) {
                for (SpireMap.Node node : map.getRow(r)) {
                    float dx = x - columnX(node), dy = y - rowCenterY(node.row);
                    float hit = radius(node) * 1.5f;
                    if (dx * dx + dy * dy <= hit * hit) {
                        onNodeTapped.accept(node);
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
