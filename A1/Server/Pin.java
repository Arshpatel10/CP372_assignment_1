/**
 * Represents a pin placed on the bulletin board at a specific coordinate.
 */
public class Pin {
    private final int x;
    private final int y;

    /**
     * Constructs a new Pin at the specified coordinates.
     *
     * @param x X-coordinate of the pin
     * @param y Y-coordinate of the pin
     */
    public Pin(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Pin pin = (Pin) obj;
        return x == pin.x && y == pin.y;
    }

    @Override
    public int hashCode() {
        return 31 * x + y;
    }

    @Override
    public String toString() {
        return "PIN " + x + " " + y;
    }
}
