using System;
using System.Collections.Generic;
using System.IO;
using System.Threading;
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
    private bool isDrawing = false;
    private double drawStartX, drawStartY;

    private TextBlock statusLabel = new();
    private TextBlock regionCountLabel = new();
    private CheckBox enabledCheckBox = new();
    private CheckBox crashProtectionCheckBox = new();
    private Button refreshButton = new();
    private Button undoButton = new();
    private Button clearButton = new();
    private Button saveButton = new();

    private Canvas canvas = new();

    private static readonly IBrush RegionFill = new SolidColorBrush(Color.Parse("#4D96F25F"));
    private static readonly IBrush RegionStroke = new SolidColorBrush(Color.Parse("#FF2196F3"));
    private static readonly IBrush DrawStroke = new SolidColorBrush(Color.Parse("#FFFF5722"));
    private static readonly IBrush DrawFill = new SolidColorBrush(Color.Parse("#4DFF5722"));

    public MainWindow()
    {
        Title = "InputBlocker Setup - by Laviesss";
        Width = 650;
        Height = 900;
        MinWidth = 500;
        MinHeight = 600;
        Background = new SolidColorBrush(Color.Parse("#1E1E1E"));

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
            Avalonia.Threading.Dispatcher.UIThread.Post(() =>
            {
                var result = MessageBox.Show(
                    _window,
                    $"A new version ({info.Version}) is available!\n\nYou have: {currentVersion}\n\nWould you like to download it?",
                    "Update Available",
                    MessageBox.MessageBoxButtons.YesNo,
                    MessageBox.MessageBoxIcon.Info);
                
                if (result == MessageBoxResult.Yes)
                {
                    try
                    {
                        OpenUrl(info.ReleaseUrl);
                    }
                    catch { }
                }
            });
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
        var topPanel = new Border
        {
            Background = new SolidColorBrush(Color.Parse("#2D2D2D")),
            Padding = new Thickness(10),
            Height = 60
        };

        statusLabel = new TextBlock
        {
            Text = "Connecting...",
            Foreground = Brushes.White,
            FontSize = 14,
            FontWeight = FontWeight.Bold,
            Margin = new Thickness(0, 0, 0, 5)
        };

        regionCountLabel = new TextBlock
        {
            Text = "Regions: 0",
            Foreground = new SolidColorBrush(Color.Parse("#B0B0B0")),
            FontSize = 12
        };

        var checkBoxPanel = new StackPanel
        {
            Orientation = Avalonia.Layout.Orientation.Horizontal,
            Margin = new Thickness(0, 5, 0, 0)
        };

        enabledCheckBox = new CheckBox
        {
            Content = "Blocking Enabled",
            Foreground = Brushes.White,
            IsChecked = true,
            Margin = new Thickness(0, 0, 20, 0)
        };

        crashProtectionCheckBox = new CheckBox
        {
            Content = "Crash Protection",
            Foreground = Brushes.White,
            IsChecked = true
        };

        checkBoxPanel.Children.Add(enabledCheckBox);
        checkBoxPanel.Children.Add(crashProtectionCheckBox);

        canvas = new Canvas
        {
            Background = new SolidColorBrush(Color.Parse("#2D2D2D")),
            MinHeight = 400
        };

        canvas.PointerPressed += Canvas_PointerPressed;
        canvas.PointerMoved += Canvas_PointerMoved;
        canvas.PointerReleased += Canvas_PointerReleased;

        var bottomPanel = new Border
        {
            Background = new SolidColorBrush(Color.Parse("#2D2D2D")),
            Padding = new Thickness(10),
            Height = 90
        };

        var helpLabel = new TextBlock
        {
            Text = "Left-click drag to draw | Right-click to delete | R=Undo C=Clear S=Save Space=Refresh",
            Foreground = new SolidColorBrush(Color.Parse("#808080")),
            FontSize = 10,
            HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Center,
            Margin = new Thickness(0, 0, 0, 5)
        };

        var buttonPanel = new StackPanel
        {
            Orientation = Avalonia.Layout.Orientation.Horizontal,
            HorizontalAlignment = Avalonia.Layout.HorizontalAlignment.Center,
            Margin = new Thickness(0, 5, 0, 0)
        };

        int btnWidth = 80;
        int btnHeight = 35;
        int spacing = 5;

        refreshButton = new Button { Content = "Refresh", Width = btnWidth, Height = btnHeight };
        refreshButton.Click += (s, e) => { RefreshScreen(); LoadExistingRegions(); };

        undoButton = new Button { Content = "Undo", Width = btnWidth, Height = btnHeight };
        undoButton.Click += (s, e) => UndoLastRegion();

        clearButton = new Button { Content = "Clear All", Width = btnWidth, Height = btnHeight };
        clearButton.Background = new SolidColorBrush(Color.Parse("#F44336"));
        clearButton.Click += (s, e) => { regions.Clear(); UpdateRegionCount(); InvalidateVisual(); };

        saveButton = new Button { Content = "Save & Push", Width = btnWidth + 20, Height = btnHeight };
        saveButton.Background = new SolidColorBrush(Color.Parse("#4CAF50"));
        saveButton.Click += (s, e) => SaveConfig();

        buttonPanel.Children.Add(refreshButton);
        buttonPanel.Children.Add(new Border { Width = spacing });
        buttonPanel.Children.Add(undoButton);
        buttonPanel.Children.Add(new Border { Width = spacing });
        buttonPanel.Children.Add(clearButton);
        buttonPanel.Children.Add(new Border { Width = spacing });
        buttonPanel.Children.Add(saveButton);

        var topStack = new StackPanel();
        topStack.Children.Add(statusLabel);
        topStack.Children.Add(regionCountLabel);
        topStack.Children.Add(checkBoxPanel);

        var mainStack = new StackPanel();
        mainStack.Children.Add(topStack);
        mainStack.Children.Add(canvas);
        mainStack.Children.Add(bottomPanel);

        Content = mainStack;

        KeyDown += MainWindow_KeyDown;
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
            isDrawing = true;
            drawStartX = point.X;
            drawStartY = point.Y;
            currentDrawRegion = new BlockRegion();
        }
    }

    private void Canvas_PointerMoved(object? sender, PointerEventArgs e)
    {
        if (isDrawing && currentDrawRegion != null && adb != null && adb.ScreenWidth > 0)
        {
            var point = e.GetPosition(canvas);
            double scaleX = canvas.Bounds.Width / adb.ScreenWidth;
            double scaleY = canvas.Bounds.Height / adb.ScreenHeight;

            int x1 = (int)(Math.Min(drawStartX, point.X) / scaleX);
            int y1 = (int)(Math.Min(drawStartY, point.Y) / scaleY);
            int x2 = (int)(Math.Max(drawStartX, point.X) / scaleX);
            int y2 = (int)(Math.Max(drawStartY, point.Y) / scaleY);

            currentDrawRegion.SetCoords(x1, y1, x2, y2);
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
    }

    private void DeleteRegionAt(int x, int y)
    {
        if (adb == null || adb.ScreenWidth == 0) return;

        double scaleX = canvas.Bounds.Width / adb.ScreenWidth;
        double scaleY = canvas.Bounds.Height / adb.ScreenHeight;

        int scaledX = (int)(x / scaleX);
        int scaledY = (int)(y / scaleY);

        for (int i = regions.Count - 1; i >= 0; i--)
        {
            if (regions[i].Contains(scaledX, scaledY))
            {
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

    public override void Render(DrawingContext context)
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
                int x1 = (int)(r.Left * scaleX);
                int y1 = (int)(r.Top * scaleY);
                int x2 = (int)(r.Right * scaleX);
                int y2 = (int)(r.Bottom * scaleY);
                int w = x2 - x1;
                int h = y2 - y1;

                context.FillRectangle(RegionFill, new Rect(x1, y1, w, h));
                context.DrawRectangle(RegionStroke, new Pen(RegionStroke, 3), new Rect(x1, y1, w, h));

                int cx = (x1 + x2) / 2;
                int cy = (y1 + y2) / 2;
                context.FillRectangle(RegionStroke, new Rect(cx - 15, cy - 15, 30, 30));
            }

            if (currentDrawRegion != null && isDrawing)
            {
                int x1 = (int)(currentDrawRegion.Left * scaleX);
                int y1 = (int)(currentDrawRegion.Top * scaleY);
                int x2 = (int)(currentDrawRegion.Right * scaleX);
                int y2 = (int)(currentDrawRegion.Bottom * scaleY);
                int w = x2 - x1;
                int h = y2 - y1;

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

    private void SaveConfig()
    {
        if (regions.Count == 0)
        {
            Console.WriteLine("Please draw at least one blocked region.");
            return;
        }

        if (adb == null || !adb.IsConnected)
        {
            Console.WriteLine("Device not connected.");
            return;
        }

        bool enabled = enabledCheckBox.IsChecked ?? true;
        bool forceSafeMode = crashProtectionCheckBox.IsChecked ?? true;

        bool success = adb.PushConfig(regions, enabled, forceSafeMode);

        if (success)
        {
            Console.WriteLine($"Config saved!\n\nRegions: {regions.Count}\nBlocking: {(enabled ? "ENABLED" : "DISABLED")}\nCrash Protection: {(forceSafeMode ? "ON" : "OFF")}");
        }
        else
        {
            Console.WriteLine("Failed to push config.");
        }
    }

    private void UpdateRegionCount()
    {
        regionCountLabel.Text = $"Regions: {regions.Count}";
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
