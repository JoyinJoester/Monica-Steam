package takagi.ru.monica.ui.main.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val SteamDockIcon: ImageVector
    get() {
        if (_steamDockIcon != null) {
            return _steamDockIcon!!
        }
        _steamDockIcon = ImageVector.Builder(
            name = "SteamDockIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 1.0f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(189f, 800f)
                quadTo(129f, 800f, 86.5f, 757f)
                reflectiveQuadTo(42f, 653f)
                quadTo(42f, 644f, 43f, 635f)
                reflectiveQuadTo(46f, 617f)
                lineTo(130f, 281f)
                quadTo(144f, 227f, 187f, 193.5f)
                reflectiveQuadTo(285f, 160f)
                horizontalLineTo(675f)
                quadTo(730f, 160f, 773f, 193.5f)
                reflectiveQuadTo(830f, 281f)
                lineTo(914f, 617f)
                quadTo(916f, 626f, 917.5f, 635.5f)
                reflectiveQuadTo(919f, 654f)
                quadTo(919f, 715f, 875.5f, 757.5f)
                reflectiveQuadTo(771f, 800f)
                quadTo(729f, 800f, 693f, 778f)
                reflectiveQuadTo(639f, 718f)
                lineTo(611f, 660f)
                quadTo(606f, 650f, 596f, 645f)
                reflectiveQuadTo(575f, 640f)
                horizontalLineTo(385f)
                quadTo(374f, 640f, 364f, 645f)
                reflectiveQuadTo(349f, 660f)
                lineTo(321f, 718f)
                quadTo(303f, 756f, 267f, 778f)
                reflectiveQuadTo(189f, 800f)
                close()
                moveTo(192f, 720f)
                quadTo(211f, 720f, 226.5f, 710f)
                reflectiveQuadTo(250f, 683f)
                lineTo(278f, 626f)
                quadTo(293f, 595f, 322f, 577.5f)
                reflectiveQuadTo(385f, 560f)
                horizontalLineTo(575f)
                quadTo(609f, 560f, 638f, 578f)
                reflectiveQuadTo(683f, 626f)
                lineTo(711f, 683f)
                quadTo(719f, 700f, 734.5f, 710f)
                reflectiveQuadTo(769f, 720f)
                quadTo(797f, 720f, 817f, 701.5f)
                reflectiveQuadTo(838f, 655f)
                quadTo(838f, 656f, 836f, 636f)
                lineTo(752f, 301f)
                quadTo(745f, 274f, 724f, 257f)
                reflectiveQuadTo(675f, 240f)
                horizontalLineTo(285f)
                quadTo(257f, 240f, 235.5f, 257f)
                reflectiveQuadTo(208f, 301f)
                lineTo(124f, 636f)
                quadTo(122f, 642f, 122f, 654f)
                quadTo(122f, 682f, 142.5f, 701f)
                reflectiveQuadTo(192f, 720f)
                close()
                moveTo(568.5f, 428.5f)
                quadTo(580f, 417f, 580f, 400f)
                reflectiveQuadTo(568.5f, 371.5f)
                quadTo(557f, 360f, 540f, 360f)
                reflectiveQuadTo(511.5f, 371.5f)
                quadTo(500f, 383f, 500f, 400f)
                reflectiveQuadTo(511.5f, 428.5f)
                quadTo(523f, 440f, 540f, 440f)
                reflectiveQuadTo(568.5f, 428.5f)
                close()
                moveTo(648.5f, 348.5f)
                quadTo(660f, 337f, 660f, 320f)
                reflectiveQuadTo(648.5f, 291.5f)
                quadTo(637f, 280f, 620f, 280f)
                reflectiveQuadTo(591.5f, 291.5f)
                quadTo(580f, 303f, 580f, 320f)
                reflectiveQuadTo(591.5f, 348.5f)
                quadTo(603f, 360f, 620f, 360f)
                reflectiveQuadTo(648.5f, 348.5f)
                close()
                moveTo(648.5f, 508.5f)
                quadTo(660f, 497f, 660f, 480f)
                reflectiveQuadTo(648.5f, 451.5f)
                quadTo(637f, 440f, 620f, 440f)
                reflectiveQuadTo(591.5f, 451.5f)
                quadTo(580f, 463f, 580f, 480f)
                reflectiveQuadTo(591.5f, 508.5f)
                quadTo(603f, 520f, 620f, 520f)
                reflectiveQuadTo(648.5f, 508.5f)
                close()
                moveTo(728.5f, 428.5f)
                quadTo(740f, 417f, 740f, 400f)
                reflectiveQuadTo(728.5f, 371.5f)
                quadTo(717f, 360f, 700f, 360f)
                reflectiveQuadTo(671.5f, 371.5f)
                quadTo(660f, 383f, 660f, 400f)
                reflectiveQuadTo(671.5f, 428.5f)
                quadTo(683f, 440f, 700f, 440f)
                reflectiveQuadTo(728.5f, 428.5f)
                close()
                moveTo(361.5f, 491f)
                quadTo(370f, 483f, 370f, 470f)
                verticalLineTo(430f)
                horizontalLineTo(410f)
                quadTo(423f, 430f, 431.5f, 421.5f)
                reflectiveQuadTo(440f, 400f)
                quadTo(440f, 387f, 431.5f, 378.5f)
                reflectiveQuadTo(410f, 370f)
                horizontalLineTo(370f)
                verticalLineTo(330f)
                quadTo(370f, 317f, 361.5f, 308.5f)
                reflectiveQuadTo(340f, 300f)
                quadTo(327f, 300f, 318.5f, 308.5f)
                reflectiveQuadTo(310f, 330f)
                verticalLineTo(370f)
                horizontalLineTo(270f)
                quadTo(257f, 370f, 248.5f, 378.5f)
                reflectiveQuadTo(240f, 400f)
                quadTo(240f, 413f, 248.5f, 421.5f)
                reflectiveQuadTo(270f, 430f)
                horizontalLineTo(310f)
                verticalLineTo(470f)
                quadTo(310f, 483f, 318.5f, 491.5f)
                reflectiveQuadTo(340f, 500f)
                quadTo(353f, 500f, 361.5f, 491f)
                close()
                moveTo(480f, 480f)
                close()
            }
        }.build()
        return _steamDockIcon!!
    }

private var _steamDockIcon: ImageVector? = null
