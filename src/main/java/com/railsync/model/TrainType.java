package com.railsync.model;

/**
 * The category label of a train: EXPRESS, PASSENGER or FREIGHT.
 *
 * <p>Why it exists: a Platform needs a simple way to say which categories it
 * accepts (a set of TrainType values). The enum is only a <b>label</b>.
 * The different <b>behaviour</b> of each category (priority, platform length,
 * dwell time, clearance) lives in the Train subclasses, because an enum field
 * alone would force us to write if/switch statements on the type in many
 * places. See docs/OOP_CONCEPTS.md "Why inheritance and not just an enum".</p>
 *
 * <p>Works with: Train.getType(), Platform.supports().</p>
 */
public enum TrainType {
    EXPRESS,
    PASSENGER,
    FREIGHT
}
