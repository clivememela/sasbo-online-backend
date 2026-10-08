package za.co.titandynamix.sasbo_online_backend.payroll;

import java.util.List;

/**
 * The first sheet of a file as text: its non-empty rows, each with its row number as a person counts them, and each
 * cell's text trimmed -- numbers written plainly ({@code -174}, {@code 99000041}, never {@code 9.9E7}), dates as ISO
 * days, a formula as the value it last worked out to. A gap between two row numbers is a blank row.
 */
public record SheetGrid(String sheetName, List<Row> rows) {

    public SheetGrid {
        rows = List.copyOf(rows);
    }

    /** One non-empty row. {@code cells.get(i)} is column {@code i} (A is 0); an empty cell is {@code null}. */
    public record Row(int number, List<String> cells) {

        public Row {
            cells = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(cells));
        }

        public String cell(int column) {
            return column >= 0 && column < cells.size() ? cells.get(column) : null;
        }

        /** 🔒 The cells are a person's details: never written out whole. */
        @Override
        public String toString() {
            return "Row[" + number + ", " + cells.size() + " cells]";
        }
    }
}
