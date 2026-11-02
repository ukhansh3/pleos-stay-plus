package com.pleosstay.app.waiting.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "local_waiting_sessions", indices = {@Index(value = {"activeSlot"}, unique = true)})
public class WaitingEntity {
    @PrimaryKey @NonNull public String id = "";
    public long startedAt;
    public long plannedEndAt;
    @Nullable public Long endedAt;
    @NonNull public String status = "ACTIVE";
    @Nullable public String endReason;
    @Nullable public String parkingPlaceId;
    @Nullable public String placeName;
    public int revision;
    // SQLite permits multiple NULLs, but only one activeSlot=1.
    @Nullable public Integer activeSlot;
}
