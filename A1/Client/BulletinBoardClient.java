import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BulletinBoardClient - GUI client for the Bulletin Board System.
 * Provides a Swing-based interface for connecting to the server and
 * issuing all protocol commands (POST, GET, PIN, UNPIN, SHAKE, CLEAR, DISCONNECT).
 * Includes a visual board panel that displays notes and pins graphically.
 */
public class BulletinBoardClient extends JFrame {

    // Network components
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private boolean connected = false;

    // Board configuration received from server handshake
    private int boardWidth;
    private int boardHeight;
    private int noteWidth;
    private int noteHeight;
    private List<String> validColors = new ArrayList<>();

    // Visual board components
    private BoardPanel boardPanel;
    private List<VisualNote> visualNotes = new ArrayList<>();
    private List<VisualPin> visualPins = new ArrayList<>();
    private static final int SCALE = 3; // Scale factor for display (board coords * SCALE = pixels)

    // Connection panel components
    private JTextField serverIpField;
    private JTextField portField;
    private JButton connectButton;
    private JButton disconnectButton;
    private JLabel statusLabel;

    // POST panel components
    private JTextField postXField;
    private JTextField postYField;
    private JComboBox<String> postColorCombo;
    private JTextField postMessageField;
    private JButton postButton;

    // GET panel components
    private JCheckBox getPinsCheck;
    private JCheckBox colorFilterCheck;
    private JComboBox<String> getColorCombo;
    private JCheckBox containsFilterCheck;
    private JTextField getContainsXField;
    private JTextField getContainsYField;
    private JCheckBox refersToFilterCheck;
    private JTextField getRefersToField;
    private JButton getButton;

    // PIN/UNPIN panel components
    private JTextField pinXField;
    private JTextField pinYField;
    private JButton pinButton;
    private JButton unpinButton;

    // Action buttons
    private JButton shakeButton;
    private JButton clearButton;
    private JButton refreshButton;

    // Output display
    private JTextArea outputArea;

    // Color mapping for rendering notes
    private static final Map<String, Color> COLOR_MAP = new HashMap<>();
    static {
        COLOR_MAP.put("red", new Color(255, 102, 102));
        COLOR_MAP.put("white", new Color(255, 255, 240));
        COLOR_MAP.put("green", new Color(144, 238, 144));
        COLOR_MAP.put("yellow", new Color(255, 255, 153));
        COLOR_MAP.put("blue", new Color(135, 206, 250));
        COLOR_MAP.put("pink", new Color(255, 182, 193));
        COLOR_MAP.put("orange", new Color(255, 200, 100));
        COLOR_MAP.put("purple", new Color(200, 160, 255));
        COLOR_MAP.put("cyan", new Color(175, 238, 238));
    }

    /**
     * Constructs and displays the client GUI.
     */
    public BulletinBoardClient() {
        super("Bulletin Board Client - CP372");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleWindowClose();
            }
        });

        initializeUI();
        setCommandsEnabled(false);
        setMinimumSize(new Dimension(1100, 780));
        setPreferredSize(new Dimension(1100, 780));
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    /**
     * Initializes all UI components and layouts.
     */
    private void initializeUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Top: Connection panel
        mainPanel.add(createConnectionPanel(), BorderLayout.NORTH);

        // Left side: Commands panel
        JPanel commandsPanel = new JPanel();
        commandsPanel.setLayout(new BoxLayout(commandsPanel, BoxLayout.Y_AXIS));
        commandsPanel.add(createPostPanel());
        commandsPanel.add(Box.createVerticalStrut(6));
        commandsPanel.add(createGetPanel());
        commandsPanel.add(Box.createVerticalStrut(6));
        commandsPanel.add(createPinPanel());
        commandsPanel.add(Box.createVerticalStrut(6));
        commandsPanel.add(createActionsPanel());

        JPanel leftWrapper = new JPanel(new BorderLayout());
        leftWrapper.add(commandsPanel, BorderLayout.NORTH);

        // Right side: Visual board panel
        boardPanel = new BoardPanel();
        JScrollPane boardScrollPane = new JScrollPane(boardPanel);
        boardScrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Visual Board (Click to select coordinates)",
                TitledBorder.LEFT, TitledBorder.TOP));
        boardScrollPane.setPreferredSize(new Dimension(450, 350));

        // Split left (commands) and right (board)
        JSplitPane topSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftWrapper, boardScrollPane);
        topSplitPane.setResizeWeight(0.45);
        topSplitPane.setDividerLocation(480);

        // Bottom: Output area
        outputArea = new JTextArea(10, 60);
        outputArea.setEditable(false);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(outputArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Server Output",
                TitledBorder.LEFT, TitledBorder.TOP));

        // Split: top (commands + board) and bottom (output)
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplitPane, scrollPane);
        mainSplitPane.setResizeWeight(0.6);
        mainSplitPane.setDividerLocation(380);

        mainPanel.add(mainSplitPane, BorderLayout.CENTER);
        setContentPane(mainPanel);
    }

    /**
     * Creates the connection panel with IP, port, and connect/disconnect buttons.
     */
    private JPanel createConnectionPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Connection",
                TitledBorder.LEFT, TitledBorder.TOP));

        panel.add(new JLabel("Server IP:"));
        serverIpField = new JTextField("localhost", 12);
        panel.add(serverIpField);

        panel.add(new JLabel("Port:"));
        portField = new JTextField("4554", 6);
        panel.add(portField);

        connectButton = new JButton("Connect");
        connectButton.addActionListener(e -> handleConnect());
        panel.add(connectButton);

        disconnectButton = new JButton("Disconnect");
        disconnectButton.setEnabled(false);
        disconnectButton.addActionListener(e -> handleDisconnect());
        panel.add(disconnectButton);

        statusLabel = new JLabel("  Disconnected");
        statusLabel.setForeground(Color.RED);
        panel.add(statusLabel);

        return panel;
    }

    /**
     * Creates the POST command panel.
     */
    private JPanel createPostPanel() {
        JPanel outerPanel = new JPanel(new BorderLayout());
        outerPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "POST Note",
                TitledBorder.LEFT, TitledBorder.TOP));

        // Top row: coordinates and color
        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        topRow.add(new JLabel("X:"));
        postXField = new JTextField(4);
        topRow.add(postXField);

        topRow.add(new JLabel("Y:"));
        postYField = new JTextField(4);
        topRow.add(postYField);

        topRow.add(new JLabel("Color:"));
        postColorCombo = new JComboBox<>();
        postColorCombo.setPreferredSize(new Dimension(90, 25));
        topRow.add(postColorCombo);

        outerPanel.add(topRow, BorderLayout.NORTH);

        // Bottom row: message and button
        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        bottomRow.add(new JLabel("Message:"));
        postMessageField = new JTextField(22);
        bottomRow.add(postMessageField);

        postButton = new JButton("POST");
        postButton.addActionListener(e -> handlePost());
        bottomRow.add(postButton);

        outerPanel.add(bottomRow, BorderLayout.SOUTH);

        return outerPanel;
    }

    /**
     * Creates the GET command panel with filter options.
     */
    private JPanel createGetPanel() {
        JPanel outerPanel = new JPanel(new BorderLayout());
        outerPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "GET Notes / Pins",
                TitledBorder.LEFT, TitledBorder.TOP));

        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        getPinsCheck = new JCheckBox("GET PINS");
        getPinsCheck.addActionListener(e -> {
            boolean pinsMode = getPinsCheck.isSelected();
            colorFilterCheck.setEnabled(!pinsMode);
            containsFilterCheck.setEnabled(!pinsMode);
            refersToFilterCheck.setEnabled(!pinsMode);
            getColorCombo.setEnabled(!pinsMode && colorFilterCheck.isSelected());
            getContainsXField.setEnabled(!pinsMode && containsFilterCheck.isSelected());
            getContainsYField.setEnabled(!pinsMode && containsFilterCheck.isSelected());
            getRefersToField.setEnabled(!pinsMode && refersToFilterCheck.isSelected());
        });
        topRow.add(getPinsCheck);
        topRow.add(Box.createHorizontalStrut(20));

        colorFilterCheck = new JCheckBox("color=");
        colorFilterCheck.addActionListener(e -> getColorCombo.setEnabled(colorFilterCheck.isSelected()));
        topRow.add(colorFilterCheck);
        getColorCombo = new JComboBox<>();
        getColorCombo.setPreferredSize(new Dimension(100, 25));
        getColorCombo.setEnabled(false);
        topRow.add(getColorCombo);

        outerPanel.add(topRow, BorderLayout.NORTH);

        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        containsFilterCheck = new JCheckBox("contains=");
        containsFilterCheck.addActionListener(e -> {
            boolean sel = containsFilterCheck.isSelected();
            getContainsXField.setEnabled(sel);
            getContainsYField.setEnabled(sel);
        });
        bottomRow.add(containsFilterCheck);
        bottomRow.add(new JLabel("X:"));
        getContainsXField = new JTextField(5);
        getContainsXField.setEnabled(false);
        bottomRow.add(getContainsXField);
        bottomRow.add(new JLabel("Y:"));
        getContainsYField = new JTextField(5);
        getContainsYField.setEnabled(false);
        bottomRow.add(getContainsYField);

        bottomRow.add(Box.createHorizontalStrut(10));
        refersToFilterCheck = new JCheckBox("refersTo=");
        refersToFilterCheck.addActionListener(e -> getRefersToField.setEnabled(refersToFilterCheck.isSelected()));
        bottomRow.add(refersToFilterCheck);
        getRefersToField = new JTextField(15);
        getRefersToField.setEnabled(false);
        bottomRow.add(getRefersToField);

        bottomRow.add(Box.createHorizontalStrut(10));
        getButton = new JButton("GET");
        getButton.addActionListener(e -> handleGet());
        bottomRow.add(getButton);

        outerPanel.add(bottomRow, BorderLayout.SOUTH);

        return outerPanel;
    }

    /**
     * Creates the PIN/UNPIN command panel.
     */
    private JPanel createPinPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "PIN / UNPIN",
                TitledBorder.LEFT, TitledBorder.TOP));

        panel.add(new JLabel("X:"));
        pinXField = new JTextField(5);
        panel.add(pinXField);

        panel.add(new JLabel("Y:"));
        pinYField = new JTextField(5);
        panel.add(pinYField);

        pinButton = new JButton("PIN");
        pinButton.addActionListener(e -> handlePin());
        panel.add(pinButton);

        unpinButton = new JButton("UNPIN");
        unpinButton.addActionListener(e -> handleUnpin());
        panel.add(unpinButton);

        return panel;
    }

    /**
     * Creates the SHAKE and CLEAR action buttons panel.
     */
    private JPanel createActionsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Board Actions",
                TitledBorder.LEFT, TitledBorder.TOP));

        shakeButton = new JButton("SHAKE");
        shakeButton.setToolTipText("Remove all unpinned notes");
        shakeButton.addActionListener(e -> handleShake());
        panel.add(shakeButton);

        clearButton = new JButton("CLEAR");
        clearButton.setToolTipText("Remove all notes and pins");
        clearButton.addActionListener(e -> handleClear());
        panel.add(clearButton);

        refreshButton = new JButton("Refresh Board");
        refreshButton.setToolTipText("Refresh the visual board display");
        refreshButton.addActionListener(e -> refreshBoardVisual());
        panel.add(refreshButton);

        JButton clearOutputBtn = new JButton("Clear Output");
        clearOutputBtn.setToolTipText("Clear the output display area");
        clearOutputBtn.addActionListener(e -> outputArea.setText(""));
        panel.add(clearOutputBtn);

        return panel;
    }

    // ========================================================================
    // Connection Handling
    // ========================================================================

    /**
     * Handles the Connect button click.
     * Establishes TCP connection and reads the handshake from server.
     */
    private void handleConnect() {
        String serverIp = serverIpField.getText().trim();
        String portStr = portField.getText().trim();

        if (serverIp.isEmpty()) {
            showError("Please enter a server IP address.");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) {
                showError("Port must be between 1 and 65535.");
                return;
            }
        } catch (NumberFormatException e) {
            showError("Port must be a valid integer.");
            return;
        }

        try {
            socket = new Socket(serverIp, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Read handshake
            String handshake = in.readLine();
            if (handshake == null || !handshake.startsWith("HELLO")) {
                showError("Invalid handshake from server.");
                closeConnection();
                return;
            }

            parseHandshake(handshake);

            connected = true;
            setCommandsEnabled(true);
            connectButton.setEnabled(false);
            disconnectButton.setEnabled(true);
            serverIpField.setEnabled(false);
            portField.setEnabled(false);
            statusLabel.setText("  Connected");
            statusLabel.setForeground(new Color(0, 128, 0));

            appendOutput("Connected to " + serverIp + ":" + port);
            appendOutput("Board: " + boardWidth + "x" + boardHeight +
                    " | Note: " + noteWidth + "x" + noteHeight +
                    " | Colors: " + validColors);

            // Update board panel size and refresh
            boardPanel.revalidate();
            boardPanel.repaint();
            refreshBoardVisual();

        } catch (IOException e) {
            showError("Could not connect to server: " + e.getMessage());
        }
    }

    /**
     * Parses the HELLO handshake message from the server.
     * Format: HELLO <board_width> <board_height> <note_width> <note_height> <color1> [<color2>...]
     */
    private void parseHandshake(String handshake) {
        String[] parts = handshake.split(" ");
        boardWidth = Integer.parseInt(parts[1]);
        boardHeight = Integer.parseInt(parts[2]);
        noteWidth = Integer.parseInt(parts[3]);
        noteHeight = Integer.parseInt(parts[4]);

        validColors.clear();
        postColorCombo.removeAllItems();
        getColorCombo.removeAllItems();

        for (int i = 5; i < parts.length; i++) {
            validColors.add(parts[i]);
            postColorCombo.addItem(parts[i]);
            getColorCombo.addItem(parts[i]);
        }
    }

    /**
     * Handles the Disconnect button click.
     * Sends DISCONNECT command and closes the connection gracefully.
     */
    private void handleDisconnect() {
        if (!connected) return;

        try {
            sendCommand("DISCONNECT");
            String response = readResponse();
            appendOutput(">> DISCONNECT");
            appendOutput("<< " + response);
        } catch (IOException e) {
            appendOutput("Error during disconnect: " + e.getMessage());
        }

        closeConnection();
        appendOutput("Disconnected from server.");
    }

    /**
     * Closes the socket and resets UI to disconnected state.
     */
    private void closeConnection() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore
        }

        socket = null;
        in = null;
        out = null;

        setCommandsEnabled(false);
        connectButton.setEnabled(true);
        disconnectButton.setEnabled(false);
        serverIpField.setEnabled(true);
        portField.setEnabled(true);
        statusLabel.setText("  Disconnected");
        statusLabel.setForeground(Color.RED);
    }

    /**
     * Handles window close: disconnect if connected, then exit.
     */
    private void handleWindowClose() {
        if (connected) {
            try {
                sendCommand("DISCONNECT");
            } catch (IOException e) {
                // Ignore on close
            }
            closeConnection();
        }
        dispose();
        System.exit(0);
    }

    // ========================================================================
    // Command Handlers
    // ========================================================================

    /**
     * Handles the POST button click.
     * Validates input client-side before sending to server.
     */
    private void handlePost() {
        if (!connected) return;

        String xStr = postXField.getText().trim();
        String yStr = postYField.getText().trim();
        String message = postMessageField.getText().trim();

        // Client-side validation
        if (xStr.isEmpty() || yStr.isEmpty()) {
            showError("Please enter X and Y coordinates.");
            return;
        }

        int x, y;
        try {
            x = Integer.parseInt(xStr);
            y = Integer.parseInt(yStr);
        } catch (NumberFormatException e) {
            showError("Coordinates must be non-negative integers.");
            return;
        }

        if (x < 0 || y < 0) {
            showError("Coordinates must be non-negative.");
            return;
        }

        if (postColorCombo.getSelectedItem() == null) {
            showError("Please select a color.");
            return;
        }
        String color = (String) postColorCombo.getSelectedItem();

        if (message.isEmpty()) {
            showError("Please enter a message for the note.");
            return;
        }

        String command = "POST " + x + " " + y + " " + color + " " + message;
        sendAndDisplay(command);
    }

    /**
     * Handles the GET button click.
     * Builds the GET command string based on selected filters.
     */
    private void handleGet() {
        if (!connected) return;

        if (getPinsCheck.isSelected()) {
            sendAndDisplay("GET PINS");
            return;
        }

        StringBuilder cmd = new StringBuilder("GET");
        boolean hasFilter = false;

        if (colorFilterCheck.isSelected() && getColorCombo.getSelectedItem() != null) {
            cmd.append(" color=").append(getColorCombo.getSelectedItem());
            hasFilter = true;
        }

        if (containsFilterCheck.isSelected()) {
            String cxStr = getContainsXField.getText().trim();
            String cyStr = getContainsYField.getText().trim();

            if (cxStr.isEmpty() || cyStr.isEmpty()) {
                showError("Please enter both X and Y for the contains filter.");
                return;
            }

            try {
                int cx = Integer.parseInt(cxStr);
                int cy = Integer.parseInt(cyStr);
                if (cx < 0 || cy < 0) {
                    showError("Contains coordinates must be non-negative.");
                    return;
                }
                cmd.append(" contains=").append(cx).append(" ").append(cy);
                hasFilter = true;
            } catch (NumberFormatException e) {
                showError("Contains coordinates must be integers.");
                return;
            }
        }

        if (refersToFilterCheck.isSelected()) {
            String ref = getRefersToField.getText().trim();
            if (ref.isEmpty()) {
                showError("Please enter a search term for refersTo.");
                return;
            }
            cmd.append(" refersTo=").append(ref);
            hasFilter = true;
        }

        sendAndDisplay(cmd.toString());
    }

    /**
     * Handles the PIN button click.
     */
    private void handlePin() {
        if (!connected) return;

        String xStr = pinXField.getText().trim();
        String yStr = pinYField.getText().trim();

        if (xStr.isEmpty() || yStr.isEmpty()) {
            showError("Please enter X and Y coordinates for the pin.");
            return;
        }

        int x, y;
        try {
            x = Integer.parseInt(xStr);
            y = Integer.parseInt(yStr);
        } catch (NumberFormatException e) {
            showError("Pin coordinates must be non-negative integers.");
            return;
        }

        if (x < 0 || y < 0) {
            showError("Pin coordinates must be non-negative.");
            return;
        }

        sendAndDisplay("PIN " + x + " " + y);
    }

    /**
     * Handles the UNPIN button click.
     */
    private void handleUnpin() {
        if (!connected) return;

        String xStr = pinXField.getText().trim();
        String yStr = pinYField.getText().trim();

        if (xStr.isEmpty() || yStr.isEmpty()) {
            showError("Please enter X and Y coordinates for the pin to remove.");
            return;
        }

        int x, y;
        try {
            x = Integer.parseInt(xStr);
            y = Integer.parseInt(yStr);
        } catch (NumberFormatException e) {
            showError("Unpin coordinates must be non-negative integers.");
            return;
        }

        if (x < 0 || y < 0) {
            showError("Unpin coordinates must be non-negative.");
            return;
        }

        sendAndDisplay("UNPIN " + x + " " + y);
    }

    /**
     * Handles the SHAKE button click with confirmation dialog.
     */
    private void handleShake() {
        if (!connected) return;
        int confirm = JOptionPane.showConfirmDialog(this,
                "Remove all unpinned notes from the board?",
                "Confirm SHAKE", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            sendAndDisplay("SHAKE");
        }
    }

    /**
     * Handles the CLEAR button click with confirmation dialog.
     */
    private void handleClear() {
        if (!connected) return;
        int confirm = JOptionPane.showConfirmDialog(this,
                "Remove ALL notes and pins from the board?",
                "Confirm CLEAR", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            sendAndDisplay("CLEAR");
        }
    }

    // ========================================================================
    // Network Communication
    // ========================================================================

    /**
     * Sends a command and displays both the command and response in the output area.
     * Handles multi-line responses (e.g., GET results).
     * Auto-refreshes the visual board after state-changing commands.
     *
     * @param command The command to send
     */
    private void sendAndDisplay(String command) {
        try {
            appendOutput(">> " + command);
            sendCommand(command);

            String response = readResponse();
            if (response == null) {
                appendOutput("<< (no response - connection may be lost)");
                closeConnection();
                return;
            }

            // Check if this is a multi-line response (GET with count)
            if (response.startsWith("OK ") && !response.startsWith("OK NOTE_POSTED") &&
                !response.startsWith("OK PIN_ADDED") && !response.startsWith("OK PIN_REMOVED") &&
                !response.startsWith("OK SHAKE_COMPLETE") && !response.startsWith("OK CLEAR_COMPLETE") &&
                !response.startsWith("OK GOODBYE")) {

                // This might be a GET response with a count
                String afterOK = response.substring(3).trim();
                try {
                    int count = Integer.parseInt(afterOK);
                    // Read 'count' additional lines
                    StringBuilder fullResponse = new StringBuilder(response);
                    for (int i = 0; i < count; i++) {
                        String dataLine = in.readLine();
                        if (dataLine != null) {
                            fullResponse.append("\n").append(dataLine);
                        }
                    }
                    appendOutput("<< " + fullResponse.toString());
                    // Auto-refresh visual after GET (but not for user-initiated refresh)
                    return;
                } catch (NumberFormatException e) {
                    // Not a count-based response, just display as-is
                }
            }

            appendOutput("<< " + response);

            // Auto-refresh the visual board after state-changing commands
            if (response.startsWith("OK NOTE_POSTED") ||
                response.startsWith("OK PIN_ADDED") ||
                response.startsWith("OK PIN_REMOVED") ||
                response.startsWith("OK SHAKE_COMPLETE") ||
                response.startsWith("OK CLEAR_COMPLETE")) {
                refreshBoardVisual();
            }

        } catch (IOException e) {
            appendOutput("Communication error: " + e.getMessage());
            closeConnection();
        }
    }

    /**
     * Sends a raw command to the server.
     */
    private void sendCommand(String command) throws IOException {
        if (out == null) throw new IOException("Not connected");
        out.println(command);
        if (out.checkError()) {
            throw new IOException("Failed to send command");
        }
    }

    /**
     * Reads a single line response from the server.
     */
    private String readResponse() throws IOException {
        if (in == null) throw new IOException("Not connected");
        return in.readLine();
    }

    // ========================================================================
    // UI Helpers
    // ========================================================================

    /**
     * Enables or disables all command controls.
     */
    private void setCommandsEnabled(boolean enabled) {
        postXField.setEnabled(enabled);
        postYField.setEnabled(enabled);
        postColorCombo.setEnabled(enabled);
        postMessageField.setEnabled(enabled);
        postButton.setEnabled(enabled);

        getPinsCheck.setEnabled(enabled);
        colorFilterCheck.setEnabled(enabled);
        containsFilterCheck.setEnabled(enabled);
        refersToFilterCheck.setEnabled(enabled);
        getColorCombo.setEnabled(enabled && colorFilterCheck.isSelected());
        getContainsXField.setEnabled(enabled && containsFilterCheck.isSelected());
        getContainsYField.setEnabled(enabled && containsFilterCheck.isSelected());
        getRefersToField.setEnabled(enabled && refersToFilterCheck.isSelected());
        getButton.setEnabled(enabled);

        pinXField.setEnabled(enabled);
        pinYField.setEnabled(enabled);
        pinButton.setEnabled(enabled);
        unpinButton.setEnabled(enabled);

        shakeButton.setEnabled(enabled);
        clearButton.setEnabled(enabled);
        refreshButton.setEnabled(enabled);

        // Update board panel state
        if (!enabled) {
            visualNotes.clear();
            visualPins.clear();
            boardPanel.repaint();
        }
    }

    /**
     * Appends a line of text to the output area and scrolls to bottom.
     */
    private void appendOutput(String text) {
        SwingUtilities.invokeLater(() -> {
            outputArea.append(text + "\n");
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
        });
    }

    /**
     * Shows an error dialog.
     */
    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // ========================================================================
    // Visual Board Methods
    // ========================================================================

    /**
     * Refreshes the visual board by fetching current notes and pins from the server.
     */
    private void refreshBoardVisual() {
        if (!connected) return;

        try {
            // Fetch all notes
            sendCommand("GET");
            String notesResponse = readResponse();
            visualNotes.clear();

            if (notesResponse != null && notesResponse.startsWith("OK ")) {
                String afterOK = notesResponse.substring(3).trim();
                try {
                    int count = Integer.parseInt(afterOK);
                    for (int i = 0; i < count; i++) {
                        String line = in.readLine();
                        if (line != null && line.startsWith("NOTE ")) {
                            VisualNote note = parseNoteResponse(line);
                            if (note != null) {
                                visualNotes.add(note);
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                    // Not a valid response
                }
            }

            // Fetch all pins
            sendCommand("GET PINS");
            String pinsResponse = readResponse();
            visualPins.clear();

            if (pinsResponse != null && pinsResponse.startsWith("OK ")) {
                String afterOK = pinsResponse.substring(3).trim();
                try {
                    int count = Integer.parseInt(afterOK);
                    for (int i = 0; i < count; i++) {
                        String line = in.readLine();
                        if (line != null && line.startsWith("PIN ")) {
                            VisualPin pin = parsePinResponse(line);
                            if (pin != null) {
                                visualPins.add(pin);
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                    // Not a valid response
                }
            }

            // Repaint the board
            boardPanel.repaint();

        } catch (IOException e) {
            appendOutput("Error refreshing board: " + e.getMessage());
        }
    }

    /**
     * Parses a NOTE response line into a VisualNote object.
     * Format: NOTE <x> <y> <color> <message> PINNED=<true|false>
     */
    private VisualNote parseNoteResponse(String line) {
        try {
            // Remove "NOTE " prefix
            String data = line.substring(5);

            // Find PINNED= at the end
            int pinnedIdx = data.lastIndexOf(" PINNED=");
            if (pinnedIdx == -1) return null;

            boolean pinned = data.substring(pinnedIdx + 8).equals("true");
            data = data.substring(0, pinnedIdx);

            // Parse: x y color message
            String[] parts = data.split(" ", 4);
            if (parts.length < 4) return null;

            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            String color = parts[2];
            String message = parts[3];

            return new VisualNote(x, y, noteWidth, noteHeight, color, message, pinned);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parses a PIN response line into a VisualPin object.
     * Format: PIN <x> <y>
     */
    private VisualPin parsePinResponse(String line) {
        try {
            String[] parts = line.split(" ");
            if (parts.length < 3) return null;
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            return new VisualPin(x, y);
        } catch (Exception e) {
            return null;
        }
    }

    // ========================================================================
    // Inner Classes for Visual Representation
    // ========================================================================

    /**
     * Represents a note for visual display.
     */
    private static class VisualNote {
        final int x, y, width, height;
        final String color, message;
        final boolean pinned;

        VisualNote(int x, int y, int width, int height, String color, String message, boolean pinned) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.color = color;
            this.message = message;
            this.pinned = pinned;
        }
    }

    /**
     * Represents a pin for visual display.
     */
    private static class VisualPin {
        final int x, y;

        VisualPin(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    /**
     * Custom panel that renders the bulletin board with notes and pins.
     */
    private class BoardPanel extends JPanel {

        BoardPanel() {
            setBackground(new Color(139, 119, 101)); // Cork board color

            // Add mouse listener to click and set coordinates
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!connected) return;

                    // Convert pixel coordinates to board coordinates
                    int boardX = e.getX() / SCALE;
                    int boardY = e.getY() / SCALE;

                    // Clamp to board bounds
                    boardX = Math.max(0, Math.min(boardX, boardWidth - 1));
                    boardY = Math.max(0, Math.min(boardY, boardHeight - 1));

                    // Set the coordinates in the POST and PIN fields
                    postXField.setText(String.valueOf(boardX));
                    postYField.setText(String.valueOf(boardY));
                    pinXField.setText(String.valueOf(boardX));
                    pinYField.setText(String.valueOf(boardY));
                    getContainsXField.setText(String.valueOf(boardX));
                    getContainsYField.setText(String.valueOf(boardY));
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            if (boardWidth > 0 && boardHeight > 0) {
                return new Dimension(boardWidth * SCALE + 20, boardHeight * SCALE + 20);
            }
            return new Dimension(400, 300);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int offsetX = 10;
            int offsetY = 10;

            // Draw board background
            if (boardWidth > 0 && boardHeight > 0) {
                g2d.setColor(new Color(210, 180, 140)); // Tan color for board
                g2d.fillRect(offsetX, offsetY, boardWidth * SCALE, boardHeight * SCALE);

                // Draw grid lines (subtle)
                g2d.setColor(new Color(190, 160, 120));
                for (int x = 0; x <= boardWidth; x += 20) {
                    g2d.drawLine(offsetX + x * SCALE, offsetY, offsetX + x * SCALE, offsetY + boardHeight * SCALE);
                }
                for (int y = 0; y <= boardHeight; y += 20) {
                    g2d.drawLine(offsetX, offsetY + y * SCALE, offsetX + boardWidth * SCALE, offsetY + y * SCALE);
                }

                // Draw border
                g2d.setColor(new Color(101, 67, 33)); // Dark brown border
                g2d.setStroke(new BasicStroke(3));
                g2d.drawRect(offsetX, offsetY, boardWidth * SCALE, boardHeight * SCALE);
            }

            // Draw notes
            for (VisualNote note : visualNotes) {
                int nx = offsetX + note.x * SCALE;
                int ny = offsetY + note.y * SCALE;
                int nw = note.width * SCALE;
                int nh = note.height * SCALE;

                // Get note color
                Color noteColor = COLOR_MAP.getOrDefault(note.color.toLowerCase(), new Color(255, 255, 200));

                // Draw shadow
                g2d.setColor(new Color(0, 0, 0, 50));
                g2d.fillRect(nx + 3, ny + 3, nw, nh);

                // Draw note background
                g2d.setColor(noteColor);
                g2d.fillRect(nx, ny, nw, nh);

                // Draw note border
                g2d.setColor(noteColor.darker());
                g2d.setStroke(new BasicStroke(1));
                g2d.drawRect(nx, ny, nw, nh);

                // Draw message text (truncated if needed)
                g2d.setColor(Color.BLACK);
                g2d.setFont(new Font("SansSerif", Font.PLAIN, 10));
                String displayMsg = note.message;
                FontMetrics fm = g2d.getFontMetrics();
                int maxWidth = nw - 4;
                if (fm.stringWidth(displayMsg) > maxWidth) {
                    while (displayMsg.length() > 3 && fm.stringWidth(displayMsg + "...") > maxWidth) {
                        displayMsg = displayMsg.substring(0, displayMsg.length() - 1);
                    }
                    displayMsg += "...";
                }
                g2d.drawString(displayMsg, nx + 2, ny + 12);

                // Draw pinned indicator
                if (note.pinned) {
                    g2d.setColor(new Color(0, 128, 0));
                    g2d.setFont(new Font("SansSerif", Font.BOLD, 8));
                    g2d.drawString("PINNED", nx + 2, ny + nh - 3);
                }
            }

            // Draw pins
            for (VisualPin pin : visualPins) {
                int px = offsetX + pin.x * SCALE;
                int py = offsetY + pin.y * SCALE;

                // Draw pin head (red circle)
                g2d.setColor(new Color(200, 0, 0));
                g2d.fillOval(px - 5, py - 5, 10, 10);
                g2d.setColor(new Color(255, 100, 100));
                g2d.fillOval(px - 3, py - 4, 4, 4); // Highlight
                g2d.setColor(Color.BLACK);
                g2d.setStroke(new BasicStroke(1));
                g2d.drawOval(px - 5, py - 5, 10, 10);
            }

            // Draw info text if not connected
            if (!connected) {
                g2d.setColor(new Color(100, 100, 100));
                g2d.setFont(new Font("SansSerif", Font.ITALIC, 14));
                g2d.drawString("Connect to server to view board", 50, 100);
            } else if (visualNotes.isEmpty() && visualPins.isEmpty()) {
                g2d.setColor(new Color(100, 100, 100));
                g2d.setFont(new Font("SansSerif", Font.ITALIC, 12));
                g2d.drawString("Board is empty. POST a note or click Refresh.", offsetX + 10, offsetY + 30);
            }
        }
    }

    /**
     * Main entry point for the client application.
     */
    public static void main(String[] args) {
        // Use system look and feel for better appearance
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Fall back to default
        }

        SwingUtilities.invokeLater(BulletinBoardClient::new);
    }
}
