import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Handles a single client connection in a dedicated thread.
 * Implements the command parsing and dispatching logic per the RFC protocol.
 */
public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final BulletinBoard board;
    private final int clientId;

    /**
     * Constructs a new ClientHandler.
     *
     * @param clientSocket The client's socket connection
     * @param board        The shared bulletin board
     * @param clientId     Unique identifier for this client (for logging)
     */
    public ClientHandler(Socket clientSocket, BulletinBoard board, int clientId) {
        this.clientSocket = clientSocket;
        this.board = board;
        this.clientId = clientId;
    }

    @Override
    public void run() {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            // Handshake: send board configuration
            StringBuilder handshake = new StringBuilder();
            handshake.append("HELLO ")
                     .append(board.getBoardWidth()).append(" ")
                     .append(board.getBoardHeight()).append(" ")
                     .append(board.getNoteWidth()).append(" ")
                     .append(board.getNoteHeight());
            for (String color : board.getValidColors()) {
                handshake.append(" ").append(color);
            }
            out.println(handshake.toString());
            System.out.println("[Client " + clientId + "] Connected. Handshake sent.");

            // Command loop
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                System.out.println("[Client " + clientId + "] Received: " + line);

                String response = processCommand(line);

                if (response != null) {
                    // Response may be multi-line (e.g., GET results)
                    out.println(response);
                    System.out.println("[Client " + clientId + "] Sent: " + response);
                }

                // Check for disconnect
                if (line.equals("DISCONNECT")) {
                    break;
                }
            }

        } catch (IOException e) {
            System.out.println("[Client " + clientId + "] Connection error: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // Ignore close errors
            }
            System.out.println("[Client " + clientId + "] Disconnected.");
        }
    }

    /**
     * Parses and dispatches a single client command.
     *
     * @param line The raw command string from the client
     * @return The response string to send back
     */
    private String processCommand(String line) {
        // Determine command type
        if (line.startsWith("POST ")) {
            return handlePost(line);
        } else if (line.startsWith("GET")) {
            return handleGet(line);
        } else if (line.startsWith("PIN ")) {
            return handlePin(line);
        } else if (line.startsWith("UNPIN ")) {
            return handleUnpin(line);
        } else if (line.equals("SHAKE")) {
            return board.shake();
        } else if (line.equals("CLEAR")) {
            return board.clear();
        } else if (line.equals("DISCONNECT")) {
            return "OK GOODBYE";
        } else {
            return "ERROR INVALID_FORMAT Unknown command";
        }
    }

    /**
     * Handles the POST command.
     * Syntax: POST <x> <y> <color> <message>
     */
    private String handlePost(String line) {
        // Remove "POST " prefix
        String args = line.substring(5).trim();

        // Parse: x y color message
        String[] parts = args.split(" ", 4);
        if (parts.length < 4) {
            return "ERROR INVALID_FORMAT POST requires coordinates, color, and message";
        }

        int x, y;
        try {
            x = Integer.parseInt(parts[0]);
            y = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return "ERROR INVALID_FORMAT POST coordinates must be non-negative integers";
        }

        if (x < 0 || y < 0) {
            return "ERROR INVALID_FORMAT POST coordinates must be non-negative integers";
        }

        String color = parts[2];
        String message = parts[3];

        if (message.trim().isEmpty()) {
            return "ERROR INVALID_FORMAT POST requires a non-empty message";
        }

        return board.postNote(x, y, color, message);
    }

    /**
     * Handles the GET command.
     * Syntax: GET PINS
     *      or GET [color=<color>] [contains=<x> <y>] [refersTo=<substring>]
     */
    private String handleGet(String line) {
        String args = line.substring(3).trim();

        // GET PINS
        if (args.equals("PINS")) {
            return board.getPins();
        }

        // GET with optional filters
        String colorFilter = null;
        int containsX = -1;
        int containsY = -1;
        String refersTo = null;
        boolean hasContains = false;

        if (args.isEmpty()) {
            // GET with no filters -> return all notes
            return board.getNotes(null, -1, -1, null, false);
        }

        // Parse filters from the args string
        // We need to handle: color=<color> contains=<x> <y> refersTo=<substring>
        // These can appear in any combination
        try {
            String remaining = args;

            while (!remaining.isEmpty()) {
                remaining = remaining.trim();

                if (remaining.startsWith("color=")) {
                    // Extract color value
                    remaining = remaining.substring(6);
                    int nextSpace = findNextFilterStart(remaining);
                    if (nextSpace == -1) {
                        colorFilter = remaining;
                        remaining = "";
                    } else {
                        colorFilter = remaining.substring(0, nextSpace).trim();
                        remaining = remaining.substring(nextSpace).trim();
                    }
                } else if (remaining.startsWith("contains=")) {
                    // Extract x y coordinates
                    remaining = remaining.substring(9);
                    // Parse x
                    int spaceIdx = remaining.indexOf(' ');
                    if (spaceIdx == -1) {
                        return "ERROR INVALID_FORMAT GET contains filter requires two coordinates";
                    }
                    String xStr = remaining.substring(0, spaceIdx);
                    remaining = remaining.substring(spaceIdx + 1).trim();

                    // Parse y - find end of y value
                    int nextFilter = findNextFilterStart(remaining);
                    String yStr;
                    if (nextFilter == -1) {
                        yStr = remaining;
                        remaining = "";
                    } else {
                        yStr = remaining.substring(0, nextFilter).trim();
                        remaining = remaining.substring(nextFilter).trim();
                    }

                    containsX = Integer.parseInt(xStr);
                    containsY = Integer.parseInt(yStr);
                    hasContains = true;

                    if (containsX < 0 || containsY < 0) {
                        return "ERROR INVALID_FORMAT GET contains coordinates must be non-negative";
                    }
                } else if (remaining.startsWith("refersTo=")) {
                    // Extract substring - everything after refersTo= until next filter or end
                    remaining = remaining.substring(9);
                    int nextFilter = findNextFilterStart(remaining);
                    if (nextFilter == -1) {
                        refersTo = remaining;
                        remaining = "";
                    } else {
                        refersTo = remaining.substring(0, nextFilter).trim();
                        remaining = remaining.substring(nextFilter).trim();
                    }
                } else {
                    return "ERROR INVALID_FORMAT Invalid GET filter: " + remaining;
                }
            }

        } catch (NumberFormatException e) {
            return "ERROR INVALID_FORMAT GET filter coordinates must be integers";
        }

        return board.getNotes(colorFilter, containsX, containsY, refersTo, hasContains);
    }

    /**
     * Finds the start index of the next filter keyword in the string.
     * Returns -1 if no filter keyword is found.
     */
    private int findNextFilterStart(String s) {
        int colorIdx = s.indexOf("color=");
        int containsIdx = s.indexOf("contains=");
        int refersIdx = s.indexOf("refersTo=");

        int min = -1;
        if (colorIdx > 0) min = colorIdx;
        if (containsIdx > 0 && (min == -1 || containsIdx < min)) min = containsIdx;
        if (refersIdx > 0 && (min == -1 || refersIdx < min)) min = refersIdx;

        return min;
    }

    /**
     * Handles the PIN command.
     * Syntax: PIN <x> <y>
     */
    private String handlePin(String line) {
        String args = line.substring(4).trim();
        String[] parts = args.split(" ");

        if (parts.length != 2) {
            return "ERROR INVALID_FORMAT PIN requires exactly two coordinates";
        }

        int x, y;
        try {
            x = Integer.parseInt(parts[0]);
            y = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return "ERROR INVALID_FORMAT PIN coordinates must be non-negative integers";
        }

        if (x < 0 || y < 0) {
            return "ERROR INVALID_FORMAT PIN coordinates must be non-negative integers";
        }

        return board.pinAt(x, y);
    }

    /**
     * Handles the UNPIN command.
     * Syntax: UNPIN <x> <y>
     */
    private String handleUnpin(String line) {
        String args = line.substring(6).trim();
        String[] parts = args.split(" ");

        if (parts.length != 2) {
            return "ERROR INVALID_FORMAT UNPIN requires exactly two coordinates";
        }

        int x, y;
        try {
            x = Integer.parseInt(parts[0]);
            y = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return "ERROR INVALID_FORMAT UNPIN coordinates must be non-negative integers";
        }

        if (x < 0 || y < 0) {
            return "ERROR INVALID_FORMAT UNPIN coordinates must be non-negative integers";
        }

        return board.unpinAt(x, y);
    }
}
