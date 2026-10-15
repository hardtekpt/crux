package com.hardtekpt.crux.data

import kotlinx.serialization.Serializable

/** The training figures a week, a stat tile or a Home widget can show. */
@Serializable
enum class TrainingStat(val label: String, val description: String) {
    ON_PLAN("On plan", "Sessions and climbing days done against the Week schedule"),
    SESSIONS("Sessions", "Training sessions finished"),
    SETS("Sets", "Sets done in sessions"),
    TIME("Time trained", "Time in training sessions"),
    NEW_BESTS("New bests", "Results that beat your best on an exercise"),
}
