package com.pleosstay.app.waiting.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface WaitingDao {
    @Query("SELECT * FROM local_waiting_sessions ORDER BY startedAt DESC, id DESC")
    List<WaitingEntity> all();
    @Query("SELECT * FROM local_waiting_sessions WHERE id=:id LIMIT 1")
    WaitingEntity get(String id);
    @Insert void insert(WaitingEntity value);
    @Query("UPDATE local_waiting_sessions SET plannedEndAt=:end, revision=revision+1 WHERE id=:id AND status='ACTIVE' AND revision=:revision")
    int extend(String id, long end, int revision);
    @Query("UPDATE local_waiting_sessions SET status='ENDED', endedAt=:at, endReason=:reason, activeSlot=NULL, revision=revision+1 WHERE id=:id AND status='ACTIVE'")
    int end(String id, long at, String reason);
}
