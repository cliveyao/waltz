/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016, 2017, 2018, 2019 Waltz open source project
 * See README.md for more information
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific
 *
 */

package com.khartec.waltz.model.server_information;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.khartec.waltz.model.*;
import org.immutables.value.Value;

import java.util.Date;

import static com.khartec.waltz.model.EndOfLifeStatus.calculateEndOfLifeStatus;


@Value.Immutable
@JsonSerialize(as = ImmutableServerInformation.class)
@JsonDeserialize(as = ImmutableServerInformation.class)
public abstract class ServerInformation implements
        IdProvider,
        ProvenanceProvider,
        ExternalIdProvider,
        WaltzEntity,
        CustomEnvironmentAsset{

    public abstract String hostname();
    public abstract String operatingSystem();
    public abstract String operatingSystemVersion();
    public abstract String location();
    public abstract String country();
    public abstract LifecycleStatus lifecycleStatus();

    @Nullable
    public abstract Date hardwareEndOfLifeDate();


    @Value.Derived
    public EndOfLifeStatus hardwareEndOfLifeStatus() {
        return calculateEndOfLifeStatus(hardwareEndOfLifeDate());
    }


    @Nullable
    public abstract Date operatingSystemEndOfLifeDate();


    @Value.Derived
    public EndOfLifeStatus operatingSystemEndOfLifeStatus() {
        return calculateEndOfLifeStatus(operatingSystemEndOfLifeDate());
    }


    @Value.Default
    public String provenance() {
        return "waltz";
    }


    @Value.Default
    public boolean virtual() {
        return false;
    }


    public EntityReference entityReference() {
        return ImmutableEntityReference.builder()
                .kind(EntityKind.SERVER)
                .id(id().get())
                .name(hostname())
                .entityLifecycleStatus(EntityLifecycleStatus.ACTIVE)
                .build();
    }

}
