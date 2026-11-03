# 液态玻璃材质系统（Liquid Glass Design System）

> 代码位置：`app/src/main/java/com/example/inventorymaster/ui/liquidglass/`
> 对应组件：`LiquidGlass.kt`、`LiquidGlassStyle.kt`、`LiquidGlassPreview.kt`

---

## 1. 材质概述与光学原理

**LiquidGlass** 是一套专为 Android / Jetpack Compose 打造的高性能无依赖液态玻璃材质系统。它不依赖开销高昂且兼容性受限的窗口级磨砂模糊，而是基于**物理光学折射模型（Physical Refraction & Bevel Model）**，通过分层边缘着色与曲面流光来塑造真实物理玻璃的晶莹厚度感。

### 五层光学模型（自外向内）

```
 ┌────────────────────────────────────────────────────────┐
 │ L1  外部软阴影 (Directional Drop Shadow)                │
 │     跟随形状轮廓，等价 CSS drop-shadow，按压时随位移减弱 │
 ├────────────────────────────────────────────────────────┤
 │ L2  最外层高光边框 (Outer Specular Rim)                 │
 │     135° 对角线渐变，左上极亮捕捉主光源，右下微弱反光   │
 ├────────────────────────────────────────────────────────┤
 │ L3  物理折射暗缝 (Refraction Crease) ── 质感灵魂所在     │
 │     外边缘内缩 1~1.5dp 的极细暗边（Fresnel 临界角折射）  │
 │     拉开玻璃厚度感，彻底消除贴纸塑料感                 │
 ├────────────────────────────────────────────────────────┤
 │ L4  内倒角漫反射高光 (Inner Bevel Glow)                 │
 │     紧贴暗缝内侧 1~2dp，模拟光线在玻璃内部的反射漫散   │
 ├────────────────────────────────────────────────────────┤
 │ L5  主体填充与凸面流光 (Convex Surface Sheen)           │
 │     半透明主体 + 左上到右下微弱渐变流光，呈现饱满弧面   │
 └────────────────────────────────────────────────────────┘
```

---

## 2. 材质预设（LiquidGlassStyle）

### 2.1 晶透白液态玻璃 (`CrystalClear`)
* **设计参照**：Apple VisionOS / 现代化高透亚克力凸透镜胶囊。
* **适用场景**：浅色背景悬浮按钮（FAB）、高光胶囊按钮（如 `Get started >`）、图标底座。
* **参数特征**：
  * 主体极透：`containerColor = Color.White`, `backgroundAlpha = 0.10f`
  * 凸面弧度流光：`surfaceSheenAlpha = 0.18f`（呈现饱满的透镜曲面反光）
  * 锐利外高光边：`borderAlpha = 0.85f`, `highlightAlpha = 0.95f`
  * 细致折射暗缝：`innerShadowAlpha = 0.12f`, `innerShadowWidth = 1.2.dp`
  * 搭配技巧：内部嵌入深色文字或纯黑圆形动作键（`Color(0xFF0E1013)`），利用**高明度反差**凸显外层玻璃的晶透反光。

### 2.2 深色烟熏玻璃 (`DarkGlass` / `DarkStrong`)
* **适用场景**：计算器浮层（`InventoryCalcDialog`）、深色半透明操作面板。
* **参数特征**：
  * 主体深色：`containerColor = Color(0xFF14161A)`, `backgroundAlpha = 0.75f ~ 0.80f`
  * 边框与高光收敛，强调沉浸感与数字对比度。

---

## 3. 核心几何与渲染优化要点

1. **真实同心圆角几何内缩（Concentric RoundRect Inset）**：
   * 传统 `scale(scaleX, scaleY)` 在宽矩形（如胶囊按钮）上由于长宽比不为 1，会导致正圆角被各向异性拉扯为**椭圆**，且各边缘厚度不一致。
   * `LiquidGlass` 内部检测 `outline is Outline.Rounded`，直接对 `roundRect` 进行几何等距内缩（`left+px, right-px, radius-px`），确保在任何宽高比下内外边框绝对等距、圆角严格同心。
2. **轻量按压物理交互**：
   * 传入 `interactionSource` 时自动启用按压物理变化：
     * 图层下沉 1dp（`graphicsLayer { translationY = ... }`，不触发重组）；
     * 外阴影随下沉减弱；
     * 表面高光收暗，内侧折射暗缝增强，营造“按进玻璃”的沉浸手感。
3. **重组与 GC 性能规约**：
   * 边框渐变笔刷通过 `remember` 缓存；
   * 内部高光层在绘制期根据 `pressProgress` 动态计算 Alpha，杜绝动画期间全量重组。

---

## 4. 典型用法示例

### 4.1 高透圆形动作按钮
```kotlin
LiquidGlass(
    shape = CircleShape,
    style = LiquidGlassStyle.CrystalClear,
    modifier = Modifier.size(64.dp)
) {
    Icon(
        imageVector = Icons.Default.Add,
        contentDescription = "Add",
        tint = Color(0xFF1E2024),
        modifier = Modifier.align(Alignment.Center)
    )
}
```

### 4.2 高透胶囊操作栏
```kotlin
val interactionSource = remember { MutableInteractionSource() }
LiquidGlass(
    shape = RoundedCornerShape(percent = 50),
    style = LiquidGlassStyle.CrystalClear,
    interactionSource = interactionSource,
    modifier = Modifier
        .height(64.dp)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = { /* 执行动作 */ }
        )
) {
    Row(
        modifier = Modifier.padding(start = 24.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Get started", color = Color(0xFF141618), fontWeight = FontWeight.Medium)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF0E1013)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, tint = Color.White, contentDescription = null)
        }
    }
}
```
