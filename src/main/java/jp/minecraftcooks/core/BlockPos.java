package jp.minecraftcooks.core;

/** ブロックの座標。 */
public record BlockPos(int x, int y, int z) {
    public BlockPos plus(BlockPos o) {
        return new BlockPos(x + o.x, y + o.y, z + o.z);
    }

    public BlockPos minus(BlockPos o) {
        return new BlockPos(x - o.x, y - o.y, z - o.z);
    }

    /** 成分ごとの小さいほう。 */
    public static BlockPos min(BlockPos a, BlockPos b) {
        return new BlockPos(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z));
    }

    public static BlockPos max(BlockPos a, BlockPos b) {
        return new BlockPos(Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z));
    }

    /** a と b を対角とする箱の中(端を含む)にあるか。 */
    public boolean within(BlockPos a, BlockPos b) {
        BlockPos lo = min(a, b), hi = max(a, b);
        return x >= lo.x && x <= hi.x && y >= lo.y && y <= hi.y && z >= lo.z && z <= hi.z;
    }

    @Override
    public String toString() {
        return x + "," + y + "," + z;
    }

    /** "x,y,z" を読む。 */
    public static BlockPos parse(String s) {
        String[] p = s.split(",");
        if (p.length != 3) throw new IllegalArgumentException("座標ではありません: " + s);
        return new BlockPos(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
    }
}
