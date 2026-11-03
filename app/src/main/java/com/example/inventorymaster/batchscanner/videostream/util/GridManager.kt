package com.example.inventorymaster.batchscanner.videostream.util

import com.example.inventorymaster.batchscanner.videostream.models.GridCellData
import com.example.inventorymaster.batchscanner.videostream.models.ScanConfig

/**
 * 网格管理器 — 固定网格数量 + 自适应屏幕 + 覆盖率统计 + 回扫冷却
 *
 * 设计原则：
 * - 固定网格数量（默认 8×12），不固定像素大小
 * - 根据缩放倍率动态调整
 * - 使用 Compose View 坐标（已通过 Matrix 变换）
 */
class GridManager {

    var cols: Int = 0
        private set
    var rows: Int = 0
        private set
    private lateinit var cells: Array<Array<GridCellData>>

    /**
     * 初始化网格
     */
    fun init(config: ScanConfig) {
        cols = config.gridCols
        rows = config.gridRows
        cells = Array(cols) { Array(rows) { GridCellData() } }
    }

    /**
     * 从 ScanSession 恢复网格状态
     */
    fun restoreFromSession(gridCells: Map<String, GridCellData>) {
        for ((key, data) in gridCells) {
            val (c, r) = key.split(",").map { it.toInt() }
            if (c in 0 until cols && r in 0 until rows) {
                cells[c][r] = data
            }
        }
    }

    /**
     * 保存网格状态到 ScanSession
     */
    fun saveToSession(gridCells: MutableMap<String, GridCellData>) {
        gridCells.clear()
        for (c in 0 until cols) {
            for (r in 0 until rows) {
                val cell = cells[c][r]
                if (cell.visited) {
                    gridCells["$c,$r"] = cell
                }
            }
        }
    }

    /**
     * 根据缩放倍率重新计算网格
     */
    fun updateForZoom(zoomLevel: Float, config: ScanConfig) {
        val adjustedCols = when {
            zoomLevel >= 3f -> 5
            zoomLevel >= 2f -> 6
            else -> config.gridCols
        }
        val adjustedRows = when {
            zoomLevel >= 3f -> 8
            zoomLevel >= 2f -> 10
            else -> config.gridRows
        }
        if (adjustedCols != cols || adjustedRows != rows) {
            rebuild(adjustedCols, adjustedRows)
        }
    }

    /**
     * 标记覆盖 — 使用 Compose View 坐标（已通过 Matrix 变换）
     *
     * @param viewX Tracker 中心点的 View X 坐标
     * @param viewY Tracker 中心点的 View Y 坐标
     * @param viewWidth PreviewView 的宽度
     * @param viewHeight PreviewView 的高度
     */
    fun markCovered(viewX: Float, viewY: Float, viewWidth: Float, viewHeight: Float) {
        if (viewWidth <= 0 || viewHeight <= 0) return

        val col = (viewX / viewWidth * cols).toInt().coerceIn(0, cols - 1)
        val row = (viewY / viewHeight * rows).toInt().coerceIn(0, rows - 1)

        val cell = cells[col][row]
        val now = System.currentTimeMillis()
        cell.visited = true
        cell.visitCount++
        cell.lastVisitTime = now
        cell.coverageRatio = ((cell.coverageRatio * (cell.visitCount - 1)) + 1f) / cell.visitCount
    }

    /**
     * 回扫冷却检查
     *
     * @return true = 可以访问（未访问过 或 已超过冷却时间）
     */
    fun canVisit(col: Int, row: Int, now: Long): Boolean {
        if (col !in 0 until cols || row !in 0 until rows) return false
        val cell = cells[col][row]
        if (!cell.visited) return true
        if (now - cell.lastVisitTime > 5000) return true  // 5s 后重新开放
        return false
    }

    /**
     * 获取覆盖率百分比
     */
    fun getCoveragePercent(): Float {
        val total = cols * rows
        if (total == 0) return 0f
        var visited = 0
        for (c in 0 until cols) {
            for (r in 0 until rows) {
                if (cells[c][r].visited) visited++
            }
        }
        return visited.toFloat() / total * 100f
    }

    /**
     * 获取指定单元格
     */
    fun getCell(col: Int, row: Int): GridCellData? {
        if (col !in 0 until cols || row !in 0 until rows) return null
        return cells[col][row]
    }

    /**
     * 重建网格
     */
    private fun rebuild(newCols: Int, newRows: Int) {
        val oldCells = cells
        cols = newCols
        rows = newRows
        cells = Array(cols) { Array(rows) { GridCellData() } }

        // 近似映射：将旧网格的覆盖状态映射到新网格
        for (c in 0 until oldCells.size) {
            for (r in 0 until oldCells[c].size) {
                val oldCell = oldCells[c][r]
                if (!oldCell.visited) continue

                val newC = (c.toFloat() / oldCells.size * cols).toInt().coerceIn(0, cols - 1)
                val newR = (r.toFloat() / oldCells[c].size * rows).toInt().coerceIn(0, rows - 1)
                cells[newC][newR] = oldCell.copy()
            }
        }
    }

    /**
     * 重置（新 Session 时）
     */
    fun reset() {
        cells = Array(cols) { Array(rows) { GridCellData() } }
    }
}
