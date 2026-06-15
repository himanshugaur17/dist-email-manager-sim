package com.example.cell;

import java.util.TreeMap;

import com.example.cell.db.PhysicalDbNode;
import com.example.helper.HashCalculator;
import com.example.helper.ThreeDigitRangeHasher;

public class CellContainer implements CellControlPlane, CellDataPlane {
    private final TreeMap<Integer, PhysicalDbNode> nodesConsistentHashRingMap = new TreeMap<>();
    private final HashCalculator hashCalculator = new ThreeDigitRangeHasher();

    @Override
    public void addCellNode(PhysicalDbNode node) {
        // in reality this won't be as simple
        // we need to move data around
        // do double writes for a while
        // once the gap is too small we can stop double writes
        // and sync the remaining data
        nodesConsistentHashRingMap.put(hashCalculator.hash(node.id()), node);
    }

    @Override
    public void removeCellNode(String nodeId) {
        // refer to addCellNode for the complexity of this operation
        nodesConsistentHashRingMap.remove(hashCalculator.hash(nodeId));
    }

    @Override
    public void sendEmail(String from, String to, String subject, String body) {
        int hash = hashCalculator.hash(to);
        PhysicalDbNode node = nodesConsistentHashRingMap.get(hash);
        if (node != null) {
            // Simulate sending email to the node
            System.out.println("Email sent to node: " + node.id() + " for recipient: " + to + " from: " + from);
        } else {
            System.out.println(" Email from: " + from + " could not be sent.");
        }
    }
}
