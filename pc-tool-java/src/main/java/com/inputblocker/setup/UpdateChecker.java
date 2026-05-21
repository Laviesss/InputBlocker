package com.inputblocker.setup;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

public class UpdateChecker implements Runnable {
    
    private static final String GITHUB_API = "https://api.github.com/repos/Laviesss/InputBlocker/releases/latest";
    private final String currentVersion;
    private final Main mainWindow;
    
    public interface UpdateCallback {
        void onUpdateAvailable(String version, String releaseUrl, String currentVersion);
        void onNoUpdateAvailable(String currentVersion);
        void onError(String error);
    }
    
    private UpdateCallback callback;
    
    public UpdateChecker(String currentVersion, Main mainWindow) {
        this.currentVersion = currentVersion;
        this.mainWindow = mainWindow;
    }
    
    public void setCallback(UpdateCallback callback) {
        this.callback = callback;
    }
    
    @Override
    public void run() {
        try {
            String latestVersion = fetchLatestVersion();
            
            if (latestVersion != null && isNewerVersion(latestVersion, currentVersion)) {
                String releaseUrl = "https://github.com/Laviesss/InputBlocker/releases/tag/v" + latestVersion;
                
                if (callback != null) {
                    callback.onUpdateAvailable(latestVersion, releaseUrl, currentVersion);
                }
            } else {
                if (callback != null) {
                    callback.onNoUpdateAvailable(currentVersion);
                }
            }
        } catch (Exception e) {
            if (callback != null) {
                callback.onError(e.getMessage());
            }
        }
    }
    
    private String fetchLatestVersion() throws Exception {
        URL url = new URL(GITHUB_API);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "InputBlocker-Java");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        
        int responseCode = connection.getResponseCode();
        
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream())
            );
            StringBuilder response = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            String json = response.toString();
            
            int tagIndex = json.indexOf("\"tag_name\"");
            if (tagIndex != -1) {
                int start = json.indexOf("\"v", tagIndex) != -1 ? json.indexOf("\"v", tagIndex) + 2 : json.indexOf("\"", tagIndex) + 1;
                int end = json.indexOf("\"", start);
                return json.substring(start, end);
            }
        }
        
        return null;
    }
    
    private boolean isNewerVersion(String latest, String current) {
        String[] latestParts = latest.split("\\.");
        String[] currentParts = current.split("\\.");
        
        int maxLen = Math.max(latestParts.length, currentParts.length);
        
        for (int i = 0; i < maxLen; i++) {
            int latestNum = i < latestParts.length ? parseVersionPart(latestParts[i]) : 0;
            int currentNum = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;
            
            if (latestNum > currentNum) return true;
            if (latestNum < currentNum) return false;
        }
        
        return false;
    }
    
    private int parseVersionPart(String part) {
        try {
            String numStr = part.replaceAll("[^0-9]", "");
            return numStr.isEmpty() ? 0 : Integer.parseInt(numStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
