using System;

namespace InputBlockerSetup;

public class BlockRegion
{
    public float X1 { get; set; }
    public float Y1 { get; set; }
    public float X2 { get; set; }
    public float Y2 { get; set; }

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
        X1 = Math.Min(x1, x2);
        Y1 = Math.Min(y1, y2);
        X2 = Math.Max(x1, x2);
        Y2 = Math.Max(y1, y2);
    }

    public bool Contains(float x, float y)
    {
        return x >= Left && x <= Right && y >= Top && y <= Bottom;
    }

    public string ToConfigString()
    {
        return $"{Left:F4},{Top:F4},{Right:F4},{Bottom:F4}";
    }

    public static BlockRegion? FromConfigString(string s)
    {
        try
        {
            string[] parts = s.Split(',');
            if (parts.Length != 4) return null;
            return new BlockRegion(
                float.Parse(parts[0].Trim()),
                float.Parse(parts[1].Trim()),
                float.Parse(parts[2].Trim()),
                float.Parse(parts[3].Trim())
            );
        }
        catch
        {
            return null;
        }
    }

    public override string ToString()
    {
        return $"({Left:F4},{Top:F4}) -> ({Right:F4},{Bottom:F4}) [{Width:F4}x{Height:F4}]";
    }
}
