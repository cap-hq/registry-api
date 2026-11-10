// SPDX-FileCopyrightText: © 2026 CVS Health and/or one of its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.cvshealth.cap.registry.rollup.service;

import com.cvshealth.cap.registry.rollup.mapper.MapperDecoratorProvider;
import org.springframework.util.CollectionUtils;

import java.util.List;

public abstract class AbstractRollupService {

    private final List<MapperDecoratorProvider> mapperDecoratorProviders;

    public AbstractRollupService(List<MapperDecoratorProvider> mapperDecoratorProviders) {
        this.mapperDecoratorProviders = mapperDecoratorProviders;
    }

    public void postMapperDecoration(Object mappedView) {
        if (!CollectionUtils.isEmpty(mapperDecoratorProviders)) {
            for (MapperDecoratorProvider mapperDecoratorProvider : mapperDecoratorProviders) {
                if (mapperDecoratorProvider.supports(mappedView.getClass())) {
                    mapperDecoratorProvider.map(mappedView);
                }
            }
        }
    }
}
