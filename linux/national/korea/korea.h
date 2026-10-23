/*
 * Korea Economic & Supply Evaluation
 * Copyright (c) 2026 Max Rupplin - MEARVK LLC 2026.
 */
#ifndef KOREA_H
#define KOREA_H

typedef struct {
    double beef_price_index;
    double quality_grade_score;
    double cattle_index;
    double feed_index;
    double consumer_price_index;
    double water_lifetime_use;
    double water_lifetime_baseline;
    double american_height_cm;
    double oldest_player_age;
} KoreaData;

typedef struct {
    double beef_price_pressure;
    double quality_score;
    double supply_line_score;
    double consumer_pressure;
    double water_lifetime_index;
    double american_stature_index;
    double oldest_player_index;
    double combined_evaluation;
} KoreaResult;

KoreaResult korea_evaluate(const KoreaData *data);
void korea_print_result(const KoreaResult *result);

#endif
