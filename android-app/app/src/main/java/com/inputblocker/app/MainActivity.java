package com.inputblocker.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "InputBlockerPrefs";
    private static final String PREF_THEME = "theme";
    private static final int THEME_SYSTEM = 0;
    private static final int THEME_LIGHT = 1;
    private static final int THEME_DARK = 2;
    private static final int THEME_AMOLED = 3;

    private Switch switchEnabled;
    private LinearLayout regionsList;
    private TextView tvStatus;
    private Button btnLaunchSetup;
    private Button btnAddRegion;
    private Button btnClearAll;
    private Button btnTheme;

    private boolean isEnabled = true;
    private List<Region> regions = new ArrayList<>();
    private int currentTheme = THEME_SYSTEM;

    private final ActivityResultLauncher<Intent> overlayPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Settings.canDrawOverlays(this)) {
                        launchSetupActivity();
                    } else {
                        showOverlayPermissionDenied();
                    }
                }
            });

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                checkOverlayPermission();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        loadThemePreference();
        applyTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();
        loadConfig();
        updateUI();
        applyThemeToViews();
    }

    private void loadThemePreference() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentTheme = prefs.getInt(PREF_THEME, THEME_SYSTEM);
    }

    private void saveThemePreference(int theme) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putInt(PREF_THEME, theme).apply();
        currentTheme = theme;
    }

    private void applyTheme() {
        switch (currentTheme) {
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                updateAppColors(R.color.light_background, R.color.light_surface, R.color.light_surface_elevated,
                        R.color.light_text_primary, R.color.light_text_secondary, R.color.light_border);
                break;
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                updateAppColors(R.color.dark_background, R.color.dark_surface, R.color.dark_surface_elevated,
                        R.color.dark_text_primary, R.color.dark_text_secondary, R.color.dark_border);
                break;
            case THEME_AMOLED:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                updateAppColors(R.color.amoled_background, R.color.amoled_surface, R.color.amoled_surface_elevated,
                        R.color.amoled_text_primary, R.color.amoled_text_secondary, R.color.amoled_border);
                break;
            case THEME_SYSTEM:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                updateAppColors(R.color.dark_background, R.color.dark_surface, R.color.dark_surface_elevated,
                        R.color.dark_text_primary, R.color.dark_text_secondary, R.color.dark_border);
                break;
        }
    }

    private void updateAppColors(int bgRes, int surfaceRes, int elevatedRes, int textPrimaryRes, int textSecondaryRes, int borderRes) {
        getTheme().applyStyle(new android.view.ContextThemeWrapper(this, getThemeResId()).getThemeResId(), true);
    }

    private int getThemeResId() {
        switch (currentTheme) {
            case THEME_LIGHT:
                return R.style.Theme_InputBlocker_Light;
            case THEME_DARK:
                return R.style.Theme_InputBlocker_Dark;
            case THEME_AMOLED:
                return R.style.Theme_InputBlocker_AMOLED;
            case THEME_SYSTEM:
            default:
                return R.style.Theme_InputBlocker;
        }
    }

    private void applyThemeToViews() {
        int bgColor = getBackgroundColor();
        int surfaceColor = getSurfaceColor();
        int elevatedColor = getSurfaceElevatedColor();
        int textPrimary = getTextPrimaryColor();
        int textSecondary = getTextSecondaryColor();

        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            rootView.setBackgroundColor(bgColor);
        }

        if (tvStatus != null) {
            tvStatus.setTextColor(isEnabled ?
                    ContextCompat.getColor(this, R.color.accent_green) :
                    ContextCompat.getColor(this, R.color.accent_red));
        }

        if (btnTheme != null) {
            btnTheme.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.accent_blue)));
        }

        if (btnAddRegion != null) {
            btnAddRegion.setBackgroundTintList(ColorStateList.valueOf(elevatedColor));
            btnAddRegion.setTextColor(textPrimary);
        }
    }

    private int getBackgroundColor() {
        switch (currentTheme) {
            case THEME_LIGHT:
                return ContextCompat.getColor(this, R.color.light_background);
            case THEME_DARK:
                return ContextCompat.getColor(this, R.color.dark_background);
            case THEME_AMOLED:
                return ContextCompat.getColor(this, R.color.amoled_background);
            default:
                return ContextCompat.getColor(this, R.color.dark_background);
        }
    }

    private int getSurfaceColor() {
        switch (currentTheme) {
            case THEME_LIGHT:
                return ContextCompat.getColor(this, R.color.light_surface);
            case THEME_DARK:
                return ContextCompat.getColor(this, R.color.dark_surface);
            case THEME_AMOLED:
                return ContextCompat.getColor(this, R.color.amoled_surface);
            default:
                return ContextCompat.getColor(this, R.color.dark_surface);
        }
    }

    private int getSurfaceElevatedColor() {
        switch (currentTheme) {
            case THEME_LIGHT:
                return ContextCompat.getColor(this, R.color.light_surface_elevated);
            case THEME_DARK:
                return ContextCompat.getColor(this, R.color.dark_surface_elevated);
            case THEME_AMOLED:
                return ContextCompat.getColor(this, R.color.amoled_surface_elevated);
            default:
                return ContextCompat.getColor(this, R.color.dark_surface_elevated);
        }
    }

    private int getTextPrimaryColor() {
        switch (currentTheme) {
            case THEME_LIGHT:
                return ContextCompat.getColor(this, R.color.light_text_primary);
            case THEME_DARK:
                return ContextCompat.getColor(this, R.color.dark_text_primary);
            case THEME_AMOLED:
                return ContextCompat.getColor(this, R.color.amoled_text_primary);
            default:
                return ContextCompat.getColor(this, R.color.dark_text_primary);
        }
    }

    private int getTextSecondaryColor() {
        switch (currentTheme) {
            case THEME_LIGHT:
                return ContextCompat.getColor(this, R.color.light_text_secondary);
            case THEME_DARK:
                return ContextCompat.getColor(this, R.color.dark_text_secondary);
            case THEME_AMOLED:
                return ContextCompat.getColor(this, R.color.amoled_text_secondary);
            default:
                return ContextCompat.getColor(this, R.color.dark_text_secondary);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadConfig();
        updateUI();
        applyThemeToViews();
    }

    private void initViews() {
        switchEnabled = findViewById(R.id.switch_enabled);
        regionsList = findViewById(R.id.regions_list);
        tvStatus = findViewById(R.id.tv_status);
        btnLaunchSetup = findViewById(R.id.btn_launch_setup);
        btnAddRegion = findViewById(R.id.btn_add_region);
        btnClearAll = findViewById(R.id.btn_clear_all);
        btnTheme = findViewById(R.id.btn_theme);
    }

    private void setupListeners() {
        switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isEnabled = isChecked;
            saveEnabledState(isChecked);
            updateStatus();
        });

        btnLaunchSetup.setOnClickListener(v -> checkOverlayPermission());
        btnAddRegion.setOnClickListener(v -> showAddRegionDialog());
        btnClearAll.setOnClickListener(v -> confirmClearAll());
        btnTheme.setOnClickListener(v -> showThemeDialog());
    }

    private void showThemeDialog() {
        String[] themes = {"System Default", "Light", "Dark", "AMOLED"};
        int selected = currentTheme;

        new AlertDialog.Builder(this)
                .setTitle(R.string.select_theme)
                .setSingleChoiceItems(themes, selected, (dialog, which) -> {
                    saveThemePreference(which);
                    dialog.dismiss();
                    applyTheme();
                    recreate();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                launchSetupActivity();
            } else {
                showOverlayPermissionRequest();
            }
        } else {
            launchSetupActivity();
        }
    }

    private void showOverlayPermissionRequest() {
        new AlertDialog.Builder(this)
                .setTitle("Overlay Permission Required")
                .setMessage("InputBlocker needs permission to display an overlay for visual region setup.")
                .setPositiveButton("Grant", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    overlayPermissionLauncher.launch(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showOverlayPermissionDenied() {
        Toast.makeText(this, "Overlay permission denied. Visual setup unavailable.", Toast.LENGTH_LONG).show();
    }

    private void launchSetupActivity() {
        Intent intent = new Intent(this, SetupActivity.class);
        intent.putExtra("regions", new ArrayList<>(regions));
        intent.putExtra("theme", currentTheme);
        startActivity(intent);
    }

    private void loadConfig() {
        regions.clear();

        File configFile = new File(InputBlockerServiceManager.getConfigFile(this));
        if (!configFile.exists()) {
            File dir = new File(configFile.getParent());
            if (!dir.exists()) {
                dir.mkdirs();
            }
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.startsWith("enabled=")) {
                    isEnabled = line.substring(8).equals("1");
                    continue;
                }

                String[] parts = line.split(",");
                if (parts.length == 4) {
                    try {
                        Region region = new Region();
                        region.x1 = Integer.parseInt(parts[0].trim());
                        region.y1 = Integer.parseInt(parts[1].trim());
                        region.x2 = Integer.parseInt(parts[2].trim());
                        region.y2 = Integer.parseInt(parts[3].trim());
                        regions.add(region);
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveConfig() {
        File configFile = new File(InputBlockerServiceManager.getConfigFile(this));
        File dir = configFile.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }

        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("# InputBlocker Configuration\n");
            writer.write("# Format: x1,y1,x2,y2\n");
            writer.write("# Lines starting with # are comments\n\n");
            writer.write("enabled=" + (isEnabled ? "1" : "0") + "\n\n");

            for (Region region : regions) {
                writer.write(String.format("%d,%d,%d,%d\n",
                        region.x1, region.y1, region.x2, region.y2));
            }
        } catch (IOException e) {
            Toast.makeText(this, "Failed to save config", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void saveEnabledState(boolean enabled) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean("enabled", enabled).apply();
        loadConfig();
        saveConfig();
    }

    private void updateUI() {
        switchEnabled.setChecked(isEnabled);
        updateStatus();
        updateRegionsList();
    }

    private void updateStatus() {
        String status = isEnabled ? "ENABLED" : "DISABLED";
        tvStatus.setText("Status: " + status);
        tvStatus.setTextColor(isEnabled ?
                ContextCompat.getColor(this, R.color.accent_green) :
                ContextCompat.getColor(this, R.color.accent_red));
    }

    private void updateRegionsList() {
        regionsList.removeAllViews();

        int textColor = getTextSecondaryColor();

        if (regions.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText("No blocked regions configured.\nTap 'Visual Setup' to add regions.");
            emptyView.setTextColor(textColor);
            emptyView.setPadding(32, 32, 32, 32);
            regionsList.addView(emptyView);
            return;
        }

        for (int i = 0; i < regions.size(); i++) {
            Region region = regions.get(i);
            addRegionView(i, region);
        }
    }

    private void addRegionView(int index, Region region) {
        LinearLayout itemLayout = new LinearLayout(this);
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        itemLayout.setPadding(16, 16, 16, 16);
        itemLayout.setBackgroundColor(getSurfaceColor());

        TextView tvRegion = new TextView(this);
        int width = region.x2 - region.x1;
        int height = region.y2 - region.y1;
        tvRegion.setText(String.format("[%d] (%d,%d) - (%d,%d)\nSize: %dx%d",
                index + 1, region.x1, region.y1, region.x2, region.y2, width, height));
        tvRegion.setTextColor(getTextPrimaryColor());
        tvRegion.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button btnRemove = new Button(this);
        btnRemove.setText("X");
        btnRemove.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.accent_red)));
        btnRemove.setOnClickListener(v -> removeRegion(index));

        itemLayout.addView(tvRegion);
        itemLayout.addView(btnRemove);

        regionsList.addView(itemLayout);
    }

    private void showAddRegionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Region Manually");
        builder.setMessage("Enter coordinates in format: x1,y1,x2,y2\n\nExample: 0,0,100,200");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("0,0,100,200");
        input.setPadding(48, 32, 48, 32);
        builder.setView(input);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String coordsStr = input.getText().toString().trim();
            Region region = parseRegion(coordsStr);
            if (region != null) {
                regions.add(region);
                saveConfig();
                updateUI();
                Toast.makeText(this, "Region added", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Invalid coordinates", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private Region parseRegion(String coords) {
        try {
            String[] parts = coords.split(",");
            if (parts.length != 4) return null;

            Region region = new Region();
            region.x1 = Integer.parseInt(parts[0].trim());
            region.y1 = Integer.parseInt(parts[1].trim());
            region.x2 = Integer.parseInt(parts[2].trim());
            region.y2 = Integer.parseInt(parts[3].trim());

            if (region.x1 >= region.x2 || region.y1 >= region.y2) return null;

            return region;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void removeRegion(int index) {
        if (index >= 0 && index < regions.size()) {
            regions.remove(index);
            saveConfig();
            updateUI();
            Toast.makeText(this, "Region removed", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(this)
                .setTitle("Clear All Regions")
                .setMessage("Are you sure you want to remove all blocked regions?")
                .setPositiveButton("Clear All", (dialog, which) -> {
                    regions.clear();
                    saveConfig();
                    updateUI();
                    Toast.makeText(this, "All regions cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public void setRegions(List<Region> newRegions) {
        this.regions.clear();
        this.regions.addAll(newRegions);
        saveConfig();
        runOnUiThread(this::updateUI);
    }

    public static class Region implements java.io.Serializable {
        public int x1, y1, x2, y2;

        public int getWidth() { return x2 - x1; }
        public int getHeight() { return y2 - y1; }
    }
}
