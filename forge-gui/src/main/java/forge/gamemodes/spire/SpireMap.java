package forge.gamemodes.spire;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The map of one act: rows of nodes from the first fight up to the boss.
 * Each row has up to {@link #WIDTH} nodes; a node connects to the nodes of the next row
 * in the same column or the columns right next to it.
 */
public class SpireMap {
    public static final int WIDTH = 3;
    /** Rows before the campfire row and the boss. */
    public static final int ROWS = 6;
    private static final double CAMPFIRE_CHANCE = 0.25;

    public enum NodeType {
        FIGHT("Fight"), CAMPFIRE("Campfire"), BOSS("Boss");

        public final String label;
        NodeType(String label) { this.label = label; }
    }

    public enum Monster {
        GIANT_RAT("Giant Rat", "Spire Giant Rat", 10),
        SKELETON_GUARDIAN("Skeleton Guardian", "Spire Skeleton Guardian", 14),
        RAT_KING("Rat King", "Spire Rat King", 40);

        public final String label;
        public final String cardName;
        public final int life;
        Monster(String label, String cardName, int life) {
            this.label = label;
            this.cardName = cardName;
            this.life = life;
        }
    }

    public static class Node {
        public final int row;
        public final int column;
        public final NodeType type;
        /** The monster for fight and boss nodes, null for a campfire. */
        public final Monster monster;

        Node(int row, int column, NodeType type, Monster monster) {
            this.row = row;
            this.column = column;
            this.type = type;
            this.monster = monster;
        }

        @Override
        public String toString() {
            String text = monster == null ? type.label : type.label + ": " + monster.label + " (" + monster.life + " life)";
            return type == NodeType.BOSS ? text : (char) ('A' + column) + ": " + text;
        }
    }

    private final List<List<Node>> rows = new ArrayList<>();

    private SpireMap() {
    }

    /**
     * Row 0 is all fights, rows 1 to ROWS-1 mix fights and campfires, then a campfire row and the boss.
     */
    public static SpireMap generate(Random rng) {
        SpireMap map = new SpireMap();
        Monster[] fights = { Monster.GIANT_RAT, Monster.SKELETON_GUARDIAN };
        for (int r = 0; r < ROWS; r++) {
            List<Node> row = new ArrayList<>();
            for (int c = 0; c < WIDTH; c++) {
                boolean campfire = r > 0 && rng.nextDouble() < CAMPFIRE_CHANCE;
                row.add(campfire ? new Node(r, c, NodeType.CAMPFIRE, null)
                        : new Node(r, c, NodeType.FIGHT, fights[rng.nextInt(fights.length)]));
            }
            map.rows.add(row);
        }
        List<Node> rest = new ArrayList<>();
        for (int c = 0; c < WIDTH; c++) {
            rest.add(new Node(ROWS, c, NodeType.CAMPFIRE, null));
        }
        map.rows.add(rest);
        map.rows.add(Collections.singletonList(new Node(ROWS + 1, WIDTH / 2, NodeType.BOSS, Monster.RAT_KING)));
        return map;
    }

    public int getRowCount() { return rows.size(); }

    public List<Node> getRow(int row) { return Collections.unmodifiableList(rows.get(row)); }

    public Node getNode(int row, int column) {
        for (Node node : rows.get(row)) {
            if (node.column == column) {
                return node;
            }
        }
        return null;
    }

    /** The nodes the player may pick next; from before the first row ({@code current} null) that is all of row 0. */
    public List<Node> getNextChoices(Node current) {
        int next = current == null ? 0 : current.row + 1;
        if (next >= rows.size()) {
            return Collections.emptyList();
        }
        List<Node> choices = new ArrayList<>();
        for (Node node : rows.get(next)) {
            if (current == null || rows.get(next).size() == 1 || Math.abs(node.column - current.column) <= 1) {
                choices.add(node);
            }
        }
        return choices;
    }

    // Saving: one line per row, nodes separated by spaces as column:TYPE[:MONSTER].

    public List<String> save() {
        List<String> lines = new ArrayList<>();
        for (List<Node> row : rows) {
            StringBuilder sb = new StringBuilder();
            for (Node node : row) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(node.column).append(':').append(node.type.name());
                if (node.monster != null) {
                    sb.append(':').append(node.monster.name());
                }
            }
            lines.add(sb.toString());
        }
        return lines;
    }

    public static SpireMap load(List<String> lines) {
        SpireMap map = new SpireMap();
        for (int r = 0; r < lines.size(); r++) {
            List<Node> row = new ArrayList<>();
            for (String token : lines.get(r).trim().split(" ")) {
                String[] parts = token.split(":");
                if (parts.length < 2) {
                    continue;
                }
                Monster monster = parts.length > 2 ? Monster.valueOf(parts[2]) : null;
                row.add(new Node(r, Integer.parseInt(parts[0]), NodeType.valueOf(parts[1]), monster));
            }
            map.rows.add(row);
        }
        return map;
    }
}
