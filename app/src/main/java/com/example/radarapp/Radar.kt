package com.example.radarapp

data class Radar(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val type: String,
    val vitesse: Int,
    val route: String
)
