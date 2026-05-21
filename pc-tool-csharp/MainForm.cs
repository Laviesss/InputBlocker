using System;
using System.Collections.Generic;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Media;
using Avalonia.Media.Imaging;

namespace InputBlockerSetup;

public class MainWindow : Window
{
    private ADBHelper? adb;
    private List<BlockRegion> regions = new();
    private Bitmap? screenImage;
    private Thread? screenshotThread;
    private bool isRunning = true;

    private BlockRegion? currentDrawRegion;
    private BlockRegion? selectedRegion;
    private bool isDrawing = false;
    private bool isDragging = false;
    private bool isResizing = false;
    private int resizeHandleIndex = -1;
    private double drawStartX, drawStartY;
    private double dragOffsetX, dragOffsetY;

    private TextBlock statusLabel = new();
    private TextBlock regionCountLabel = new();
    private CheckBox enabledCheckBox = new();
    private CheckBox crashProtectionCheckBox = new();
    private Button refreshButton = new();
    private Button undoButton = new();
    private Button clearButton = new();
    private Button saveButton = new();
    private Button saveProfileButton = new();
    private Button loadProfileButton = new();
    private Button analyzeLogsButton = new();
    private Canvas canvas = new();

    private TextBlock footerStatusLabel = new();
    private Border footerBar = new();

    private Border propertyPanel = new();
    private TextBlock propTitleLabel = new();
    private TextBox pressureInput = new();
    private TextBox durationInput = new();
    private TextBlock pressureLabel = new();
    private TextBlock durationLabel = new();

    private static readonly IBrush RegionFill = new SolidColorBrush(Color.Parse("#4DB388FF"));
    private static readonly IBrush RegionStroke = new SolidColorBrush(Color.Parse("#FFB388FF"));
    private static readonly IBrush DrawStroke = new SolidColorBrush(Color.Parse("#FF448AFF"));
    private static readonly IBrush DrawFill = new SolidColorBrush(Color.Parse("#4D448AFF"));

    private void UpdateRegionCount()
    {
        regionCountLabel.Text = $"Regions: {regions.Count}";
    }

    public MainWindow()
    {
        Title = "InputBlocker Setup - by Laviesss";
        Width = 650;
        Height = 900;
        MinWidth = 500;
        MinHeight = 600;
        Background = new SolidColorBrush(Color.Parse("#000000"));

        InitializeComponent();
        ConnectToDevice();
        LoadExistingRegions();
        StartScreenshotTimer();
        CheckForUpdates();
    }

    private async void CheckForUpdates()
    {
        var currentVersion = Program.Version;
        
        await UpdateChecker.CheckForUpdateAsync(new UpdateCallback(this), currentVersion);
    }
    
    private class UpdateCallback : UpdateChecker.IUpdateCallback
    {
        private readonly MainWindow _window;
        
        public UpdateCallback(MainWindow window) => _window = window;
        
        public void OnUpdateAvailable(UpdateChecker.UpdateInfo info, string currentVersion)
        {
            Avalonia.Threading.Dispatcher.UIThread.Post(async () =>
            {
                var result = await ShowUpdateDialog(info.Version, currentVersion);
                
                if (result)
                {
                    OpenUrl(info.ReleaseUrl);
                }
            });
        }
        
        private async Task<bool> ShowUpdateDialog(string newVersion, string currentVersion)
        {
            var window = _window;
            
            var dialog = new Avalonia.Controls.TextBlock
            {
                Text = $"A new version ({newVersion}) is available!\n\nYou have: {currentVersion}\n\nWould you like to download it?",
                TextWrapping = Avalonia.Media.TextWrapping.Wrap,
                Margin = new Avalonia.Thickness(0, 0, 0, 16)
            };
            
            var yesButton = new Avalonia.Controls.Button { Content = "Download", Width = 100 };
            var noButton = new Avalonia.Controls.Button { Content = "Later", Width = 100 };
            
            var buttonPanel = new Avalonia.Controls.StackPanel
            {
                Orientation = Avalonia.Layout.Orientation.Horizontal,
                HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Right,
                Spacing = 8,
                Children = { noButton, yesButton }
            };
            
            var panel = new Avalonia.Controls.StackPanel
            {
                Margin = new Avalonia.Thickness(24),
                Spacing = 16,
                Children = { dialog, buttonPanel }
            };
            
            var dialogWindow = new Avalonia.Controls.Window
            {
                Title = "Update Available",
                Width = 350,
                Height = 200,
                Content = panel,
                WindowStartupLocation = Avalonia.Controls.WindowStartupLocation.CenterOwner,
                CanResize = false,
                Topmost = true
            };
            
            yesButton.Click += (s, e) => { dialogWindow.Close(true); };
            noButton.Click += (s, e) => { dialogWindow.Close(false); };
            
            return await dialogWindow.ShowDialog<bool>(window);
        }
        
        public void OnNoUpdateAvailable(string currentVersion)
        {
            Console.WriteLine($"You have the latest version ({currentVersion})");
        }
        
        public void OnError(string error)
        {
            Console.WriteLine($"Update check failed: {error}");
        }
    }
    
    private static void OpenUrl(string url)
    {
        try
        {
            var psi = new System.Diagnostics.ProcessStartInfo
            {
                FileName = url,
                UseShellExecute = true
            };
            System.Diagnostics.Process.Start(psi);
        }
        catch { }
    }

    private void InitializeComponent()
    {
        // Global Theme Colors
        var bgDark = new SolidColorBrush(Color.Parse("#121212"));
        var bgPanel = new SolidColorBrush(Color.Parse("#1E1E1E"));
        var accentColor = new SolidColorBrush(Color.Parse("#BB86FC"));
        var borderColor = new SolidColorBrush(Color.Parse("#333333"));
        var textMain = Brushes.White;
        var textMuted = new SolidColorBrush(Color.Parse("#A0A0A0"));

        var mainGrid = new Grid();

        // --- TOP TOOLBAR ---
        var toolbar = new Border
        {
            Background = bgPanel,
            Padding = new Thickness(15, 10),
            Height = 70,
            BorderBrush = borderColor,
            BorderThickness = new Thickness(0, 0, 0, 1)
        };

        var toolStack = new StackPanel { Orientation = Avalonia.Layout.Orientation.Horizontal, Spacing = 15, VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center };

        statusLabel = new TextBlock
        {
            Text = "Connecting...",
            Foreground = textMain,
            FontSize = 14,
            FontWeight = FontWeight.Bold,
            VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center,
            Margin = new Thickness(0, 0, 20, 0)
        };

        regionCountLabel = new TextBlock
        {
            Text = "Regions: 0",
            Foreground = textMuted,
            FontSize = 12,
            VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center,
            Margin = new Thickness(0, 0, 20, 0)
        };

        enabledCheckBox = new CheckBox { Content = "Blocking Enabled", Foreground = textMain, IsChecked = true };
        crashProtectionCheckBox = new CheckBox { Content = "Crash Protection", Foreground = textMain, IsChecked = true };

        var checkboxStack = new StackPanel { Orientation = Avalonia.Layout.Orientation.Horizontal, Spacing = 10, VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center };
        checkboxStack.Children.Add(enabledCheckBox);
        checkboxStack.Children.Add(crashProtectionCheckBox);

        toolStack.Children.Add(statusLabel);
        toolStack.Children.Add(regionCountLabel);
        toolStack.Children.Add(checkboxStack);
        toolbar.Child = toolStack;

        // --- MIDDLE CONTENT ---
        var contentGrid = new Grid();

        canvas = new Canvas
        {
            Background = bgDark,
            MinHeight = 600
        };

        // Property Inspector
        propertyPanel = new Border
        {
            Background = bgPanel,
            Padding = new Thickness(15),
            Width = 220,
            IsVisible = false,
            BorderBrush = borderColor,
            BorderThickness = new Thickness(1, 0, 0, 0)
        };

        propTitleLabel = new TextBlock
        {
            Text = "Region Properties",
            Foreground = textMain,
            FontSize = 16,
            FontWeight = FontWeight.Bold,
            Margin = new Thickness(0, 0, 0, 15)
        };

        pressureLabel = new TextBlock { Text = "Min Pressure:", Foreground = textMuted, FontSize = 12 };
        pressureInput = new TextBox { Width = 160, Margin = new Thickness(0, 5, 0, 15) };
        pressureInput.TextChanged += (s, e) => { if (selectedRegion != null && float.TryParse(pressureInput.Text, out float p)) selectedRegion.MinPressure = p; };

        durationLabel = new TextBlock { Text = "Max Duration (ms):", Foreground = textMuted, FontSize = 12 };
        durationInput = new TextBox { Width = 160, Margin = new Thickness(0, 5, 0, 15) };
        durationInput.TextChanged += (s, e) => { if (selectedRegion != null && long.TryParse(durationInput.Text, out long d)) selectedRegion.MaxDuration = d; };

        var propStack = new StackPanel();
        propStack.Children.Add(propTitleLabel);
        propStack.Children.Add(pressureLabel);
        propStack.Children.Add(pressureInput);
        propStack.Children.Add(durationLabel);
        propStack.Children.Add(durationInput);
        propertyPanel.Child = propStack;

        contentGrid.ColumnDefinitions.Add(new ColumnDefinition(GridLength.Star));
        contentGrid.ColumnDefinitions.Add(new ColumnDefinition(GridLength.Auto));
        Grid.SetColumn(canvas, 0);
        Grid.SetColumn(propertyPanel, 1);
        contentGrid.Children.Add(canvas);
        contentGrid.Children.Add(propertyPanel);

        // --- BOTTOM BAR ---
        var bottomBar = new Border
        {
            Background = bgPanel,
            Padding = new Thickness(15, 10),
            Height = 100,
            BorderBrush = borderColor,
            BorderThickness = new Thickness(0, 1, 0, 0)
        };

        var bottomStack = new StackPanel { VerticalAlignment = Avalonia.Layout.VerticalAlignment.Center };

        var helpLabel = new TextBlock
        {
            Text = "Left-click drag to draw | Right-click to delete | R=Undo C=Clear S=Save Space=Refresh",
            Foreground = new SolidColorBrush(Color.Parse("#808080")),
            FontSize = 11,
            HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Center,
            Margin = new Thickness(0, 0, 0, 8)
        };

        var buttonPanel = new StackPanel
        {
            Orientation = Avalonia.Layout.Orientation.Horizontal,
            HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Center,
            Spacing = 8
        };

        int btnWidth = 90;
        int btnHeight = 32;

        refreshButton = new Button { Content = "Refresh", Width = btnWidth, Height = btnHeight };
        refreshButton.Click += (s, e) => { RefreshScreen(); LoadExistingRegions(); };

        undoButton = new Button { Content = "Undo", Width = btnWidth, Height = btnHeight };
        undoButton.Click += (s, e) => UndoLastRegion();

        clearButton = new Button { Content = "Clear All", Width = btnWidth, Height = btnHeight };
        clearButton.Background = new SolidColorBrush(Color.Parse("#C62828"));
        clearButton.Click += (s, e) => { regions.Clear(); UpdateRegionCount(); InvalidateVisual(); };

        saveProfileButton = new Button { Content = "Save Profile", Width = btnWidth, Height = btnHeight };
        saveProfileButton.Click += (s, e) => SaveProfile();

        loadProfileButton = new Button { Content = "Load Profile", Width = btnWidth, Height = btnHeight };
        loadProfileButton.Click += (s, e) => LoadProfile();

        analyzeLogsButton = new Button { Content = "Analyze Logs", Width = btnWidth, Height = btnHeight };
        analyzeLogsButton.Click += (s, e) => AnalyzeLogs();

        saveButton = new Button { Content = "Push to Device", Width = btnWidth + 20, Height = btnHeight };
        saveButton.Background = new SolidColorBrush(Color.Parse("#2E7D32"));
        saveButton.Click += (s, e) => SaveConfig();

        buttonPanel.Children.Add(refreshButton);
        buttonPanel.Children.Add(undoButton);
        buttonPanel.Children.Add(clearButton);
        buttonPanel.Children.Add(saveProfileButton);
        buttonPanel.Children.Add(loadProfileButton);
        buttonPanel.Children.Add(analyzeLogsButton);
        buttonPanel.Children.Add(saveButton);

        footerStatusLabel = new TextBlock
        {
            Text = "Ready",
            Foreground = textMuted,
            FontSize = 10,
            HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Center,
            Margin = new Thickness(0, 5, 0, 0)
        };

        bottomStack.Children.Add(helpLabel);
        bottomStack.Children.Add(buttonPanel);
        bottomStack.Children.Add(footerStatusLabel);
        bottomBar.Child = bottomStack;

        // Assemble Main Grid
        mainGrid.RowDefinitions.Add(new RowDefinition(GridLength.Auto));
        mainGrid.RowDefinitions.Add(new RowDefinition(GridLength.Star));
        mainGrid.RowDefinitions.Add(new RowDefinition(GridLength.Auto));

        Grid.SetRow(toolbar, 0);
        Grid.SetRow(contentGrid, 1);
        Grid.SetRow(bottomBar, 2);
        mainGrid.Children.Add(toolbar);
        mainGrid.Children.Add(contentGrid);
        mainGrid.Children.Add(bottomBar);

        Content = mainGrid;

        canvas.PointerPressed += Canvas_PointerPressed;
        canvas.PointerMoved += Canvas_PointerMoved;
        canvas.PointerReleased += Canvas_PointerReleased;
        KeyDown += MainWindow_KeyDown;
    }

    private void UpdatePropertyPanel()
    {
        if (selectedRegion == null)
        {
            propertyPanel.IsVisible = false;
            return;
        }

        propertyPanel.IsVisible = true;
        propTitleLabel.Text = $"Region {regions.IndexOf(selectedRegion) + 1} Properties";
        pressureInput.Text = selectedRegion.MinPressure.ToString("F4");
        durationInput.Text = selectedRegion.MaxDuration.ToString();
        
        footerStatusLabel.Text = $"Editing Region {regions.IndexOf(selectedRegion) + 1} | Type: {(selectedRegion.Type == 0 ? "Rect" : selectedRegion.Type == 1 ? "Circle" : "Ellipse")} | Exclude: {selectedRegion.IsExclude}";
    }

    private void Canvas_PointerPressed(object? sender, PointerPressedEventArgs e)
    {
        var point = e.GetPosition(canvas);
        var props = e.GetCurrentPoint(canvas);

        if (props.Properties.IsRightButtonPressed)
        {
            DeleteRegionAt((int)point.X, (int)point.Y);
            return;
        }

        if (props.Properties.IsLeftButtonPressed)
        {
            float nx = (float)(point.X / canvas.Bounds.Width);
            float ny = (float)(point.Y / canvas.Bounds.Height);

            if (selectedRegion != null)
            {
                // Check if clicking a resize handle of the currently selected region
                float handleSize = 0.01f; // 1% of screen size as handle
                float left = selectedRegion.Left;
                float right = selectedRegion.Right;
                float top = selectedRegion.Top;
                float bottom = selectedRegion.Bottom;

                if (Math.Abs(nx - left) < handleSize && Math.Abs(ny - top) < handleSize) { resizeHandleIndex = 0; isResizing = true; isDragging = false; InvalidateVisual(); return; }
                if (Math.Abs(nx - right) < handleSize && Math.Abs(ny - top) < handleSize) { resizeHandleIndex = 1; isResizing = true; isDragging = false; InvalidateVisual(); return; }
                if (Math.Abs(nx - left) < handleSize && Math.Abs(ny - bottom) < handleSize) { resizeHandleIndex = 2; isResizing = true; isDragging = false; InvalidateVisual(); return; }
                if (Math.Abs(nx - right) < handleSize && Math.Abs(ny - bottom) < handleSize) { resizeHandleIndex = 3; isResizing = true; isDragging = false; InvalidateVisual(); return; }
            }

            // Check if clicking an existing region to select/drag it
            for (int i = regions.Count - 1; i >= 0; i--)
            {
                if (regions[i].Contains(nx, ny))
                {
                    selectedRegion = regions[i];
                    isDragging = true;
                    dragOffsetX = point.X - (selectedRegion.Left * canvas.Bounds.Width);
                    dragOffsetY = point.Y - (selectedRegion.Top * canvas.Bounds.Height);
                    isDrawing = false;
                    isResizing = false;
                    UpdatePropertyPanel();
                    InvalidateVisual();
                    return;
                }
            }

            // If no region selected, start drawing a new one
            selectedRegion = null;
            isDrawing = true;
            drawStartX = point.X;
            drawStartY = point.Y;
            currentDrawRegion = new BlockRegion();
            UpdatePropertyPanel();
        }
    }

    private void Canvas_PointerMoved(object? sender, PointerEventArgs e)
    {
        var point = e.GetPosition(canvas);

        if (isResizing && selectedRegion != null)
        {
            float nx = (float)(point.X / canvas.Bounds.Width);
            float ny = (float)(point.Y / canvas.Bounds.Height);

            if (resizeHandleIndex == 0) { selectedRegion.X1 = nx; selectedRegion.Y1 = ny; }
            else if (resizeHandleIndex == 1) { selectedRegion.X2 = nx; selectedRegion.Y1 = ny; }
            else if (resizeHandleIndex == 2) { selectedRegion.X1 = nx; selectedRegion.Y2 = ny; }
            else if (resizeHandleIndex == 3) { selectedRegion.X2 = nx; selectedRegion.Y2 = ny; }
            
            InvalidateVisual();
        }
        else if (isDragging && selectedRegion != null)
        {
            float newLeft = (float)((point.X - dragOffsetX) / canvas.Bounds.Width);
            float newTop = (float)((point.Y - dragOffsetY) / canvas.Bounds.Height);
            float width = selectedRegion.Width;
            float height = selectedRegion.Height;

            selectedRegion.SetCoords(newLeft, newTop, newLeft + width, newTop + height);
            InvalidateVisual();
        }
        else if (isDrawing && currentDrawRegion != null && adb != null && adb.ScreenWidth > 0)
        {
            float nx = (float)(point.X / canvas.Bounds.Width);
            float ny = (float)(point.Y / canvas.Bounds.Height);

            currentDrawRegion.SetCoords(
                (float)(drawStartX / canvas.Bounds.Width), 
                (float)(drawStartY / canvas.Bounds.Height), 
                nx, 
                ny
            );
            InvalidateVisual();
        }
    }

    private void Canvas_PointerReleased(object? sender, PointerReleasedEventArgs e)
    {
        if (isDrawing && currentDrawRegion != null)
        {
            if (currentDrawRegion.Width > 20 && currentDrawRegion.Height > 20)
            {
                regions.Add(currentDrawRegion);
                UpdateRegionCount();
                Console.WriteLine($"Added region: {currentDrawRegion}");
            }
            currentDrawRegion = null;
            isDrawing = false;
            InvalidateVisual();
        }
        
        isDragging = false;
        isResizing = false;
        resizeHandleIndex = -1;
        UpdatePropertyPanel();
        InvalidateVisual();
    }

    private void DeleteRegionAt(double x, double y)
    {
        if (adb == null || adb.ScreenWidth == 0) return;

        float nx = (float)(x / canvas.Bounds.Width);
        float ny = (float)(y / canvas.Bounds.Height);

        for (int i = regions.Count - 1; i >= 0; i--)
        {
            if (regions[i].Contains(nx, ny))
            {
                if (regions[i] == selectedRegion)
                {
                    selectedRegion = null;
                    UpdatePropertyPanel();
                }
                var removed = regions[i];
                regions.RemoveAt(i);
                Console.WriteLine($"Deleted region: {removed}");
                UpdateRegionCount();
                InvalidateVisual();
                return;
            }
        }
    }

    private void UndoLastRegion()
    {
        if (regions.Count > 0)
        {
            var removed = regions[regions.Count - 1];
            if (removed == selectedRegion)
            {
                selectedRegion = null;
                UpdatePropertyPanel();
            }
            regions.RemoveAt(regions.Count - 1);
            Console.WriteLine($"Undo: {removed}");
            UpdateRegionCount();
            InvalidateVisual();
        }
    }

    private void MainWindow_KeyDown(object? sender, KeyEventArgs e)
    {
        switch (e.Key)
        {
            case Key.R:
                UndoLastRegion();
                break;
            case Key.C:
                regions.Clear();
                UpdateRegionCount();
                InvalidateVisual();
                break;
            case Key.S:
                SaveConfig();
                break;
            case Key.Space:
                RefreshScreen();
                e.Handled = true;
                break;
        }
    }

    private void Render(DrawingContext context)
    {
        var bounds = new Rect(0, 0, Bounds.Width, Bounds.Height);
        context.FillRectangle(new SolidColorBrush(Color.Parse("#2D2D2D")), bounds);

        if (screenImage != null)
        {
            var imgWidth = (double)screenImage.PixelSize.Width;
            var imgHeight = (double)screenImage.PixelSize.Height;
            var canvasWidth = canvas.Bounds.Width;
            var canvasHeight = canvas.Bounds.Height;

            var scale = Math.Min(canvasWidth / imgWidth, canvasHeight / imgHeight);
            var scaledWidth = imgWidth * scale;
            var scaledHeight = imgHeight * scale;
            var offsetX = (canvasWidth - scaledWidth) / 2;
            var offsetY = (canvasHeight - scaledHeight) / 2;

            context.DrawImage(screenImage, new Rect(0, 0, imgWidth, imgHeight), 
                new Rect(offsetX, offsetY, scaledWidth, scaledHeight));
        }

        if (adb != null && adb.ScreenWidth > 0)
        {
            double scaleX = canvas.Bounds.Width / adb.ScreenWidth;
            double scaleY = canvas.Bounds.Height / adb.ScreenHeight;

            for (int i = 0; i < regions.Count; i++)
            {
                var r = regions[i];
                bool isSelected = (r == selectedRegion);
                
                IBrush fill = r.IsExclude ? new SolidColorBrush(Color.Parse("#4DFF0000")) : RegionFill;
                IBrush stroke = r.IsExclude ? new SolidColorBrush(Color.Parse("#FFFF0000")) : RegionStroke;
                
                if (isSelected)
                {
                    stroke = new SolidColorBrush(Color.Parse("#FFFFFFFF")); // White for selected
                }

                if (r.Type == 0) {
                    float x1 = r.Left * (float)canvas.Bounds.Width;
                    float y1 = r.Top * (float)canvas.Bounds.Height;
                    float x2 = r.Right * (float)canvas.Bounds.Width;
                    float y2 = r.Bottom * (float)canvas.Bounds.Height;
                    float w = x2 - x1;
                    float h = y2 - y1;
                    context.FillRectangle(fill, new Rect(x1, y1, w, h));
                    context.DrawRectangle(stroke, new Pen(stroke, isSelected ? 4 : 3), new Rect(x1, y1, w, h));
                } else if (r.Type == 1) {
                                 float cx = r.X1 * (float)canvas.Bounds.Width;
                                 float cy = r.Y1 * (float)canvas.Bounds.Height;
                                 float radius = r.X2 * (float)canvas.Bounds.Width;
                                 context.DrawEllipse(fill, new Pen(fill, radius * 2), new Rect(cx - radius, cy - radius, radius * 2, radius * 2));
                                 context.DrawEllipse(stroke, new Pen(stroke, isSelected ? 4 : 3), new Rect(cx - radius, cy - radius, radius * 2, radius * 2));
                              } else if (r.Type == 2) {
                                 float cx = r.X1 * (float)canvas.Bounds.Width;
                                 float cy = r.Y1 * (float)canvas.Bounds.Height;
                                 float rx = r.X2 * (float)canvas.Bounds.Width;
                                 float ry = r.Y2 * (float)canvas.Bounds.Height;
                                 context.DrawEllipse(fill, new Pen(fill, ry * 2), new Rect(cx - rx, cy - ry, rx * 2, ry * 2));
                                 context.DrawEllipse(stroke, new Pen(stroke, isSelected ? 4 : 3), new Rect(cx - rx, cy - ry, rx * 2, ry * 2));
                              }


                float centerX = (r.Left + r.Right) / 2 * (float)canvas.Bounds.Width;
                float centerY = (r.Top + r.Bottom) / 2 * (float)canvas.Bounds.Height;
                context.FillRectangle(RegionStroke, new Rect(centerX - 15, centerY - 15, 30, 30));
            }

            if (selectedRegion != null)
            {
                float l = selectedRegion.Left * (float)canvas.Bounds.Width;
                float t = selectedRegion.Top * (float)canvas.Bounds.Height;
                float r = selectedRegion.Right * (float)canvas.Bounds.Width;
                float b = selectedRegion.Bottom * (float)canvas.Bounds.Height;
                float hSize = 6;
                var handleBrush = new SolidColorBrush(Color.Parse("#FFFFFFFF"));
                
                context.FillRectangle(handleBrush, new Rect(l - hSize/2, t - hSize/2, hSize, hSize));
                context.FillRectangle(handleBrush, new Rect(r - hSize/2, t - hSize/2, hSize, hSize));
                context.FillRectangle(handleBrush, new Rect(l - hSize/2, b - hSize/2, hSize, hSize));
                context.FillRectangle(handleBrush, new Rect(r - hSize/2, b - hSize/2, hSize, hSize));
            }

            if (currentDrawRegion != null && isDrawing)
            {
                float x1 = currentDrawRegion.Left * (float)canvas.Bounds.Width;
                float y1 = currentDrawRegion.Top * (float)canvas.Bounds.Height;
                float x2 = currentDrawRegion.Right * (float)canvas.Bounds.Width;
                float y2 = currentDrawRegion.Bottom * (float)canvas.Bounds.Height;
                float w = x2 - x1;
                float h = y2 - y1;

                context.FillRectangle(DrawFill, new Rect(x1, y1, w, h));
                context.DrawRectangle(DrawStroke, new Pen(DrawStroke, 3), new Rect(x1, y1, w, h));
            }
        }

        base.Render(context);
    }

    private void ConnectToDevice()
    {
        try
        {
            adb = new ADBHelper();
            if (adb.IsConnected)
            {
                statusLabel.Text = $"Connected: {adb.DeviceSerial}";
                statusLabel.Foreground = new SolidColorBrush(Color.Parse("#90EE90"));
            }
            else
            {
                statusLabel.Text = "Not Connected";
                statusLabel.Foreground = new SolidColorBrush(Color.Parse("#FF6B6B"));
            }
        }
        catch (Exception ex)
        {
            statusLabel.Text = "Connection Error";
            statusLabel.Foreground = new SolidColorBrush(Color.Parse("#FF6B6B"));
            Console.WriteLine($"Failed to connect: {ex.Message}");
        }
    }

    private void SaveConfig()
    {
        if (adb == null || !adb.IsConnected)
        {
            Console.WriteLine("Device not connected");
            return;
        }

        bool enabled = enabledCheckBox.IsChecked ?? true;
        bool forceSafeMode = crashProtectionCheckBox.IsChecked ?? true;

        bool success = adb.PushConfig(regions, enabled, forceSafeMode);

        if (success)
        {
            Console.WriteLine($"Config pushed successfully!\n\nRegions: {regions.Count}\nBlocking: {(enabled ? "ENABLED" : "DISABLED")}\nCrash Protection: {(forceSafeMode ? "ON" : "OFF")}");
        }
        else
        {
            Console.WriteLine("Failed to push config.");
        }
    }

    private void AnalyzeLogs()
    {
        if (adb == null || !adb.IsConnected)
        {
            footerStatusLabel.Text = "Error: Device not connected";
            footerStatusLabel.Foreground = new SolidColorBrush(Color.Parse("#FF6B6B"));
            return;
        }

        try
        {
            string logPath = $"{adb.ModulePath}/config/blocklog.txt";
            var output = adb.RunProcess("adb", $"-s {adb.DeviceSerial} shell cat {logPath}");
            
            if (string.IsNullOrWhiteSpace(output))
            {
                footerStatusLabel.Text = "No logs found on device.";
                footerStatusLabel.Foreground = new SolidColorBrush(Color.Parse("#B0B0B0"));
                return;
            }

            int hotspotsFound = 0;
            var lines = output.Split('\n');
            
            foreach (var region in regions)
            {
                float minX = 1f, minY = 1f, maxX = 0f, maxY = 0f;
                int count = 0;

                foreach (var line in lines)
                {
                    if (string.IsNullOrWhiteSpace(line) || line.StartsWith("#")) continue;
                    var parts = line.Split('|');
                    if (parts.Length < 2) continue;
                    
                    var coordsPart = parts[1].Trim();
                    var coords = coordsPart.Split(',');
                    if (coords.Length < 2) continue;

                    if (float.TryParse(coords[0].Replace("X: ", "").Trim(), out float x) && 
                        float.TryParse(coords[1].Replace("Y: ", "").Trim(), out float y))
                    {
                        if (region.Contains(x, y))
                        {
                            minX = Math.Min(minX, x);
                            minY = Math.Min(minY, y);
                            maxX = Math.Max(maxX, x);
                            maxY = Math.Max(maxY, y);
                            count++;
                        }
                    }
                }

                if (count > 5)
                {
                    hotspotsFound++;
                    Console.WriteLine($"Region {regions.IndexOf(region)+1} hotspot found: {count} taps. Suggested: [{minX},{minY}] -> [{maxX},{maxY}]");
                }
            }

            footerStatusLabel.Text = hotspotsFound > 0 ? $"Found {hotspotsFound} hotspots! Check console for suggestions." : "No significant hotspots detected.";
            footerStatusLabel.Foreground = hotspotsFound > 0 ? new SolidColorBrush(Color.Parse("#90EE90")) : new SolidColorBrush(Color.Parse("#B0B0B0"));
        }
        catch (Exception ex)
        {
            footerStatusLabel.Text = $"Log analysis failed: {ex.Message}";
            footerStatusLabel.Foreground = new SolidColorBrush(Color.Parse("#FF6B6B"));
        }
    }

    private void SaveProfile()
    {
        var dialog = new Avalonia.Controls.Window
        {
            Title = "Save Profile",
            Width = 400,
            Height = 200,
            WindowStartupLocation = Avalonia.Controls.WindowStartupLocation.CenterOwner
        };

        var pathInput = new TextBox { Width = 300, Text = "profile.conf" };
        var saveBtn = new Button { Content = "Save", Width = 100 };
        var cancelBtn = new Button { Content = "Cancel", Width = 100 };

        var panel = new StackPanel
        {
            Margin = new Thickness(20),
            Spacing = 10,
            Children = { new TextBlock { Text = "Enter filename:" }, pathInput, new StackPanel { Orientation = Avalonia.Layout.Orientation.Horizontal, Spacing = 10, Children = { cancelBtn, saveBtn } } }
        };
        dialog.Content = panel;

        saveBtn.Click += (s, e) => {
            try {
                var lines = new List<string> { "# InputBlocker Profile", $"enabled={(enabledCheckBox.IsChecked ?? true ? "1" : "0")}", $"force_safe_mode={(crashProtectionCheckBox.IsChecked ?? true ? "1" : "0")}", "" };
                foreach (var r in regions) lines.Add(r.ToConfigString());
                File.WriteAllLines(pathInput.Text, lines);
                dialog.Close(true);
            } catch (Exception ex) { Console.WriteLine($"Save failed: {ex.Message}"); }
        };
        cancelBtn.Click += (s, e) => dialog.Close(false);

        dialog.ShowDialog<bool>(this);
    }

    private void LoadProfile()
    {
        var dialog = new Avalonia.Controls.Window
        {
            Title = "Load Profile",
            Width = 400,
            Height = 200,
            WindowStartupLocation = Avalonia.Controls.WindowStartupLocation.CenterOwner
        };

        var pathInput = new TextBox { Width = 300, Text = "profile.conf" };
        var loadBtn = new Button { Content = "Load", Width = 100 };
        var cancelBtn = new Button { Content = "Cancel", Width = 100 };

        var panel = new StackPanel
        {
            Margin = new Thickness(20),
            Spacing = 10,
            Children = { new TextBlock { Text = "Enter filename:" }, pathInput, new StackPanel { Orientation = Avalonia.Layout.Orientation.Horizontal, Spacing = 10, Children = { cancelBtn, loadBtn } } }
        };
        dialog.Content = panel;

        loadBtn.Click += (s, e) => {
            try {
                var lines = File.ReadAllLines(pathInput.Text);
                regions.Clear();
                bool enabled = true;
                bool forceSafe = true;
                foreach (var line in lines) {
                    if (line.StartsWith("enabled=")) enabled = line.Split('=')[1] == "1";
                    else if (line.StartsWith("force_safe_mode=")) forceSafe = line.Split('=')[1] == "1";
                    else if (!line.StartsWith("#") && !string.IsNullOrWhiteSpace(line)) {
                        var r = BlockRegion.FromConfigString(line);
                        if (r != null) regions.Add(r);
                    }
                }
                enabledCheckBox.IsChecked = enabled;
                crashProtectionCheckBox.IsChecked = forceSafe;
                UpdateRegionCount();
                InvalidateVisual();
                dialog.Close(true);
            } catch (Exception ex) { Console.WriteLine($"Load failed: {ex.Message}"); }
        };
        cancelBtn.Click += (s, e) => dialog.Close(false);

        dialog.ShowDialog<bool>(this);
    }

    private void LoadExistingRegions()
    {
        if (adb == null || !adb.IsConnected) return;

        var existing = adb.GetCurrentConfig();
        if (existing.Count > 0)
        {
            regions.Clear();
            regions.AddRange(existing);
            UpdateRegionCount();
            Console.WriteLine($"Loaded {existing.Count} existing regions");
        }
    }

    private void StartScreenshotTimer()
    {
        screenshotThread = new Thread(() =>
        {
            while (isRunning)
            {
                try
                {
                    Thread.Sleep(3000);
                    if (isRunning && adb != null && adb.IsConnected)
                    {
                        var imgStream = adb.ScreenshotStream();
                        if (imgStream != null)
                        {
                            try
                            {
                                imgStream.Position = 0;
                                var bitmap = new Bitmap(imgStream);
                                Avalonia.Threading.Dispatcher.UIThread.Post(() =>
                                {
                                    screenImage?.Dispose();
                                    screenImage = bitmap;
                                    InvalidateVisual();
                                });
                            }
                            catch { }
                        }
                    }
                }
                catch { }
            }
        });
        screenshotThread.IsBackground = true;
        screenshotThread.Start();
    }

    private void RefreshScreen()
    {
        if (adb == null || !adb.IsConnected) return;

        try
        {
            var imgStream = adb.ScreenshotStream();
            if (imgStream != null)
            {
                imgStream.Position = 0;
                var bitmap = new Bitmap(imgStream);
                screenImage?.Dispose();
                screenImage = bitmap;
                InvalidateVisual();
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Refresh failed: {ex.Message}");
        }
    }

    protected override void OnClosed(EventArgs e)
    {
        isRunning = false;
        screenshotThread?.Join(1000);
        adb?.Dispose();
        screenImage?.Dispose();
        base.OnClosed(e);
    }
}
