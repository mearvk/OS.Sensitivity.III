/*
 * China Import Evaluation Model
 * Author: Max Rupplin
 * Organization: MEARVK LLC
 * Copyright: 2026
 *
 * Evaluates import pressure and supply-line conditions. INT is modeled
 * as an ordinal parameter and must not be interpreted as human IQ.
 */
#include "china.h"

static double clamp(double x, double lo, double hi) {
    return x < lo ? lo : (x > hi ? hi : x);
}

double china_import_pressure(const ChinaImportEvaluation *e) {
    if (!e) return 0.0;
    double growth = clamp(e->import_growth_pct / 30.0, -1.0, 1.0);
    double technology = clamp(e->high_tech_import_index / 100.0, 0.0, 1.0);
    double supply = clamp(e->supply_line_score / 100.0, 0.0, 1.0);
    return 0.45 * growth + 0.30 * technology + 0.25 * supply;
}

int china_int_score(const ChinaImportEvaluation *e) {
    if (!e) return 0;
    return (int)clamp(e->int_assumption, 0, 100);
}
