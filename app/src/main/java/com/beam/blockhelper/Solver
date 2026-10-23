package com.beam.blockhelper

/**
 * ตัวคำนวณแบบ bitboard + beam search
 * เร็วกว่าเดิมหลายร้อยเท่า (เดิมไล่ครบ ~1.5 ล้านเคส ตอนนี้ราว 1 แสนเคสหรือน้อยกว่า)
 */
object Solver {
    private val LIB = listOf("#","##","#|#","###","#|#|#","####","#|#|#|#","#####","#|#|#|#|#","##|##","###|###|###","###|###","##|##|##",
        "#.|##",".#|##","##|#.","##|.#","#..|#..|###","..#|..#|###","###|#..|#..","###|..#|..#","#.|#.|##",".#|.#|##","##|#.|#.","##|.#|.#",
        "###|#..","###|..#","#..|###","..#|###","###|.#.",".#.|###","#.|##|#.",".#|##|.#","##.|.##",".##|##.",".#|##|#.","#.|##|.#",".#.|###|.#.")

    fun parse(p: String): List<IntArray> {
        val o = ArrayList<IntArray>()
        p.split('|').forEachIndexed { r, row -> row.forEachIndexed { c, ch -> if (ch == '#') o.add(intArrayOf(r, c)) } }
        return o
    }

    class Step(val p: Int, val r: Int, val c: Int, val n: Int)
    class Plan(val steps: List<Step>, val cb: Int, val lines: Int, val ids: List<Int>, val after: List<Long>)

    private class Pc(val mask: Long, val w: Int, val h: Int)
    private class Node(val steps: List<Step>, val after: List<Long>, val b: Long, val lines: Int, val s: Double)

    private fun toPc(cells: List<IntArray>): Pc {
        var m = 0L; var w = 0; var h = 0
        for (q in cells) {
            m = m or (1L shl (q[0] * 8 + q[1]))
            if (q[1] + 1 > w) w = q[1] + 1
            if (q[0] + 1 > h) h = q[0] + 1
        }
        return Pc(m, w, h)
    }

    private val LIBP = LIB.map { toPc(parse(it)) }
    private val ROWM = LongArray(8) { 0xFFL shl (it * 8) }
    private val COLM = LongArray(8) { j -> var m = 0L; for (i in 0..7) m = m or (1L shl (i * 8 + j)); m }

    private fun fullLines(m: Long): Int {
        var n = 0
        for (i in 0..7) { if (m and ROWM[i] == ROWM[i]) n++; if (m and COLM[i] == COLM[i]) n++ }
        return n
    }

    private fun clearLines(m: Long): Long {
        var clr = 0L
        for (i in 0..7) { if (m and ROWM[i] == ROWM[i]) clr = clr or ROWM[i]; if (m and COLM[i] == COLM[i]) clr = clr or COLM[i] }
        return m and clr.inv()
    }

    private fun bit(m: Long, r: Int, c: Int): Int =
        if (r < 0 || c < 0 || r > 7 || c > 7) 1 else ((m ushr (r * 8 + c)) and 1L).toInt()

    private fun cheap(b: Long, lines: Int): Double {
        var holes = 0; var tr = 0
        for (r in 0..7) for (c in 0..7) {
            val v = bit(b, r, c)
            if (v == 0 && bit(b, r - 1, c) + bit(b, r + 1, c) + bit(b, r, c - 1) + bit(b, r, c + 1) >= 3) holes++
            if (v != bit(b, r, c + 1)) tr++
            if (v != bit(b, r + 1, c)) tr++
        }
        return lines * 30.0 - holes * 15 - tr * 1.5 - java.lang.Long.bitCount(b)
    }

    private fun canFit(b: Long, p: Pc): Boolean {
        for (r in 0..8 - p.h) for (c in 0..8 - p.w) if (b and (p.mask shl (r * 8 + c)) == 0L) return true
        return false
    }

    fun combo(st: List<Step>): Int {
        var k = 0; var sc = 0
        st.forEach { if (it.n > 0) { k++; sc += it.n * it.n * 20 + (k - 1) * 25 } }
        return sc
    }

    private fun perms(n: Int): List<IntArray> = when (n) {
        1 -> listOf(intArrayOf(0))
        2 -> listOf(intArrayOf(0, 1), intArrayOf(1, 0))
        else -> listOf(intArrayOf(0, 1, 2), intArrayOf(0, 2, 1), intArrayOf(1, 0, 2), intArrayOf(1, 2, 0), intArrayOf(2, 0, 1), intArrayOf(2, 1, 0))
    }

    fun mask(board: IntArray): Long {
        var m = 0L
        for (i in 0..63) if (board[i] != 0) m = m or (1L shl i)
        return m
    }

    /**
     * หาแผนที่ "รอด" ที่สุด: วางให้ครบทุกชิ้นก่อน (ถ้าทำได้) แล้วค่อยดูคะแนน/ที่ว่างเหลือ
     * ถ้าวางไม่ครบ จะวางให้ได้มากชิ้นที่สุด ถ้าไม่มีชิ้นไหนวางได้เลยค่อยคืน null
     */
    fun solve(board: IntArray, pcs: List<List<IntArray>?>): Plan? {
        val ids = pcs.indices.filter { pcs[it] != null }
        if (ids.isEmpty()) return null
        val pm = ids.map { toPc(pcs[it]!!) }
        val start = mask(board)
        val fill = java.lang.Long.bitCount(start)
        val beamW = if (fill >= 30) 250 else 80
        val fw = if (fill >= 36) 30.0 else 8.0

        var best: Node? = null; var bestScore = -1e18
        for (perm in perms(ids.size)) {
            var beam = listOf(Node(emptyList(), emptyList(), start, 0, 0.0))
            for (k in perm) {
                val p = pm[k]; val next = ArrayList<Node>()
                for (n in beam) {
                    var any = false
                    for (r in 0..8 - p.h) for (c in 0..8 - p.w) {
                        val sh = p.mask shl (r * 8 + c)
                        if (n.b and sh != 0L) continue
                        any = true
                        val filled = n.b or sh
                        val ln = fullLines(filled)
                        val nb = if (ln > 0) clearLines(filled) else filled
                        val st = n.steps + Step(ids[k], r, c, ln)
                        next.add(Node(st, n.after + nb, nb, n.lines + ln, st.size * 500.0 + cheap(nb, n.lines + ln) + combo(st)))
                    }
                    if (!any) next.add(n)
                }
                next.sortByDescending { it.s }
                beam = if (next.size > beamW) next.subList(0, beamW) else next
            }
            for (n in beam.take(30)) {
                val fits = LIBP.count { canFit(n.b, it) }
                var sc = n.s + fw * fits
                if (fits == 0) sc -= 2000.0
                if (sc > bestScore) { bestScore = sc; best = n }
            }
        }
        val b = best ?: return null
        if (b.steps.isEmpty()) return null
        return Plan(b.steps, combo(b.steps), b.lines, ids, b.after)
    }
}
