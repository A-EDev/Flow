package io.github.aedev.flow.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Every built-in palette with its two authored variants. The first twenty are Flow Desktop's
 * catalogue, copied from its `themes.ts`; the last six are Flow for Android's own palettes that
 * desktop does not have yet (notes/theme-desktop-additions.md carries them over).
 *
 * Several palettes keep their original enum name so a stored choice survives the update:
 * DARK is Flow Default, LAVENDER_MIST is Lavender, NORDIC_HORIZON is Nord, OCEAN_BLUE is Deep Ocean
 * and CREAM_LIGHT is Cream Paper.
 */
object FlowPalettes {
    private val palettes: Map<ThemeMode, PalettePair> =
        mapOf(
            ThemeMode.DARK to
                PalettePair(
                    id = "default",
                    light =
                        PaletteSeed(
                            primary = Color(0xFFFF0000),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFF606060),
                            background = Color(0xFFFFFFFF),
                            surface = Color(0xFFF3F3F3),
                            onSurface = Color(0xFF111111),
                            onSurfaceVariant = Color(0xFF5F5F5F),
                            outline = Color(0xFFD7D7D7),
                            error = Color(0xFFD32F2F),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFFFF0000),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFFAAAAAA),
                            background = Color(0xFF0F0F0F),
                            surface = Color(0xFF1D1D1D),
                            onSurface = Color(0xFFF4F4F4),
                            onSurfaceVariant = Color(0xFFB8B8B8),
                            outline = Color(0xFF343434),
                            error = Color(0xFFEF5350),
                        ),
                ),
            ThemeMode.MONOCHROME to
                PalettePair(
                    id = "monochrome",
                    light =
                        PaletteSeed(
                            primary = Color(0xFF202020),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFF595959),
                            background = Color(0xFFFFFFFF),
                            surface = Color(0xFFF2F2F2),
                            onSurface = Color(0xFF111111),
                            onSurfaceVariant = Color(0xFF575757),
                            outline = Color(0xFFD2D2D2),
                            error = Color(0xFFA62B2B),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFFEEEEEE),
                            onPrimary = Color(0xFF111111),
                            secondary = Color(0xFFBCBCBC),
                            background = Color(0xFF111111),
                            surface = Color(0xFF1D1D1D),
                            onSurface = Color(0xFFF3F3F3),
                            onSurfaceVariant = Color(0xFFBCBCBC),
                            outline = Color(0xFF3A3A3A),
                            error = Color(0xFFE06C6C),
                        ),
                ),
            ThemeMode.CATPPUCCIN to
                PalettePair(
                    id = "catppuccin",
                    light =
                        PaletteSeed(
                            primary = Color(0xFF8839EF),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFF7287FD),
                            background = Color(0xFFEFF1F5),
                            surface = Color(0xFFE6E9EF),
                            onSurface = Color(0xFF4C4F69),
                            onSurfaceVariant = Color(0xFF6C6F85),
                            outline = Color(0xFFBCC0CC),
                            error = Color(0xFFD20F39),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFFCBA6F7),
                            onPrimary = Color(0xFF1E1E2E),
                            secondary = Color(0xFF89B4FA),
                            background = Color(0xFF11111B),
                            surface = Color(0xFF1E1E2E),
                            onSurface = Color(0xFFCDD6F4),
                            onSurfaceVariant = Color(0xFFA6ADC8),
                            outline = Color(0xFF45475A),
                            error = Color(0xFFF38BA8),
                        ),
                ),
            ThemeMode.GREEN_APPLE to
                PalettePair(
                    id = "green-apple",
                    light =
                        PaletteSeed(
                            primary = Color(0xFF3F7D20),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFF72A83B),
                            background = Color(0xFFFBFFF7),
                            surface = Color(0xFFEEF6E8),
                            onSurface = Color(0xFF17210F),
                            onSurfaceVariant = Color(0xFF53614A),
                            outline = Color(0xFFC7D7BB),
                            error = Color(0xFFBA1A1A),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFFA5D66A),
                            onPrimary = Color(0xFF1D3700),
                            secondary = Color(0xFF89B75A),
                            background = Color(0xFF10150C),
                            surface = Color(0xFF1A2214),
                            onSurface = Color(0xFFE7F0DF),
                            onSurfaceVariant = Color(0xFFBDCBB3),
                            outline = Color(0xFF3B4932),
                            error = Color(0xFFFFB4AB),
                        ),
                ),
            ThemeMode.LAVENDER_MIST to
                PalettePair(
                    id = "lavender",
                    light =
                        PaletteSeed(
                            primary = Color(0xFF7357A4),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFF8D73B8),
                            background = Color(0xFFFDF9FF),
                            surface = Color(0xFFF3EDFA),
                            onSurface = Color(0xFF211A29),
                            onSurfaceVariant = Color(0xFF62586C),
                            outline = Color(0xFFD0C4DB),
                            error = Color(0xFFBA1A1A),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFFD2B8FF),
                            onPrimary = Color(0xFF3E246C),
                            secondary = Color(0xFFBEA4E6),
                            background = Color(0xFF151119),
                            surface = Color(0xFF211A28),
                            onSurface = Color(0xFFEEE6F2),
                            onSurfaceVariant = Color(0xFFCABFD0),
                            outline = Color(0xFF4A4053),
                            error = Color(0xFFFFB4AB),
                        ),
                ),
            ThemeMode.NORDIC_HORIZON to
                PalettePair(
                    id = "nord",
                    light =
                        PaletteSeed(
                            primary = Color(0xFF5E81AC),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFF81A1C1),
                            background = Color(0xFFECEFF4),
                            surface = Color(0xFFE5E9F0),
                            onSurface = Color(0xFF2E3440),
                            onSurfaceVariant = Color(0xFF4C566A),
                            outline = Color(0xFFC3CAD5),
                            error = Color(0xFFBF616A),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFF88C0D0),
                            onPrimary = Color(0xFF1F2933),
                            secondary = Color(0xFF81A1C1),
                            background = Color(0xFF242933),
                            surface = Color(0xFF2E3440),
                            onSurface = Color(0xFFECEFF4),
                            onSurfaceVariant = Color(0xFFD8DEE9),
                            outline = Color(0xFF4C566A),
                            error = Color(0xFFBF616A),
                        ),
                ),
            ThemeMode.TAKO to
                PalettePair(
                    id = "tako",
                    light =
                        PaletteSeed(
                            primary = Color(0xFF6650A4),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFF7D5260),
                            background = Color(0xFFFFF7FF),
                            surface = Color(0xFFF6EEF8),
                            onSurface = Color(0xFF211F26),
                            onSurfaceVariant = Color(0xFF625B67),
                            outline = Color(0xFFCAC2CF),
                            error = Color(0xFFBA1A1A),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFFD0BCFF),
                            onPrimary = Color(0xFF381E72),
                            secondary = Color(0xFFE8B9C7),
                            background = Color(0xFF17131C),
                            surface = Color(0xFF221D29),
                            onSurface = Color(0xFFE9E1EB),
                            onSurfaceVariant = Color(0xFFCCC3D0),
                            outline = Color(0xFF4B4450),
                            error = Color(0xFFFFB4AB),
                        ),
                ),
            ThemeMode.YIN_YANG to
                PalettePair(
                    id = "yin-yang",
                    light =
                        PaletteSeed(
                            primary = Color(0xFF343434),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFF6E6E6E),
                            background = Color(0xFFFAFAFA),
                            surface = Color(0xFFEDEDED),
                            onSurface = Color(0xFF151515),
                            onSurfaceVariant = Color(0xFF5B5B5B),
                            outline = Color(0xFFCCCCCC),
                            error = Color(0xFFB3261E),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFFFAFAFA),
                            onPrimary = Color(0xFF171717),
                            secondary = Color(0xFFC7C7C7),
                            background = Color(0xFF0B0B0B),
                            surface = Color(0xFF171717),
                            onSurface = Color(0xFFF5F5F5),
                            onSurfaceVariant = Color(0xFFBDBDBD),
                            outline = Color(0xFF383838),
                            error = Color(0xFFF2B8B5),
                        ),
                ),
            ThemeMode.STRAWBERRY_DAIQUIRI to
                PalettePair(
                    id = "strawberry-daiquiri",
                    light =
                        PaletteSeed(
                            primary = Color(0xFFB3264F),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFF9B405B),
                            background = Color(0xFFFFF8F8),
                            surface = Color(0xFFFCEBED),
                            onSurface = Color(0xFF28171B),
                            onSurfaceVariant = Color(0xFF6B555B),
                            outline = Color(0xFFDBC0C7),
                            error = Color(0xFFBA1A1A),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFFFFB1C3),
                            onPrimary = Color(0xFF67002B),
                            secondary = Color(0xFFE8B8C4),
                            background = Color(0xFF1C1013),
                            surface = Color(0xFF291A1E),
                            onSurface = Color(0xFFF4DFE4),
                            onSurfaceVariant = Color(0xFFD6C0C6),
                            outline = Color(0xFF523B41),
                            error = Color(0xFFFFB4AB),
                        ),
                ),
            ThemeMode.KANAGAWA to
                PalettePair(
                    id = "kanagawa",
                    light =
                        PaletteSeed(
                            primary = Color(0xFF6F5C2F),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFF597B75),
                            background = Color(0xFFF2ECDC),
                            surface = Color(0xFFE7DFCF),
                            onSurface = Color(0xFF36322B),
                            onSurfaceVariant = Color(0xFF6F685B),
                            outline = Color(0xFFC5BAA5),
                            error = Color(0xFFC34043),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFFE6C384),
                            onPrimary = Color(0xFF282727),
                            secondary = Color(0xFF7E9CD8),
                            background = Color(0xFF1F1F28),
                            surface = Color(0xFF2A2A37),
                            onSurface = Color(0xFFDCD7BA),
                            onSurfaceVariant = Color(0xFFC8C093),
                            outline = Color(0xFF54546D),
                            error = Color(0xFFE46876),
                        ),
                ),
            ThemeMode.TOKYO_NIGHT to
                PalettePair(
                    id = "tokyo-night",
                    light =
                        PaletteSeed(
                            primary = Color(0xFF34548A),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFF5A4A78),
                            background = Color(0xFFD5D6DB),
                            surface = Color(0xFFCBCCD1),
                            onSurface = Color(0xFF343B58),
                            onSurfaceVariant = Color(0xFF596172),
                            outline = Color(0xFFA8ABB5),
                            error = Color(0xFF8C4351),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFF7AA2F7),
                            onPrimary = Color(0xFF10121B),
                            secondary = Color(0xFFBB9AF7),
                            background = Color(0xFF16161E),
                            surface = Color(0xFF1F2335),
                            onSurface = Color(0xFFC0CAF5),
                            onSurfaceVariant = Color(0xFFA9B1D6),
                            outline = Color(0xFF3B4261),
                            error = Color(0xFFF7768E),
                        ),
                ),
            ThemeMode.ROSE_PINE to
                PalettePair(
                    id = "rose-pine",
                    light =
                        PaletteSeed(
                            primary = Color(0xFF907AA9),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFFD7827E),
                            background = Color(0xFFFAF4ED),
                            surface = Color(0xFFF2E9E1),
                            onSurface = Color(0xFF575279),
                            onSurfaceVariant = Color(0xFF797593),
                            outline = Color(0xFFCECACD),
                            error = Color(0xFFB4637A),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFFC4A7E7),
                            onPrimary = Color(0xFF191724),
                            secondary = Color(0xFFEBBCBA),
                            background = Color(0xFF191724),
                            surface = Color(0xFF26233A),
                            onSurface = Color(0xFFE0DEF4),
                            onSurfaceVariant = Color(0xFF908CAA),
                            outline = Color(0xFF403D52),
                            error = Color(0xFFEB6F92),
                        ),
                ),
            ThemeMode.EVERFOREST to
                PalettePair(
                    id = "everforest",
                    light =
                        PaletteSeed(
                            primary = Color(0xFF8DA101),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFF35A77C),
                            background = Color(0xFFFDF6E3),
                            surface = Color(0xFFF4F0D9),
                            onSurface = Color(0xFF5C6A72),
                            onSurfaceVariant = Color(0xFF829181),
                            outline = Color(0xFFD3CDB2),
                            error = Color(0xFFF85552),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFFA7C080),
                            onPrimary = Color(0xFF1E2326),
                            secondary = Color(0xFF83C092),
                            background = Color(0xFF1E2326),
                            surface = Color(0xFF272E33),
                            onSurface = Color(0xFFD3C6AA),
                            onSurfaceVariant = Color(0xFF9DA9A0),
                            outline = Color(0xFF414B50),
                            error = Color(0xFFE67E80),
                        ),
                ),
            ThemeMode.GRUVBOX to
                PalettePair(
                    id = "gruvbox",
                    light =
                        PaletteSeed(
                            primary = Color(0xFFB57614),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFF79740E),
                            background = Color(0xFFFBF1C7),
                            surface = Color(0xFFEBDBB2),
                            onSurface = Color(0xFF3C3836),
                            onSurfaceVariant = Color(0xFF665C54),
                            outline = Color(0xFFD5C4A1),
                            error = Color(0xFFCC241D),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFFFABD2F),
                            onPrimary = Color(0xFF282828),
                            secondary = Color(0xFFB8BB26),
                            background = Color(0xFF1D2021),
                            surface = Color(0xFF282828),
                            onSurface = Color(0xFFEBDBB2),
                            onSurfaceVariant = Color(0xFFBDAE93),
                            outline = Color(0xFF504945),
                            error = Color(0xFFFB4934),
                        ),
                ),
            ThemeMode.DRACULA to
                PalettePair(
                    id = "dracula",
                    light =
                        PaletteSeed(
                            primary = Color(0xFF6D4AA2),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFFA33C83),
                            background = Color(0xFFF8F7FB),
                            surface = Color(0xFFECEAF2),
                            onSurface = Color(0xFF282A36),
                            onSurfaceVariant = Color(0xFF626473),
                            outline = Color(0xFFCFCCD8),
                            error = Color(0xFFC93654),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFFBD93F9),
                            onPrimary = Color(0xFF282A36),
                            secondary = Color(0xFFFF79C6),
                            background = Color(0xFF21222C),
                            surface = Color(0xFF282A36),
                            onSurface = Color(0xFFF8F8F2),
                            onSurfaceVariant = Color(0xFFC5C8D4),
                            outline = Color(0xFF44475A),
                            error = Color(0xFFFF5555),
                        ),
                ),
            ThemeMode.SOLARIZED to
                PalettePair(
                    id = "solarized",
                    light =
                        PaletteSeed(
                            primary = Color(0xFF268BD2),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFF2AA198),
                            background = Color(0xFFFDF6E3),
                            surface = Color(0xFFEEE8D5),
                            onSurface = Color(0xFF586E75),
                            onSurfaceVariant = Color(0xFF657B83),
                            outline = Color(0xFFD6CFB8),
                            error = Color(0xFFDC322F),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFF2AA198),
                            onPrimary = Color(0xFF002B36),
                            secondary = Color(0xFF268BD2),
                            background = Color(0xFF002B36),
                            surface = Color(0xFF073642),
                            onSurface = Color(0xFFEEE8D5),
                            onSurfaceVariant = Color(0xFF93A1A1),
                            outline = Color(0xFF335963),
                            error = Color(0xFFDC322F),
                        ),
                ),
            ThemeMode.TIDE to
                PalettePair(
                    id = "tide",
                    light =
                        PaletteSeed(
                            primary = Color(0xFF247F83),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFF5C8F91),
                            background = Color(0xFFF4FBFB),
                            surface = Color(0xFFE5F1F1),
                            onSurface = Color(0xFF183638),
                            onSurfaceVariant = Color(0xFF587173),
                            outline = Color(0xFFBFD1D1),
                            error = Color(0xFFBA1A1A),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFF78C6C5),
                            onPrimary = Color(0xFF093737),
                            secondary = Color(0xFF97B8B8),
                            background = Color(0xFF101A1C),
                            surface = Color(0xFF182529),
                            onSurface = Color(0xFFDCE8E8),
                            onSurfaceVariant = Color(0xFFAABBBB),
                            outline = Color(0xFF34474B),
                            error = Color(0xFFFFB4AB),
                        ),
                ),
            ThemeMode.SAGE to
                PalettePair(
                    id = "sage",
                    light =
                        PaletteSeed(
                            primary = Color(0xFF5D7456),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFF778870),
                            background = Color(0xFFF7FAF4),
                            surface = Color(0xFFEBF0E7),
                            onSurface = Color(0xFF20281D),
                            onSurfaceVariant = Color(0xFF5D6858),
                            outline = Color(0xFFC5CEC0),
                            error = Color(0xFFBA1A1A),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFFB4CDA9),
                            onPrimary = Color(0xFF21351D),
                            secondary = Color(0xFFAEBFA7),
                            background = Color(0xFF121812),
                            surface = Color(0xFF1C251D),
                            onSurface = Color(0xFFE2E9DF),
                            onSurfaceVariant = Color(0xFFBEC8BA),
                            outline = Color(0xFF3B493A),
                            error = Color(0xFFFFB4AB),
                        ),
                ),
            ThemeMode.CAFFEINE to
                PalettePair(
                    id = "caffeine",
                    light =
                        PaletteSeed(
                            primary = Color(0xFF795548),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFF9A6F5D),
                            background = Color(0xFFFFFAF6),
                            surface = Color(0xFFF3E9E1),
                            onSurface = Color(0xFF30231E),
                            onSurfaceVariant = Color(0xFF6D5A51),
                            outline = Color(0xFFD7C4B9),
                            error = Color(0xFFBA1A1A),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFFE6C2AA),
                            onPrimary = Color(0xFF442A1E),
                            secondary = Color(0xFFC7A797),
                            background = Color(0xFF15110F),
                            surface = Color(0xFF221A17),
                            onSurface = Color(0xFFEEE4DF),
                            onSurfaceVariant = Color(0xFFCDBDB5),
                            outline = Color(0xFF493A34),
                            error = Color(0xFFFFB4AB),
                        ),
                ),
            ThemeMode.CLAUDE to
                PalettePair(
                    id = "claude",
                    light =
                        PaletteSeed(
                            primary = Color(0xFFC15F3C),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFF7D685F),
                            background = Color(0xFFF7F4EF),
                            surface = Color(0xFFECE7DF),
                            onSurface = Color(0xFF2F2926),
                            onSurfaceVariant = Color(0xFF6B605B),
                            outline = Color(0xFFD2C9C0),
                            error = Color(0xFFB3261E),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFFE07A58),
                            onPrimary = Color(0xFF2B1510),
                            secondary = Color(0xFFB9A49B),
                            background = Color(0xFF1D1B19),
                            surface = Color(0xFF282522),
                            onSurface = Color(0xFFEEE9E5),
                            onSurfaceVariant = Color(0xFFC8BEB8),
                            outline = Color(0xFF4B4541),
                            error = Color(0xFFFFB4AB),
                        ),
                ),
            ThemeMode.OCEAN_BLUE to
                PalettePair(
                    id = "deep-ocean",
                    light =
                        PaletteSeed(
                            primary = Color(0xFF00629B),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFF2E7FA6),
                            background = Color(0xFFF5FAFF),
                            surface = Color(0xFFE6F0F9),
                            onSurface = Color(0xFF0B2135),
                            onSurfaceVariant = Color(0xFF4A6072),
                            outline = Color(0xFFBFD0DF),
                            error = Color(0xFFBA1A1A),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFF4FB3E8),
                            onPrimary = Color(0xFF00253A),
                            secondary = Color(0xFF7FD1E0),
                            background = Color(0xFF0A1929),
                            surface = Color(0xFF132438),
                            onSurface = Color(0xFFE3F2FD),
                            onSurfaceVariant = Color(0xFFA9C1D6),
                            outline = Color(0xFF2A4058),
                            error = Color(0xFFFFB4AB),
                        ),
                ),
            ThemeMode.GUNMETAL to
                PalettePair(
                    id = "gunmetal",
                    light =
                        PaletteSeed(
                            primary = Color(0xFF455A64),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFF607D8B),
                            background = Color(0xFFF7F9FA),
                            surface = Color(0xFFECEFF1),
                            onSurface = Color(0xFF1C2429),
                            onSurfaceVariant = Color(0xFF546E7A),
                            outline = Color(0xFFCFD8DC),
                            error = Color(0xFFB3261E),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFF90A4AE),
                            onPrimary = Color(0xFF11181D),
                            secondary = Color(0xFF78909C),
                            background = Color(0xFF0F1216),
                            surface = Color(0xFF1A1F26),
                            onSurface = Color(0xFFECEFF1),
                            onSurfaceVariant = Color(0xFFB0BEC5),
                            outline = Color(0xFF37424C),
                            error = Color(0xFFEF9A9A),
                        ),
                ),
            ThemeMode.COSMIC_VOID to
                PalettePair(
                    id = "cosmic-void",
                    light =
                        PaletteSeed(
                            primary = Color(0xFF5B2DE0),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFF7C4DFF),
                            background = Color(0xFFFBF9FF),
                            surface = Color(0xFFF0ECFB),
                            onSurface = Color(0xFF1B1726),
                            onSurfaceVariant = Color(0xFF5B5470),
                            outline = Color(0xFFD6CFE6),
                            error = Color(0xFFB3261E),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFFA384FF),
                            onPrimary = Color(0xFF1A0059),
                            secondary = Color(0xFF8C63FF),
                            background = Color(0xFF050505),
                            surface = Color(0xFF121212),
                            onSurface = Color(0xFFE8E3F5),
                            onSurfaceVariant = Color(0xFFB9B0CC),
                            outline = Color(0xFF2E2A38),
                            error = Color(0xFFFF8A80),
                        ),
                ),
            ThemeMode.CYBERPUNK to
                PalettePair(
                    id = "cyberpunk",
                    light =
                        PaletteSeed(
                            primary = Color(0xFFA3008F),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFF00838F),
                            background = Color(0xFFFFF7FE),
                            surface = Color(0xFFF6E9F7),
                            onSurface = Color(0xFF24102A),
                            onSurfaceVariant = Color(0xFF66506B),
                            outline = Color(0xFFDCC5DF),
                            error = Color(0xFFBA1A1A),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFFFF4DF0),
                            onPrimary = Color(0xFF2B0029),
                            secondary = Color(0xFF00E5FF),
                            background = Color(0xFF0D001A),
                            surface = Color(0xFF1A0A2E),
                            onSurface = Color(0xFFF2E6FF),
                            onSurfaceVariant = Color(0xFFC4B0DD),
                            outline = Color(0xFF3D2A5A),
                            error = Color(0xFFFF5C8A),
                        ),
                ),
            ThemeMode.ROYAL_GOLD to
                PalettePair(
                    id = "royal-gold",
                    light =
                        PaletteSeed(
                            primary = Color(0xFF7D6300),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFF9A7D12),
                            background = Color(0xFFFFFDF5),
                            surface = Color(0xFFF7F0DC),
                            onSurface = Color(0xFF221C08),
                            onSurfaceVariant = Color(0xFF665C3D),
                            outline = Color(0xFFDDD2B0),
                            error = Color(0xFFBA1A1A),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFFFFD700),
                            onPrimary = Color(0xFF2A2100),
                            secondary = Color(0xFFC5A000),
                            background = Color(0xFF050505),
                            surface = Color(0xFF141210),
                            onSurface = Color(0xFFFFF8E1),
                            onSurfaceVariant = Color(0xFFD6C9A3),
                            outline = Color(0xFF3A3423),
                            error = Color(0xFFFFB4AB),
                        ),
                ),
            ThemeMode.CREAM_LIGHT to
                PalettePair(
                    id = "cream-paper",
                    light =
                        PaletteSeed(
                            primary = Color(0xFF7F6157),
                            onPrimary = Color(0xFFFFFFFF),
                            secondary = Color(0xFFA1887F),
                            background = Color(0xFFFFFBF0),
                            surface = Color(0xFFF5F5DC),
                            onSurface = Color(0xFF3E2723),
                            onSurfaceVariant = Color(0xFF6D5C52),
                            outline = Color(0xFFDCD3BD),
                            error = Color(0xFFB3261E),
                        ),
                    dark =
                        PaletteSeed(
                            primary = Color(0xFFD7B7A8),
                            onPrimary = Color(0xFF3B2620),
                            secondary = Color(0xFFBCAAA4),
                            background = Color(0xFF1A1712),
                            surface = Color(0xFF25211A),
                            onSurface = Color(0xFFF1EBDF),
                            onSurfaceVariant = Color(0xFFCFC4B3),
                            outline = Color(0xFF4A4336),
                            error = Color(0xFFFFB4AB),
                        ),
                ),
        )

    /** The palette behind [mode], or Flow Default for the modes that are not palettes themselves. */
    fun forMode(mode: ThemeMode): PalettePair = palettes[mode] ?: palettes.getValue(ThemeMode.DARK)

    val default: PalettePair get() = palettes.getValue(ThemeMode.DARK)
}
