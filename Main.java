import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;

public class MusicPlayer extends JFrame {
    // UI Components
    private JButton playBtn, pauseBtn, stopBtn, nextBtn, prevBtn, shuffleBtn, repeatBtn, addBtn, saveBtn, loadBtn;
    private JList<String> playlist;
    private DefaultListModel<String> playlistModel;
    private JSlider volumeSlider;

    // Playback
    private Clip clip;
    private int currentIndex = -1;
    private boolean isPaused = false;
    private boolean isRepeat = false;
    private Random random = new Random();

    public MusicPlayer() {
        setTitle("Music Player with Playlist");
        setSize(700, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Playlist
        playlistModel = new DefaultListModel<>();
        playlist = new JList<>(playlistModel);
        add(new JScrollPane(playlist), BorderLayout.CENTER);

        // Controls
        JPanel controls = new JPanel();
        prevBtn = new JButton("Previous");
        playBtn = new JButton("Play");
        pauseBtn = new JButton("Pause");
        stopBtn = new JButton("Stop");
        nextBtn = new JButton("Next");
        shuffleBtn = new JButton("Shuffle");
        repeatBtn = new JButton("Repeat");
        addBtn = new JButton("Add Songs");
        saveBtn = new JButton("Save Playlist");
        loadBtn = new JButton("Load Playlist");

        controls.add(prevBtn);
        controls.add(playBtn);
        controls.add(pauseBtn);
        controls.add(stopBtn);
        controls.add(nextBtn);
        controls.add(shuffleBtn);
        controls.add(repeatBtn);
        controls.add(addBtn);
        controls.add(saveBtn);
        controls.add(loadBtn);

        add(controls, BorderLayout.SOUTH);

        // Volume
        volumeSlider = new JSlider(0, 100, 50);
        add(volumeSlider, BorderLayout.NORTH);

        // Button Listeners
        addBtn.addActionListener(e -> addSongs());
        playBtn.addActionListener(e -> playSelectedSong());
        pauseBtn.addActionListener(e -> pauseSong());
        stopBtn.addActionListener(e -> stopSong());
        nextBtn.addActionListener(e -> nextSong());
        prevBtn.addActionListener(e -> prevSong());
        shuffleBtn.addActionListener(e -> shuffleSong());
        repeatBtn.addActionListener(e -> toggleRepeat());
        saveBtn.addActionListener(e -> savePlaylist());
        loadBtn.addActionListener(e -> loadPlaylist());

        // Volume control
        volumeSlider.addChangeListener(e -> adjustVolume());
    }

    // ---------- FILE HANDLING ----------
    private void addSongs() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(true);
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            for (File file : fileChooser.getSelectedFiles()) {
                playlistModel.addElement(file.getAbsolutePath());
            }
        }
    }

    private void savePlaylist() {
        try (PrintWriter writer = new PrintWriter("playlist.txt")) {
            for (int i = 0; i < playlistModel.size(); i++) {
                writer.println(playlistModel.get(i));
            }
            JOptionPane.showMessageDialog(this, "Playlist saved!");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error saving playlist.");
        }
    }

    private void loadPlaylist() {
        try (BufferedReader reader = new BufferedReader(new FileReader("playlist.txt"))) {
            playlistModel.clear();
            String line;
            while ((line = reader.readLine()) != null) {
                playlistModel.addElement(line);
            }
            JOptionPane.showMessageDialog(this, "Playlist loaded!");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error loading playlist.");
        }
    }

    // ---------- PLAYBACK ----------
    private void playSelectedSong() {
        if (playlist.isSelectionEmpty() && currentIndex == -1) {
            JOptionPane.showMessageDialog(this, "Please select a song first!");
            return;
        }

        if (!playlist.isSelectionEmpty()) {
            currentIndex = playlist.getSelectedIndex();
        }

        playSongAtIndex(currentIndex);
    }

    private void playSongAtIndex(int index) {
        if (index < 0 || index >= playlistModel.size()) return;

        try {
            if (clip != null && clip.isRunning()) clip.stop();
            if (clip != null) clip.close();

            File file = new File(playlistModel.get(index));
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(file);
            clip = AudioSystem.getClip();
            clip.open(audioStream);
            clip.start();

            adjustVolume();

            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP && !isPaused) {
                    if (isRepeat) {
                        playSongAtIndex(currentIndex);
                    } else {
                        nextSong();
                    }
                }
            });

            setTitle("Playing: " + file.getName());
        } catch (UnsupportedAudioFileException e) {
            JOptionPane.showMessageDialog(this, "Unsupported audio file!");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "File not found!");
        } catch (LineUnavailableException e) {
            JOptionPane.showMessageDialog(this, "Audio line unavailable!");
        }
    }

    private void pauseSong() {
        if (clip != null && clip.isRunning()) {
            clip.stop();
            isPaused = true;
        } else if (clip != null && isPaused) {
            clip.start();
            isPaused = false;
        }
    }

    private void stopSong() {
        if (clip != null) {
            clip.stop();
            clip.setFramePosition(0);
        }
    }

    private void nextSong() {
        if (playlistModel.size() == 0) return;
        currentIndex = (currentIndex + 1) % playlistModel.size();
        playSongAtIndex(currentIndex);
    }

    private void prevSong() {
        if (playlistModel.size() == 0) return;
        currentIndex = (currentIndex - 1 + playlistModel.size()) % playlistModel.size();
        playSongAtIndex(currentIndex);
    }

    private void shuffleSong() {
        if (playlistModel.size() == 0) return;
        currentIndex = random.nextInt(playlistModel.size());
        playSongAtIndex(currentIndex);
    }

    private void toggleRepeat() {
        isRepeat = !isRepeat;
        JOptionPane.showMessageDialog(this, "Repeat is now " + (isRepeat ? "ON" : "OFF"));
    }

    private void adjustVolume() {
        if (clip != null) {
            FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float range = gainControl.getMaximum() - gainControl.getMinimum();
            float gain = (range * volumeSlider.getValue() / 100f) + gainControl.getMinimum();
            gainControl.setValue(gain);
        }
    }

    // ---------- MAIN ----------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MusicPlayer().setVisible(true));
    }
}
