package com.schoolsync.teacher.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class MealMenuDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val weekStart: String = "",
    val weekEnd: String = "",
    val meals: Map<String, DayMealsDoc> = emptyMap(),  // "Monday" -> meals
    val createdBy: String = ""
)

data class DayMealsDoc(
    val breakfast: MealDoc = MealDoc(),
    val lunch: MealDoc = MealDoc(),
    val snacks: MealDoc = MealDoc(),
    val dinner: MealDoc = MealDoc()
)

data class MealDoc(
    val items: List<String> = emptyList(),
    val time: String = "",
    val type: String = "veg"
)
