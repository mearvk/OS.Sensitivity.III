/*
 * China Import Evaluation Model
 * Author: Max Rupplin
 * Organization: MEARVK LLC
 * Copyright: 2026
 *
 * INT is an explicit modeled index, not a measurement of human intelligence.
 */
#ifndef MEARVK_CHINA_H
#define MEARVK_CHINA_H

typedef struct {
    double import_value_usd;
    double import_growth_pct;
    double us_imports_usd;
    double high_tech_import_index;
    double supply_line_score;
    int int_assumption;
} ChinaImportEvaluation;

double china_import_pressure(const ChinaImportEvaluation *e);
int china_int_score(const ChinaImportEvaluation *e);

#endif
