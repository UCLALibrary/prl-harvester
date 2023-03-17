<script setup>
import { reactive } from "vue"
import { StatusCodes } from "http-status-codes"

import { version } from "../package.json"
import HarvesterAdmin from "./components/HarvesterAdmin.vue"

const state = reactive({ institutions: {}, jobs: {} })

/**
 * Sends an addInstitution request and updates the front-end state according to the response.
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
 * Sends an updateInstitution request and updates the front-end state according to the response.
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
 * Sends an removeInstitution request and updates the front-end state according to the response.
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
                state.jobs[job.institutionID] = []
            }

            state.jobs[job.institutionID].push(job)
        })
    })
</script>

<template>
    <header>
        <h1>PRL Harvester Admin</h1>
    </header>

    <main>
        <HarvesterAdmin
            v-bind="state"
            :sendAddInstitutionRequest="sendAddInstitutionRequest"
            :sendUpdateInstitutionRequest="sendUpdateInstitutionRequest"
            :sendRemoveInstitutionRequest="sendRemoveInstitutionRequest"
        />
    </main>

    <footer>PRL Harvester Admin v{{ version }}</footer>
</template>

<style scoped>
header {
    padding-bottom: 2rem;
}

footer {
    font-style: italic;
    padding-top: 2rem;
    text-align: right;
}
</style>
