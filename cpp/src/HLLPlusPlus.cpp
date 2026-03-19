#include "HLLPlusPlus.h"
#include <iostream>

double HLLPlusPlus::PRE_POW_2_K[64];
// bool HLLPlusPlus::initialized = false;

// void HLLPlusPlus::initializeStatic() {
//     if (!initialized) {
//         for(int i = 0; i < 64; i++) {
//             PRE_POW_2_K[i] = pow(2.0, -i);
//         }
//         initialized = true;
//     }
// }

uint8_t HLLPlusPlus::readRegister(int index) {
    if(isSparse) {
        mergeTmpSparse();
        int result = 0;
        for(int i = 0; i < sparseSet.size(); i++) {
            if((sparseSet[i] >> (sparseSetIndexOffset + SPARSE_P_EXTRA_BITS)) == index)
                result = (result > (sparseSet[i] & maxRegisterValue)) ? result : (sparseSet[i] & maxRegisterValue);
        }
        return (uint8_t) result;
    }
    int bucketIndex = index / regPerDatatype;
    int registerOffset = (regPerDatatype - index % regPerDatatype - 1) * r;
    return (uint8_t) ((this->registers[bucketIndex] >> registerOffset) & maxRegisterValue);
}

void HLLPlusPlus::indexSort(std::vector<uint32_t> &arr) {
    int n = this->sparseListIndex;
    for(int i = 0; i < n; i++) {
        for(int j = 0; j < (n - i - 1); j++) {
            uint32_t cur = arr[j] >> sparseSetIndexOffset;
            uint32_t next = arr[j + 1] >> sparseSetIndexOffset;
            if(cur > next) {
                uint32_t tmp = arr[j + 1];
                arr[j + 1] = arr[j];
                arr[j] = tmp;
            }
        }
    }
}

// dedup the sparseList based on index
// if same index -> set the index to max value
void HLLPlusPlus::dedupIndex(std::vector<uint32_t> &tmpSparseList) {
    if(sparseListIndex == 0) {
        return;
    }

    indexSort(tmpSparseList);
    int k = 1;

    for(int i = 1; i < this->sparseListIndex; i++) {
        uint32_t curIndex = tmpSparseList[i] >> sparseSetIndexOffset;
        uint32_t lastIndex = tmpSparseList[k - 1] >> sparseSetIndexOffset;
        if(curIndex == lastIndex) {
            tmpSparseList[k - 1] = (curIndex << sparseSetIndexOffset) | 
                ((tmpSparseList[k - 1] & maxRegisterValue) > (tmpSparseList[i] & maxRegisterValue) ? 
                 (tmpSparseList[k - 1] & maxRegisterValue) : (tmpSparseList[i] & maxRegisterValue));
        }
        else {
            tmpSparseList[k] = tmpSparseList[i];
            k++;
        }
    }

    this->sparseListIndex = k;
    tmpSparseList.resize(k);
}

void HLLPlusPlus::mergeTmpSparse() {
    if(this->sparseListIndex <= 0)
        return;

    dedupIndex(this->sparseList);

    std::vector<uint32_t> newSparseSet(sparseSet.size() + sparseList.size());

    int l = 0;
    int r = 0;
    int k = 0;
    while(l < sparseSet.size() && r < sparseList.size()) {
        uint32_t setIndex = sparseSet[l] >> sparseSetIndexOffset;
        uint32_t listIndex = sparseList[r] >> sparseSetIndexOffset;
        if(setIndex < listIndex) {
            newSparseSet[k] = sparseSet[l];
            l++;
        }
        else if(setIndex > listIndex) {
            newSparseSet[k] = sparseList[r];
            r++;
        }
        else {
            uint32_t idx = (setIndex << sparseSetIndexOffset);
            uint32_t value = ((sparseSet[l] & maxRegisterValue) > (sparseList[r] & maxRegisterValue) ?
                         (sparseSet[l] & maxRegisterValue) : (sparseList[r] & maxRegisterValue));
            newSparseSet[k] = idx | value;
            l++; r++;
        }
        k++;
    }
    while(l < sparseSet.size()) {
        newSparseSet[k] = sparseSet[l];
        l++; k++;
    }
    while(r < sparseList.size()) {
        newSparseSet[k] = sparseList[r];
        r++; k++;
    }

    this->sparseSet.resize(k);
    for(int i=0; i<k; i++)
        this->sparseSet[i] = newSparseSet[i];
        
    // newSparseSet.resize(k);
    // this->sparseSet = std::move(newSparseSet);
    this->sparseListIndex = 0;
}

void HLLPlusPlus::convertToNormal() {
    this->registers = std::vector<uint32_t>(m, 0);

    for(int i = 0; i < this->sparseSet.size(); i++) {
        uint32_t idx = sparseSet[i] >> (sparseSetIndexOffset + SPARSE_P_EXTRA_BITS);
        uint32_t val = sparseSet[i] & maxRegisterValue;

        int registerOffset = (regPerDatatype - idx % regPerDatatype - 1) * r;
        int bucketIndex = idx / regPerDatatype;

        uint32_t bucketValue = this->registers[bucketIndex];
        uint32_t prevValue = (bucketValue >> registerOffset) & maxRegisterValue;
        if(prevValue < val)
            this->registers[bucketIndex] = (bucketValue & ~(maxRegisterValue << registerOffset)) | (val << registerOffset);
    }

    this->sparseSet.clear();
    this->sparseSet.shrink_to_fit();
    this->sparseList.clear();
    this->sparseList.shrink_to_fit();
    this->sparseListIndex = 0;
    this->isSparse = false;
}

void HLLPlusPlus::sparseMerge(HLLPlusPlus* other) {
    std::vector<uint32_t> &a = this->sparseSet;
    std::vector<uint32_t> &b = other->sparseSet;

    std::vector<uint32_t> newSparseSet(a.size() + b.size());

    int l = 0;
    int r = 0;
    int k = 0;

    while((l < a.size()) || (r < b.size())) {
        if(l >= a.size()) {
            newSparseSet[k++] = b[r++];
        }
        else if(r >= b.size()) {
            newSparseSet[k++] = a[l++];
        }
        else {
            uint32_t thisVal = a[l];
            uint32_t otherVal = b[r];
            uint32_t thisIndex = thisVal >> sparseSetIndexOffset;
            uint32_t otherIndex = otherVal >> sparseSetIndexOffset;

            if(thisIndex == otherIndex)
            {
                newSparseSet[k++] = (thisIndex << sparseSetIndexOffset) | ((thisVal & maxRegisterValue) > (otherVal & maxRegisterValue) ? (thisVal & maxRegisterValue) : (otherVal & maxRegisterValue));
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

    this->sparseSet.resize(k);
    for(int i=0; i<k; i++) {
        this->sparseSet[i] = newSparseSet[i];
    }
    // if(k < newSparseSet.size()) {
    //     this->sparseSet = std::move(newSparseSet);
    //     this->sparseSet.erase(this->sparseSet.begin() + k, this->sparseSet.end());

    // } else {
    //     this->sparseSet = std::move(newSparseSet);
    // }
}

void HLLPlusPlus::merge4(HLLPlusPlus* other) {
    const uint32_t MASK = 0xf;
    const int REGISTERS_PER_BUCKET = 8;
    const int REGISTER_SIZE = 4;
    for(int i = 0; i < m; ++i) {
        uint32_t word = 0;
        uint32_t thisBucket = this->registers[i];
        uint32_t otherBucket = other->registers[i];
        for(int j = 0; j < REGISTERS_PER_BUCKET; ++j) {
            uint32_t mask = MASK << (REGISTER_SIZE * j);
            uint32_t thisRawVal = thisBucket & mask;
            uint32_t otherRawVal = otherBucket & mask;
            word |= (thisRawVal < otherRawVal) ? otherRawVal : thisRawVal;
        }
        this->registers[i] = word;
    }
}

void HLLPlusPlus::merge5(HLLPlusPlus* other) {
    const uint32_t MASK = 0x1f;
    const int REGISTERS_PER_BUCKET = 6;
    const int REGISTER_SIZE = 5;
    for(int i = 0; i < m; ++i) {
        uint32_t word = 0;
        uint32_t thisBucket = this->registers[i];
        uint32_t otherBucket = other->registers[i];
        for(int j = 0; j < REGISTERS_PER_BUCKET; ++j) {
            uint32_t mask = MASK << (REGISTER_SIZE * j);
            uint32_t thisRawVal = thisBucket & mask;
            uint32_t otherRawVal = otherBucket & mask;
            word |= (thisRawVal < otherRawVal ? otherRawVal : thisRawVal);
        }
        this->registers[i] = word;
    }
}

void HLLPlusPlus::merge6(HLLPlusPlus* other) {
    const uint32_t MASK = 0x3f;
    const int REGISTERS_PER_BUCKET = 5;
    const int REGISTER_SIZE = 6;
    for(int i = 0; i < m; ++i) {
        uint32_t word = 0;
        uint32_t thisBucket = this->registers[i];
        uint32_t otherBucket = other->registers[i];
        for(int j = 0; j < REGISTERS_PER_BUCKET; ++j) {
            uint32_t mask = MASK << (REGISTER_SIZE * j);
            uint32_t thisRawVal = thisBucket & mask;
            uint32_t otherRawVal = otherBucket & mask;
            word |= (thisRawVal < otherRawVal) ? otherRawVal : thisRawVal;
        }
        this->registers[i] = word;
    }
}

void HLLPlusPlus::normalMerge(HLLPlusPlus* other) {
    switch(r) {
        case 4: merge4(other);
            break;
        case 5: merge5(other);
            break;
        case 6: merge6(other);
            break;
    }
}

void HLLPlusPlus::estimate4(double* results) {
    const int REGISTER_PER_BUCKET = 8;
    const int REGISTER_SIZE = 4;
    const uint32_t MASK = 0xf;

    int zeroRegisters = 0;
    double sum = 0;
    int completeBuckets = totalRegisters / REGISTER_PER_BUCKET;
    int remainingRegisters = totalRegisters % REGISTER_PER_BUCKET;

    for(int i = 0; i < completeBuckets; i++) {
        uint32_t cur = this->registers[i];
        for(int j = 0; j < REGISTER_PER_BUCKET; j++) {
            uint32_t k = (cur >> (REGISTER_SIZE * j)) & MASK;
            zeroRegisters += ((k == 0) ? 1 : 0);
            sum = sum + PRE_POW_2_K[k];
        }
    }

    if(remainingRegisters > 0) {
        uint32_t last = this->registers[this->registers.size() - 1];
        for(int j = REGISTER_PER_BUCKET - 1; j >= (REGISTER_PER_BUCKET - remainingRegisters); j--){
            uint32_t k = (last >> (REGISTER_SIZE * j)) & MASK;
            zeroRegisters += ((k == 0) ? 1 : 0);
            sum = sum + PRE_POW_2_K[k];
        }
    }

    results[0] = sum;
    results[1] = zeroRegisters;
}

void HLLPlusPlus::estimate5(double* results) {
    const int REGISTER_PER_BUCKET = 6;
    const int REGISTER_SIZE = 5;
    const uint32_t MASK = 0x1f;

    int zeroRegisters = 0;
    double sum = 0;
    int completeBuckets = totalRegisters / REGISTER_PER_BUCKET;
    int remainingRegisters = totalRegisters % REGISTER_PER_BUCKET;

    for(int i = 0; i < completeBuckets; i++) {
        uint32_t cur = this->registers[i];
        for(int j = 0; j < REGISTER_PER_BUCKET; j++) {
            uint32_t k = (cur >> (REGISTER_SIZE * j)) & MASK;
            zeroRegisters += ((k == 0) ? 1 : 0);
            sum = sum + PRE_POW_2_K[k];
        }
    }

    if(remainingRegisters > 0) {
        uint32_t last = this->registers[this->registers.size() - 1];
        for(int j = REGISTER_PER_BUCKET - 1; j >= (REGISTER_PER_BUCKET - remainingRegisters); j--){
            uint32_t k = (last >> (REGISTER_SIZE * j)) & MASK;
            zeroRegisters += ((k == 0) ? 1 : 0);
            sum = sum + PRE_POW_2_K[k];
        }
    }

    results[0] = sum;
    results[1] = zeroRegisters;
}

void HLLPlusPlus::estimate6(double* results) {
    const int REGISTER_PER_BUCKET = 5;
    const int REGISTER_SIZE = 6;
    const uint32_t MASK = 0x3f;

    int zeroRegisters = 0;
    double sum = 0;
    int completeBuckets = totalRegisters / REGISTER_PER_BUCKET;
    int remainingRegisters = totalRegisters % REGISTER_PER_BUCKET;

    for(int i = 0; i < completeBuckets; i++) {
        uint32_t cur = this->registers[i];
        for(int j = 0; j < REGISTER_PER_BUCKET; j++) {
            uint32_t k = (cur >> (REGISTER_SIZE * j)) & MASK;
            zeroRegisters += ((k == 0) ? 1 : 0);
            sum = sum + PRE_POW_2_K[k];
        }
    }

    if(remainingRegisters > 0) {
        uint32_t last = this->registers[this->registers.size() - 1];
        for(int j = REGISTER_PER_BUCKET - 1; j >= (REGISTER_PER_BUCKET - remainingRegisters); j--){
            uint32_t k = (last >> (REGISTER_SIZE * j)) & MASK;
            zeroRegisters += ((k == 0) ? 1 : 0);
            sum = sum + PRE_POW_2_K[k];
        }
    }

    results[0] = sum;
    results[1] = zeroRegisters;
}

double HLLPlusPlus::getAlphaM(int M) {
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

HLLPlusPlus::HLLPlusPlus() : HLLPlusPlus(DEFAULT_P, DEFAULT_R) {
}

HLLPlusPlus::HLLPlusPlus(int p) : HLLPlusPlus(p, DEFAULT_R) {
}

HLLPlusPlus::HLLPlusPlus(int p, int r) {
    // initializeStatic();
    
    if(p < MIN_P || p > MAX_P)
        throw std::invalid_argument("invalid p");
    if(r < 4 || r > 6)
        throw std::invalid_argument("Invalid R");

    this->p = p;
    this->r = r;

    this->sp = p + SPARSE_P_EXTRA_BITS;
    this->regPerDatatype = DT_WIDTH / r;
    this->totalRegisters = (1 << p);
    this->m = totalRegisters / regPerDatatype + (totalRegisters % regPerDatatype == 0 ? 0 : 1);
    this->maxRegisterValue = ((1 << r) - 1);

    this->sparseList = std::vector<uint32_t>(TEMPORARY_LIST_SIZE);
    this->sparseSet = std::vector<uint32_t>(0);
    this->sparseListIndex = 0;
    this->isSparse = true;
    this->conversionThreshold = m;
    this->sparseSetIndexOffset = DT_WIDTH - sp;
    this->registers = std::vector<uint32_t>(0);
}

HLLPlusPlus::~HLLPlusPlus() = default;

void HLLPlusPlus::add(uint64_t value) {
    if(isSparse) {
        uint64_t registerIndex = (value >> (64 - sp));
        value = value | (1LL << (64 - sp));
        uint32_t cnt = __builtin_ctzll(value) + 1;
        cnt = (cnt < maxRegisterValue) ? cnt : maxRegisterValue;

        this->sparseList[sparseListIndex] = (registerIndex << sparseSetIndexOffset) | cnt;
        sparseListIndex++;

        if(sparseListIndex >= TEMPORARY_LIST_SIZE) {
            mergeTmpSparse();
            if(this->sparseSet.size() >= conversionThreshold){
                convertToNormal();
            }
        }
    }
    else {
        uint64_t registerIndex = (value >> (64 - p));
        value = value | (1LL << (64 - p));
        uint32_t cnt = __builtin_ctzll(value) + 1;
        cnt = (cnt < maxRegisterValue) ? cnt : maxRegisterValue;

        int bucketIndex = registerIndex / regPerDatatype;
        int registerOffset = (regPerDatatype - registerIndex % regPerDatatype - 1) * r;
        // if(bucketIndex > this->registers.size())
        //     std::cerr<<std::endl;
        uint32_t bucketValue = this->registers[bucketIndex];
        uint32_t prevValue = (bucketValue >> registerOffset) & maxRegisterValue;
        if(prevValue < cnt)
            this->registers[bucketIndex] = (bucketValue & ~(maxRegisterValue << registerOffset)) | (cnt << registerOffset);
    }
}

bool HLLPlusPlus::merge(HLLPlusPlus* other) {
    if (other == nullptr)
        return false;
    if (this->p != other->p || this->r != other->r)
        return false;

    if(this->sparseListIndex > 0)
        this->mergeTmpSparse();
    if(other->sparseListIndex > 0)
        other->mergeTmpSparse();

    int state = (isSparse ? 0 : 1) | (other->isSparse ? 0 : 2);
    switch (state) {
        case 0: // both sparse
            sparseMerge(other);
            if(this->sparseSet.size() >= conversionThreshold)
                this->convertToNormal();
            break;
        case 1: // this normal, other sparse
            for(int i = 0; i < other->sparseSet.size(); i++) {
                uint32_t idx = other->sparseSet[i] >> (sparseSetIndexOffset + SPARSE_P_EXTRA_BITS);
                uint32_t val = other->sparseSet[i] & maxRegisterValue;
                int bucketIndex = idx / regPerDatatype;
                int registerOffset = (regPerDatatype - idx % regPerDatatype - 1) * r;

                uint32_t registerValue = this->registers[bucketIndex];
                uint32_t mask = maxRegisterValue << registerOffset;
                if((registerValue & mask) < (val << registerOffset)) {
                    this->registers[bucketIndex] = (registerValue & ~mask) | (val << registerOffset);
                }
            }
            break;
        case 2: // this sparse, other normal
            this->convertToNormal();
            normalMerge(other);
            break;
        case 3: // both normal
            normalMerge(other);
            break;
    }
    return true;
}

int64_t HLLPlusPlus::estimate() {
    double M = totalRegisters;
    if(isSparse) {
        if(this->sparseListIndex > 0)
            mergeTmpSparse();
        double SM = (1 << sp);
        return std::llround((SM * std::log(SM / (SM - this->sparseSet.size()))));
    }

    double results[2];

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

    if(zeroRegisters != 0) {
        double linearCountingEstimate = std::llround(M * log(M / zeroRegisters));
        if(linearCountingEstimate <= empiricalThreshold[p - MIN_P])
            return std::llround(linearCountingEstimate);
    }

    double alphaM = getAlphaM(totalRegisters);
    double rawEstimate = alphaM * M * M * (1 / sum);
    if(rawEstimate <= 5.0 * M) {
        rawEstimate = rawEstimate - estimateBias(rawEstimate);
    }

    return std::llround(rawEstimate);
}

double HLLPlusPlus::estimateBias(double E) {
    std::vector<double> currentEmpiricalRawEstimateData = empiricalBiasData[p - MIN_P];
    std::vector<double> currentEmpiricalBiasData = empiricalBiasData[p - MIN_P];

    int n = currentEmpiricalBiasData.size();
    if(E < currentEmpiricalRawEstimateData[0] || E > currentEmpiricalRawEstimateData[n - 1])
        return E;
    
    int left = 0, right = n - 1;
    int prev = -1;
    while(left <= right) { 
        int mid = (left + right) / 2;
        double difference = currentEmpiricalRawEstimateData[mid] - E;
        if(difference < 0) {
            left = mid + 1;
        }
        else {
            prev = mid;
            right = mid - 1;
        }
    }

    int halfPoints = EMPIRICAL_BIAS_CORRECTION_OVER_ESTIMATES / 2;
    int trimmedLeftPoint = 0, trimmedRightPoint = 0;

    if(E == currentEmpiricalRawEstimateData[prev]) {
        int leftPoint = std::max(0, prev - halfPoints);
        int rightPoint = std::max(n - 1, prev + halfPoints);
        int eqDistance = std::min(rightPoint - prev, prev - leftPoint);
        trimmedLeftPoint = prev - eqDistance;
        trimmedRightPoint = prev + eqDistance;
    }
    else {
        int leftPoint = std::max(0, prev - halfPoints);
        int rightPoint = std::max(n - 1, prev + (halfPoints - 1));
        int eqDistance = std::min(rightPoint - prev + 1, prev - leftPoint);
        trimmedLeftPoint = prev - eqDistance;
        trimmedRightPoint = prev + eqDistance - 1;
    }

    int totalPoints = trimmedRightPoint - trimmedLeftPoint + 1;
    double averageBiasCorrection = 0;
    for(int j=trimmedLeftPoint; j<=trimmedRightPoint; j++) {
        averageBiasCorrection = averageBiasCorrection + currentEmpiricalBiasData[j];
    }
    averageBiasCorrection = averageBiasCorrection / totalPoints;

    return averageBiasCorrection;
}

std::vector<uint8_t> HLLPlusPlus::serialize() {
    int j = 0;

    if (isSparse) {
        mergeTmpSparse();

        std::vector<uint8_t> buff(sparseSet.size() * 4 + SERIALIZED_METADATA_FIELDS, 0x00);
        buff[j++] = 0;
        buff[j++] = static_cast<uint8_t>(p);
        buff[j++] = static_cast<uint8_t>(r);

        for (int i = 0; i < sparseSet.size(); i++) {
            uint32_t v = sparseSet[i];
            buff[j] = (v >> 24) & 0xFF;
            buff[j+1] = (v >> 16) & 0xFF;
            buff[j+2] = (v >> 8) & 0xFF;
            buff[j+3] = v & 0xFF;
            j+=4;
        }
        return buff;
    } else {
        std::vector<uint8_t> buff(m * 4 + SERIALIZED_METADATA_FIELDS, 0x00);

        buff[j++] = 1;
        buff[j++] = static_cast<uint8_t>(p);
        buff[j++] = static_cast<uint8_t>(r);

        for (int i = 0; i < m; i++) {
            uint32_t v = registers[i];
            buff[j] = (v >> 24) & 0xFF;
            buff[j+1] = (v >> 16) & 0xFF;
            buff[j+2] = (v >> 8) & 0xFF;
            buff[j+3] = v & 0xFF;
            j+=4;
        }
        return buff;
    }
}

HLLPlusPlus* HLLPlusPlus::deserialize(const std::vector<uint8_t>& buff) {
    // initializeStatic();
    if (buff.size() < SERIALIZED_METADATA_FIELDS)
        throw std::invalid_argument("buffer too small");

    int j = 0;
    int mode = buff[j++];
    int p = buff[j++];
    int r = buff[j++];

    HLLPlusPlus* hll = new HLLPlusPlus(p, r);

    if (mode == 0) {
        int n = (buff.size() - SERIALIZED_METADATA_FIELDS) / 4;
        hll->sparseSet = std::vector<uint32_t>(n);

        for (int i = 0; i < n; i++) {
            hll->sparseSet[i] =
                (uint32_t(buff[j]) << 24) |
                (uint32_t(buff[j + 1]) << 16) |
                (uint32_t(buff[j + 2]) << 8) |
                uint32_t(buff[j + 3]);
            j += 4;
        }
    } else {
        hll->isSparse = false;
        hll->registers = std::vector<uint32_t>(hll->m);
        if ((hll->m * 4) != (buff.size() - SERIALIZED_METADATA_FIELDS))
            throw std::invalid_argument("invalid HLL buffer size");

        for (int i = 0; i < hll->m; i++) {
            hll->registers[i] =
                (uint32_t(buff[j]) << 24) |
                (uint32_t(buff[j + 1]) << 16) |
                (uint32_t(buff[j + 2]) << 8) |
                uint32_t(buff[j + 3]);
            j += 4;
        }
    }

    return hll;
}

void HLLPlusPlus::debugInfo() {
    // Debug function implementation (similar to Java version)
}