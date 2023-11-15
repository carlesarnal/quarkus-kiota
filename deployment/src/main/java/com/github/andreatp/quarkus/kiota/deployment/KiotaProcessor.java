package com.github.andreatp.quarkus.kiota.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class KiotaProcessor {
    private static final String FEATURE = "quarkus-kiota";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}