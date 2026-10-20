package com.festpass.event;

/** Row labels like a spreadsheet: A..Z, then AA, AB, ... */
public final class RowLabels {

    private RowLabels() {}

    /** @param index 0-based row index */
    public static String of(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Row index must not be negative");
        }
        StringBuilder label = new StringBuilder();
        int n = index + 1;
        while (n > 0) {
            n--;
            label.insert(0, (char) ('A' + n % 26));
            n /= 26;
        }
        return label.toString();
    }
}
