/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016  Khartec Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

function store($http, baseApiUrl) {

    const base = `${baseApiUrl}/survey-instance`;

    const getById = (id) => {
        return $http
            .get(`${base}/id/${id}`)
            .then(result => result.data);
    };

    const findForUser = () => {
        return $http
            .get(`${base}/user`)
            .then(result => result.data);
    };

    const findResponses = (id) => {
        return $http
            .get(`${base}/${id}/responses`)
            .then(result => result.data);
    };

    const saveResponse = (id, questionResponse) => {
        return $http
            .put(`${base}/${id}/response`, questionResponse)
            .then(result => result.data);
    };

    const updateStatus = (id, command) => {
        return $http
            .post(`${base}/${id}/status`, command)
            .then(result => result.data);
    };

    return {
        getById,
        findForUser,
        findResponses,
        saveResponse,
        updateStatus
    };
}


store.$inject = [
    '$http',
    'BaseApiUrl'
];


export default store;