package com.example.cell.router;

import java.util.HashMap;
import java.util.Map;

import com.example.cell.CellDataPlane;
import com.example.cell.router.store.CellUserMapStore;

public class TierOneCellRouter implements CellRouter {
    private final CellUserMapStore cellUserMapStore;
    private final Map<String, CellDataPlane> cellDataPlanes = new HashMap<>();
    private final CellSelectionStrategy cellSelectionStrategy;

    public TierOneCellRouter(CellUserMapStore cellUserMapStore, CellSelectionStrategy cellSelectionStrategy) {
        // Initialization for Tier One routing
        this.cellUserMapStore = cellUserMapStore;
        this.cellSelectionStrategy = cellSelectionStrategy;
    }

    @Override
    public void routeRequest(String from, String to, String subject, String body) {
        String cellId = cellUserMapStore.getUserCell(from);
        if (cellId == null)
            cellId = cellSelectionStrategy.selectCellForUser(from,
                    cellDataPlanes.values().stream().map(CellDataPlane::getCellMetadata).toList());
        CellDataPlane cellDataPlane = cellDataPlanes.get(cellId);
        if (cellDataPlane != null) {
            cellDataPlane.sendEmail(from, to, subject, body);
        } else {
            throw new RuntimeException("No cell found for user: " + from);
        }
    }
}
