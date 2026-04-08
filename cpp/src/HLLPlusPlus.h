#ifndef HLLPLUSPLUS_H
#define HLLPLUSPLUS_H

#include <cstdint>
#include <cmath>
#include <cstring>
#include <stdexcept>
#include <algorithm>
#include <vector>

class HLLPlusPlus {
private:
    // below variables need to be serialized
    int p;
    int r;
    std::vector<uint32_t> registers;
    bool isSparse;
    std::vector<uint32_t> sparseSet;
    std::vector<uint32_t> sparseList;
    double preEstimate;
    int zeroRegs;

    // below variables are derived
    int m;
    int sp;
    int maxRegisterValue;
    int regPerDatatype;
    int totalRegisters;
    int conversionThreshold;
    int sparseSetIndexOffset;
    int sparseListIndex;

    // below are constants
    static const unsigned char VERSION = 1;
    static const int TEMPORARY_LIST_SIZE = 5;
    static const int MIN_P = 4;
    static const int MAX_P = 18;
    static const int MIN_R = 4;
    static const int MAX_R = 6;
    static const int DEFAULT_P = 12;
    static const int DEFAULT_R = 6;
    static const int SPARSE_P_EXTRA_BITS = 4;
    static const int DT_WIDTH = 32;
    static const int SPARSE_SERIALIZED_METADATA_FIELDS_BYTES = 8; // 1 byte version, 1 byte mode, 1 byte p, 1 byte r, 4 byte buffer length
    static const int DENSE_SERIALIZED_METADATA_FIELDS_BYTES = 20; // 1 byte version, 1 byte mode, 1 byte p, 1 byte r, 4 byte buffer length, 4 byte zeroRegs, 8 byte preEstimate
    static const int EMPIRICAL_BIAS_CORRECTION_OVER_ESTIMATES = 6;
    static std::vector<std::vector<double>> empiricalRawEstimateData;
    static std::vector<std::vector<double>> empiricalBiasData;
    static const int empiricalThreshold[];

    static double PRE_POW_2_K[64];

    inline static struct Init {
        Init() {
            for (int i = 0; i < 64; i++) {
                PRE_POW_2_K[i] = pow(2.0, -i);
            }

            for (int i = 0; i < (int)empiricalRawEstimateData.size(); i++) {
                std::vector<double>& rawEst  = empiricalRawEstimateData[i];
                std::vector<double>& biasEst = empiricalBiasData[i];

                int n = rawEst.size();
                std::vector<std::pair<double, double>> combined(n);

                for (int j = 0; j < n; j++) {
                    combined[j] = { rawEst[j], biasEst[j] };
                }

                std::sort(combined.begin(), combined.end());
                for (int j = 0; j < n; j++) {
                    rawEst[j]  = combined[j].first;
                    biasEst[j] = combined[j].second;
                }
            }
        }
    } _init;


    // read r bits of the registers from a specified bit location and return it as a byte.
    // this is for debugging purposes only
    uint8_t readRegister(int index);

    void indexSort(std::vector<uint32_t>&);

    // dedup the sparseList based on index
    // if same index -> set the index to max value
    void dedupIndex(std::vector<uint32_t>&);

    void mergeTmpSparse();

    void convertToNormal();

    void sparseMerge(HLLPlusPlus other);

    void merge4(HLLPlusPlus other);

    void merge5(HLLPlusPlus other);

    void merge6(HLLPlusPlus other);

    void normalMerge(HLLPlusPlus other);

    double estimateBias(double E);

    double getAlphaM(int M);

    static int countTrailingZeros(int64_t value) {
        if (value == 0) return 64;
        int count = 0;
        while ((value & 1) == 0) {
            count++;
            value >>= 1;
        }
        return count;
    }

public:
    HLLPlusPlus();

    HLLPlusPlus(int p);

    HLLPlusPlus(int p, int r);

    ~HLLPlusPlus();

    void add(uint64_t value);

    bool merge(HLLPlusPlus other);

    int64_t estimate();

    template <typename Container = std::vector<uint8_t>>
    Container serialize();

    static HLLPlusPlus deserialize(const std::vector<uint8_t>&);

    static HLLPlusPlus deserialize(const unsigned char *, size_t);

    void debugInfo();
};

// explicit instantiation declarations
extern template std::vector<uint8_t> HLLPlusPlus::serialize<std::vector<uint8_t>>();
extern template std::string HLLPlusPlus::serialize<std::string>();

#endif
