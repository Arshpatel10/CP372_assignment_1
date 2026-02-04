import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * BBoard - Main server application for the Bulletin Board System.
 *
 * Usage: java BBoard <port> <board_width> <board_height> <note_width> <note_height> <color1> [<color2> ...]
 *
 * Example: java BBoard 4554 200 100 20 10 red white green yellow
 *
 * The server listens on the specified TCP port and spawns a new thread
 * for each connecting client (thread-per-client model).
 */
public class BBoard {

    /**
     * Main entry point for the Bulletin Board Server.
     *
     * @param args Command-line arguments: port, board dimensions, note dimensions, and valid colors
     */
    public static void main(String[] args) {
        // Validate minimum arguments: port + board_w + board_h + note_w + note_h + at least 1 color
        if (args.length < 6) {
            System.err.println("Usage: java BBoard <port> <board_width> <board_height> <note_width> <note_height> <color1> [<color2> ...]");
            System.err.println("Example: java BBoard 4554 200 100 20 10 red white green yellow");
            System.exit(1);
        }

        int port, boardWidth, boardHeight, noteWidth, noteHeight;

        try {
            port = Integer.parseInt(args[0]);
            boardWidth = Integer.parseInt(args[1]);
            boardHeight = Integer.parseInt(args[2]);
            noteWidth = Integer.parseInt(args[3]);
            noteHeight = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            System.err.println("Error: port, board dimensions, and note dimensions must be positive integers.");
            System.exit(1);
            return;
        }

        // Validate values
        if (port < 1 || port > 65535) {
            System.err.println("Error: port must be between 1 and 65535.");
            System.exit(1);
        }
        if (boardWidth <= 0 || boardHeight <= 0) {
            System.err.println("Error: board dimensions must be positive.");
            System.exit(1);
        }
        if (noteWidth <= 0 || noteHeight <= 0) {
            System.err.println("Error: note dimensions must be positive.");
            System.exit(1);
        }
        if (noteWidth > boardWidth || noteHeight > boardHeight) {
            System.err.println("Error: note dimensions must not exceed board dimensions.");
            System.exit(1);
        }

        // Collect valid colors
        List<String> validColors = new ArrayList<>();
        for (int i = 5; i < args.length; i++) {
            validColors.add(args[i]);
        }

        // Create the bulletin board
        BulletinBoard board = new BulletinBoard(boardWidth, boardHeight, noteWidth, noteHeight, validColors);

        System.out.println("=== Bulletin Board Server ===");
        System.out.println("Port:        " + port);
        System.out.println("Board:       " + boardWidth + " x " + boardHeight);
        System.out.println("Note size:   " + noteWidth + " x " + noteHeight);
        System.out.println("Colors:      " + validColors);
        System.out.println("=============================");

        // Start listening for connections
        int clientCounter = 0;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server listening on port " + port + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                clientCounter++;
                System.out.println("Client " + clientCounter + " connected from " +
                        clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort());

                // Spawn a new thread for this client
                ClientHandler handler = new ClientHandler(clientSocket, board, clientCounter);
                Thread clientThread = new Thread(handler, "Client-" + clientCounter);
                clientThread.setDaemon(true);
                clientThread.start();
            }

        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            System.exit(1);
        }
    }
}
