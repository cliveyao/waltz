import _ from "lodash";
import {termSearch} from "../../../common";

/**
 * returns empty list if given nothing.
 *
 * @param usages
 * @returns {*}
 *
 */
export function groupUsagesByApplication(usages) {
    return _
        .chain(usages)
        .groupBy(u => u.owningApplication.id)
        .map((v, k) => {
            const application = _.head(v).owningApplication;
            const [serverUsages, databaseUsages] = _
                .chain(v)
                .partition(d => d.usage.entityReference.kind === "SERVER_USAGE")
                .value();
            return Object.assign({}, {application, serverUsages, databaseUsages})
        })
        .value();
}

export function combineServerData(servers, usages) {
    if (_.isEmpty(servers) || _.isEmpty(usages)) {
        return [];
    } else {
        const serversById = _.keyBy(servers, d => d.id);
        return _
            .chain(usages)
            .map(u => Object.assign({}, {serverUsage: u, serverInformation: serversById[u.serverId]}))
            .filter(s => s.serverInformation != null)
            .value();
    }
}

export function doAssetSearch(qry, assets, omitIds, searchFields) {
    const searchResults = termSearch(assets, qry, searchFields, d => _.includes(omitIds, d.id));
    return _.take(searchResults, 15);

}