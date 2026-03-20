#include <iostream>
#include <string>
#include <vector>
#include <stdexcept>

#include <nlohmann/json.hpp>
#include <xxhash.h>

#include "utils/base64.h"
#include "../../../cpp/src/HLLPlusPlus.h"


using json = nlohmann::json;

// ── base64 helpers ────────────────────────────────────────────────────────────

static std::string b64_encode(const std::vector<uint8_t>& data) {
    return base64_encode(data.data(), data.size());
}

static std::vector<uint8_t> b64_decode(const std::string& s) {
    std::string decoded = base64_decode(s);
    return std::vector<uint8_t>(decoded.begin(), decoded.end());
}

// ── hash helper (same seed as test suite) ────────────────────────────────────

static inline uint64_t h(uint64_t value) {
    return XXH64(&value, sizeof(value), 0);
}

// ── ops ───────────────────────────────────────────────────────────────────────

static json do_serialize(const json& cmd) {
    int p = cmd.at("p").get<int>();
    int r = cmd.at("r").get<int>();

    HLLPlusPlus hll(p, r);
    for (long long v : cmd.at("values")) {
        hll.add(h((uint64_t)v));
    }

    return {
        {"bytes",    b64_encode(hll.serialize())},
        {"estimate", hll.estimate()},
    };
}

static json do_deserialize(const json& cmd) {
    auto bytes = b64_decode(cmd.at("bytes").get<std::string>());
    HLLPlusPlus* hll  = HLLPlusPlus::deserialize(bytes);
    long long est = hll->estimate();

    delete hll;
    return {{"estimate", est}};
}

static json do_merge(const json& cmd) {
    auto sketches = cmd.at("sketches");
    if (sketches.empty()) throw std::runtime_error("no sketches provided");

    auto* base = HLLPlusPlus::deserialize(b64_decode(sketches[0].get<std::string>()));

    for (size_t i = 1; i < sketches.size(); i++) {
        auto* other = HLLPlusPlus::deserialize(b64_decode(sketches[i].get<std::string>()));
        if (!base->merge(other)) {
            delete other; delete base;
            throw std::runtime_error("incompatible sketches at index " + std::to_string(i));
        }
        delete other;
    }

    auto bytes    = base->serialize();
    long long est = base->estimate();
    delete base;

    return {
        {"bytes",    b64_encode(bytes)},
        {"estimate", est},
    };
}

// ── main ──────────────────────────────────────────────────────────────────────

int main() {
    std::string input;
    std::getline(std::cin, input);
    json cmd = json::parse(input);
    
    std::string op = cmd.at("op").get<std::string>();

    json result;

    if      (op == "serialize")   result = do_serialize(cmd);
    else if (op == "deserialize") result = do_deserialize(cmd);
    else if (op == "merge")       result = do_merge(cmd);
    else throw ("unknown op: " + op);

    std::cout << result.dump() << "\n";
    return 0;
}