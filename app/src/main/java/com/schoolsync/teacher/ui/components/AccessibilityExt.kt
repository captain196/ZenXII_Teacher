package com.schoolsync.teacher.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

fun Modifier.describeAs(label: String, role: Role? = Role.Button): Modifier =
    this.semantics(mergeDescendants = true) {
        contentDescription = label
        if (role != null) this.role = role
    }
