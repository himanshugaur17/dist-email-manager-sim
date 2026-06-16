package com.example.cell.router.store;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemCellUserMapStore implements CellUserMapStore {
    // Looking at the documentation
    // i found that get is lock free and only put acquires a lock on the segment of
    // the map that is being modified.
    // But the case where a put is happening while a get is happening, the get can
    // get either the old value or newvalue
    // but it never be the cases that it returns half updated value. Reason for
    // this: the
    // put and get operations are atomic, by defintion atomic in concurrency
    // implies- that all the threads either see
    // the old value or the new value, but never a partially updated value.
    private final Map<String, String> userCellMap = new ConcurrentHashMap<>();

    @Override
    public String getUserCell(String userId) {
        return userCellMap.get(userId);
    }

    @Override
    public void addUserCellMapping(String userId, String cellId) {
        userCellMap.put(userId, cellId);
    }

    @Override
    public void removeUserCellMapping(String userId) {
        userCellMap.remove(userId);
    }

    @Override
    public void updateUserCellMapping(String userId, String oldCellId, String newCellId) {
        if (userCellMap.containsKey(userId) && userCellMap.get(userId).equals(oldCellId)) {
            userCellMap.put(userId, newCellId);
        }
    }

}
