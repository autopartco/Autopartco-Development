package com.autopartco.backend.core.ml;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PoissonTest {
    @Test
    void quantilesMatchKnownValues() {
        assertThat(Poisson.quantile(0, 0.9)).isZero();
        assertThat(Poisson.quantile(2.0, 0.1)).isZero();   // P(X<=0)=0.135
        assertThat(Poisson.quantile(2.0, 0.9)).isEqualTo(4); // P(X<=3)=0.857, P(X<=4)=0.947
        assertThat(Poisson.quantile(10.0, 0.5)).isEqualTo(10);
        assertThat(Poisson.quantile(1_000_000.0, 0.9)).isBetween(1_001_000, 1_001_500);
    }
}
