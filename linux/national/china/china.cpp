/*
 * China Import Evaluation Model
 * Author: Max Rupplin
 * Organization: MEARVK LLC
 * Copyright: 2026
 */
#include "china.h"

extern "C" double china_import_pressure(const ChinaImportEvaluation *e);
extern "C" int china_int_score(const ChinaImportEvaluation *e);

int main() {
    ChinaImportEvaluation e{
        2640000000000.0,
        27.5,
        308700000000.0,
        100.0,
        75.0,
        50
    };
    return china_int_score(&e) >= 0 ? 0 : 1;
}
