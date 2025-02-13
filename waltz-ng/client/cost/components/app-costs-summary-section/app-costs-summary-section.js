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
import {initialiseData} from "../../../common";

import template from "./app-costs-summary-section.html";
import {CORE_API} from "../../../common/services/core-api-utils";
import * as _ from "lodash";
import {mkSelectionOptions} from "../../../common/selector-utils";
import namedSettings from "../../../system/named-settings";


const bindings = {
    parentEntityRef: "<",
    targetEntityKind: "<?"
};

const initialState = {
    targetEntityKind: 'APPLICATION',
    selectedKind: null,
    costInfo: [],
    visibility: {
        selectKind: false,
        allCosts: false,
        loading: false
    },
    selectedEntity: null,
    exportAllowed: true,
};


function mkColumnDefs(uiGridConstants){
    return [
        {
            field: 'entityReference.name',
            displayName: 'Name',
            width: '30%',
            cellTemplate:`
            <div class="ui-grid-cell-contents">
                 <waltz-entity-link entity-ref="row.entity.entityReference"
                                    icon-placement="right">
                 </waltz-entity-link>
            </div>`
        },{
            field: 'costKind.name',
            displayName: 'Kind',
            width: '20%',
            cellTemplate:`
            <div class="ui-grid-cell-contents">
                 <span ng-bind="row.entity.costKind.name"
                 uib-popover="{{row.entity.costKind.description}}"
                 popover-append-to-body="true"
                 popover-placement="top"
                 popover-popup-delay="300"
                 popover-trigger="mouseenter">
                </span>
            </div>`
        },{
            field: 'year',
            displayName: 'Year',
            width: '10%',
            sort: {
                direction: uiGridConstants.DESC,
                priority: 0,
            },
        },{
            field: 'amount',
            displayName: 'Amount',
            width: '20%',
            headerCellClass: 'waltz-grid-header-right',
            cellTemplate:`
            <div class="ui-grid-cell-contents"
            style="padding-right: 2em">
                 <span class="pull-right">
                    <waltz-currency-amount amount="COL_FIELD">
                    </waltz-currency-amount>
                </span>
            </div>`
        },{
            field: 'provenance',
            displayName: 'Provenance',
            width: '20%'
        }];
}


/**
 * Determines the default cost kind by either finding a
 * cost kind flagged as the default or falling back to the
 * first cost kind if none are flagged.
 *
 * @param costKinds
 * @returns {unknown}
 */
function findDefaultKind(costKinds = []) {
    const defaultKind = _.find(costKinds, d => d.isDefault);
    return defaultKind
        ? defaultKind
        : _.first(costKinds);
}


function mkKindToLatestYearMap(tuples) {
    return _
        .chain(tuples)
        .keyBy(d => d.v1.id)
        .mapValues(d => d.v2)
        .value();
}


function extractOrderedListOfKinds(tuples) {
    return _
        .chain(tuples)
        .map(d => d.v1)
        .orderBy(d => d.name)
        .value();
}


function enrichCostsWithKind(costs, costKinds) {
    const costKindsById = _.keyBy(costKinds, d => d.id);
    return _.map(
        costs,
        d => Object.assign(
            {},
            d,
            {costKind: _.get(costKindsById, [d.costKindId], 'Unknown')}));
}


function controller($q, serviceBroker, uiGridConstants, settingsService) {

    const vm = initialiseData(this, initialState);

    function allowCostExport() {
        settingsService
            .findOrDefault(namedSettings.costExportEnabled, true)
            .then(exportAllowed => {
                vm.exportAllowed = !(exportAllowed === 'false');
            });
    }

    function loadCostKinds() {
        return serviceBroker
            .loadAppData(CORE_API.CostKindStore.findBySelector,
                [vm.targetEntityKind, vm.selector])
            .then(r => {
                // result is tuple of (v1:costKind, v2:latestYear)
                vm.costKinds = extractOrderedListOfKinds(r.data);
                vm.latestYearByKindId = mkKindToLatestYearMap(r.data);
                vm.selectedKind = findDefaultKind(vm.costKinds);
            });
    }

    function loadSummaryForCostKind(){
        return serviceBroker
            .loadViewData(
                CORE_API.CostStore.summariseByCostKindAndSelector,
                [vm.selectedKind.id, vm.targetEntityKind, vm.selector],
                { force: true })
            .then(r => vm.costKindSummary = r.data);
    }

    vm.loadAllCosts = () => {
        vm.visibility.loading = true;
        serviceBroker
            .loadViewData(
                CORE_API.CostStore.findBySelector,
                [vm.targetEntityKind, mkSelectionOptions(vm.parentEntityRef)])
            .then(r => {
                vm.costInfo = enrichCostsWithKind(r.data, vm.costKinds);
                vm.visibility.loading = false;
            });
    };

    vm.$onInit = () => {
        allowCostExport();
        vm.entityCostColumnDefs = mkColumnDefs(uiGridConstants);
        vm.selector = mkSelectionOptions(vm.parentEntityRef);

        loadCostKinds()
            .then(() => loadSummaryForCostKind());
    };

    vm.$onChanges = () => {
        if (vm.selector){
            loadCostKinds()
                .then(() => loadSummaryForCostKind())
        }
    };

    vm.refresh = () => {
        vm.visibility.selectKind = false;
        loadSummaryForCostKind();
        vm.onClearSelectedEntity();
    };

    vm.showAllCosts = () =>  {
        vm.loadAllCosts();
        vm.visibility.allCosts = !vm.visibility.allCosts;
    };

    vm.onSelect = (d) => {
        if (vm.selectedEntity && vm.selectedEntity.entityReference.id === d.entityReference.id){
            vm.onClearSelectedEntity();
        } else {
            vm.selectedEntity = d;
        }
    };

    vm.onClearSelectedEntity = () => {
        vm.selectedEntity = null;
    };
}


controller.$inject = [
    "$q",
    "ServiceBroker",
    "uiGridConstants",
    "SettingsService"
];


const component = {
    template,
    bindings,
    controller
};


export default {
    component,
    id: "waltzAppCostsSummarySection"
};
