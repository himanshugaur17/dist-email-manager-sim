package com.example.cell;

import com.example.cell.db.PhysicalDbNode;

public interface CellControlPlane {

    void addCellNode(PhysicalDbNode node);

    void removeCellNode(String nodeId);

}