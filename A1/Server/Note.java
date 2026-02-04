/**
 * Represents a single note on the bulletin board.
 * Each note has a position, color, message content, and derived pin status.
 */
public class Note {
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final String color;
    private final String message;

    /**
     * Constructs a new Note.
     *
     * @param x       X-coordinate of upper-left corner
     * @param y       Y-coordinate of upper-left corner
     * @param width   Width of the note (from server config)
     * @param height  Height of the note (from server config)
     * @param color   Color of the note
     * @param message Text content of the note
     */
    public Note(int x, int y, int width, int height, String color, String message) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.color = color;
        this.message = message;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getColor() {
        return color;
    }

    public String getMessage() {
        return message;
    }

    /**
     * Checks if the given point (px, py) is inside this note's boundaries.
     * A point is inside if: x <= px < x + width AND y <= py < y + height
     *
     * @param px X-coordinate of the point
     * @param py Y-coordinate of the point
     * @return true if the point is within this note's boundaries
     */
    public boolean containsPoint(int px, int py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    /**
     * Checks if this note completely overlaps with another note.
     * Complete overlap means both notes share the exact same upper-left corner.
     * Since all notes have the same fixed dimensions, same (x, y) means same rectangle.
     *
     * @param other The other note to compare with
     * @return true if this note completely overlaps with the other
     */
    public boolean completelyOverlaps(Note other) {
        return this.x == other.x && this.y == other.y;
    }

    @Override
    public String toString() {
        return "NOTE " + x + " " + y + " " + color + " " + message;
    }
}
