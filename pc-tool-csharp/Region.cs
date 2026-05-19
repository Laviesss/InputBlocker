using System;

namespace InputBlockerSetup;

public class BlockRegion
{
    public bool IsExclude { get; set; } = false;
    public int Type { get; set; } = 0; // 0: Rect, 1: Circle, 2: Ellipse
    public float X1 { get; set; }
    public float Y1 { get; set; }
    public float X2 { get; set; }
    public float Y2 { get; set; }
    public float MinPressure { get; set; } = 0f;
    public long MaxDuration { get; set; } = 1000L;

    public float Width => Math.Abs(X2 - X1);
    public float Height => Math.Abs(Y2 - Y1);

    public float Left => Math.Min(X1, X2);
    public float Right => Math.Max(X1, X2);
    public float Top => Math.Min(Y1, Y2);
    public float Bottom => Math.Max(Y1, Y2);

    public BlockRegion() { }

    public BlockRegion(float x1, float y1, float x2, float y2)
    {
        SetCoords(x1, y1, x2, y2);
    }

    public void SetCoords(float x1, float y1, float x2, float y2)
    {
        if (Type == 0) {
            X1 = Math.Min(x1, x2);
            Y1 = Math.Min(y1, y2);
            X2 = Math.Max(x1, x2);
            Y2 = Math.Max(y1, y2);
        } else {
            X1 = x1; Y1 = y1; X2 = x2; Y2 = y2;
        }
    }

    public string ToConfigString()
    {
        return $"{(IsExclude ? 1 : 0)},{Type},{X1:F4},{Y1:F4},{X2:F4},{Y2:F4},{MinPressure:F4},{MaxDuration}";
    }

    public static BlockRegion? FromConfigString(string s)
    {
        try
        {
            string[] parts = s.Split(',');
            if (parts.Length == 4) {
                return new BlockRegion {
                    IsExclude = false, Type = 0,
                    X1 = float.Parse(parts[0]), Y1 = float.Parse(parts[1]),
                    X2 = float.Parse(parts[2]), Y2 = float.Parse(parts[3])
                };
            }
            if (parts.Length == 5) {
                return new BlockRegion {
                    IsExclude = false, Type = int.Parse(parts[0]),
                    X1 = float.Parse(parts[1]), Y1 = float.Parse(parts[2]),
                    X2 = float.Parse(parts[3]), Y2 = float.Parse(parts[4])
                };
            }
            if (parts.Length == 7) {
                return new BlockRegion {
                    IsExclude = false, Type = int.Parse(parts[0]),
                    X1 = float.Parse(parts[1]), Y1 = float.Parse(parts[2]),
                    X2 = float.Parse(parts[3]), Y2 = float.Parse(parts[4]),
                    MinPressure = float.Parse(parts[5]), MaxDuration = long.Parse(parts[6])
                };
            }
            if (parts.Length == 8) {
                return new BlockRegion {
                    IsExclude = parts[0] == "1", Type = int.Parse(parts[1]),
                    X1 = float.Parse(parts[2]), Y1 = float.Parse(parts[3]),
                    X2 = float.Parse(parts[4]), Y2 = float.Parse(parts[5]),
                    MinPressure = float.Parse(parts[6]), MaxDuration = long.Parse(parts[7])
                };
            }
            return null;
        }
        catch { return null; }
    }

    public bool Contains(float x, float y)
    {
        if (Type == 0) { // Rectangle
            return x >= Left && x <= Right && y >= Top && y <= Bottom;
        } else if (Type == 1) { // Circle
            float dx = x - X1;
            float dy = y - Y1;
            return (dx * dx + dy * dy) <= (X2 * X2);
        } else if (Type == 2) { // Ellipse
            float dx = (x - X1) / X2;
            float dy = (y - Y1) / Y2;
            return (dx * dx + dy * dy) <= 1.0f;
        }
        return false;
    }

    public override string ToString()
    {
        string shape = Type switch { 0 => "Rect", 1 => "Circle", 2 => "Ellipse", _ => "Unknown" };
        return $"{shape} ({(IsExclude ? "Exclude" : "Block")}) [{X1:F2}, {Y1:F2}] -> [{X2:F2}, {Y2:F2}]";
    }
}
