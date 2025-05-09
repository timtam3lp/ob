public class OrderBook {
    private final int N;
    private final long[] data;

    private final int BID_DEPTH = 0;
    private final int ASK_DEPTH = 1;
    private final int BID_BASE;
    private final int ASK_BASE;
    private final int BID_PTRS;
    private final int ASK_PTRS;
    private final int BID_UNX;
    private final int ASK_UNX;
    private final int BID_COLLAPSED_DEPTH;
    private final int ASK_COLLAPSED_DEPTH;
    private final int BID_UNCROSS_DEPTH;
    private final int ASK_UNCROSS_DEPTH;

    public OrderBook(int depth) {
        this.N = depth;
	this.data = new long[2 + 12 * depth + 4]; // extra 2 for collapsed depths
        this.BID_BASE = 2;
        this.ASK_BASE = BID_BASE + 4 * depth;
        this.BID_PTRS = ASK_BASE + 4 * depth;
        this.ASK_PTRS = BID_PTRS + depth;
        this.BID_UNX = ASK_PTRS + depth;
        this.ASK_UNX = BID_UNX + depth;
        this.BID_COLLAPSED_DEPTH = ASK_UNX + depth;
        this.ASK_COLLAPSED_DEPTH = BID_COLLAPSED_DEPTH + 1;
        this.BID_UNCROSS_DEPTH = ASK_COLLAPSED_DEPTH + 1;
        this.ASK_UNCROSS_DEPTH = BID_UNCROSS_DEPTH + 1;

    }

    public void clear(int side) {
        setDepth(side, 0);
    }

    public void addPrice(int side, long price, long size, long ts, long id) {
        int depth = depth(side);
        if (depth >= N) throw new IllegalStateException("Depth limit reached");

        int base = base(side);
        int insertAt = depth;
        for (int i = 0; i < depth; i++) {
            long p = data[base + i * 4];
            if ((side == 0 && price > p) || (side == 1 && price < p)) {
                insertAt = i;
                break;
            }
        }

        if (insertAt < depth) {
            System.arraycopy(data, base + insertAt * 4, data, base + (insertAt + 1) * 4, (depth - insertAt) * 4);
        }
        int offset = base + insertAt * 4;
        data[offset] = price;
        data[offset + 1] = size;
        data[offset + 2] = ts;
        data[offset + 3] = id;

        setDepth(side, depth + 1);
        rebuildPtrs(side);
    }

    public void removeLevel(int side, int level) {
        int depth = depth(side);
        if (level >= depth) throw new IndexOutOfBoundsException();

        int base = base(side);
        System.arraycopy(data, base + (level + 1) * 4, data, base + level * 4, (depth - level - 1) * 4);

        setDepth(side, depth - 1);
        rebuildPtrs(side);
    }

    public void modifyLevel(int side, int level, long size, long ts, long id) {
        int base = base(side);
        int offset = base + level * 4;
        data[offset + 1] = size;
        data[offset + 2] = ts;
        data[offset + 3] = id;
    }


    private void rebuildPtrs(int side) {
        int base = base(side);
        int ptrs = side == 0 ? BID_PTRS : ASK_PTRS;
        int depth = depth(side);

        int count = 0;
        long prevPrice = Long.MIN_VALUE;
        for (int i = 0, offset = base; i < depth; i++, offset += 4) {
            long price = data[offset];
            if (price != prevPrice) {
                data[ptrs + count++] = offset;
                prevPrice = price;
            }
        }
        data[side == 0 ? BID_COLLAPSED_DEPTH : ASK_COLLAPSED_DEPTH] = count;
    }

    private int depth(int side) {
        return (int) data[side == 0 ? BID_DEPTH : ASK_DEPTH];
    }

    private void setDepth(int side, int d) {
        data[side == 0 ? BID_DEPTH : ASK_DEPTH] = d;
    }

    private int base(int side) {
        return side == 0 ? BID_BASE : ASK_BASE;
    }

    public static void uncross(long[] data) {
        int block = (data.length - 6) / 12;
        int bidPtrs = 2 + 8 * block;
        int askPtrs = 2 + 9 * block;
        int bidUnx = 2 + 10 * block;
        int askUnx = 2 + 11 * block;
        int bidDepth = (int) data[0];
        int askDepth = (int) data[1];

        int u = 0;
        for (int b = 0; b < bidDepth; b++) {
            int bPtr = (int) data[bidPtrs + b];
            long bp = data[bPtr];
            boolean valid = true;
            for (int a = 0; a < askDepth; a++) {
                int aPtr = (int) data[askPtrs + a];
                long ap = data[aPtr];
                if (bp >= ap) {
                    valid = false;
                    break;
                }
            }
            if (valid) data[bidUnx + u++] = bPtr;
        }
        data[2 + 12 * block + 2] = u;

        u = 0;
        for (int a = 0; a < askDepth; a++) {
            int aPtr = (int) data[askPtrs + a];
            long ap = data[aPtr];
            boolean valid = true;
            for (int i = 0; i < bidDepth; i++) {
                int bPtr = (int) data[bidPtrs + i];
                long bp = data[bPtr];
                if (bp >= ap) {
                    valid = false;
                    break;
                }
            }
            if (valid) data[askUnx + u++] = aPtr;
        }
        data[2 + 12 * block + 3] = u;
    }

    public long getLevelPrice(int side, int level, boolean collapse, boolean uncross) {
        return getLevelPrice(data, side, level, collapse, uncross);
    }

    public long getLevelSize(int side, int level, boolean collapse, boolean uncross) {
        return getLevelSize(data, side, level, collapse, uncross);
    }

    public long getLevelTimestamp(int side, int level, boolean collapse, boolean uncross) {
        return getLevelTimestamp(data, side, level, collapse, uncross);
    }

    public long getLevelId(int side, int level, boolean collapse, boolean uncross) {
        return getLevelId(data, side, level, collapse, uncross);
    }

   public double getMidpoint() {
        return getMidpoint(data, false, false);
    }

    public long getSpread() {
        return getSpread(data, false, false);
    }

    public double getVWAP(int side) {
        return getVWAP(data, side, false);
    }

    public double getMidpoint(boolean collapse, boolean uncross) {
        return getMidpoint(data, collapse, uncross);
    }

    public double getMidpoint(int start, int end, boolean collapse, boolean uncross) {
        return getMidpoint(data, start, end, collapse, uncross);
    }

    public long getSpread(boolean collapse, boolean uncross) {
        return getSpread(data, collapse, uncross);
    }

    public double getSpread(int start, int end, boolean collapse, boolean uncross) {
        return getSpread(data, start, end, collapse, uncross);
    }

    public double getVWAP(int side, boolean uncross) {
        return getVWAP(data, side, uncross);
    }

    public double getVWAP(int side, int start, int end, boolean collapse, boolean uncross) {
        return getVWAP(data, side, start, end, collapse, uncross);
    }

    public double getImbalance() {
        return getImbalance(data, false, false);
    }

    public double getImbalance(boolean collapse) {
        return getImbalance(data, collapse, false);
    }

    public double getImbalance(boolean collapse, boolean uncross) {
        return getImbalance(data, collapse, uncross);
    }

    public long[] getData() {
        return data;
    }

    private static long sumCollapsedSizeAt(long[] data, int side, int level, boolean uncross) {
        int block = (data.length - 2) / 12;
        int ptrs = side == 0 ? 2 + 8 * block : 2 + 9 * block;
        int unx = side == 0 ? 2 + 10 * block : 2 + 11 * block;
        int ptr = uncross ? (int) data[unx + level] : (int) data[ptrs + level];
        long price = data[ptr];
        long sum = 0;

        int depth = (int) data[side == 0 ? 0 : 1];
        int base = side == 0 ? 2 : 2 + 4 * block;

        for (int i = 0; i < depth; i++) {
            int off = base + i * 4;
            if (data[off] == price) sum += data[off + 1];
        }
        return sum;
    }


    public static long getLevelPrice(long[] data, int side, int level, boolean collapse, boolean uncross) {
        if (!hasLevel(data, side, level, collapse, uncross)) return Long.MIN_VALUE;
        int block = (data.length - 2) / 12;
        int base = side == 0 ? 2 : 2 + 4 * block;
        int ptrs = side == 0 ? 2 + 8 * block : 2 + 9 * block;
        int unx = side == 0 ? 2 + 10 * block : 2 + 11 * block;

        if (collapse) {
            int ptr = uncross ? (int) data[unx + level] : (int) data[ptrs + level];
            return data[ptr];
        } else {
            return data[base + level * 4];
        }
    }

    public static long getLevelSize(long[] data, int side, int level, boolean collapse, boolean uncross) {
        if (!hasLevel(data, side, level, collapse, uncross)) return Long.MIN_VALUE;
        if (collapse) return sumCollapsedSizeAt(data, side, level, uncross);

        int block = (data.length - 2) / 12;
        int base = side == 0 ? 2 : 2 + 4 * block;
        return data[base + level * 4 + 1];
    }

    public static long getLevelTimestamp(long[] data, int side, int level, boolean collapse, boolean uncross) {
        if (!hasLevel(data, side, level, collapse, uncross)) return Long.MIN_VALUE;
        int block = (data.length - 2) / 12;
        int base = side == 0 ? 2 : 2 + 4 * block;
        int ptrs = side == 0 ? 2 + 8 * block : 2 + 9 * block;
        int unx = side == 0 ? 2 + 10 * block : 2 + 11 * block;

        if (collapse) return data[(int) (uncross ? data[unx + level] : data[ptrs + level]) + 2];
        return data[base + level * 4 + 2];
    }

    public static long getLevelId(long[] data, int side, int level, boolean collapse, boolean uncross) {
        if (!hasLevel(data, side, level, collapse, uncross)) return Long.MIN_VALUE;
        int block = (data.length - 2) / 12;
        int base = side == 0 ? 2 : 2 + 4 * block;
        int ptrs = side == 0 ? 2 + 8 * block : 2 + 9 * block;
        int unx = side == 0 ? 2 + 10 * block : 2 + 11 * block;

        if (collapse) return data[(int) (uncross ? data[unx + level] : data[ptrs + level]) + 3];
        return data[base + level * 4 + 3];
    }

    public static double getMidpoint(long[] data, boolean collapse, boolean uncross) {
        if (uncross && !collapse) throw new IllegalArgumentException("Uncross requires collapse=true");
        if (!hasLevel(data, 0, 0, collapse, uncross) || !hasLevel(data, 1, 0, collapse, uncross)) return Double.NaN;
        long bid = getLevelPrice(data, 0, 0, collapse, uncross);
        long ask = getLevelPrice(data, 1, 0, collapse, uncross);
        return (bid + ask) / 2.0;
    }

    public static double getMidpoint(long[] data, int start, int end, boolean collapse, boolean uncross) {
        double bid = getVWAP(data, 0, start, end, collapse, uncross);
        double ask = getVWAP(data, 1, start, end, collapse, uncross);
        if (Double.isNaN(bid) || Double.isNaN(ask)) return Double.NaN;
        return (bid + ask) / 2.0;
    }

    public static long getSpread(long[] data, boolean collapse, boolean uncross) {
        if (uncross && !collapse) throw new IllegalArgumentException("Uncross requires collapse=true");
        if (!hasLevel(data, 0, 0, collapse, uncross) || !hasLevel(data, 1, 0, collapse, uncross)) return Long.MIN_VALUE;
        long bid = getLevelPrice(data, 0, 0, collapse, uncross);
        long ask = getLevelPrice(data, 1, 0, collapse, uncross);
        return ask - bid;
    }

    public static double getSpread(long[] data, int start, int end, boolean collapse, boolean uncross) {
        double bid = getVWAP(data, 0, start, end, collapse, uncross);
        double ask = getVWAP(data, 1, start, end, collapse, uncross);
        if (Double.isNaN(bid) || Double.isNaN(ask)) return Double.NaN;
        return ask - bid;
    }

    public static double getImbalance(long[] data, boolean collapse, boolean uncross) {
        if (uncross && !collapse) throw new IllegalArgumentException("Uncross requires collapse=true");
        if (!hasLevel(data, 0, 0, collapse, uncross) || !hasLevel(data, 1, 0, collapse, uncross)) return Double.NaN;
        long bid = getLevelSize(data, 0, 0, collapse, uncross);
        long ask = getLevelSize(data, 1, 0, collapse, uncross);
        long total = bid + ask;
        return total == 0 ? 0.0 : (double) (bid - ask) / total;
    }


    private static boolean hasLevel(long[] data, int side, int level, boolean collapse, boolean uncross) {
        int block = (data.length - 6) / 12;
        int bidCollapsedDepthIndex = 2 + 12 * block;
        int askCollapsedDepthIndex = bidCollapsedDepthIndex + 1;
        int bidUncrossDepthIndex = askCollapsedDepthIndex + 1;
        int askUncrossDepthIndex = bidUncrossDepthIndex + 1;

        if (!collapse) {
            int depth = (int) data[side == 0 ? 0 : 1];
            return level < depth;
        }

        int collapsedDepth = (int) data[side == 0 ? bidCollapsedDepthIndex : askCollapsedDepthIndex];
        if (!uncross) return level < collapsedDepth;

        int uncrossDepth = (int) data[side == 0 ? bidUncrossDepthIndex : askUncrossDepthIndex];
        return level < uncrossDepth;
    }

    public static double getVWAP(long[] data, int side, boolean uncross) {
        int block = (data.length - 6) / 12;
        int count = uncross
            ? (int) data[side == 0 ? 2 + 12 * block + 2 : 2 + 12 * block + 3]
            : (int) data[side == 0 ? 0 : 1];
        return getVWAP(data, side, 0, count, false, uncross);
    }

    public static double getVWAP(long[] data, int side, int start, int end, boolean collapse, boolean uncross) {
    int block = (data.length - 6) / 12;
    int base = side == 0 ? 2 : 2 + 4 * block;
    int ptrs = side == 0 ? 2 + 8 * block : 2 + 9 * block;
    int unx  = side == 0 ? 2 + 10 * block : 2 + 11 * block;

    int count;
    if (collapse) {
        count = uncross
            ? (int) data[side == 0 ? 2 + 12 * block + 2 : 2 + 12 * block + 3]
            : (int) data[side == 0 ? 2 + 12 * block : 2 + 12 * block + 1];
    } else {
        count = (int) data[side == 0 ? 0 : 1];
    }

    int max = Math.min(end, count);
    double totalValue = 0;
    double totalSize = 0;

    if (collapse) {
        int offset = uncross ? unx : ptrs;
        for (int i = start; i < max; i++) {
            int ptr = (int) data[offset + i];
            long price = data[ptr];
            long size  = data[ptr + 1];
            totalValue += price * size;
            totalSize += size;
        }
    } else {
        int off = base + start * 4;
        for (int i = start; i < max; i++, off += 4) {
            long price = data[off];
            long size  = data[off + 1];
            totalValue += price * size;
            totalSize += size;
        }
    }

    return totalSize > 0 ? totalValue / totalSize : Double.NaN;
}


    public String formatSide(int side, boolean collapse, boolean uncross) {
        StringBuilder sb = new StringBuilder();
        String label = (side == 0 ? "BID" : "ASK") + (collapse ? (uncross ? "_UNCROSS" : "_COLLAPSED") : "_RAW");
        sb.append(label).append(":\n");
        int level = 0;
        while (hasLevel(data, side, level, collapse, uncross)) {
            long price = getLevelPrice(data, side, level, collapse, uncross);
            long size = getLevelSize(data, side, level, collapse, uncross);
            long ts = getLevelTimestamp(data, side, level, collapse, uncross);
            long id = getLevelId(data, side, level, collapse, uncross);
            sb.append("L").append(level).append(" -> ")
              .append("P:").append(price).append(", S:").append(size)
              .append(", T:").append(ts).append(", ID:").append(id).append("\n");
            level++;
        }
        return sb.toString();
    }

} 
