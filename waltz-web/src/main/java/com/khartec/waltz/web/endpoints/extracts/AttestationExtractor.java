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

package com.khartec.waltz.web.endpoints.extracts;

import com.khartec.waltz.data.application.ApplicationIdSelectorFactory;
import com.khartec.waltz.model.EntityKind;
import com.khartec.waltz.model.EntityReference;
import com.khartec.waltz.model.IdSelectionOptions;
import com.khartec.waltz.schema.tables.AttestationInstance;
import com.khartec.waltz.web.json.AttestationStatus;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import spark.Request;

import java.sql.Timestamp;
import java.util.Optional;

import static com.khartec.waltz.common.Checks.checkNotNull;
import static com.khartec.waltz.schema.tables.Application.APPLICATION;
import static com.khartec.waltz.schema.tables.AttestationInstance.ATTESTATION_INSTANCE;
import static com.khartec.waltz.schema.tables.AttestationInstanceRecipient.ATTESTATION_INSTANCE_RECIPIENT;
import static com.khartec.waltz.schema.tables.AttestationRun.ATTESTATION_RUN;
import static com.khartec.waltz.web.WebUtilities.*;
import static java.lang.String.format;
import static spark.Spark.get;
import static spark.Spark.post;


@Service
public class AttestationExtractor extends DirectQueryBasedDataExtractor {

    private final ApplicationIdSelectorFactory applicationIdSelectorFactory = new ApplicationIdSelectorFactory();


    public AttestationExtractor(DSLContext dsl) {
        super(dsl);
    }


    @Override
    public void register() {
        registerExtractForRun(mkPath(
                "data-extract",
                "attestation",
                ":id"));
        registerExtractForAttestedEntityKindAndSelector(mkPath(
                "data-extract",
                "attestations",
                ":kind"));
    }


    private void registerExtractForAttestedEntityKindAndSelector(String path) {
        post(path, (request, response) -> {
            IdSelectionOptions idSelectionOptions = readIdSelectionOptionsFromBody(request);
            EntityReference entityReference = idSelectionOptions.entityReference();
            EntityKind kind = getKind(request);
            Optional<Integer> year = getYearParam(request);
            Optional<String> lifecycle = getLifecycleParam(request);
            AttestationStatus status = getStatusParam(request);

            String fileName = format(
                    "%s-for-%s-%s-%s",
                    status,
                    entityReference.kind().name().toLowerCase(),
                    entityReference.id(),
                    kind.name().toLowerCase());

            Select<Record1<Long>> appSelector = applicationIdSelectorFactory.apply(idSelectionOptions);
            SelectConditionStep<Record> qry = mkQueryForReportingAttestationsByKindAndSelector(
                    appSelector,
                    kind,
                    year,
                    lifecycle,
                    status);

            return writeExtract(
                    fileName,
                    qry,
                    request,
                    response);
        });
    }


    private SelectConditionStep<Record> mkQueryForReportingAttestationsByKindAndSelector(Select<Record1<Long>> appIds,
                                                                                         EntityKind kind,
                                                                                         Optional<Integer> year,
                                                                                         Optional<String> lifecycle,
                                                                                         AttestationStatus status) {

        AttestationInstance latestAttestationInstance = ATTESTATION_INSTANCE.as("latestAttestationInstance");
        AttestationInstance attestationInstanceForPerson= ATTESTATION_INSTANCE.as("attestationInstanceForPerson");

        Field<Long> latestAttestationParentId = latestAttestationInstance.PARENT_ENTITY_ID.as("parent_id");
        Field<Timestamp> latestAttestationAt = DSL.max(latestAttestationInstance.ATTESTED_AT).as("latest_attested_at");

        SelectHavingStep<Record2<Long, Timestamp>> latestAttestation = dsl
                .selectDistinct(
                        latestAttestationParentId,
                        latestAttestationAt)
                .from(latestAttestationInstance)
                .innerJoin(ATTESTATION_RUN).on(latestAttestationInstance.ATTESTATION_RUN_ID.eq(ATTESTATION_RUN.ID))
                .where(latestAttestationInstance.PARENT_ENTITY_KIND.eq(EntityKind.APPLICATION.name())
                .and(ATTESTATION_RUN.ATTESTED_ENTITY_KIND.eq(kind.name())))
                .groupBy(latestAttestationInstance.PARENT_ENTITY_ID);

        Field<Long> entityPersonIsAttesting = attestationInstanceForPerson.PARENT_ENTITY_ID.as("entityPersonIsAttesting");

        SelectOnConditionStep<Record3<String, Timestamp, Long>> peopleToAttest = dsl
                .select(attestationInstanceForPerson.ATTESTED_BY,
                        attestationInstanceForPerson.ATTESTED_AT,
                        entityPersonIsAttesting)
                .from(attestationInstanceForPerson)
                .innerJoin(latestAttestation)
                .on(attestationInstanceForPerson.PARENT_ENTITY_ID.eq(latestAttestationParentId))
                .and(attestationInstanceForPerson.ATTESTED_AT.eq(latestAttestationAt));

        Condition yearCondition = year
                .map(y -> DSL.year((peopleToAttest.field(attestationInstanceForPerson.ATTESTED_AT))).eq(year.get()))
                .orElse(DSL.trueCondition());

        Condition lifecycleCondition = lifecycle
                .map(l -> APPLICATION.LIFECYCLE_PHASE.eq(lifecycle.get()))
                .orElse(DSL.trueCondition());

        Condition statusCondition = status == AttestationStatus.NEVER_ATTESTED
                ? peopleToAttest.field(attestationInstanceForPerson.ATTESTED_AT).isNull()
                : peopleToAttest.field(attestationInstanceForPerson.ATTESTED_AT).isNotNull();

        return dsl
                .select(APPLICATION.NAME.as("Name"),
                        APPLICATION.ASSET_CODE.as("Asset Code"),
                        APPLICATION.KIND.as("Kind"),
                        APPLICATION.BUSINESS_CRITICALITY.as("Business Criticality"),
                        APPLICATION.LIFECYCLE_PHASE.as("Lifecycle Phase"))
                .select(peopleToAttest.field(attestationInstanceForPerson.ATTESTED_BY).as("Last Attested By"),
                        peopleToAttest.field(attestationInstanceForPerson.ATTESTED_AT).as("Last Attested At"))
                .from(APPLICATION)
                .leftJoin(peopleToAttest)
                .on(APPLICATION.ID.eq(entityPersonIsAttesting))
                .where(APPLICATION.ID.in(appIds))
                .and(lifecycleCondition)
                .and(yearCondition)
                .and(statusCondition);
    }


    private void registerExtractForRun(String path) {
        get(path, (request, response) -> {
            long runId = getId(request);

            String runName = dsl
                    .select(ATTESTATION_RUN.NAME)
                    .from(ATTESTATION_RUN)
                    .where(ATTESTATION_RUN.ID.eq(runId))
                    .fetchOne(ATTESTATION_RUN.NAME);

            checkNotNull(runName, "AttestationRun cannot be null");
            String suggestedFilename = runName
                    .replace(".", "-")
                    .replace(" ", "-")
                    .replace(",", "-");

            return writeExtract(
                    suggestedFilename,
                    prepareExtractQuery(runId),
                    request,
                    response);
        });
    }


    private SelectConditionStep<Record> prepareExtractQuery(long runId) {
        return dsl
                .select(APPLICATION.NAME.as("Application"),
                        APPLICATION.ASSET_CODE.as("External Id"))
                .select(ATTESTATION_RUN.ATTESTED_ENTITY_KIND.as("Attesting Kind"),
                        ATTESTATION_RUN.ATTESTED_ENTITY_ID.as("Attesting Kind Id"))
                .select(ATTESTATION_INSTANCE.ID.as("Attestation Id"),
                        ATTESTATION_INSTANCE.ATTESTED_BY.as("Attested By"),
                        ATTESTATION_INSTANCE.ATTESTED_AT.as("Attested At"))
                .select(ATTESTATION_INSTANCE_RECIPIENT.USER_ID.as("Recipient"))
                .from(ATTESTATION_INSTANCE)
                .join(ATTESTATION_INSTANCE_RECIPIENT)
                    .on(ATTESTATION_INSTANCE_RECIPIENT.ATTESTATION_INSTANCE_ID.eq(ATTESTATION_INSTANCE.ID))
                .join(ATTESTATION_RUN)
                    .on(ATTESTATION_RUN.ID.eq(ATTESTATION_INSTANCE.ATTESTATION_RUN_ID))
                .join(APPLICATION)
                    .on(APPLICATION.ID.eq(ATTESTATION_INSTANCE.PARENT_ENTITY_ID))
                .where(ATTESTATION_INSTANCE.ATTESTATION_RUN_ID.eq(runId))
                    .and(ATTESTATION_INSTANCE.PARENT_ENTITY_KIND.eq(EntityKind.APPLICATION.name()));
    }


    private Optional<Integer> getYearParam(Request request) {
        String yearVal = request.queryParams("year");
        return Optional
                .ofNullable(yearVal)
                .map(Integer::valueOf);
    }


    private Optional<String> getLifecycleParam(Request request) {
        String lifecycleVal = request.queryParams("lifecycle");
        return Optional
                .ofNullable(lifecycleVal)
                .map(String::valueOf);
    }


    private AttestationStatus getStatusParam(Request request) {
        return AttestationStatus.valueOf(request.queryParams("status"));
    }

}
