package io.github.siddheshdhinge.bareboneshll;

import java.util.Arrays;

public class HLLPlusPlus {
    // below variables need to be serialized
    private final int p;
    private final int r;
    private int[] registers;
    private enum State {
        SPARSE,
        DENSE
    };
    private State state;
    private int randomCount;
    private int[] sparseSet;
    private int[] sparseList;

    // below variables are derived
    private final double universeSizeN = 2_000_000_000;
    private final int universeSizeM = 29_700_000;
    private final int m;
    private final int sp;
    private final int maxRegisterValue;
    private final int regPerDatatype;
    private final int totalRegisters;
    private final int conversionThreshold;
    private final int sparseSetIndexOffset;
    private int sparseListIndex;

    // below are constants
    private static final int TEMPORARY_LIST_SIZE = 5;
    private static final int MIN_P = 4;
    private static final int MAX_P = 18;
    private static final int DEFAULT_P = 12;
    private static final int DEFAULT_R = 6;
    private static final int SPARSE_P_EXTRA_BITS = 4;
    private static final double POW_2_32 = Math.pow(2, 32);
    private static final int DT_WIDTH = 32;
    private static final int SERIALIZED_METADATA_FIELDS = 3 + 4;
    private static final double[] PRE_POW_2_K = new double[64]; // as largest r = 6, the max value of a register can be at max 63
    static {
        for(int i = 0; i< PRE_POW_2_K.length; i++) {
            PRE_POW_2_K[i] = Math.pow(2, -i);
        }
    }

    public HLLPlusPlus() {
        this(DEFAULT_P, DEFAULT_R);
    }

    public HLLPlusPlus(int p) {
        this(p, DEFAULT_R);
    }

    public HLLPlusPlus(int p, int r) {
        if(p < MIN_P || p > MAX_P)
            throw new IllegalArgumentException("invalid p: " + p);
        if(r < 4 || r > 6)
            throw new IllegalArgumentException("Invalid R: " + r);

        this.p = p;
        this.r = r;

        this.sp = p + SPARSE_P_EXTRA_BITS;
        this.regPerDatatype = DT_WIDTH / r;
        this.totalRegisters = (1 << p);
        this.m = totalRegisters / regPerDatatype + (totalRegisters % regPerDatatype == 0 ? 0 : 1);
        this.maxRegisterValue = ((1 << r) - 1);

        this.sparseList = new int[TEMPORARY_LIST_SIZE];
        this.sparseSet = new int[0];
        this.sparseListIndex = 0;
        this.state = State.SPARSE;
        this.conversionThreshold = m;
        this.sparseSetIndexOffset = DT_WIDTH - sp;
    }

    // read r bits of the registers from a specified bit location and return it as a byte.
    // this is for debugging purposes only
    private byte readRegister(int index) {
        if(this.state == State.SPARSE) {
            mergeTmpSparse();
            int result = 0;
            for(int i = 0; i<sparseSet.length; i++) {
                if((sparseSet[i] >>> (sparseSetIndexOffset + SPARSE_P_EXTRA_BITS)) == index)
                    result = Math.max(result, sparseSet[i] & maxRegisterValue);
            }
            return (byte) result;
        }
        int bucketIndex = index / regPerDatatype;
        int registerOffset = (regPerDatatype - index % regPerDatatype - 1) * r;
        return (byte) ((this.registers[bucketIndex] >>> registerOffset) & maxRegisterValue);
    }

    private void indexSort(int[] arr) {
        int n = this.sparseListIndex;
        for(int i = 0; i < n; i++) {
            for(int j = 0; j < (n - i - 1); j++) {
                int cur = arr[j] >>> sparseSetIndexOffset;
                int next = arr[j + 1] >>> sparseSetIndexOffset;
                if(cur > next) {
                    int tmp = arr[j + 1];
                    arr[j + 1] = arr[j];
                    arr[j] = tmp;
                }
            }
        }
    }

    // dedup the sparseList based on index
    // if same index -> set the index to max value
    private int[] dedupIndex(int[] tmpSparseList) {
        if(sparseListIndex == 0) return new int[0];

        indexSort(tmpSparseList);
        int[] newSparseList = new int[tmpSparseList.length];
        int k = 0;
        newSparseList[k++] = tmpSparseList[0];

        for(int i=1; i<this.sparseListIndex; i++) {
            int curIndex = tmpSparseList[i] >>> sparseSetIndexOffset;
            int lastIndex = newSparseList[k - 1] >>> sparseSetIndexOffset;
            if(curIndex == lastIndex) {
                newSparseList[k - 1] = (curIndex << sparseSetIndexOffset) | Math.max(newSparseList[k - 1] & maxRegisterValue, tmpSparseList[i] & maxRegisterValue);
            }
            else {
                newSparseList[k] = tmpSparseList[i];
                k++;
            }
        }

        sparseListIndex = k;
        return Arrays.copyOf(newSparseList, k);
    }

    private void mergeTmpSparse() {
        if(this.sparseListIndex <= 0)
            return;

        int[] newSparseList = dedupIndex(this.sparseList);

        int[] newSparseSet = new int[sparseSet.length + newSparseList.length];
        int l = 0;
        int r = 0;
        int k = 0;
        while(l < sparseSet.length && r < newSparseList.length) {
            int setIndex = sparseSet[l] >>> sparseSetIndexOffset;
            int listIndex = newSparseList[r] >>> sparseSetIndexOffset;
            if(setIndex < listIndex) {
                newSparseSet[k] = sparseSet[l];
                l++;
            }
            else if(setIndex > listIndex) {
                newSparseSet[k] = newSparseList[r];
                r++;
            }
            else {
                newSparseSet[k] = (setIndex << sparseSetIndexOffset) | Math.max(sparseSet[l] & maxRegisterValue, newSparseList[r] & maxRegisterValue);
                l++; r++;
            }
            k++;
        }
        while(l < sparseSet.length) {
            newSparseSet[k] = sparseSet[l];
            l++; k++;
        }
        while(r < newSparseList.length) {
            newSparseSet[k] = newSparseList[r];
            r++; k++;
        }

        this.sparseSet = Arrays.copyOf(newSparseSet, k);
        this.sparseListIndex = 0;
    }

    public void addRandom(int count) {
        this.randomCount = addRandomFormula(this.randomCount, count, universeSizeM, universeSizeM);
    }

    private int addRandomFormula(double X, double Y, double universeSize) {
        return (int) Math.round(X + (universeSize - X) * (1.0 - Math.pow((1.0 - 1.0 / universeSize), Y)));
    }

    private int addRandomDistinctFormula(double X, double Y, double universeSize) {
        return (int) Math.round(X + Y - ((X * Y) / universeSize));
    }

    private int addRandomFormula(double X, double Y, double XUniverse, double YUniverse) {
        return (int) Math.round(X + YUniverse * (1.0 - Math.pow((1.0 - 1.0 / YUniverse), Y)) * (1.0 - X / XUniverse));
    }

    public void add(long value) {
        if(this.state == State.SPARSE) {
            int registerIndex = (int) (value >>> (64 - sp));
            value = value | (1L << (64 - sp));
            int cnt = Long.numberOfTrailingZeros(value) + 1;
            cnt = Math.min(cnt, maxRegisterValue);

            this.sparseList[sparseListIndex] = (registerIndex << sparseSetIndexOffset) | cnt;
            sparseListIndex++;

            if(sparseListIndex >= TEMPORARY_LIST_SIZE) {
                mergeTmpSparse();
                if(this.sparseSet.length >= conversionThreshold)
                    convertToNormal();
            }
        }
        if(this.state == State.DENSE) {
            int registerIndex = (int) (value >>> (64 - p));
            value = value | (1L << (64 - p));
            int cnt = Long.numberOfTrailingZeros(value) + 1;
            cnt = Math.min(cnt, maxRegisterValue);

            int bucketIndex = registerIndex / regPerDatatype;
            int registerOffset = (regPerDatatype - registerIndex % regPerDatatype - 1) * r;
            int bucketValue = this.registers[bucketIndex];
            int prevValue = (bucketValue >>> registerOffset) & maxRegisterValue;
            if(prevValue < cnt)
                this.registers[bucketIndex] = (bucketValue & ~(maxRegisterValue << registerOffset)) | (cnt << registerOffset);
        }
    }

    private void convertToNormal() {
        this.registers = new int[m];

        for(int i=0; i<this.sparseSet.length; i++) {
            int idx = sparseSet[i] >>> (sparseSetIndexOffset + SPARSE_P_EXTRA_BITS);
            int val = sparseSet[i] & maxRegisterValue;

            int registerOffset = (regPerDatatype - idx % regPerDatatype - 1) * r;
            int bucketIndex = idx / regPerDatatype;

            int bucketValue = this.registers[bucketIndex];
            int prevValue = (bucketValue >>> registerOffset) & maxRegisterValue;
            if(prevValue < val)
                this.registers[bucketIndex] = (bucketValue & ~(maxRegisterValue << registerOffset)) | (val << registerOffset);
        }

        this.sparseList = new int[0];
        this.sparseSet = new int[0];
        this.state = State.DENSE;
    }

    private void sparseMerge(HLLPlusPlus other) {
        int[] a = this.sparseSet;
        int[] b = other.sparseSet;

        // int unq = determineTotalUniques(this.sparseSet, other.sparseSet);
        int[] newSparseSet = new int[a.length + b.length];

//        System.err.println(a.length + " + " + b.length);

        int l = 0;
        int r = 0;
        int k = 0;

        while((l < a.length) || (r < b.length)) {
            if(l >= a.length) {
                newSparseSet[k++] = b[r++];
            }
            else if(r >= b.length) {
                newSparseSet[k++] = a[l++];
            }
            else {
                int thisVal = a[l];
                int otherVal = b[r];
                int thisIndex = thisVal >>> sparseSetIndexOffset;
                int otherIndex = otherVal >>> sparseSetIndexOffset;

                if(thisIndex == otherIndex)
                {
                    newSparseSet[k++] = (thisIndex << sparseSetIndexOffset) | Math.max(thisVal & maxRegisterValue, otherVal & maxRegisterValue);
                    l++; r++;
                }
                else if(thisIndex < otherIndex) {
                    newSparseSet[k++] = thisVal;
                    l++;
                }
                else {
                    newSparseSet[k++] = otherVal;
                    r++;
                }
            }
        }
//        System.err.println("unq -> " + k);

        this.sparseSet = k < newSparseSet.length ? Arrays.copyOf(newSparseSet, k) : newSparseSet;
    }

    private void merge4(HLLPlusPlus other) {
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

    private void merge5(HLLPlusPlus other) {
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

    private void merge6(HLLPlusPlus other) {
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

    private void normalMerge(HLLPlusPlus other) {
        switch(r) {
            case 4: merge4(other);
                break;
            case 5: merge5(other);
                break;
            case 6: merge6(other);
                break;
        }
    }

    public boolean merge(HLLPlusPlus other) {
        if (other == null)
            return false;
        if (this.p != other.p || this.r != other.r)
            return false;

        if(this.sparseListIndex > 0)
            this.mergeTmpSparse();
        if(other.sparseListIndex > 0)
            other.mergeTmpSparse();
        if(other.randomCount > 0)
            this.randomCount = addRandomDistinctFormula(this.randomCount, other.randomCount, universeSizeM);

        int state = ((this.state == State.SPARSE) ? 0 : 1) | ((other.state == State.SPARSE) ? 0 : 2);
        switch (state) {
            case 0: // both sparse
                sparseMerge(other);
                if(this.sparseSet.length >= conversionThreshold)
                    this.convertToNormal();
                break;
            case 1: // this normal, other sparse
                for(int i=0; i<other.sparseSet.length; i++) {
                    int idx = other.sparseSet[i] >>> (sparseSetIndexOffset + SPARSE_P_EXTRA_BITS);
                    int val = other.sparseSet[i] & maxRegisterValue;
                    int bucketIndex = idx / regPerDatatype;
                    int registerOffset = (regPerDatatype - idx % regPerDatatype - 1) * r;

                    int registerValue = this.registers[bucketIndex];
                    int mask = maxRegisterValue << registerOffset;
                    if(Integer.compareUnsigned(registerValue & mask, val << registerOffset) < 0) {
                        this.registers[bucketIndex] = (registerValue & ~mask) | (val << registerOffset);
                    }
                }
                break;
            case 2: // this sparse, other normal
                this.convertToNormal();
                normalMerge(other);
                break;
            case 3: // both normal
                normalMerge(other);
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
        if(this.state == State.SPARSE) {
            if(this.sparseListIndex > 0)
                mergeTmpSparse();
            double SM = (1 << sp);
            long sparseEstimate = Math.round(SM * Math.log(SM / (SM - this.sparseSet.length)));
            if(this.randomCount > 0)
                sparseEstimate = addRandomDistinctFormula((int) sparseEstimate, this.randomCount, universeSizeN);
            return sparseEstimate;
        }

        double[] results = new double[2];

        switch(r) {
            case 4: estimate4(results);
                break;
            case 5: estimate5(results);
                break;
            case 6: estimate6(results);
                break;
        }

        double sum = results[0];
        double zeroRegisters = results[1];

        double alphaM = getAlphaM(totalRegisters);
        double rawEstimate = alphaM * M * M * (1 / sum);
//        System.err.println(rawEstimate);

        if(rawEstimate <= (5.0 * M / 2.0) && zeroRegisters > 0) {
            rawEstimate = M * Math.log(M / zeroRegisters);
        } else if(rawEstimate > ((1.0 / 30.0) * POW_2_32)) {
            rawEstimate = -POW_2_32 * Math.log(1 - rawEstimate / POW_2_32);
        }

        if(this.randomCount > 0)
            rawEstimate = addRandomDistinctFormula((int) rawEstimate, this.randomCount, universeSizeN);

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
        if(this.state == State.SPARSE) {
            mergeTmpSparse();
            int size = this.sparseSet.length * 4 + SERIALIZED_METADATA_FIELDS;

            byte[] buff = new byte[size];
            int j = 0;
            buff[j++] = (byte) 0;
            buff[j++] = (byte) this.p;
            buff[j++] = (byte) this.r;
            buff[j++] = (byte) (this.randomCount >>> 24 & 0xFF);
            buff[j++] = (byte) (this.randomCount >>> 16 & 0xFF);
            buff[j++] = (byte) (this.randomCount >>> 8 & 0xFF);
            buff[j++] = (byte) (this.randomCount & 0xFF);

            for(int i=0; i<this.sparseSet.length; i++) {
                buff[j++] = (byte) ((this.sparseSet[i] >>> 24) & 0xFF);
                buff[j++] = (byte) ((this.sparseSet[i] >>> 16) & 0xFF);
                buff[j++] = (byte) ((this.sparseSet[i] >>> 8) & 0xFF);
                buff[j++] = (byte) (this.sparseSet[i] & 0xFF);
            }
            return buff;
        }
        if(this.state == State.DENSE) {
            int size = m * 4 + SERIALIZED_METADATA_FIELDS;

            byte[] buff = new byte[size];
            int j = 0;
            buff[j++] = (byte) 1;
            buff[j++] = (byte) p;
            buff[j++] = (byte) r;
            buff[j++] = (byte) (this.randomCount >>> 24 & 0xFF);
            buff[j++] = (byte) (this.randomCount >>> 16 & 0xFF);
            buff[j++] = (byte) (this.randomCount >>> 8 & 0xFF);
            buff[j++] = (byte) (this.randomCount & 0xFF);

            for(int i=0; i<m; i++) {
                buff[j++] = (byte) ((this.registers[i] >>> 24) & 0xFF);
                buff[j++] = (byte) ((this.registers[i] >>> 16) & 0xFF);
                buff[j++] = (byte) ((this.registers[i] >>> 8) & 0xFF);
                buff[j++] = (byte) (this.registers[i] & 0xFF);
            }
            return buff;
        }
        return null;
    }

    public static HLLPlusPlus deserialize(byte[] buff) {
        if (buff == null || buff.length < 7)
            throw new IllegalArgumentException("array is null or smaller than 6 bytes");

        int j = 0;
        int mode = buff[j++];
        int p = buff[j++];
        int r = buff[j++];
        int randomCount = ((buff[j] & 0xFF) << 24) | ((buff[j + 1] & 0xFF) << 16) | ((buff[j + 2] & 0xFF) << 8) | (buff[j + 3] & 0xFF);
        j += 4;

        HLLPlusPlus hll = new HLLPlusPlus(p, r);
        hll.randomCount = randomCount;

        if(mode == 0) {
            int n = (buff.length - SERIALIZED_METADATA_FIELDS) / 4;
            int[] sparseSet = new int[n];
            for(int i=0; i<n; i++) {
                sparseSet[i] = ((buff[j] & 0xFF) << 24) |
                        ((buff[j + 1] & 0xFF) << 16) |
                        ((buff[j + 2] & 0xFF) << 8) |
                        (buff[j + 3] & 0xFF);
                j += 4;
            }
            hll.sparseSet = sparseSet;
        }
        if(mode == 1) {
            hll.state = State.DENSE;
            hll.sparseList = new int[0];
            hll.registers = new int[hll.m];
            if((hll.m * 4) != (buff.length - SERIALIZED_METADATA_FIELDS))
                throw new IllegalArgumentException("HLL buffer invalid size: " + (buff.length - SERIALIZED_METADATA_FIELDS) + " expected: " + hll.m);

            for(int i=0; i < hll.m; i++) {
                hll.registers[i] = 
                    ((buff[j] & 0xFF) << 24) |
                    ((buff[j + 1] & 0xFF) << 16) |
                    ((buff[j + 2] & 0xFF) << 8) |
                    (buff[j + 3] & 0xFF);
                j += 4;
            }
        }
        return hll;
    }

    protected void debugInfo() {
        System.err.println("p=" + p + " r=" + r + " M=" + totalRegisters);
        int[] hist = new int[maxRegisterValue + 1];
        for (int i = 0; i < totalRegisters; i++) {
            int v = readRegister(i) & 0xFF;
            hist[v]++;
        }
        for (int i = 0; i <= maxRegisterValue; i++)
            System.err.printf("reg %2d: %6d%n", i, hist[i]);
        double zero = hist[0];
        System.err.println("zeroRegs=" + zero);
    }
}