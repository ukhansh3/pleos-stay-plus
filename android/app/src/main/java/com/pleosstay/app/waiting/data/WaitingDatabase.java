package com.pleosstay.app.waiting.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {WaitingEntity.class}, version = 1, exportSchema = false)
public abstract class WaitingDatabase extends RoomDatabase {
    public abstract WaitingDao waitingDao();
}
