package com.example;

import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Component
@ConfigurationProperties(prefix="grid")
public class Grid {
    private int rows;
    private int cols;

    private int row;
    private int col;

    public int getRows() {
        return rows;
    }
    public void setRows(int rows) {
        this.rows = rows;
    }

    public int getCols() {
        return cols;
    }
    public void setCols(int cols) {
        this.cols = cols;
    }

    public int getRow() {
        return row;
    }
    public int getCol() {
        return col;
    }

    public void reset() {
        java.util.Random r = new java.util.Random();
        row = (int)(r.nextDouble() * rows);
        col = (int)(r.nextDouble() * cols);
    }

    boolean bomb(int row, int col) {
        return (this.row == row && this.col == col);
    }

    @Override
    public String toString() {
        return String.format(
            "%d rows and %d cols, the ship is at [%d, %d]",
            rows, cols, row, col
        );
    }
}
