import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents the shared bulletin board data structure.
 * All public methods are synchronized to ensure thread safety and atomicity.
 * Uses a global lock (synchronized methods) per the RFC concurrency model.
 */
public class BulletinBoard {
    private final int boardWidth;
    private final int boardHeight;
    private final int noteWidth;
    private final int noteHeight;
    private final List<String> validColors;
    private final List<Note> notes;
    private final List<Pin> pins;

    /**
     * Constructs a new BulletinBoard with the given configuration.
     *
     * @param boardWidth  Width of the board
     * @param boardHeight Height of the board
     * @param noteWidth   Fixed width of all notes
     * @param noteHeight  Fixed height of all notes
     * @param validColors List of valid colors for notes
     */
    public BulletinBoard(int boardWidth, int boardHeight, int noteWidth, int noteHeight, List<String> validColors) {
        this.boardWidth = boardWidth;
        this.boardHeight = boardHeight;
        this.noteWidth = noteWidth;
        this.noteHeight = noteHeight;
        this.validColors = new ArrayList<>(validColors);
        this.notes = new ArrayList<>();
        this.pins = new ArrayList<>();
    }

    public int getBoardWidth() {
        return boardWidth;
    }

    public int getBoardHeight() {
        return boardHeight;
    }

    public int getNoteWidth() {
        return noteWidth;
    }

    public int getNoteHeight() {
        return noteHeight;
    }

    public List<String> getValidColors() {
        return new ArrayList<>(validColors);
    }

    /**
     * Posts a new note to the board.
     * Atomic operation: validates and adds in one synchronized block.
     *
     * @param x       X-coordinate of the note
     * @param y       Y-coordinate of the note
     * @param color   Color of the note
     * @param message Message content
     * @return Response string (OK or ERROR)
     */
    public synchronized String postNote(int x, int y, String color, String message) {
        // Validate bounds: note must fit entirely within the board
        if (x < 0 || y < 0 || x + noteWidth > boardWidth || y + noteHeight > boardHeight) {
            return "ERROR OUT_OF_BOUNDS Note exceeds board boundaries";
        }

        // Validate color
        if (!validColors.contains(color)) {
            return "ERROR COLOR_NOT_SUPPORTED " + color + " is not a valid color";
        }

        // Check for complete overlap (same x, y coordinates)
        Note newNote = new Note(x, y, noteWidth, noteHeight, color, message);
        for (Note existing : notes) {
            if (existing.completelyOverlaps(newNote)) {
                return "ERROR COMPLETE_OVERLAP Note overlaps an existing note entirely";
            }
        }

        notes.add(newNote);
        return "OK NOTE_POSTED";
    }

    /**
     * Places a pin at the given coordinate.
     * All notes containing the coordinate become pinned.
     *
     * @param x X-coordinate of the pin
     * @param y Y-coordinate of the pin
     * @return Response string (OK or ERROR)
     */
    public synchronized String pinAt(int x, int y) {
        // Check if any note contains this coordinate
        boolean anyNoteContains = false;
        for (Note note : notes) {
            if (note.containsPoint(x, y)) {
                anyNoteContains = true;
                break;
            }
        }

        if (!anyNoteContains) {
            return "ERROR NO_NOTE_AT_COORDINATE No note contains the given point";
        }

        pins.add(new Pin(x, y));
        return "OK PIN_ADDED";
    }

    /**
     * Removes a pin at the exact specified coordinate.
     *
     * @param x X-coordinate of the pin
     * @param y Y-coordinate of the pin
     * @return Response string (OK or ERROR)
     */
    public synchronized String unpinAt(int x, int y) {
        Pin target = new Pin(x, y);
        // Remove the first matching pin (only one pin removed per UNPIN command)
        for (int i = 0; i < pins.size(); i++) {
            if (pins.get(i).equals(target)) {
                pins.remove(i);
                return "OK PIN_REMOVED";
            }
        }
        return "ERROR PIN_NOT_FOUND No pin exists at the given coordinates";
    }

    /**
     * Removes all unpinned notes from the board.
     * Atomic operation: board transitions from pre-SHAKE to post-SHAKE state atomically.
     *
     * @return Response string
     */
    public synchronized String shake() {
        List<Note> toRemove = new ArrayList<>();
        for (Note note : notes) {
            if (!isNotePinned(note)) {
                toRemove.add(note);
            }
        }
        notes.removeAll(toRemove);
        return "OK SHAKE_COMPLETE";
    }

    /**
     * Removes all notes and all pins from the board.
     * Atomic operation: board returns to empty state.
     *
     * @return Response string
     */
    public synchronized String clear() {
        notes.clear();
        pins.clear();
        return "OK CLEAR_COMPLETE";
    }

    /**
     * Returns all pins on the board.
     *
     * @return Formatted response with pin count and coordinates
     */
    public synchronized String getPins() {
        StringBuilder sb = new StringBuilder();
        sb.append("OK ").append(pins.size());
        for (Pin pin : pins) {
            sb.append("\n").append(pin.toString());
        }
        return sb.toString();
    }

    /**
     * Returns notes matching the given filter criteria.
     * Missing (null) criteria match ALL notes.
     *
     * @param colorFilter    Color to filter by (null = all)
     * @param containsX      X-coordinate for contains filter (-1 = not set)
     * @param containsY      Y-coordinate for contains filter (-1 = not set)
     * @param refersTo       Substring to search in message (null = all)
     * @param hasContains    Whether the contains filter is active
     * @return Formatted response with matching notes
     */
    public synchronized String getNotes(String colorFilter, int containsX, int containsY,
                                         String refersTo, boolean hasContains) {
        List<Note> results = new ArrayList<>();

        for (Note note : notes) {
            // Apply color filter
            if (colorFilter != null && !note.getColor().equals(colorFilter)) {
                continue;
            }

            // Apply contains filter
            if (hasContains && !note.containsPoint(containsX, containsY)) {
                continue;
            }

            // Apply refersTo filter
            if (refersTo != null && !note.getMessage().contains(refersTo)) {
                continue;
            }

            results.add(note);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("OK ").append(results.size());
        for (Note note : results) {
            boolean pinned = isNotePinned(note);
            sb.append("\nNOTE ").append(note.getX()).append(" ").append(note.getY())
              .append(" ").append(note.getColor()).append(" ").append(note.getMessage())
              .append(" PINNED=").append(pinned);
        }
        return sb.toString();
    }

    /**
     * Checks if a note is pinned (has at least one pin within its boundaries).
     *
     * @param note The note to check
     * @return true if the note has at least one pin
     */
    private boolean isNotePinned(Note note) {
        for (Pin pin : pins) {
            if (note.containsPoint(pin.getX(), pin.getY())) {
                return true;
            }
        }
        return false;
    }
}
