package com.bia10.herblore;

import com.tonic.Static;
import com.tonic.model.ui.components.FancyCard;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;

public class Serum207MakerSidePanel extends PluginPanel {
    private final JLabel timerLabel;
    private final JLabel statusLabel;

    // --- METRIC LABELS ---
    private final JLabel potionsMadeLabel;
    private final JLabel potionsPerHourLabel;
    private final JLabel potionsPerMinuteLabel;
    private final JLabel potionsPerSecondLabel;
    private final JLabel potionsPerTickLabel;
    private final JLabel materialsConsumedLabel;
    private final JLabel cycleTicksLabel;

    private final JButton startStopButton;

    private final Timer timer;
    private long startTime;
    private boolean isRunning = false;
    private long totalPotionsMade = 0;
    private int lastCycleTicks = 0;

    private static final int MATERIALS_PER_POTION = 2;
    private static final double GAME_TICK_MS = 600.0;
    private static final NumberFormat FORMATTER = new DecimalFormat("#,###");
    private static final NumberFormat DECIMAL_FORMATTER = new DecimalFormat("0.00");
    @Inject
    public Serum207MakerSidePanel(Serum207MakerConfig config)
    {
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 10, 0);
        // --- Card Header ---
        FancyCard card = new FancyCard("Serum 207 Maker", "Serum 207 automation.");
        add(card, c);
        c.gridy++;

        // --- Timer Display ---
        JPanel timerPanel = new JPanel();
        timerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        timerPanel.setBorder(new EmptyBorder(15, 10, 15, 10));
        timerPanel.setLayout(new BorderLayout());

        timerLabel = new JLabel("00:00:00", SwingConstants.CENTER);
        Color TIMER_COLOR = new Color(0, 180, 255);
        timerLabel.setForeground(TIMER_COLOR);
        timerLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 24));
        timerPanel.add(timerLabel, BorderLayout.CENTER);
        add(timerPanel, c);
        c.gridy++;
        // --- Status Label ---
        statusLabel = new JLabel("Ready", SwingConstants.CENTER);
        statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        JPanel statusPanel = new JPanel();
        statusPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        statusPanel.setBorder(new EmptyBorder(5, 10, 5, 10));
        statusPanel.add(statusLabel);
        add(statusPanel, c);
        c.gridy++;

        // --- Statistics Panel ---
        JPanel statsPanel = new JPanel(new GridLayout(8, 2));
        statsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        statsPanel.setBorder(new EmptyBorder(5, 10, 5, 10));

        // 1. Total Potions Made
        statsPanel.add(createStatLabel("Potions Made:"));
        potionsMadeLabel = createStatValueLabel("0");
        statsPanel.add(potionsMadeLabel);

        // 2. Potions Per Hour (Rate)
        statsPanel.add(createStatLabel("Potions/hr:"));
        potionsPerHourLabel = createStatValueLabel("0");
        statsPanel.add(potionsPerHourLabel);

        // 3. Potions Per Minute (New)
        statsPanel.add(createStatLabel("Potions/min:"));
        potionsPerMinuteLabel = createStatValueLabel("0");
        statsPanel.add(potionsPerMinuteLabel);

        // 4. Potions Per Second (New)
        statsPanel.add(createStatLabel("Potions/sec:"));
        potionsPerSecondLabel = createStatValueLabel("0");
        statsPanel.add(potionsPerSecondLabel);

        // 5. Potions Per Tick (New)
        statsPanel.add(createStatLabel("Potions/tick:"));
        potionsPerTickLabel = createStatValueLabel("0");
        statsPanel.add(potionsPerTickLabel);

        // 6. Materials Consumed
        statsPanel.add(createStatLabel("Materials Consumed:"));
        materialsConsumedLabel = createStatValueLabel("0");
        statsPanel.add(materialsConsumedLabel);

        // 7. Last Cycle Ticks (Speed Measurement)
        statsPanel.add(createStatLabel("Last Cycle Ticks:"));
        cycleTicksLabel = createStatValueLabel("0");
        statsPanel.add(cycleTicksLabel);

        c.insets = new Insets(10, 0, 10, 0);
        add(statsPanel, c);
        c.gridy++;
        c.insets = new Insets(0, 0, 10, 0);
        // --- Start/Stop Button ---
        startStopButton = new JButton("Start");
        startStopButton.setBackground(new Color(50, 205, 50)); // Lime Green
        startStopButton.setForeground(Color.WHITE);
        startStopButton.setFocusable(false);
        startStopButton.addActionListener(e -> toggleTimer());
        c.insets = new Insets(15, 0, 0, 0);
        add(startStopButton, c);
        c.gridy++;
        // --- Spacer ---
        timer = new Timer(100, e -> updateTimerDisplay());
        c.weighty = 1;
        c.insets = new Insets(0, 0, 0, 0);
        add(new JPanel(), c);
    }

    /** Helper to create a descriptive stat label */
    private JLabel createStatLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        return label;
    }

    /** Helper to create a value stat label */
    private JLabel createStatValueLabel(String text) {
        JLabel label = new JLabel(text, SwingConstants.RIGHT);
        label.setForeground(new Color(0, 255, 0)); // Green for value
        label.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        return label;
    }

    /**
     * Updates the statistics displayed on the side panel.
     * @param totalPotionsMade The total number of Serum 207 (3 dose) made.
     * @param lastCycleTicks The tick count of the last completed banking/crafting cycle.
     */
    public void updateStats(long totalPotionsMade, int lastCycleTicks)
    {
        this.totalPotionsMade = totalPotionsMade;
        this.lastCycleTicks = lastCycleTicks;

        SwingUtilities.invokeLater(() -> {
            potionsMadeLabel.setText(FORMATTER.format(totalPotionsMade));
            long totalMaterialsConsumed = totalPotionsMade * MATERIALS_PER_POTION;
            materialsConsumedLabel.setText(FORMATTER.format(totalMaterialsConsumed));

            // Display cycle speed
            if (lastCycleTicks > 0) {
                cycleTicksLabel.setText(String.valueOf(lastCycleTicks));
            } else {
                cycleTicksLabel.setText("0");
            }

            long elapsedMillis = System.currentTimeMillis() - startTime;

            if (elapsedMillis > 1000 && totalPotionsMade > 0) {
                // Calculate all elapsed time units
                double elapsedHours = (double) elapsedMillis / (1000 * 60 * 60);
                double elapsedMinutes = (double) elapsedMillis / (1000 * 60);
                double elapsedSeconds = (double) elapsedMillis / 1000;
                double elapsedTicks = (double) elapsedMillis / GAME_TICK_MS;

                // 1. Potions Per Hour
                long potionsPerHour = Math.round(totalPotionsMade / elapsedHours);
                potionsPerHourLabel.setText(FORMATTER.format(potionsPerHour));

                // 2. Potions Per Minute
                long potionsPerMinute = Math.round(totalPotionsMade / elapsedMinutes);
                potionsPerMinuteLabel.setText(FORMATTER.format(potionsPerMinute));

                // 3. Potions Per Second
                double potionsPerSecond = totalPotionsMade / elapsedSeconds;
                potionsPerSecondLabel.setText(DECIMAL_FORMATTER.format(potionsPerSecond));

                // 4. Potions Per Tick
                double potionsPerTick = totalPotionsMade / elapsedTicks;
                potionsPerTickLabel.setText(DECIMAL_FORMATTER.format(potionsPerTick));

            } else {
                potionsPerHourLabel.setText("0");
                potionsPerMinuteLabel.setText("0");
                potionsPerSecondLabel.setText("0");
                potionsPerTickLabel.setText("0");
            }
        });
    }

    // --- Timer and Logic Control ---

    private void toggleTimer()
    {
        Client client = Static.getClient();
        if (client.getGameState() != GameState.LOGGED_IN && client.getGameState() != GameState.LOADING)
        {
            updateStatus("Error: Not logged in", new Color(255, 100, 100));
            return;
        }

        if (isRunning)
        {
            isRunning = false;
            timer.stop();
            startStopButton.setText("Start");
            startStopButton.setBackground(new Color(50, 205, 50));
            updateStatus("Stopped", ColorScheme.LIGHT_GRAY_COLOR);
        }
        else
        {
            isRunning = true;
            startTime = System.currentTimeMillis();
            totalPotionsMade = 0;
            lastCycleTicks = 0;
            timer.start();
            startStopButton.setText("Stop");
            startStopButton.setBackground(new Color(255, 100, 100));
            updateStatus("Running...", new Color(0, 255, 0));
        }
    }

    private void updateTimerDisplay()
    {
        if (!isRunning) return;
        Client client = Static.getClient();
        if (client.getGameState() != GameState.LOGGED_IN && client.getGameState() != GameState.LOADING)
        {
            updateStatus("Error: Not logged in", new Color(255, 100, 100));
            return;
        }

        long elapsedMillis = System.currentTimeMillis() - startTime;
        String formattedTime = formatTime(elapsedMillis);
        SwingUtilities.invokeLater(() -> timerLabel.setText(formattedTime));

        updateStats(totalPotionsMade, lastCycleTicks);
    }

    private String formatTime(long millis)
    {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        seconds = seconds % 60;
        minutes = minutes % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public void updateStatus(String message, Color color)
    {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(message);
            statusLabel.setForeground(color);
        });
    }

    public boolean isRunning()
    {
        return isRunning;
    }

    public void forceStop()
    {
        if (isRunning)
        {
            isRunning = false;
            timer.stop();
            startStopButton.setText("Start");
            startStopButton.setBackground(new Color(50, 205, 50));
            updateStatus("Stopped", ColorScheme.LIGHT_GRAY_COLOR);
        }
    }

    public void setTotalPotionsMade(long count)
    {
        this.totalPotionsMade = count;
    }

    public void setLastCycleTicks(int ticks)
    {
        this.lastCycleTicks = ticks;
    }

    public void shutdown()
    {
        if (timer != null)
        {
            timer.stop();
        }
    }
}