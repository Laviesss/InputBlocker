using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Text;

namespace InputBlockerSetup;

public class ADBHelper : IDisposable
{
    private string? deviceSerial;
    private bool connected = false;
    private int screenWidth = 1080;
    private int screenHeight = 1920;

    public int ScreenWidth => screenWidth;
    public int ScreenHeight => screenHeight;
    public bool IsConnected => connected;
    public string? DeviceSerial => deviceSerial;

    public ADBHelper()
    {
        StartADBServer();
        Connect();
    }

    private void StartADBServer()
    {
        try
        {
            RunProcess("adb", "start-server");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Failed to start ADB server: {ex.Message}");
        }
    }

    private void Connect()
    {
        try
        {
            var devices = ListDevices();
            if (devices.Count == 0)
            {
                Console.WriteLine("No devices found");
                return;
            }

            if (devices.Count > 1)
            {
                Console.WriteLine($"Multiple devices found, using first: {devices[0]}");
            }

            deviceSerial = devices[0];
            connected = true;
            Console.WriteLine($"Connected to device: {deviceSerial}");

            GetScreenSize();
        }
        catch (Exception ex)
        {
            Console.WriteLine($"ADB connection failed: {ex.Message}");
            connected = false;
        }
    }

    private List<string> ListDevices()
    {
        var devices = new List<string>();
        try
        {
            var output = RunProcess("adb", "devices");
            var lines = output.Split('\n');
            foreach (var line in lines)
            {
                if (line.Contains("\tdevice"))
                {
                    var parts = line.Split('\t');
                    if (parts.Length > 0)
                    {
                        devices.Add(parts[0].Trim());
                    }
                }
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Failed to list devices: {ex.Message}");
        }
        return devices;
    }

    private void GetScreenSize()
    {
        try
        {
            var output = RunProcess("adb", $"-s {deviceSerial} shell wm size");
            var parts = output.Split(':');
            if (parts.Length > 1)
            {
                var sizeParts = parts[1].Trim().Split('x');
                if (sizeParts.Length == 2)
                {
                    screenWidth = int.Parse(sizeParts[0]);
                    screenHeight = int.Parse(sizeParts[1]);
                }
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Failed to get screen size: {ex.Message}");
        }
        Console.WriteLine($"Screen size: {screenWidth}x{screenHeight}");
    }

    public MemoryStream? ScreenshotStream()
    {
        if (!connected || deviceSerial == null) return null;

        try
        {
            var psi = new ProcessStartInfo
            {
                FileName = "adb",
                Arguments = $"-s {deviceSerial} exec-out screencap -p",
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true
            };

            using var process = Process.Start(psi);
            if (process == null) return null;

            using var ms = new MemoryStream();
            process.StandardOutput.BaseStream.CopyTo(ms);
            process.WaitForExit();

            if (ms.Length > 0)
            {
                return new MemoryStream(ms.ToArray());
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Screenshot failed: {ex.Message}");
        }

        return null;
    }

    public bool PushConfig(List<BlockRegion> regions, bool enabled, bool forceSafeMode)
    {
        if (!connected || deviceSerial == null)
        {
            Console.WriteLine("Device not connected");
            return false;
        }

        var config = new StringBuilder();
        config.AppendLine("# InputBlocker Configuration");
        config.AppendLine("# Format: x1,y1,x2,y2");
        config.AppendLine("# Lines starting with # are comments");
        config.AppendLine();
        config.AppendLine($"enabled={(enabled ? "1" : "0")}");
        config.AppendLine($"force_safe_mode={(forceSafeMode ? "1" : "0")}");
        config.AppendLine();

        foreach (var region in regions)
        {
            config.AppendLine(region.ToConfigString());
        }

        try
        {
            string tempFile = Path.Combine(Path.GetTempPath(), "inputblocker_config.txt");
            File.WriteAllText(tempFile, config.ToString());

            RunProcess("adb", $"-s {deviceSerial} shell mkdir -p /data/adb/modules/inputblocker/config");
            RunProcess("adb", $"-s {deviceSerial} push \"{tempFile}\" /data/adb/modules/inputblocker/config/blocked_regions.conf");
            RunProcess("adb", $"-s {deviceSerial} shell chmod 644 /data/adb/modules/inputblocker/config/blocked_regions.conf");

            File.Delete(tempFile);

            Console.WriteLine("Config pushed successfully!");
            return true;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Failed to push config: {ex.Message}");
            return false;
        }
    }

    public List<BlockRegion> GetCurrentConfig()
    {
        var regions = new List<BlockRegion>();
        if (!connected || deviceSerial == null) return regions;

        try
        {
            var output = RunProcess("adb", $"-s {deviceSerial} shell cat /data/adb/modules/inputblocker/config/blocked_regions.conf");
            var lines = output.Split('\n');

            foreach (var rawLine in lines)
            {
                var line = rawLine.Trim();
                // Skip empty lines, comments, and config lines
                if (string.IsNullOrEmpty(line) || 
                    line.StartsWith("#") ||
                    line.StartsWith("enabled=") ||
                    line.StartsWith("force_safe_mode=")) continue;

                var region = BlockRegion.FromConfigString(line);
                if (region != null)
                {
                    regions.Add(region);
                }
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Failed to get current config: {ex.Message}");
        }

        return regions;
    }

    private string RunProcess(string fileName, string arguments)
    {
        var psi = new ProcessStartInfo
        {
            FileName = fileName,
            Arguments = arguments,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true
        };

        using var process = Process.Start(psi);
        if (process == null) return string.Empty;

        string output = process.StandardOutput.ReadToEnd();
        process.WaitForExit();
        return output;
    }

    public void Disconnect()
    {
        connected = false;
    }

    public void Dispose()
    {
        Disconnect();
    }
}
