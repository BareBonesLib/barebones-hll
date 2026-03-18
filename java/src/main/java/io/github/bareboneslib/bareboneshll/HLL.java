package io.github.bareboneslib.bareboneshll;

public class HLL {
    // below variables need to be serialized
    private final int p;
    private final int r;
    private final int[] registers;

    // below variables are derived
    private final int m;
    private final int maxRegisterValue;
    private final int regPerDatatype;
    private final int totalRegisters;

    // below are constants
    private static final int DEFAULT_P = 12;
    private static final int DEFAULT_R = 6;
    private static final double POW_2_32 = Math.pow(2, 32);
    private static final int DT_WIDTH = 32;
    private static final double[] PRE_POW_2_K = new double[64]; // as largest r = 6, the max value of a register can be at max 63
    static {
        for(int i = 0; i< PRE_POW_2_K.length; i++) {
            PRE_POW_2_K[i] = Math.pow(2, -i);
        }
    }

    public HLL() {
        this(DEFAULT_P, DEFAULT_R);
    }

    public HLL(int p) {
        this(p, DEFAULT_R);
    }

    public HLL(int p, int r) {
        if(p < 5 || p > 30)
            throw new IllegalArgumentException("invalid p: " + p);
        if(r < 4 || r > 6)
            throw new IllegalArgumentException("Invalid R: " + r);

        this.p = p;
        this.r = r;
        this.regPerDatatype = DT_WIDTH / r;
        this.totalRegisters = (1 << p);
        this.m = totalRegisters / regPerDatatype + (totalRegisters % regPerDatatype == 0 ? 0 : 1);
        this.maxRegisterValue = ((1 << r) - 1);

        this.registers = new int[m];
    }

    // read r bits of the registers from a specified bit location and return it as a byte.
    private byte readRegister(int index) {
        int registerIndex = index / regPerDatatype;
        int registerEndOffset = (regPerDatatype - registerIndex % regPerDatatype - 1) * r;
        int MASK = (1 << r) - 1;
        return (byte) ((this.registers[registerIndex] >>> registerEndOffset) & MASK);
    }

    // write the rightmost r bits of given byte value to a specified bit location in the registers.
    private void writeRegister(byte value, int index) {
        int registerIndex = index / regPerDatatype;
        int registerEndOffset = (regPerDatatype - registerIndex % regPerDatatype - 1) * r;
        int MASK = (1 << r) - 1;
        this.registers[registerIndex] &= ~(MASK << registerEndOffset);
        this.registers[registerIndex] |= value << registerEndOffset;
    }

    public void add(long value) {
        int registerIndex = (int) (value >>> (64 - p));
        value = value | (1L << (64 - p));
        int cnt = Long.numberOfTrailingZeros(value) + 1;
        cnt = Math.min(cnt, maxRegisterValue);

        int bucketIndex = registerIndex / regPerDatatype;
//        int registerOffset = (DT_WIDTH - 1) - (PAD + ((registerIndex % regPerDatatype) * r) + (r - 1));
        int registerOffset = (regPerDatatype - registerIndex % regPerDatatype - 1) * r;
        int bucketValue = this.registers[bucketIndex];
        int prevValue = (bucketValue >>> registerOffset) & maxRegisterValue;
        if(prevValue < cnt)
            this.registers[bucketIndex] = (bucketValue & ~(maxRegisterValue << registerOffset)) | (cnt << registerOffset);
    }

    private void merge4(HLL other) {
        final int MASK = 0xf;
        final int REGISTERS_PER_BUCKET = 8;
        final int REGISTER_SIZE = 4;
        for(int i = 0; i < m; ++i) {
            int word = 0;
            int thisBucket = this.registers[i];
            int otherBucket = other.registers[i];
            for(int j = 0; j < REGISTERS_PER_BUCKET; ++j) {
                int mask = MASK << (REGISTER_SIZE * j);
                int thisRawVal = thisBucket & mask;
                int otherRawVal = otherBucket & mask;
                word |= (Integer.compareUnsigned(thisRawVal, otherRawVal) < 0) ? otherRawVal : thisRawVal;
            }
            this.registers[i] = word;
        }
    }

    private void merge5(HLL other) {
        final int MASK = 0x1f;
        final int REGISTERS_PER_BUCKET = 6;
        final int REGISTER_SIZE = 5;
        for(int i = 0; i < m; ++i) {
            int word = 0;
            int thisBucket = this.registers[i];
            int otherBucket = other.registers[i];
            for(int j = 0; j < REGISTERS_PER_BUCKET; ++j) {
                int mask = MASK << (REGISTER_SIZE * j);
                int thisRawVal = thisBucket & mask;
                int otherRawVal = otherBucket & mask;
                word |= (Integer.compareUnsigned(thisRawVal, otherRawVal) < 0) ? otherRawVal : thisRawVal;
            }
            this.registers[i] = word;
        }
    }

    private void merge6(HLL other) {
        final int MASK = 0x3f;
        final int REGISTERS_PER_BUCKET = 5;
        final int REGISTER_SIZE = 6;
        for(int i = 0; i < m; ++i) {
            int word = 0;
            int thisBucket = this.registers[i];
            int otherBucket = other.registers[i];
            for(int j = 0; j < REGISTERS_PER_BUCKET; ++j) {
                int mask = MASK << (REGISTER_SIZE * j);
                int thisRawVal = thisBucket & mask;
                int otherRawVal = otherBucket & mask;
                word |= (Integer.compareUnsigned(thisRawVal, otherRawVal) < 0) ? otherRawVal : thisRawVal;
            }
            this.registers[i] = word;
        }
    }

    public boolean merge(HLL other) {
        if (other == null)
            return false;
        if (this.p != other.p || this.r != other.r)
            return false;

        switch(r) {
            case 4: merge4(other);
                break;
            case 5: merge5(other);
                break;
            case 6: merge6(other);
                break;
        }
        return true;
    }

    private void estimate4(double[] results) {
        final int REGISTER_PER_BUCKET = 8;
        final int REGISTER_SIZE = 4;
        final int MASK = 0xf;

        int zeroRegisters = 0;
        double sum = 0;
        int completeBuckets = totalRegisters / REGISTER_PER_BUCKET;
        int remainingRegisters = totalRegisters % REGISTER_PER_BUCKET;

        for(int i = 0; i < completeBuckets; i++) {
            int cur = this.registers[i];
            for(int j = 0; j < REGISTER_PER_BUCKET; j++) {
                int k = (cur >>> (REGISTER_SIZE * j)) & MASK;
                zeroRegisters += ((k == 0) ? 1 : 0);
                sum = sum + PRE_POW_2_K[k];
            }
        }

        if(remainingRegisters > 0) {
            int last = this.registers[this.registers.length - 1];
            for(int j = REGISTER_PER_BUCKET -1; j >= (REGISTER_PER_BUCKET - remainingRegisters); j--){
                int k = (last >>> (REGISTER_SIZE * j)) & MASK;
                zeroRegisters += ((k == 0) ? 1 : 0);
                sum = sum + PRE_POW_2_K[k];
            }
        }

        results[0] = sum;
        results[1] = zeroRegisters;
    }

    private void estimate5(double[] results) {
        final int REGISTER_PER_BUCKET = 6;
        final int REGISTER_SIZE = 5;
        final int MASK = 0x1f;

        int zeroRegisters = 0;
        double sum = 0;
        int completeBuckets = totalRegisters / REGISTER_PER_BUCKET;
        int remainingRegisters = totalRegisters % REGISTER_PER_BUCKET;

        for(int i = 0; i < completeBuckets; i++) {
            int cur = this.registers[i];
            for(int j = 0; j < REGISTER_PER_BUCKET; j++) {
                int k = (cur >>> (REGISTER_SIZE * j)) & MASK;
                zeroRegisters += ((k == 0) ? 1 : 0);
                sum = sum + PRE_POW_2_K[k];
            }
        }

        if(remainingRegisters > 0) {
            int last = this.registers[this.registers.length - 1];
            for(int j = REGISTER_PER_BUCKET -1; j >= (REGISTER_PER_BUCKET - remainingRegisters); j--){
                int k = (last >>> (REGISTER_SIZE * j)) & MASK;
                zeroRegisters += ((k == 0) ? 1 : 0);
                sum = sum + PRE_POW_2_K[k];
            }
        }

        results[0] = sum;
        results[1] = zeroRegisters;
    }

    private void estimate6(double[] results) {
        final int REGISTER_PER_BUCKET = 5;
        final int REGISTER_SIZE = 6;
        final int MASK = 0x3f;

        int zeroRegisters = 0;
        double sum = 0;
        int completeBuckets = totalRegisters / REGISTER_PER_BUCKET;
        int remainingRegisters = totalRegisters % REGISTER_PER_BUCKET;

        for(int i = 0; i < completeBuckets; i++) {
            int cur = this.registers[i];
            for(int j = 0; j < REGISTER_PER_BUCKET; j++) {
                int k = (cur >>> (REGISTER_SIZE * j)) & MASK;
                zeroRegisters += ((k == 0) ? 1 : 0);
                sum = sum + PRE_POW_2_K[k];
            }
        }

        if(remainingRegisters > 0) {
            int last = this.registers[this.registers.length - 1];
            for(int j = REGISTER_PER_BUCKET -1; j >= (REGISTER_PER_BUCKET - remainingRegisters); j--){
                int k = (last >>> (REGISTER_SIZE * j)) & MASK;
                zeroRegisters += ((k == 0) ? 1 : 0);
                sum = sum + PRE_POW_2_K[k];
            }
        }

        results[0] = sum;
        results[1] = zeroRegisters;
    }
    public long estimate() {
        double M = totalRegisters;
        double[] results = new double[2];

        switch(r) {
            case 4:
                estimate4(results);
                break;
            case 5:
                estimate5(results);
                break;
            case 6:
                estimate6(results);
                break;
        }
        double sum = results[0];
        double zeroRegisters = results[1];

        double alphaM = getAlphaM(totalRegisters);
        double rawEstimate = alphaM * M * M * (1.0 / sum);
//        System.err.println(rawEstimate);

        if(rawEstimate <= (5.0 * M / 2.0) && zeroRegisters > 0) {
            rawEstimate = M * Math.log(M / zeroRegisters);
        } else if(rawEstimate > ((1.0 / 30.0) * POW_2_32)) {
            rawEstimate = -POW_2_32 * Math.log(1 - rawEstimate / POW_2_32);
        }

        return (long) rawEstimate;
    }

    private double getAlphaM(int M) {
        switch (M) {
            case 16:
                return 0.673;
            case 32:
                return 0.697;
            case 64:
                return 0.709;
            default:
                return 0.7213 / (1 + (1.079 / M));
        }
    }

    public byte[] serialize() {
        int size = m * 4 + 2;

        byte[] array = new byte[size];
        int j = 0;
        for(int i=0; i<m; i++) {
            array[j++] = (byte) (registers[i] >>> 24 & 0xFF);
            array[j++] = (byte) (registers[i] >>> 16 & 0xFF);
            array[j++] = (byte) (registers[i] >>> 8 & 0xFF);
            array[j++] = (byte) (registers[i] & 0xFF);
        }

        array[m * 4] = (byte) p;
        array[m * 4 + 1] = (byte) r;
        return array;
    }

    public static HLL deserialize(byte[] array) {
        if (array == null || array.length < 6)
            throw new IllegalArgumentException("array is null or smaller than 6 bytes");
        int n = array.length;

        int p = array[n - 2];
        int r = array[n - 1];

        HLL hll = new HLL(p, r);
        if((hll.m * 4) != (n - 2))
            throw new IllegalArgumentException("HLL buffer invalid size: " + (n - 2) + " expected: " + hll.m);

        for(int i=0; i < hll.m; i++) {
            hll.registers[i] = ((array[i * 4] & 0xFF) << 24) |
                    ((array[i * 4 + 1] & 0xFF) << 16) |
                    ((array[i * 4 + 2] & 0xFF) << 8) |
                    (array[i * 4 + 3] & 0xFF);
        }
        return hll;
    }

    protected void debugInfo() {
        System.err.println("p=" + p + " r=" + r + " bytes=" + m + " M=" + totalRegisters);
        int max = (1 << r) - 1;
        int[] hist = new int[max + 1];
        for (int i = 0; i < totalRegisters; i++) {
            int v = readRegister(i) & 0xFF;
            hist[v]++;
        }
        for (int i = 0; i <= max; i++)
            System.err.printf("reg %2d: %6d%n", i, hist[i]);
        double zero = hist[0];
        System.err.println("zeroRegs=" + zero);
    }
}