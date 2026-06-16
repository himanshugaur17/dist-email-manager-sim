package com.example.cell.router.store;

public interface CellUserMapStore {
    String getUserCell(String userId);

    void addUserCellMapping(String userId, String cellId);

    void removeUserCellMapping(String userId);

    void updateUserCellMapping(String userId, String oldCellId, String newCellId);
}
