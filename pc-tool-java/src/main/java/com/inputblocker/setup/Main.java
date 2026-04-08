package com.inputblocker.setup;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class Main extends JFrame {
    private ADBHelper adb;
    private List<Region> regions = new ArrayList<>();
    private Image screenImage;
    private boolean isConnected = false;

    private JLabel statusLabel;
    private JLabel regionCountLabel;
    private JCheckBox enabledCheckBox;
    private JCheckBox crashProtectionCheckBox;
    private JPanel canvasPanel;

    private Region currentDrawRegion;
    private boolean isDrawing = false;
    private int drawStartX, drawStartY;

    private static final Color REGION_FILL = new Color(77, 150, 242, 95);
    private static final Color REGION_STROKE = new Color(33, 150, 242);
    private static final Color DRAW_FILL = new Color(77, 255, 87, 34);
    private static final Color DRAW_STROKE = new Color(255, 87, 34);

    public static final String VERSION = "1.0.0";

    public Main() {
        setTitle("InputBlocker Setup - by Laviesss");
        setSize(650, 900);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);

        initComponents();
        connectToDevice();
        loadExistingRegions();
        checkForUpdates();
    }

    private void checkForUpdates() {
        UpdateChecker checker = new UpdateChecker(VERSION, this);
        checker.setCallback(new UpdateChecker.UpdateCallback() {
            @Override
            public void onUpdateAvailable(String version, String releaseUrl, String currentVersion) {
                SwingUtilities.invokeLater(() -> {
                    int result = JOptionPane.showConfirmDialog(
                        Main.this,
                        "A new version (" + version + ") is available!\n\nYou have: " + currentVersion + "\n\nWould you like to download it?",
                        "Update Available",
                        JOptionPane.YES_NO_OPTION
                    );
                    
                    if (result == JOptionPane.YES_OPTION) {
                        openUrl(releaseUrl);
                    }
                });
            }

            @Override
            public void onNoUpdateAvailable(String currentVersion) {
                System.out.println("You have the latest version (" + currentVersion + ")");
            }

            @Override
            public void onError(String error) {
                System.out.println("Update check failed: " + error);
            }
        });
        
        new Thread(checker).start();
    }
    
    private void openUrl(String url) {
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel();
        topPanel.setLayout(null);
        topPanel.setPreferredSize(new Dimension(650, 60));
        topPanel.setBackground(new Color(45, 45, 45));
        add(topPanel, BorderLayout.NORTH);

        statusLabel = new JLabel("Connecting...");
        statusLabel.setBounds(10, 5, 200, 25);
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        topPanel.add(statusLabel);

        regionCountLabel = new JLabel("Regions: 0");
        regionCountLabel.setBounds(10, 30, 100, 20);
        regionCountLabel.setForeground(Color.LIGHT_GRAY);
        topPanel.add(regionCountLabel);

        enabledCheckBox = new JCheckBox("Blocking Enabled");
        enabledCheckBox.setBounds(180, 5, 150, 25);
        enabledCheckBox.setForeground(Color.WHITE);
        enabledCheckBox.setBackground(new Color(45, 45, 45));
        enabledCheckBox.setSelected(true);
        topPanel.add(enabledCheckBox);

        crashProtectionCheckBox = new JCheckBox("Crash Protection");
        crashProtectionCheckBox.setBounds(180, 30, 150, 25);
        crashProtectionCheckBox.setForeground(Color.WHITE);
        crashProtectionCheckBox.setBackground(new Color(45, 45, 45));
        crashProtectionCheckBox.setSelected(true);
        topPanel.add(crashProtectionCheckBox);

        canvasPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(new Color(45, 45, 45));
                g.fillRect(0, 0, getWidth(), getHeight());

                if (screenImage != null) {
                    g.drawImage(screenImage, 0, 0, getWidth(), getHeight(), null);
                }

                Graphics2D g2 = (Graphics2D) g;
                g2.setStroke(new BasicStroke(3));

                for (int i = 0; i < regions.size(); i++) {
                    Region r = regions.get(i);
                    int x1 = r.getLeft();
                    int y1 = r.getTop();
                    int x2 = r.getRight();
                    int y2 = r.getBottom();

                    g2.setColor(REGION_FILL);
                    g2.fillRect(x1, y1, x2 - x1, y2 - y1);
                    g2.setColor(REGION_STROKE);
                    g2.drawRect(x1, y1, x2 - x1, y2 - y1);

                    g2.setColor(REGION_STROKE);
                    g2.fillOval((x1 + x2) / 2 - 15, (y1 + y2) / 2 - 15, 30, 30);
                    g2.setColor(Color.WHITE);
                    g2.drawString(String.valueOf(i + 1), (x1 + x2) / 2 - 4, (y1 + y2) / 2 + 6);
                }

                if (currentDrawRegion != null && isDrawing) {
                    int x1 = currentDrawRegion.getLeft();
                    int y1 = currentDrawRegion.getTop();
                    int x2 = currentDrawRegion.getRight();
                    int y2 = currentDrawRegion.getBottom();

                    g2.setColor(DRAW_FILL);
                    g2.fillRect(x1, y1, x2 - x1, y2 - y1);
                    g2.setColor(DRAW_STROKE);
                    g2.drawRect(x1, y1, x2 - x1, y2 - y1);

                    g.setColor(Color.WHITE);
                    g.drawString(currentDrawRegion.getWidth() + "x" + currentDrawRegion.getHeight(), x1 + 5, y1 + 15);
                }
            }
        };
        canvasPanel.setPreferredSize(new Dimension(1080, 1920));
        canvasPanel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                isDrawing = true;
                drawStartX = e.getX();
                drawStartY = e.getY();
                currentDrawRegion = new Region();
                currentDrawRegion.setLeft(drawStartX);
                currentDrawRegion.setTop(drawStartY);
                currentDrawRegion.setRight(drawStartX);
                currentDrawRegion.setBottom(drawStartY);
            }

            public void mouseReleased(MouseEvent e) {
                if (isDrawing && currentDrawRegion != null) {
                    if (currentDrawRegion.getWidth() > 20 && currentDrawRegion.getHeight() > 20) {
                        regions.add(currentDrawRegion);
                        updateRegionCount();
                    }
                    currentDrawRegion = null;
                    isDrawing = false;
                    canvasPanel.repaint();
                }
            }
        });
        canvasPanel.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                if (isDrawing) {
                    currentDrawRegion.setRight(e.getX());
                    currentDrawRegion.setBottom(e.getY());
                    canvasPanel.repaint();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(canvasPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(null);
        bottomPanel.setPreferredSize(new Dimension(650, 90));
        bottomPanel.setBackground(new Color(45, 45, 45));
        add(bottomPanel, BorderLayout.SOUTH);

        JLabel helpLabel = new JLabel("Click and drag to draw regions | Right-click to delete | R=Undo C=Clear S=Save");
        helpLabel.setBounds(0, 50, 650, 20);
        helpLabel.setForeground(Color.GRAY);
        helpLabel.setHorizontalAlignment(SwingConstants.CENTER);
        bottomPanel.add(helpLabel);

        int btnY = 10;
        int btnWidth = 100;
        int btnHeight = 35;

        JButton refreshBtn = createButton("Refresh", 10, btnY, btnWidth, btnHeight);
        refreshBtn.addActionListener(e -> { refreshScreen(); loadExistingRegions(); });
        bottomPanel.add(refreshBtn);

        JButton undoBtn = createButton("Undo", 120, btnY, btnWidth, btnHeight);
        undoBtn.addActionListener(e -> undoLastRegion());
        bottomPanel.add(undoBtn);

        JButton clearBtn = createButton("Clear All", 230, btnY, btnWidth, btnHeight);
        clearBtn.setBackground(new Color(244, 67, 54));
        clearBtn.addActionListener(e -> { regions.clear(); updateRegionCount(); canvasPanel.repaint(); });
        bottomPanel.add(clearBtn);

        JButton saveBtn = createButton("Save & Push", 340, btnY, btnWidth + 20, btnHeight);
        saveBtn.setBackground(new Color(76, 175, 80));
        saveBtn.addActionListener(e -> saveConfig());
        bottomPanel.add(saveBtn);

        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_R: undoLastRegion(); break;
                    case KeyEvent.VK_C: regions.clear(); updateRegionCount(); canvasPanel.repaint(); break;
                    case KeyEvent.VK_S: saveConfig(); break;
                }
            }
        });
        setFocusable(true);
    }

    private JButton createButton(String text, int x, int y, int w, int h) {
        JButton btn = new JButton(text);
        btn.setBounds(x, y, w, h);
        btn.setBackground(new Color(33, 150, 243));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        return btn;
    }

    private void connectToDevice() {
        try {
            adb = new ADBHelper();
            if (adb.IsConnected()) {
                statusLabel.setText("Connected: " + adb.getDeviceSerial());
                statusLabel.setForeground(Color.GREEN);
                isConnected = true;
            } else {
                statusLabel.setText("Not Connected");
                statusLabel.setForeground(Color.RED);
            }
        } catch (Exception e) {
            statusLabel.setText("Connection Error");
            statusLabel.setForeground(Color.RED);
            JOptionPane.showMessageDialog(this, "Failed to connect: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadExistingRegions() {
        if (adb == null || !adb.IsConnected()) return;
        List<Region> existing = adb.getCurrentConfig();
        if (!existing.isEmpty()) {
            regions.clear();
            regions.addAll(existing);
            updateRegionCount();
        }
    }

    private void refreshScreen() {
        if (adb == null || !adb.IsConnected()) return;
        new Thread(() -> {
            Image img = adb.screenshot();
            if (img != null) {
                screenImage = img;
                SwingUtilities.invokeLater(() -> canvasPanel.repaint());
            }
        }).start();
    }

    private void updateRegionCount() {
        regionCountLabel.setText("Regions: " + regions.size());
    }

    private void undoLastRegion() {
        if (!regions.isEmpty()) {
            regions.remove(regions.size() - 1);
            updateRegionCount();
            canvasPanel.repaint();
        }
    }

    private void saveConfig() {
        if (regions.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please draw at least one region.", "No Regions", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (adb == null || !adb.IsConnected()) {
            JOptionPane.showMessageDialog(this, "Device not connected.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        boolean enabled = enabledCheckBox.isSelected();
        boolean forceSafeMode = crashProtectionCheckBox.isSelected();

        boolean success = adb.pushConfig(regions, enabled, forceSafeMode);

        if (success) {
            JOptionPane.showMessageDialog(this,
                "Config saved!\n\nRegions: " + regions.size() + "\nBlocking: " + (enabled ? "ENABLED" : "DISABLED"),
                "Success", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "Failed to push config.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }
}
