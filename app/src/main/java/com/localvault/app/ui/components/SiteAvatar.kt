package com.localvault.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localvault.app.ui.theme.VaultTheme
import java.util.Locale
import kotlin.math.abs

enum class BuiltInGlyph {
    NONE, AMAZON, GITHUB, X_CORP, YOUTUBE, GOOGLE, RAKUTEN, SPOTIFY, LINE
}

data class BrandStyle(
    val background: Color,
    val foreground: Color,
    val label: String,
    val glyph: BuiltInGlyph = BuiltInGlyph.NONE,
    val knownPackages: List<String> = emptyList()
)

/**
 * 完全オフラインでサイト／アプリのアイコンを解決するリゾルバー。
 * 1. 端末にインストール済みアプリがあれば PackageManager から本物のアプリアイコンを取得
 * 2. 未インストールの主要サイトは内蔵のベクターシンボル＋ブランドカラーで描画
 * 3. その他サイトはドメインハッシュから一貫したブランドカラー＋モノグラムを描画
 * ※ モノクロ設定 ON 時は本物アイコンにも彩度0フィルターを自動適用
 */
object BrandIconResolver {
    private val iconCache = LruCache<String, Bitmap>(64)

    private val knownBrands: List<Pair<String, BrandStyle>> = listOf(
        "amazon" to BrandStyle(
            Color(0xFFFF9900), Color(0xFF111111), "a",
            BuiltInGlyph.AMAZON, listOf("com.amazon.mShop.android.shopping", "jp.co.amazon.mShop.android.shopping")
        ),
        "rakuten" to BrandStyle(
            Color(0xFFBF0000), Color.White, "R",
            BuiltInGlyph.RAKUTEN, listOf("jp.co.rakuten.android", "jp.co.rakuten.pay")
        ),
        "github" to BrandStyle(
            Color(0xFF24292F), Color.White, "GH",
            BuiltInGlyph.GITHUB, listOf("com.github.android")
        ),
        "google" to BrandStyle(
            Color(0xFF4285F4), Color.White, "G",
            BuiltInGlyph.GOOGLE, listOf("com.google.android.googlequicksearchbox")
        ),
        "gmail" to BrandStyle(
            Color(0xFFEA4335), Color.White, "M",
            BuiltInGlyph.NONE, listOf("com.google.android.gm")
        ),
        "youtube" to BrandStyle(
            Color(0xFFFF0000), Color.White, "YT",
            BuiltInGlyph.YOUTUBE, listOf("com.google.android.youtube")
        ),
        "twitter" to BrandStyle(
            Color(0xFF111111), Color.White, "X",
            BuiltInGlyph.X_CORP, listOf("com.twitter.android")
        ),
        "x.com" to BrandStyle(
            Color(0xFF111111), Color.White, "X",
            BuiltInGlyph.X_CORP, listOf("com.twitter.android")
        ),
        "yahoo" to BrandStyle(
            Color(0xFFFF0033), Color.White, "Y!",
            BuiltInGlyph.NONE, listOf("jp.co.yahoo.android.yjtop", "jp.co.yahoo.android.ymail")
        ),
        "line" to BrandStyle(
            Color(0xFF06C755), Color.White, "LINE",
            BuiltInGlyph.LINE, listOf("jp.naver.line.android")
        ),
        "netflix" to BrandStyle(
            Color(0xFFE50914), Color.White, "N",
            BuiltInGlyph.NONE, listOf("com.netflix.mediaclient")
        ),
        "spotify" to BrandStyle(
            Color(0xFF1DB954), Color(0xFF0B0D11), "S",
            BuiltInGlyph.SPOTIFY, listOf("com.spotify.music")
        ),
        "instagram" to BrandStyle(
            Color(0xFFE1306C), Color.White, "IG",
            BuiltInGlyph.NONE, listOf("com.instagram.android")
        ),
        "facebook" to BrandStyle(
            Color(0xFF1877F2), Color.White, "f",
            BuiltInGlyph.NONE, listOf("com.facebook.katana")
        ),
        "discord" to BrandStyle(
            Color(0xFF5865F2), Color.White, "D",
            BuiltInGlyph.NONE, listOf("com.discord")
        ),
        "slack" to BrandStyle(
            Color(0xFF4A154B), Color.White, "#",
            BuiltInGlyph.NONE, listOf("com.Slack")
        ),
        "apple" to BrandStyle(Color(0xFF2C2C2E), Color.White, "A"),
        "icloud" to BrandStyle(Color(0xFF007AFF), Color.White, "iC"),
        "microsoft" to BrandStyle(Color(0xFF00A4EF), Color.White, "MS"),
        "mercari" to BrandStyle(
            Color(0xFFFF0211), Color.White, "M",
            BuiltInGlyph.NONE, listOf("com.kouzoh.mercari")
        ),
        "paypay" to BrandStyle(
            Color(0xFFFF0033), Color.White, "P",
            BuiltInGlyph.NONE, listOf("jp.ne.paypay.android.app")
        ),
        "nintendo" to BrandStyle(Color(0xFFE60012), Color.White, "N"),
        "sony" to BrandStyle(Color(0xFF003087), Color.White, "S"),
        "playstation" to BrandStyle(
            Color(0xFF003791), Color.White, "PS",
            BuiltInGlyph.NONE, listOf("com.scee.psxandroid")
        ),
        "openai" to BrandStyle(
            Color(0xFF10A37F), Color.White, "AI",
            BuiltInGlyph.NONE, listOf("com.openai.chatgpt")
        ),
        "chatgpt" to BrandStyle(
            Color(0xFF10A37F), Color.White, "AI",
            BuiltInGlyph.NONE, listOf("com.openai.chatgpt")
        ),
        "notion" to BrandStyle(
            Color(0xFF1F1F1F), Color.White, "N",
            BuiltInGlyph.NONE, listOf("notion.id")
        ),
        "dropbox" to BrandStyle(
            Color(0xFF0061FF), Color.White, "DB",
            BuiltInGlyph.NONE, listOf("com.dropbox.android")
        ),
        "adobe" to BrandStyle(Color(0xFFFF0000), Color.White, "Ae"),
        "steam" to BrandStyle(
            Color(0xFF1B2838), Color.White, "ST",
            BuiltInGlyph.NONE, listOf("com.valvesoftware.android.steam.community")
        ),
        "twitch" to BrandStyle(
            Color(0xFF9146FF), Color.White, "Tw",
            BuiltInGlyph.NONE, listOf("tv.twitch.android.app")
        ),
        "reddit" to BrandStyle(
            Color(0xFFFF4500), Color.White, "r/",
            BuiltInGlyph.NONE, listOf("com.reddit.frontpage")
        ),
        "linkedin" to BrandStyle(
            Color(0xFF0A66C2), Color.White, "in",
            BuiltInGlyph.NONE, listOf("com.linkedin.android")
        ),
        "tiktok" to BrandStyle(
            Color(0xFF111111), Color.White, "TT",
            BuiltInGlyph.NONE, listOf("com.ss.android.ugc.trill", "com.zhiliaoapp.musically")
        ),
        "sbi" to BrandStyle(Color(0xFF004098), Color.White, "SBI"),
        "smbc" to BrandStyle(Color(0xFF00873C), Color.White, "三井"),
        "mufg" to BrandStyle(Color(0xFFE60012), Color.White, "三菱"),
        "dmm" to BrandStyle(Color(0xFF1A1A1A), Color.White, "DMM"),
        "docomo" to BrandStyle(Color(0xFFCC0033), Color.White, "d"),
        "au.com" to BrandStyle(Color(0xFFEB5505), Color.White, "au"),
        "softbank" to BrandStyle(Color(0xFF8C8C8C), Color.White, "SB")
    )

    private val fallbackPalette = listOf(
        Color(0xFF3B82F6),
        Color(0xFF8B5CF6),
        Color(0xFFEC4899),
        Color(0xFF10B981),
        Color(0xFFF59E0B),
        Color(0xFF06B6D4),
        Color(0xFFEF4444),
        Color(0xFF6366F1)
    )

    fun resolve(title: String, domain: String): BrandStyle {
        val key = "${title.lowercase(Locale.ROOT)} ${domain.lowercase(Locale.ROOT)}"
        for ((keyword, style) in knownBrands) {
            if (key.contains(keyword)) return style
        }
        val seed = (domain.ifBlank { title }).lowercase(Locale.ROOT)
        val color = fallbackPalette[abs(seed.hashCode()) % fallbackPalette.size]
        val initial = title.trim().firstOrNull()?.uppercaseChar()?.toString()
            ?: domain.trim().firstOrNull()?.uppercaseChar()?.toString()
            ?: "?"
        return BrandStyle(
            background = color,
            foreground = Color.White,
            label = initial
        )
    }

    /**
     * 端末内にインストール済みアプリがあればその本物のアプリアイコンを完全オフラインで取得する。
     */
    fun loadInstalledAppIcon(
        context: Context,
        explicitPackageName: String,
        style: BrandStyle
    ): Bitmap? {
        val candidates = buildList {
            if (explicitPackageName.isNotBlank() &&
                !explicitPackageName.contains("chrome", ignoreCase = true) &&
                !explicitPackageName.contains("browser", ignoreCase = true) &&
                !explicitPackageName.contains("firefox", ignoreCase = true)
            ) {
                add(explicitPackageName)
            }
            addAll(style.knownPackages)
        }

        val pm = context.packageManager
        for (pkg in candidates) {
            iconCache.get(pkg)?.let { return it }
            val drawable = runCatching { pm.getApplicationIcon(pkg) }.getOrNull() ?: continue
            val bmp = drawableToBitmap(drawable)
            iconCache.put(pkg, bmp)
            return bmp
        }
        return null
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 128
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 128
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}

@Composable
fun SiteAvatar(
    title: String,
    domain: String,
    isMonochrome: Boolean,
    size: Dp = 42.dp,
    packageName: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val style = remember(title, domain) { BrandIconResolver.resolve(title, domain) }
    val appBitmap = remember(title, domain, packageName) {
        BrandIconResolver.loadInstalledAppIcon(context, packageName, style)
    }

    val colors = VaultTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val monoFilter = remember {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }

    if (appBitmap != null) {
        // 端末内の本物のアプリアイコンを表示（モノクロ設定時は彩度0フィルター適用）
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .border(BorderStroke(1.dp, colors.borderStrong), shape)
        ) {
            Image(
                bitmap = appBitmap.asImageBitmap(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                colorFilter = if (isMonochrome) monoFilter else null,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    val bgColor = if (isMonochrome) colors.surfaceElevated else style.background
    val fgColor = if (isMonochrome) colors.textPrimary else style.foreground
    val borderStroke = if (isMonochrome) {
        BorderStroke(1.dp, colors.borderStrong)
    } else {
        BorderStroke(1.dp, colors.border)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(bgColor)
            .border(borderStroke, shape),
        contentAlignment = Alignment.Center
    ) {
        when (style.glyph) {
            BuiltInGlyph.X_CORP -> {
                ComposeCanvas(modifier = Modifier.size(size * 0.52f)) {
                    val w = this.size.width
                    val h = this.size.height
                    drawLine(fgColor, Offset(w * 0.12f, h * 0.1f), Offset(w * 0.88f, h * 0.9f), strokeWidth = w * 0.16f, cap = StrokeCap.Round)
                    drawLine(fgColor, Offset(w * 0.86f, h * 0.1f), Offset(w * 0.14f, h * 0.9f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
                }
            }
            BuiltInGlyph.YOUTUBE -> {
                ComposeCanvas(modifier = Modifier.size(size * 0.48f)) {
                    val w = this.size.width
                    val h = this.size.height
                    val path = Path().apply {
                        moveTo(w * 0.25f, h * 0.15f)
                        lineTo(w * 0.85f, h * 0.5f)
                        lineTo(w * 0.25f, h * 0.85f)
                        close()
                    }
                    drawPath(path, color = fgColor)
                }
            }
            BuiltInGlyph.AMAZON -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "a",
                        color = fgColor,
                        fontSize = (size.value * 0.44f).sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                        val w = this.size.width
                        val h = this.size.height
                        drawArc(
                            color = fgColor,
                            startAngle = 25f,
                            sweepAngle = 130f,
                            useCenter = false,
                            topLeft = Offset(w * 0.22f, h * 0.52f),
                            size = Size(w * 0.56f, h * 0.28f),
                            style = Stroke(width = w * 0.065f, cap = StrokeCap.Round)
                        )
                    }
                }
            }
            BuiltInGlyph.RAKUTEN -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "R",
                        color = fgColor,
                        fontSize = (size.value * 0.44f).sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                        val w = this.size.width
                        val h = this.size.height
                        drawLine(
                            color = fgColor,
                            start = Offset(w * 0.26f, h * 0.78f),
                            end = Offset(w * 0.74f, h * 0.78f),
                            strokeWidth = w * 0.07f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
            BuiltInGlyph.SPOTIFY -> {
                ComposeCanvas(modifier = Modifier.size(size * 0.58f)) {
                    val w = this.size.width
                    val h = this.size.height
                    for (i in 0..2) {
                        val y = h * (0.28f + i * 0.22f)
                        val inset = w * (0.08f + i * 0.08f)
                        drawArc(
                            color = fgColor,
                            startAngle = 205f,
                            sweepAngle = 130f,
                            useCenter = false,
                            topLeft = Offset(inset, y),
                            size = Size(w - inset * 2, h * 0.42f),
                            style = Stroke(width = w * (0.11f - i * 0.015f), cap = StrokeCap.Round)
                        )
                    }
                }
            }
            else -> {
                Text(
                    text = style.label,
                    color = fgColor,
                    fontSize = if (style.label.length > 2) (size.value * 0.26f).sp else (size.value * 0.40f).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}
