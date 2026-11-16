package com.railsync.model;

/**
 * Whether a platform can currently accept trains.
 *
 * <ul>
 *   <li>OPERATIONAL - working normally.</li>
 *   <li>FAILED - broken down; no train may be allocated to it.</li>
 * </ul>
 */
public enum PlatformStatus {
    OPERATIONAL,
    FAILED
}
