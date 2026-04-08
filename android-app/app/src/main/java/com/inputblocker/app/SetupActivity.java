package com.inputblocker.app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.inputblocker.app.R;

import java.util.ArrayList;
import java.util.List;

public class SetupActivity extends AppCompatActivity {

    private SetupView setupView;
    private List<MainActivity.Region> regions = new ArrayList<>();
    private TextView tvInstructions;
    private TextView tvRegionCount;
    private Button btnUndo;
    private Button btnClear;
    private Button btnSave;
    private Button btnCancel;

    private int screenWidth;
    private int screenHeight;
    private int currentTheme = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getIntent().hasExtra("theme")) {
            currentTheme = getIntent().getIntExtra("theme", 0);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAW_OVER_SYSTEM_BARS);
        }

        setContentView(R.layout.activity_setup);

        setupView = findViewById(R.id.setup_view);
        tvInstructions = findViewById(R.id.tv_instructions);
        tvRegionCount = findViewById(R.id.tv_region_count);
        btnUndo = findViewById(R.id.btn_undo);
        btnClear = findViewById(R.id.btn_clear);
        btnSave = findViewById(R.id.btn_save);
        btnCancel = findViewById(R.id.btn_cancel);

        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;

        applyThemeColors();
        loadRegions();
        setupListeners();
        updateRegionCount();
    }

    private void applyThemeColors() {
        int surfaceColor = getSurfaceColor();
        int elevatedColor = getSurfaceElevatedColor();
        int textPrimary = getTextPrimaryColor();
        int textSecondary = getTextSecondaryColor();

        tvRegionCount.setBackgroundColor(surfaceColor);
        tvRegionCount.setTextColor(textPrimary);

        tvInstructions.setBackgroundColor(elevatedColor);
        tvInstructions.setTextColor(textSecondary);

        btnUndo.setBackgroundTintList(ColorStateList.valueOf(elevatedColor));
        btnUndo.setTextColor(textPrimary);

        btnClear.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.accent_orange)));

        btnSave.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.accent_green)));

        btnCancel.setBackgroundTintList(ColorStateList.valueOf(elevatedColor));
        btnCancel.setTextColor(textPrimary);

        setupView.setThemeColors(
                ContextCompat.getColor(this, R.color.accent_blue),
                getBackgroundColor()
        );
    }

    private int getBackgroundColor() {
        switch (currentTheme) {
            case 1:
                return ContextCompat.getColor(this, R.color.light_background);
            case 2:
                return ContextCompat.getColor(this, R.color.dark_background);
            case 3:
                return ContextCompat.getColor(this, R.color.amoled_background);
            default:
                return ContextCompat.getColor(this, R.color.dark_background);
        }
    }

    private int getSurfaceColor() {
        switch (currentTheme) {
            case 1:
                return ContextCompat.getColor(this, R.color.light_surface);
            case 2:
                return ContextCompat.getColor(this, R.color.dark_surface);
            case 3:
                return ContextCompat.getColor(this, R.color.amoled_surface);
            default:
                return ContextCompat.getColor(this, R.color.dark_surface);
        }
    }

    private int getSurfaceElevatedColor() {
        switch (currentTheme) {
            case 1:
                return ContextCompat.getColor(this, R.color.light_surface_elevated);
            case 2:
                return ContextCompat.getColor(this, R.color.dark_surface_elevated);
            case 3:
                return ContextCompat.getColor(this, R.color.amoled_surface_elevated);
            default:
                return ContextCompat.getColor(this, R.color.dark_surface_elevated);
        }
    }

    private int getTextPrimaryColor() {
        switch (currentTheme) {
            case 1:
                return ContextCompat.getColor(this, R.color.light_text_primary);
            case 2:
                return ContextCompat.getColor(this, R.color.dark_text_primary);
            case 3:
                return ContextCompat.getColor(this, R.color.amoled_text_primary);
            default:
                return ContextCompat.getColor(this, R.color.dark_text_primary);
        }
    }

    private int getTextSecondaryColor() {
        switch (currentTheme) {
            case 1:
                return ContextCompat.getColor(this, R.color.light_text_secondary);
            case 2:
                return ContextCompat.getColor(this, R.color.dark_text_secondary);
            case 3:
                return ContextCompat.getColor(this, R.color.amoled_text_secondary);
            default:
                return ContextCompat.getColor(this, R.color.dark_text_secondary);
        }
    }

    private void loadRegions() {
        List<?> regionsList = (List<?>) getIntent().getSerializableExtra("regions");
        if (regionsList != null) {
            for (Object obj : regionsList) {
                if (obj instanceof MainActivity.Region) {
                    regions.add((MainActivity.Region) obj);
                }
            }
            setupView.setRegions(regions);
        }
    }

    private void setupListeners() {
        setupView.setDrawingCallback(new SetupView.DrawingCallback() {
            @Override
            public void onRegionDrawn(MainActivity.Region region) {
                regions.add(region);
                updateRegionCount();
            }

            @Override
            public void onRegionCountChanged(int count) {
                updateRegionCount();
            }
        });

        btnUndo.setOnClickListener(v -> {
            if (!regions.isEmpty()) {
                regions.remove(regions.size() - 1);
                setupView.setRegions(regions);
                updateRegionCount();
            }
        });

        btnClear.setOnClickListener(v -> {
            regions.clear();
            setupView.setRegions(regions);
            updateRegionCount();
        });

        btnSave.setOnClickListener(v -> saveAndExit());

        btnCancel.setOnClickListener(v -> finish());
    }

    private void updateRegionCount() {
        tvRegionCount.setText(String.format("Regions: %d\nScreen: %dx%d",
                regions.size(), screenWidth, screenHeight));
    }

    private void saveAndExit() {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("regions", new ArrayList<>(regions));
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (!regions.isEmpty()) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Discard Changes?")
                    .setMessage("You have unsaved regions. Discard them?")
                    .setPositiveButton("Discard", (d, w) -> super.onBackPressed())
                    .setNegativeButton("Keep Editing", null)
                    .show();
        } else {
            super.onBackPressed();
        }
    }

    public static class SetupView extends View {
        private Paint borderPaint;
        private Paint fillPaint;
        private Paint textPaint;
        private List<MainActivity.Region> regions = new ArrayList<>();
        private RectF currentRect = null;
        private float startX, startY;
        private boolean isDrawing = false;
        private int accentColor = Color.parseColor("#2196F3");
        private int backgroundColor = Color.parseColor("#88000000");

        private DrawingCallback callback;

        public interface DrawingCallback {
            void onRegionDrawn(MainActivity.Region region);
            void onRegionCountChanged(int count);
        }

        public SetupView(android.content.Context context) {
            super(context);
            init();
        }

        public SetupView(android.content.Context context, android.util.AttributeSet attrs) {
            super(context, attrs);
            init();
        }

        public SetupView(android.content.Context context, android.util.AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
            init();
        }

        private void init() {
            borderPaint = new Paint();
            borderPaint.setColor(accentColor);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(4);

            fillPaint = new Paint();
            fillPaint.setColor(Color.argb(51, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)));
            fillPaint.setStyle(Paint.Style.FILL);

            textPaint = new Paint();
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(32);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        public void setThemeColors(int accent, int bg) {
            this.accentColor = accent;
            borderPaint.setColor(accent);
            fillPaint.setColor(Color.argb(51, Color.red(accent), Color.green(accent), Color.blue(accent)));
        }

        public void setDrawingCallback(DrawingCallback callback) {
            this.callback = callback;
        }

        public void setRegions(List<MainActivity.Region> regions) {
            this.regions = new ArrayList<>(regions);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            canvas.drawColor(backgroundColor);

            for (int i = 0; i < regions.size(); i++) {
                MainActivity.Region region = regions.get(i);
                RectF rect = new RectF(region.x1, region.y1, region.x2, region.y2);
                
                Paint p = new Paint(fillPaint);
                p.setColor(Color.argb(51, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)));
                canvas.drawRect(rect, p);
                
                Paint bp = new Paint(borderPaint);
                bp.setColor(accentColor);
                canvas.drawRect(rect, bp);

                String label = String.valueOf(i + 1);
                float centerX = (region.x1 + region.x2) / 2f;
                float centerY = (region.y1 + region.y2) / 2f;
                
                Paint labelBg = new Paint();
                labelBg.setColor(accentColor);
                canvas.drawCircle(centerX, centerY, 24, labelBg);
                
                Paint labelPaint = new Paint(textPaint);
                labelPaint.setColor(Color.WHITE);
                canvas.drawText(label, centerX, centerY + 12, labelPaint);
            }

            if (currentRect != null) {
                Paint p = new Paint(fillPaint);
                p.setColor(Color.parseColor("#44FF5722"));
                canvas.drawRect(currentRect, p);

                Paint bp = new Paint(borderPaint);
                bp.setColor(Color.parseColor("#FF5722"));
                canvas.drawRect(currentRect, bp);

                String size = String.format("%dx%d",
                        (int)(currentRect.width()),
                        (int)(currentRect.height()));
                float cx = currentRect.centerX();
                float cy = currentRect.centerY();
                
                Paint bg = new Paint();
                bg.setColor(Color.parseColor("#CC000000"));
                canvas.drawRect(cx - 60, cy - 20, cx + 60, cy + 20, bg);
                
                Paint tp = new Paint(textPaint);
                tp.setTextSize(24);
                canvas.drawText(size, cx, cy + 8, tp);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = x;
                    startY = y;
                    isDrawing = true;
                    currentRect = new RectF();
                    invalidate();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (isDrawing) {
                        currentRect.set(
                                Math.min(startX, x),
                                Math.min(startY, y),
                                Math.max(startX, x),
                                Math.max(startY, y)
                        );
                        invalidate();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (isDrawing && currentRect != null) {
                        if (currentRect.width() > 20 && currentRect.height() > 20) {
                            MainActivity.Region region = new MainActivity.Region();
                            region.x1 = (int) currentRect.left;
                            region.y1 = (int) currentRect.top;
                            region.x2 = (int) currentRect.right;
                            region.y2 = (int) currentRect.bottom;
                            
                            if (callback != null) {
                                callback.onRegionDrawn(region);
                            }
                        }
                        currentRect = null;
                        isDrawing = false;
                        invalidate();
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    currentRect = null;
                    isDrawing = false;
                    invalidate();
                    return true;
            }

            return super.onTouchEvent(event);
        }
    }
}
