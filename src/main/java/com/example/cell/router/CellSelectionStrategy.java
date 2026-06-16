package com.example.cell.router;

import java.util.List;

import com.example.cell.CellMetadata;

public interface CellSelectionStrategy {
    String selectCellForUser(String userId, List<CellMetadata> availableCells);
}
