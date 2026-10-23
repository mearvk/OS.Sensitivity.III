/*
 * Korea Economic & Supply Evaluation — C++ bridge
 * Copyright (c) 2026 Max Rupplin - MEARVK LLC 2026.
 */
#include "korea.h"
#include <cmath>

/* C++ wrapper: the same deliberately modest model, useful to later XML/JSON readers. */
extern "C" KoreaResult korea_evaluate_cpp(const KoreaData& data) {
    return korea_evaluate(&data);
}

static double signed_line(double value, double reference) {
    if (value > reference) return 1.0;
    if (value < reference) return -1.0;
    return 0.0;
}

extern "C" double korea_line_property(double start, double end, double reference) {
    return signed_line(end - start, reference);
}
