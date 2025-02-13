<script>

    import _ from "lodash";
    import {customEnvironmentStore} from "../../../svelte-stores/custom-environment-store";
    import {customEnvironmentUsageStore} from "../../../svelte-stores/custom-environment-usage-store";
    import Icon from "../../../common/svelte/Icon.svelte";

    import {mode, Modes, selectedEnvironment} from "./editingCustomEnvironmentState";
    import UsagePanel from "./UsagePanel.svelte";
    import EnvironmentRegistration from "./EnvironmentRegistration.svelte";
    import NoData from "../../../common/svelte/NoData.svelte";

    export let primaryEntityRef;

    function enrichEnvWithUsages(environments, usages) {
        const usagesByEnvironment = _.groupBy(usages, d => d.usage.customEnvironmentId);
        return _.chain(environments)
            .map(e => Object.assign(
                {},
                e,
                {usageInfo: _.get(usagesByEnvironment, e.id, [])}))
            .orderBy(['groupName', 'name'])
            .value()
    }

    function addNewEnvironment() {
        selectedEnvironment.set({
            name: null,
            description: null,
            owningEntity: primaryEntityRef,
            externalId: null,
            group: null
        })
        return mode.set(Modes.EDIT);
    }

    function tableView(){
        mode.set(Modes.TABLE);
    }

    function deleteEnvironment(environment) {
        return customEnvironmentStore.remove(environment);
    }

    function isExpanded(environment){
        return _.includes(expandedEnvironmentIds, environment.id)
    }

    function toggleDetailView(environment) {
        expandedEnvironmentIds = isExpanded(environment)
            ? _.without(expandedEnvironmentIds, environment.id)
            : _.concat(expandedEnvironmentIds, [environment.id])
    }

    function cancel() {
        mode.set(Modes.TABLE)
        selectedEnvironment.set(null);
    }

    let expandedEnvironmentIds = [];
    let loadEnvironmentsCall = customEnvironmentStore.findByOwningEntityRef(primaryEntityRef);
    let loadEnvironmentUsagesCall = customEnvironmentUsageStore.findUsageInfoByOwningEntityRef(primaryEntityRef);

    $: customEnvironments = _
        .chain($loadEnvironmentsCall.data)
        .map(e => Object.assign({}, e, {expanded: true}))
        .orderBy(['groupName', 'name'])
        .value();

    $: customEnvironmentUsageInfo = $loadEnvironmentUsagesCall.data;

    $: environmentDataWithUsages = enrichEnvWithUsages(customEnvironments, customEnvironmentUsageInfo);

    $: environmentUsageCounts = _.countBy(customEnvironmentUsageInfo, d => d.usage.customEnvironmentId);
    $: environmentUsagesById = _.groupBy(customEnvironmentUsageInfo, d => d.usage.customEnvironmentId);

</script>

<p class="help-block">
Custom environments can be used to group servers and databases used by this application.
    Assets are not limited to those owned by this application. Click on an environment to view and edit assets or
    <button class="btn btn-skinny"
            on:click={addNewEnvironment}>
        register a new custom environment
    </button>
    here.
</p>

{#if $mode === Modes.EDIT}
    <EnvironmentRegistration primaryEntityRef={primaryEntityRef}
                             onCancel={cancel}/>
{:else if $mode === Modes.DETAIL}
    <UsagePanel onCancel={cancel}/>
{:else}
    {#if customEnvironments.length === 0}
        <NoData>
            No environments have been created for this
            <span>{primaryEntityRef.kind.toLowerCase()}</span>
        </NoData>
    {:else}
        <table class="table table-condensed small">
            <thead>
                <th  width="5%"></th>
                <th width="20%">Group</th>
                <th width="30%">Name</th>
                <th width="25%">Description</th>
                <th width="20%"># Linked Entities</th>
            </thead>
            <tbody>
            {#each customEnvironments as environment}
                <tr class="clickable"
                    on:click={() => toggleDetailView(environment)}>
                    <td>
                        <Icon name={_.includes(expandedEnvironmentIds, environment.id) ? "caret-down" : "caret-right"}/>
                    </td>
                    <td>{environment.groupName || "-"}</td>
                    <td>{environment.name}</td>
                    <td>{environment.description || "-"}</td>
                    <td>{_.get(environmentUsageCounts, [environment.id], 0)}</td>
                </tr>
                {#if _.includes(expandedEnvironmentIds, environment.id)}
                    <tr>
                        <td></td>
                        <td colspan="5">
                            <UsagePanel doCancel={cancel}
                                         {primaryEntityRef}
                                         environment={environment}
                                         usages={_.get(environmentUsagesById, [environment.id], [])}/>
                        </td>
                    </tr>
                {/if}
            {/each}
            </tbody>
        </table>
    {/if}
{/if}

