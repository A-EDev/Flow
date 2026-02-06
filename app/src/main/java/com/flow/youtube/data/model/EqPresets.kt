package com.flow.youtube.data.model

object EqPresets {
    val presets = mapOf(
        "Flat" to ParametricEQ.createFlat(),
        
        "Bass Boost" to ParametricEQ(
            preamp = 0.0,
            bands = listOf(
                ParametricEQBand(60.0, 9.0, 0.7, FilterType.LSC), // Deep Bass - Increased to +9dB
                ParametricEQBand(200.0, 3.0, 0.7, FilterType.PK)  // Punch
            )
        ),
        
        "Rock" to ParametricEQ(
            preamp = 0.0,
            bands = listOf(
                ParametricEQBand(60.0, 5.0, 0.7, FilterType.LSC),
                ParametricEQBand(230.0, 3.0, 1.0, FilterType.PK),
                ParametricEQBand(910.0, -2.0, 1.0, FilterType.PK), // Cut mids
                ParametricEQBand(3600.0, 3.5, 1.0, FilterType.PK),
                ParametricEQBand(14000.0, 5.0, 0.7, FilterType.HSC)
            )
        ),
        
        "Pop" to ParametricEQ(
            preamp = 0.0,
            bands = listOf(
                ParametricEQBand(100.0, 4.0, 0.7, FilterType.PK),
                ParametricEQBand(2500.0, 2.5, 1.0, FilterType.PK), // Vocal presence
                ParametricEQBand(12000.0, 3.5, 0.7, FilterType.HSC) // Air
            )
        ),
        
        "Electronic" to ParametricEQ(
            preamp = -1.0,
            bands = listOf(
                ParametricEQBand(50.0, 7.0, 0.7, FilterType.LSC), // Sub bass
                ParametricEQBand(400.0, -1.0, 1.0, FilterType.PK),
                ParametricEQBand(2000.0, 2.5, 1.0, FilterType.PK),
                ParametricEQBand(8000.0, 5.0, 0.7, FilterType.HSC)
            )
        ),
        
        "R&B" to ParametricEQ(
            preamp = 0.0,
            bands = listOf(
                ParametricEQBand(70.0, 6.0, 0.9, FilterType.LSC),
                ParametricEQBand(300.0, -2.0, 1.0, FilterType.PK),
                ParametricEQBand(1200.0, -1.5, 1.0, FilterType.PK),
                ParametricEQBand(4000.0, 2.0, 1.0, FilterType.PK)
            )
        ),
        
        "Hip-Hop" to ParametricEQ(
            preamp = -1.0,
            bands = listOf(
                 ParametricEQBand(55.0, 8.0, 0.8, FilterType.LSC), // Heavy Sub
                 ParametricEQBand(250.0, 2.0, 1.0, FilterType.PK),
                 ParametricEQBand(1000.0, -2.0, 1.0, FilterType.PK),
                 ParametricEQBand(5000.0, 3.0, 1.0, FilterType.PK)
            )
        ),

        "Acoustic" to ParametricEQ(
             preamp = 0.0,
             bands = listOf(
                  ParametricEQBand(100.0, 2.0, 0.7, FilterType.PK),
                  ParametricEQBand(400.0, -1.0, 1.0, FilterType.PK),
                  ParametricEQBand(2000.0, 1.5, 1.0, FilterType.PK),
                  ParametricEQBand(6000.0, 3.0, 0.7, FilterType.HSC)
             )
        ),

        "Classical" to ParametricEQ(
             preamp = 0.0,
             bands = listOf(
                  ParametricEQBand(100.0, 3.0, 0.7, FilterType.LSC),
                  ParametricEQBand(500.0, -2.0, 1.0, FilterType.PK),
                  ParametricEQBand(2500.0, 2.0, 1.0, FilterType.PK),
                  ParametricEQBand(10000.0, 3.0, 0.7, FilterType.HSC)
             )
        ),
        
        "Vocal / Podcast" to ParametricEQ(
            preamp = 0.0,
            bands = listOf(
                ParametricEQBand(100.0, -2.0, 0.7, FilterType.LSC), // Remove rumble
                ParametricEQBand(1000.0, 3.0, 2.0, FilterType.PK),  // Speech intelligibility
                ParametricEQBand(5000.0, 2.0, 1.0, FilterType.PK)
            )
        ),

        "Jazz" to ParametricEQ(
            preamp = 0.0,
            bands = listOf(
                ParametricEQBand(100.0, 2.0, 0.7, FilterType.LSC),
                ParametricEQBand(500.0, -1.0, 1.0, FilterType.PK),
                ParametricEQBand(2500.0, 1.5, 1.0, FilterType.PK),
                ParametricEQBand(10000.0, 2.0, 0.7, FilterType.HSC)
            )
        ),
        
        "Metal" to ParametricEQ(
             preamp = 0.0,
             bands = listOf(
                  ParametricEQBand(60.0, 4.0, 0.8, FilterType.LSC),
                  ParametricEQBand(300.0, 3.0, 1.41, FilterType.PK), // Snare/Kick body
                  ParametricEQBand(1500.0, 2.0, 1.0, FilterType.PK),
                  ParametricEQBand(4000.0, 4.0, 1.0, FilterType.PK) // Distortion/Edge
             )
        )
    )
}
