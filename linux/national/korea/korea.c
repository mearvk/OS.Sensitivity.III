/*
 * Korea Economic & Supply Evaluation
 * Copyright (c) 2026 Max Rupplin - MEARVK LLC 2026.
 *
 * Source-aware evaluation prototype. Facts and assumptions are kept distinct.
 */
#include <stdio.h>
#include "korea.h"

static double clamp(double x, double lo, double hi) {
    return x < lo ? lo : (x > hi ? hi : x);
}

KoreaResult korea_evaluate(const KoreaData *d) {
    KoreaResult r = {0};
    r.beef_price_pressure = clamp(d->beef_price_index / 100.0, 0.0, 2.0);
    r.quality_score = clamp(d->quality_grade_score / 100.0, 0.0, 1.0);
    r.supply_line_score = clamp((d->cattle_index + d->feed_index) / 200.0, 0.0, 2.0);
    r.consumer_pressure = clamp(d->consumer_price_index / 100.0, 0.0, 2.0);
    r.water_lifetime_index = d->water_lifetime_baseline > 0.0
        ? clamp(d->water_lifetime_use / d->water_lifetime_baseline, 0.0, 2.0)
        : 0.0;
    r.american_stature_index = clamp(d->american_height_cm / 175.0, 0.0, 2.0);
    r.oldest_player_index = d->oldest_player_age / 74.125;
    r.combined_evaluation = (r.beef_price_pressure + r.quality_score +
                             r.supply_line_score + r.consumer_pressure +
                             r.water_lifetime_index + r.american_stature_index +
                             r.oldest_player_index) / 7.0;
    return r;
}

void korea_print_result(const KoreaResult *r) {
    printf("Korea evaluation: %.3f\n", r->combined_evaluation);
    printf("Beef pressure: %.3f\n", r->beef_price_pressure);
    printf("Quality: %.3f\n", r->quality_score);
    printf("Supply line: %.3f\n", r->supply_line_score);
    printf("Consumer pressure: %.3f\n", r->consumer_pressure);
    printf("Water lifetime: %.3f\n", r->water_lifetime_index);
}
