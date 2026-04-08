package com.inputblocker.setup;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ADBHelper {
    private String deviceSerial;
    private Process adbProcess;
    private int screenWidth = 1080;
    private int screenHeight = 1920;

    public ADBHelper() throws IOException {
        ProcessBuilder pb = new ProcessBuilder("adb", "get-state");
        Process p = pb.start();
        try {
            int exitCode = p.waitFor();
            if (exitCode == 0) {
                pb = new ProcessBuilder("adb", "shell", "getprop", "ro.serialno");
                p = pb.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                deviceSerial = reader.readLine();
                loadScreenSize();
            }
        } catch (InterruptedException e) {
            throw new IOException("ADB interrupted", e);
        }
    }

    public boolean IsConnected() {
        return deviceSerial != null && !deviceSerial.isEmpty();
    }

    public String getDeviceSerial() {
        return deviceSerial;
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    private void loadScreenSize() {
        try {
            ProcessBuilder pb = new ProcessBuilder("adb", "-s", deviceSerial, "shell", "wm", "size");
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            if (line != null && line.contains("x")) {
                String[] parts = line.replace("Physical size: ", "").split("x");
                screenWidth = Integer.parseInt(parts[0].trim());
                screenHeight = Integer.parseInt(parts[1].trim());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public BufferedImage screenshot() {
        try {
            ProcessBuilder pb = new ProcessBuilder("adb", "-s", deviceSerial, "exec-out", "screencap", "-p");
            Process p = pb.start();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = p.getInputStream().read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            p.waitFor();
            byte[] imageBytes = baos.toByteArray();
            return ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<Region> getCurrentConfig() {
        List<Region> regions = new ArrayList<>();
        try {
            String configPath = "/data/adb/modules/inputblocker/config/blocked_regions.conf";
            ProcessBuilder pb = new ProcessBuilder("adb", "-s", deviceSerial, "shell", "cat", configPath);
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String rawLine;
            while ((rawLine = reader.readLine()) != null) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("enabled=")) continue;
                Region r = Region.fromConfigString(line);
                if (r != null) {
                    regions.add(r);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return regions;
    }

    public boolean pushConfig(List<Region> regions, boolean enabled, boolean forceSafeMode) {
        try {
            StringBuilder config = new StringBuilder();
            config.append("# InputBlocker Configuration\n");
            config.append("enabled=").append(enabled ? "1" : "0").append("\n");
            config.append("force_safe_mode=").append(forceSafeMode ? "1" : "0").append("\n\n");
            for (Region r : regions) {
                config.append(r.toConfigString()).append("\n");
            }

            ProcessBuilder pb = new ProcessBuilder("adb", "-s", deviceSerial, "shell", "mkdir", "-p", "/data/adb/modules/inputblocker/config");
            pb.start().waitFor();

            pb = new ProcessBuilder("adb", "-s", deviceSerial, "shell", "echo", "-e", config.toString().replace("\n", "\\n").replace("'", "'\"'\"'"));
            pb.start().waitFor();

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void dispose() {
        if (adbProcess != null) {
            adbProcess.destroy();
        }
    }
}
