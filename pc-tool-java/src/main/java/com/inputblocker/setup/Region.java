package com.inputblocker.setup;

public class Region {
    private int x1, y1, x2, y2;

    public Region() {}

    public Region(int x1, int y1, int x2, int y2) {
        setCoords(x1, y1, x2, y2);
    }

    public void setCoords(int x1, int y1, int x2, int y2) {
        this.x1 = Math.min(x1, x2);
        this.y1 = Math.min(y1, y2);
        this.x2 = Math.max(x1, x2);
        this.y2 = Math.max(y1, y2);
    }

    public void setLeft(int v) { this.x1 = v; }
    public void setTop(int v) { this.y1 = v; }
    public void setRight(int v) { this.x2 = v; }
    public void setBottom(int v) { this.y2 = v; }

    public int getLeft() { return x1; }
    public int getTop() { return y1; }
    public int getRight() { return x2; }
    public int getBottom() { return y2; }
    public int getX1() { return x1; }
    public int getY1() { return y1; }
    public int getX2() { return x2; }
    public int getY2() { return y2; }

    public int getWidth() { return x2 - x1; }
    public int getHeight() { return y2 - y1; }

    public boolean contains(int x, int y) {
        return x >= x1 && x <= x2 && y >= y1 && y <= y2;
    }

    public String toConfigString() {
        return x1 + "," + y1 + "," + x2 + "," + y2;
    }

    public String toString() {
        return String.format("(%d,%d) -> (%d,%d) [%dx%d]", x1, y1, x2, y2, getWidth(), getHeight());
    }

    public static Region fromConfigString(String s) {
        if (s == null) return null;
        String[] parts = s.split(",");
        if (parts.length != 4) return null;
        try {
            return new Region(
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim()),
                Integer.parseInt(parts[3].trim())
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
