<script setup>
import { reactive } from "vue"
import { StatusCodes } from "http-status-codes"

import { version } from "../package.json"
import HarvesterAdmin from "./components/HarvesterAdmin.vue"

/**
 * The state object that should be kept in sync with the back-end state.
 *
 * The institutions map is from institution IDs to institutions. So for example, `state.institutions[1]` retrieves the
 * institution with ID 1.
 *
 * The jobs map is from institutionIDs to a map from job IDs to jobs. So for example, `state.jobs[2][3]` retrieves the
 * job with ID 3 and institutionID 2.
 */
const state = reactive({ institutions: {}, jobs: {} })

/**
 * Sends an addInstitution request and, if successful, updates the state with the result.
 *
 * @param {Object} anInstitution The institution
 * @returns The HTTP response
 */
async function sendAddInstitutionRequest(anInstitution) {
    const response = await fetch("/institutions", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(anInstitution),
    })

    if (response.status === StatusCodes.CREATED) {
        const responseBody = await response.json()

        state.institutions[responseBody.id] = responseBody
    }

    return response
}

/**
 * Sends an updateInstitution request and, if successful, updates the state with the result.
 *
 * @param {Object} anInstitution The institution
 * @returns The HTTP response
 */
async function sendUpdateInstitutionRequest(anInstitution) {
    const response = await fetch(`/institutions/${anInstitution.id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(anInstitution),
    })

    if (response.status === StatusCodes.OK) {
        const responseBody = await response.json()

        state.institutions[responseBody.id] = responseBody
    }

    return response
}

/**
 * Sends an removeInstitution request and, if successful, updates the state with the result.
 *
 * @param {Number} anInstitutionID The ID of the institution
 * @returns The HTTP response
 */
async function sendRemoveInstitutionRequest(anInstitutionID) {
    const response = await fetch(`/institutions/${anInstitutionID}`, { method: "DELETE" })

    if (response.status === StatusCodes.NO_CONTENT) {
        delete state.jobs[anInstitutionID]
        delete state.institutions[anInstitutionID]
    }

    return response
}

/**
 * Sends an addJob request and, if successful, updates the state with the result.
 *
 * @param {Object} aJob A job
 * @returns The HTTP response
 */
async function sendAddJobRequest(aJob) {
    const response = await fetch("/jobs", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(aJob),
    })

    if (response.status === StatusCodes.CREATED) {
        const responseBody = await response.json()

        if (state.jobs[responseBody.institutionID] === undefined) {
            state.jobs[responseBody.institutionID] = {}
        }

        state.jobs[responseBody.institutionID][responseBody.id] = responseBody
    }

    return response
}

/**
 * Sends an updateJob request and, if successful, updates the state with the result.
 *
 * @param {Object} aJob A job
 * @returns The HTTP response
 */
async function sendUpdateJobRequest(aJob) {
    const response = await fetch(`/jobs/${aJob.id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(aJob),
    })

    if (response.status === StatusCodes.OK) {
        const responseBody = await response.json()

        state.jobs[responseBody.institutionID][responseBody.id] = responseBody
    }

    return response
}

/**
 * Sends an removeJob request and, if successful, updates the state with the result.
 *
 * @param {Number} aJobID The ID of the job
 * @param {Number} anInstitutionID The ID of the associated institution
 * @returns The HTTP response
 */
async function sendRemoveJobRequest(aJobID, anInstitutionID) {
    const response = await fetch(`/jobs/${aJobID}`, { method: "DELETE" })

    if (response.status === StatusCodes.NO_CONTENT) {
        delete state.jobs[anInstitutionID][aJobID]
    }

    return response
}

// Fetch data from back-end
fetch("/institutions")
    .then((response) => response.json())
    .then((institutions) => {
        institutions.forEach((institution) => (state.institutions[institution.id] = institution))
    })

fetch("/jobs")
    .then((response) => response.json())
    .then((jobs) => {
        jobs.forEach((job) => {
            if (state.jobs[job.institutionID] === undefined) {
                state.jobs[job.institutionID] = {}
            }

            state.jobs[job.institutionID][job.id] = job
        })
    })
</script>

<template>
    <header>
        <h1>PRL Harvester Admin</h1>
        <form id="login" action="/login" method="post">
            <fieldset>
                <label for="username">Username: </label>
                <input class="login" type="text" name="username" id="username" size="8" />
                <br />
                <label for="password">Password: </label>
                <input class="login" type="password" name="password" id="password" size="8" />
                <br />
                <input type="submit" name="Login" value="Login" />
            </fieldset>
        </form>
    </header>

    <main>
        <HarvesterAdmin
            v-bind="state"
            :sendAddInstitutionRequest="sendAddInstitutionRequest"
            :sendUpdateInstitutionRequest="sendUpdateInstitutionRequest"
            :sendRemoveInstitutionRequest="sendRemoveInstitutionRequest"
            :sendAddJobRequest="sendAddJobRequest"
            :sendUpdateJobRequest="sendUpdateJobRequest"
            :sendRemoveJobRequest="sendRemoveJobRequest" />
    </main>

    <footer>PRL Harvester Admin v{{ version }}</footer>
</template>

<style scoped>
header {
    padding-bottom: 2rem;
}

header form {
    text-align: right;
}

header form fieldset {
    border-width: 0px;
}

header form fieldset input.login {
    background-color: #f9f9f9;
    margin-bottom: 2px;
}

footer {
    font-style: italic;
    padding-top: 2rem;
    text-align: right;
}
</style>
