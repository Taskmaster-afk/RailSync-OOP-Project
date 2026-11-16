package com.railsync.model;

/**
 * The allocation state of a train.
 *
 * <ul>
 *   <li>SCHEDULED - created, not yet processed by the allocator.</li>
 *   <li>ALLOCATED - holds a platform reservation.</li>
 *   <li>UNALLOCATED - the allocator tried and no safe platform exists.</li>
 * </ul>
 *
 * <p>Delay is tracked separately (Train.getDelayMinutes()) because a train
 * can be delayed AND allocated at the same time.</p>
 */
public enum TrainStatus {
    SCHEDULED,
    ALLOCATED,
    UNALLOCATED
}
