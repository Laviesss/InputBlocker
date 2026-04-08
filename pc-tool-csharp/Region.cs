using System;

namespace InputBlockerSetup;

public class BlockRegion
{
    public int X1 { get; set; }
    public int Y1 { get; set; }
    public int X2 { get; set; }
    public int Y2 { get; set; }

    public int Width => Math.Abs(X2 - X1);
    public int Height => Math.Abs(Y2 - Y1);

    public int Left => Math.Min(X1, X2);
    public int Right => Math.Max(X1, X2);
    public int Top => Math.Min(Y1, Y2);
    public int Bottom => Math.Max(Y1, Y2);

    public BlockRegion() { }

    public BlockRegion(int x1, int y1, int x2, int y2)
    {
        SetCoords(x1, y1, x2, y2);
    }

    public void SetCoords(int x1, int y1, int x2, int y2)
    {
        X1 = Math.Min(x1, x2);
        Y1 = Math.Min(y1, y2);
        X2 = Math.Max(x1, x2);
        Y2 = Math.Max(y1, y2);
    }

    public bool Contains(int x, int y)
    {
        return x >= Left && x <= Right && y >= Top && y <= Bottom;
    }

    public string ToConfigString()
    {
        return $"{Left},{Top},{Right},{Bottom}";
    }

    public static BlockRegion? FromConfigString(string s)
    {
        try
        {
            string[] parts = s.Split(',');
            if (parts.Length != 4) return null;
            return new BlockRegion(
                int.Parse(parts[0].Trim()),
                int.Parse(parts[1].Trim()),
                int.Parse(parts[2].Trim()),
                int.Parse(parts[3].Trim())
            );
        }
        catch
        {
            return null;
        }
    }

    public override string ToString()
    {
        return $"({Left},{Top}) -> ({Right},{Bottom}) [{Width}x{Height}]";
    }
}
