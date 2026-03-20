#include "HLLPlusPlus.h"
#include <iostream>
#include <random>
#include <vector>
#include <thread>
#include <chrono>
#include <xxhash.h>

int main() {
    const int P = 12;
    const int R = 4;
    const int NUM_SKETCHES = 5000;
    
    std::vector<HLLPlusPlus*> arr(NUM_SKETCHES);
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dis(0, 9999);
    
    HLLPlusPlus* a = new HLLPlusPlus(P);
    int uniques = 0;
    
    std::cout << "Creating and populating " << NUM_SKETCHES << " HLL sketches..." << std::endl;
    
    for(int j = 0; j < NUM_SKETCHES; j++) {
        if(j % 500 == 0) {
            std::cout << "Progress: " << j << "/" << NUM_SKETCHES << std::endl;
        }
        
        arr[j] = new HLLPlusPlus(P);
        int size = dis(gen);
        uniques = std::max(uniques, size);
        
        for(int i = 0; i < size; i++) {
            // Hash the integer i
            int value = i;
            uint64_t hash_val = XXH64(&value, sizeof(i), 0);
            arr[j]->add(hash_val);
        }
    }
    
    std::cout << "Merging all sketches..." << std::endl;
    for(int i = 0; i < NUM_SKETCHES; i++) {
        if(i % 5000 == 0) {
            std::cout << "Merge progress: " << i << "/" << NUM_SKETCHES << std::endl;
        }
        a->merge(arr[i]);
    }
    
    std::cout << "Estimating cardinalities..." << std::endl;
    for(int i = 0; i < NUM_SKETCHES; i++) {
        arr[i]->estimate();
    }
    
    std::cout << "\nuniques: " << (uniques + 1) << std::endl;
    std::cout << "estimate: " << a->estimate() << std::endl;
    
    std::cerr << "\n---- debug ----" << std::endl;
    // a->debugInfo();
    std::cerr << "---- debug ----" << std::endl;
    
    std::vector<uint8_t> ser = a->serialize();
    std::cout << "Serialized length: " << ser.size() << std::endl;
    
    HLLPlusPlus* newHLL = HLLPlusPlus::deserialize(ser);
    
    std::cout << "estimate deserialized: " << newHLL->estimate() << std::endl;
    
    // Cleanup
    std::cout << "\nCleaning up..." << std::endl;
    for(int i = 0; i < NUM_SKETCHES; i++) {
        delete arr[i];
    }
    delete a;
    delete newHLL;
    
    std::cout << "Done!" << std::endl;
        
    return 0;
}