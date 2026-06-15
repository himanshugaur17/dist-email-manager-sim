package com.example.cell;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.example.cell.db.PhysicalDbNode;
import com.example.helper.HashCalculator;
import com.example.helper.ThreeDigitRangeHasher;

public class CellContainer implements CellControlPlane {
    private final ConcurrentMap<Integer, PhysicalDbNode> nodesConsistentHashRingMap = new ConcurrentHashMap<>();
    private final HashCalculator hashCalculator = new ThreeDigitRangeHasher();

    @Override
    public void addCellNode(PhysicalDbNode node) {
        nodesConsistentHashRingMap.put(hashCalculator.hash(node.id()), node);
    }

    @Override
    public void removeCellNode(String nodeId) {
        nodesConsistentHashRingMap.remove(hashCalculator.hash(nodeId));
    }
}
