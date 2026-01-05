import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;

public class LoginFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton, registerButton;

    public LoginFrame() {
        super("Login - Music Player");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(400, 180);
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6,6,6,6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Username
        gbc.gridx = 0; gbc.gridy = 0;
        add(new JLabel("Username:"), gbc);
        usernameField = new JTextField();
        gbc.gridx = 1; gbc.weightx = 1.0;
        add(usernameField, gbc);

        // Password
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        add(new JLabel("Password:"), gbc);
        passwordField = new JPasswordField();
        gbc.gridx = 1; gbc.weightx = 1.0;
        add(passwordField, gbc);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        loginButton = new JButton("Login");
        registerButton = new JButton("Register");
        btnPanel.add(loginButton);
        btnPanel.add(registerButton);
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.weightx = 0;
        add(btnPanel, gbc);

        setLocationRelativeTo(null); // center

        // Listeners
        loginButton.addActionListener(e -> login());
        registerButton.addActionListener(e -> register());

        // Enter key triggers login
        passwordField.addActionListener(e -> login());
    }

    // DB helper (same DB file used by MusicPlayer)
    private Connection connectDB() throws SQLException {
        String url = "jdbc:sqlite:musicplayer.db";
        return DriverManager.getConnection(url);
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
                     "SELECT id FROM users WHERE username = ? AND password = ?")) {
                ps.setString(1, user);
                ps.setString(2, pass);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    JOptionPane.showMessageDialog(this, "Login successful!");
                    this.dispose(); // close login
                    SwingUtilities.invokeLater(() -> new MusicPlayer().setVisible(true));
                } else {
                    JOptionPane.showMessageDialog(this, "Invalid username or password.");
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
                ps.setString(2, pass);
                ps.executeUpdate();
                JOptionPane.showMessageDialog(this, "Registration successful. Now log in.");
            }
        } catch (SQLException ex) {
            if (ex.getMessage().contains("UNIQUE")) {
                JOptionPane.showMessageDialog(this, "Username already exists. Choose another.");
            } else {
                JOptionPane.showMessageDialog(this, "DB Error: " + ex.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}