package com.example.cell.router;

import java.util.List;
import java.util.Random;

import com.example.cell.CellMetadata;

// even if there is a thundering herd problem
// the strategy will pick two random cells and choose the one with fewer users
// this helps us to avoid overloading a single cell and distribute the load more evenly across the available cells
public class PowerOfTwoChoicesStrategy implements CellSelectionStrategy {

    @Override
    public String selectCellForUser(String userId, List<CellMetadata> availableCells) {
        // pick two random cells from the available cells
        if (availableCells.size() < 2) {
            return availableCells.get(0).cellId();
        }
        int firstIndex = new Random().nextInt(availableCells.size());
        int secondIndex;
        do {
            secondIndex = new Random().nextInt(availableCells.size());
        } while (secondIndex == firstIndex);
        CellMetadata firstCell = availableCells.get(firstIndex);
        CellMetadata secondCell = availableCells.get(secondIndex);
        // choose the cell with fewer users
        return firstCell.usedCapacity() <= secondCell.usedCapacity() ? firstCell.cellId() : secondCell.cellId();
    }
}
