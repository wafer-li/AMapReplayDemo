package com.example.amapreplaydemo.bean

import kotlinx.serialization.Serializable

@Serializable
data class PointWrapper(
    val coordinates: List<List<Double>> = listOf()
)