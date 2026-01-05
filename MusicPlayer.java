

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;

import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.Random;


// -------------------- LOGIN FRAME --------------------
class LoginFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton, registerButton;

    public LoginFrame() {
        super("Login - Music Player");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(400, 220);
        setLayout(new GridBagLayout());
        setResizable(false); // fixed size
        getContentPane().setBackground(new Color(255, 148, 105)); // Light blue (AliceBlue)
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Title label
        JLabel title = new JLabel("🎵 Welcome to Music Player", JLabel.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 16));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        add(title, gbc);

        // Username
        gbc.gridwidth = 1; gbc.gridy = 1; gbc.gridx = 0;
        add(new JLabel("Username:"), gbc);
        usernameField = new JTextField();
        gbc.gridx = 1; gbc.weightx = 1.0;
        add(usernameField, gbc);

        // Password
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        add(new JLabel("Password:"), gbc);
        passwordField = new JPasswordField();
        gbc.gridx = 1; gbc.weightx = 1.0;
        add(passwordField, gbc);

        // Buttons

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        loginButton = new JButton("Login");
        registerButton = new JButton("Register");
        btnPanel.add(loginButton);
        btnPanel.add(registerButton);
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        add(btnPanel, gbc);

        setLocationRelativeTo(null); // center window

        // Listeners
        loginButton.addActionListener(e -> login());
        registerButton.addActionListener(e -> register());

        // Press Enter in password field = login
        passwordField.addActionListener(e -> login());
    }
    // ----------------- Password Hashing -----------------
    private String hashPassword(String password) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Connection connectDB() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:musicplayer.db");
    }

    private void ensureUsersTable() throws SQLException {
        try (Connection conn = connectDB(); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "username TEXT UNIQUE," +
                    "password TEXT)");
        }
    }

    private void login() {
        String user = usernameField.getText().trim();
        String pass = new String(passwordField.getPassword());

        if (user.isEmpty() || pass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter username and password.");
            return;
        }
        try {
            ensureUsersTable();
            try (Connection conn = connectDB();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id FROM users WHERE username=? AND password=?")) {
                ps.setString(1, user);
                ps.setString(2, hashPassword(pass));
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    JOptionPane.showMessageDialog(this, "✅ Login successful!");
                    this.dispose();
                    int userId = rs.getInt("id");
                    SwingUtilities.invokeLater(() -> new MusicPlayer(userId).setVisible(true));
                } else {
                    JOptionPane.showMessageDialog(this, "❌ Invalid username or password.");
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "DB Error: " + ex.getMessage());
        }
    }

    private void register() {
        String user = usernameField.getText().trim();
        String pass = new String(passwordField.getPassword());

        if (user.isEmpty() || pass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter username and password to register.");
            return;
        }
        try {
            ensureUsersTable();
            try (Connection conn = connectDB();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO users (username, password) VALUES (?, ?)")) {
                ps.setString(1, user);
                ps.setString(2, hashPassword(pass));
                ps.executeUpdate();
                JOptionPane.showMessageDialog(this, "🎉 Registered! You can log in now.");
            }
        } catch (SQLException ex) {
            if (ex.getMessage().contains("UNIQUE")) {
                JOptionPane.showMessageDialog(this, "⚠ Username already exists.");
            } else {
                JOptionPane.showMessageDialog(this, "DB Error: " + ex.getMessage());
            }
        }
    }
}
// -------------------- MUSIC PLAYER --------------------
public class MusicPlayer extends JFrame {

    // UI
    private DefaultListModel<File> playlistModel = new DefaultListModel<>();
    private JList<File> playlist = new JList<>(playlistModel);
    private JTextField searchField = new JTextField(15);
    private JButton resetBtn  = new JButton("🔄");
    private JButton searchBtn = new JButton("🔍");
    private JButton playBtn   = new JButton("▶");
    private JButton pauseBtn  = new JButton("⏸");
    private JButton stopBtn   = new JButton("⏹");
    private JButton nextBtn   = new JButton("⏭");
    private JButton prevBtn   = new JButton("⏮");
    private JButton shuffleBtn= new JButton("🔀");
    private JButton repeatBtn = new JButton("🔁");
    private JButton addBtn    = new JButton("➕");
    private JButton removeBtn = new JButton("➖");
    private JButton saveBtn   = new JButton("💾");
    private JButton loadBtn   = new JButton("⏏");



    private JSlider volumeSlider = new JSlider(0, 100, 70);

    // New UI for song info
    private JLabel songLabel = new JLabel("No song playing");
    private JLabel timeLabel = new JLabel("00:00 / 00:00");
    private JProgressBar progressBar = new JProgressBar(0, 1000);
    private javax.swing.Timer progressTimer; // Swing Timer


    // Playback state
    private Clip clip;
    private boolean isPaused = false;
    private long pauseMicroseconds = 0;
    private int currentIndex = -1;
    private boolean isRepeat = false;
    private Random random = new Random();
    private FloatControl gainControl = null;
    private DefaultListModel<File> originalPlaylist = new DefaultListModel<>();
    private int storedVolume = 70;

    private int currentUserId;
    // Constructor for logged-in users
    public MusicPlayer(int userId) {
        this(); // call default constructor
        this.currentUserId = userId;
    }

    private Connection connectDB() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            JOptionPane.showMessageDialog(this, "SQLite JDBC Driver not found!");
            e.printStackTrace();
            return null;
        }

        try {
            String url = "jdbc:sqlite:musicplayer.db";
            Connection conn = DriverManager.getConnection(url);

            // Ensure playlist table exists
            String createTableSQL = "CREATE TABLE IF NOT EXISTS playlist (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT," +
                    "path TEXT)";
            Statement stmt = conn.createStatement();
            stmt.execute(createTableSQL);

            // ✅ Add user_id column if it doesn't exist
            try {
                stmt.execute("ALTER TABLE playlist ADD COLUMN user_id INTEGER");
            } catch (SQLException ignored) {
                // Column already exists — ignore
            }

            stmt.close();
            return conn;
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Failed to connect to database: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }



    public MusicPlayer() {
        super("Music Player");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(800, 500);
        setLayout(new BorderLayout(8, 8));
        getContentPane().setBackground(new Color(255, 200, 145)); // Light gray

        // Playlist area
        playlist.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        playlist.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                String name = (value instanceof File) ? ((File) value).getName() : String.valueOf(value);
                super.getListCellRendererComponent(list, name, index, isSelected, cellHasFocus);
                return this;
            }
        });
        resetBtn.setToolTipText("Reset Playlist");
        searchBtn.setToolTipText("Search Songs");
        playBtn.setToolTipText("Play");
        pauseBtn.setToolTipText("Pause/Resume");
        stopBtn.setToolTipText("Stop");
        nextBtn.setToolTipText("Next Song");
        prevBtn.setToolTipText("Previous Song");
        shuffleBtn.setToolTipText("Shuffle Mode");
        repeatBtn.setToolTipText("Repeat Mode");
        addBtn.setToolTipText("Add Songs");
        removeBtn.setToolTipText("Remove Song");
        saveBtn.setToolTipText("Save Playlist");
        loadBtn.setToolTipText("Load Playlist");


        JPanel centerWrapper = new JPanel(new BorderLayout());
        centerWrapper.add(new JScrollPane(playlist), BorderLayout.CENTER);
        add(centerWrapper, BorderLayout.CENTER);
        JPanel topPanel = new JPanel(new BorderLayout());

// --- Search panel ---
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        searchPanel.add(new JLabel("Search:"));
        searchPanel.add(searchField);
        searchPanel.add(searchBtn);
        searchPanel.add(resetBtn);

// --- Volume panel ---
        JPanel volPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        volPanel.add(new JLabel("Volume"));
        volPanel.add(volumeSlider);
        topPanel.add(volPanel, BorderLayout.WEST);

// --- Info panel for song + time ---
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.add(songLabel, BorderLayout.CENTER);
        infoPanel.add(timeLabel, BorderLayout.EAST);
        topPanel.add(infoPanel, BorderLayout.CENTER);

// --- Add search panel below topPanel ---
        JPanel northWrapper = new JPanel(new BorderLayout());
        northWrapper.add(topPanel, BorderLayout.CENTER);
        northWrapper.add(searchPanel, BorderLayout.SOUTH);

// Add to main frame
        add(northWrapper, BorderLayout.NORTH);



        // Progress bar
        progressBar.setValue(0);
        progressBar.setStringPainted(false);
        topPanel.add(progressBar, BorderLayout.SOUTH);




        // Controls
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 6));
        controls.add(prevBtn);
        controls.add(playBtn);
        controls.add(pauseBtn);
        controls.add(stopBtn);
        controls.add(nextBtn);
        controls.add(shuffleBtn);
        controls.add(repeatBtn);
        controls.add(addBtn);
        controls.add(removeBtn);
        controls.add(saveBtn);
        controls.add(loadBtn);
        add(controls, BorderLayout.SOUTH);

        // Listeners
        // ---------- Logo ----------


// ---------- Center wrapper: logo + playlist ----------

        playlist.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        addBtn.addActionListener(e -> addSongs());
        playBtn.addActionListener(e -> playSelectedOrCurrent());
        pauseBtn.addActionListener(e -> togglePause());
        stopBtn.addActionListener(e -> stopSong());
        nextBtn.addActionListener(e -> nextSong());
        prevBtn.addActionListener(e -> prevSong());
        shuffleBtn.addActionListener(e -> shuffleAndPlay());
        repeatBtn.addActionListener(e -> toggleRepeat());
        removeBtn.addActionListener(e -> removeSelectedSongs());
        saveBtn.addActionListener(e -> savePlaylist());
        loadBtn.addActionListener(e -> loadPlaylist());
        searchBtn.addActionListener(e -> searchSong());
        resetBtn.addActionListener(e -> resetPlaylist());

        // Double click to play
        playlist.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int idx = playlist.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        currentIndex = idx;
                        playSongAtIndex(currentIndex);
                    }
                }
            }
        });

        // Volume control
        volumeSlider.addChangeListener((ChangeEvent e) -> {
            storedVolume = volumeSlider.getValue();
            applyVolume();
        });

        // --- SEEKING FEATURE ---
        progressBar.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                seekTo(e.getX());
            }
        });

        progressBar.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                seekTo(e.getX());
            }
        });


        // center window
        setLocationRelativeTo(null);
    }

    // ---------- Seeking ----------
    private void seekTo(int mouseX) {
        if (clip != null && clip.isOpen()) {
            int progressBarVal = (int) Math.round(((double) mouseX / progressBar.getWidth()) * progressBar.getMaximum());
            progressBar.setValue(progressBarVal);

            long total = clip.getMicrosecondLength();
            long newPos = (long) ((progressBarVal / 1000.0) * total);

            clip.setMicrosecondPosition(newPos);
            if (!isPaused) {
                clip.start();
            }
        }
    }
    private void removeSelectedSongs() {
        int[] selectedIndices = playlist.getSelectedIndices();
        if (selectedIndices.length == 0) {
            JOptionPane.showMessageDialog(this, "No songs selected to remove.");
            return;
        }

        String message = "Remove " + selectedIndices.length + " song(s) from playlist permanently?";
        int confirm = JOptionPane.showConfirmDialog(this, message, "Confirm Remove", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        try (Connection conn = connectDB()) {
            if (conn == null) return;

            String sql = "DELETE FROM playlist WHERE user_id = ? AND path = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                // Remove from highest index to lowest to avoid shifting
                for (int i = selectedIndices.length - 1; i >= 0; i--) {
                    int idx = selectedIndices[i];
                    File f = playlistModel.get(idx);

                    // Stop song if currently playing
                    if (currentIndex == idx) {
                        stopSong();
                        currentIndex = -1;
                    } else if (currentIndex > idx) {
                        currentIndex--;
                    }

                    // Delete from database
                    ps.setInt(1, currentUserId);
                    ps.setString(2, f.getAbsolutePath());
                    ps.executeUpdate();

                    // Remove from UI
                    playlistModel.remove(idx);
                }
            }

            JOptionPane.showMessageDialog(this, "Selected song(s) removed from playlist!");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "DB Error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // ---------- File handling ----------
    private void addSongs() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle("Select audio files (WAV recommended)");
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File[] files = chooser.getSelectedFiles();
            for (File f : files) {
                playlistModel.addElement(f);
            }
            for (File f : files) {
                originalPlaylist.addElement(f);
            }
        }
    }

    // Save playlist to DB
    private void savePlaylist() {
        if (playlistModel.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Playlist is empty.");
            return;
        }

        try (Connection conn = connectDB()) {
            if (conn == null) return;


            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM playlist WHERE user_id = ?")) {
                stmt.setInt(1, currentUserId);
                stmt.executeUpdate();
            }
            String sql = "INSERT INTO playlist (user_id, name, path) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < playlistModel.size(); i++) {
                    File f = playlistModel.get(i);
                    ps.setInt(1, currentUserId);
                    ps.setString(2, f.getName());
                    ps.setString(3, f.getAbsolutePath());
                    ps.executeUpdate();
                }
            }

            JOptionPane.showMessageDialog(this, "Playlist saved");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "DB Error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void loadPlaylist() {
        try (Connection conn = connectDB()) {
            if (conn == null) return;

            String sql = "SELECT name, path FROM playlist WHERE user_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, currentUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    playlistModel.clear();
                    boolean missingFiles = false;

                    while (rs.next()) {
                        String path = rs.getString("path");
                        File f = new File(path);
                        if (f.exists()) {
                            playlistModel.addElement(f);
                        } else {
                            missingFiles = true;
                        }
                    }
                    if (playlistModel.isEmpty()) {
                        JOptionPane.showMessageDialog(this, "Playlist is empty.");
                    } else {
                        JOptionPane.showMessageDialog(this, "Playlist loaded");
                    }
                    // Backup current playlist for reset
                    originalPlaylist.clear();
                    for (int i = 0; i < playlistModel.size(); i++) {
                        originalPlaylist.addElement(playlistModel.get(i));
                    }

                    if (missingFiles) {
                        JOptionPane.showMessageDialog(this,
                                "⚠ Some files were missing and could not be loaded.",
                                "file not found",
                                JOptionPane.WARNING_MESSAGE);
                    }
                }
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "DB Error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
    private void searchSong() {
        String keyword = searchField.getText().trim();
        if (keyword.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a song name to search.");
            return;
        }

        try (Connection conn = connectDB()) {
            if (conn == null) return;

            String sql = "SELECT name, path FROM playlist WHERE user_id = ? " +
                    "AND (name LIKE ? OR name LIKE ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, currentUserId);
                ps.setString(2, keyword + "%");      // songs starting with keyword
                ps.setString(3, "%" + keyword + "%"); // songs containing keyword

                try (ResultSet rs = ps.executeQuery()) {
                    playlistModel.clear();
                    boolean found = false;

                    while (rs.next()) {
                        String path = rs.getString("path");
                        File f = new File(path);
                        if (f.exists()) {
                            playlistModel.addElement(f);
                            found = true;
                        }
                    }

                    if (!found) {
                        JOptionPane.showMessageDialog(this, "No matching songs found.");
                    }
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "DB Error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
    private void resetPlaylist() {
        playlistModel.clear();
        for (int i = 0; i < originalPlaylist.size(); i++) {
            playlistModel.addElement(originalPlaylist.get(i));
        }
    }
    private void playSelectedOrCurrent() {
        if (!playlist.isSelectionEmpty()) {
            currentIndex = playlist.getSelectedIndex();
        }
        if (currentIndex == -1 && !playlistModel.isEmpty()) {
            currentIndex = 0;
        }
        if (currentIndex >= 0) playSongAtIndex(currentIndex);
        else JOptionPane.showMessageDialog(this, "No song selected or in playlist.");
    }
    private void playSongAtIndex(int index) {
        if (index < 0 || index >= playlistModel.size()) return;

        File f = playlistModel.get(index);
        if (!f.exists()) {
            JOptionPane.showMessageDialog(this, "File not found: " + f.getAbsolutePath());
            return;
        }

        try {
            if (clip != null) {
                clip.stop();
                clip.close();
            }
            AudioInputStream ais = AudioSystem.getAudioInputStream(f);
            clip = AudioSystem.getClip();
            clip.open(ais);

            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            } else {
                gainControl = null;
            }
            isPaused = false;
            pauseMicroseconds = 0;
            currentIndex = index;
            applyVolume();
            clip.start();
            setTitle("Playing: " + f.getName());
            playlist.setSelectedIndex(index);
            playlist.ensureIndexIsVisible(index);
            songLabel.setText("Playing: " + f.getName());
            updateTimeLabels(0, clip.getMicrosecondLength());
            if (progressTimer != null) progressTimer.stop();
            progressTimer = new javax.swing.Timer(1000, e -> {
                if (clip != null && clip.isOpen()) {
                    long current = clip.getMicrosecondPosition();
                    long total = clip.getMicrosecondLength();
                    updateTimeLabels(current, total);

                    int progress = (int) ((current * 1000) / total);
                    progressBar.setValue(progress);
                }
            });
            progressTimer.start();
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    if (!isPaused) {
                        long pos = clip.getMicrosecondPosition();
                        long len = clip.getMicrosecondLength();
                        if (len > 0 && pos >= len - 2000) {
                            SwingUtilities.invokeLater(() -> {
                                if (isRepeat) {
                                    playSongAtIndex(currentIndex);
                                } else {
                                    nextSong();
                                }
                            });
                        }
                    }
                }
            });
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Playback error: " + ex.getMessage());
        }
    }
    private void togglePause() {
        if (clip == null) return;
        if (clip.isRunning()) {
            pauseMicroseconds = clip.getMicrosecondPosition();
            clip.stop();
            isPaused = true;
            setTitle("Paused: " + (currentIndex >= 0 ? playlistModel.get(currentIndex).getName() : ""));
        } else {
            clip.setMicrosecondPosition(pauseMicroseconds);
            clip.start();
            isPaused = false;
            setTitle("Playing: " + (currentIndex >= 0 ? playlistModel.get(currentIndex).getName() : ""));
        }
    }
    private void stopSong() {
        if (clip != null) {
            clip.stop();
            clip.setMicrosecondPosition(0);
            isPaused = false;
        }
        if (progressTimer != null) progressTimer.stop();
        progressBar.setValue(0);
        timeLabel.setText("00:00 / 00:00");
        songLabel.setText("Stopped");
        setTitle("Stopped");
    }
    private void nextSong() {
        if (playlistModel.isEmpty()) return;
        currentIndex = (currentIndex + 1) % playlistModel.size();
        playSongAtIndex(currentIndex);
    }
    private void prevSong() {
        if (playlistModel.isEmpty()) return;
        currentIndex = (currentIndex - 1 + playlistModel.size()) % playlistModel.size();
        playSongAtIndex(currentIndex);
    }
    private void shuffleAndPlay() {
        if (playlistModel.isEmpty()) return;
        currentIndex = random.nextInt(playlistModel.size());
        playSongAtIndex(currentIndex);
    }
    private void toggleRepeat() {
        isRepeat = !isRepeat;
        repeatBtn.setText("Repeat: " + (isRepeat ? "ON" : "OFF"));
    }
    private void applyVolume() {
        if (gainControl != null) {
            float min = gainControl.getMinimum();
            float max = gainControl.getMaximum();
            float range = max - min;
            float gain = min + (range * (storedVolume / 100.0f));
            try {
                gainControl.setValue(gain);
            } catch (IllegalArgumentException ignored) {}
        }
    }
    private void updateTimeLabels(long currentMicros, long totalMicros) {
        String cur = formatTime(currentMicros);
        String tot = formatTime(totalMicros);
        timeLabel.setText(cur + " / " + tot);
    }
    private String formatTime(long microseconds) {
        long seconds = microseconds / 1_000_000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
