<script setup>
import { reactive, provide } from "vue"
import { version } from "../package.json"
import HarvesterAdmin from "./components/HarvesterAdmin.vue"

const state = reactive({ institutions: {}, jobs: {} })

/**
 * Sets the state. FIXME
 *
 * @param {Number} anInstitutionID The ID of the institution
 * @param {Object} anInstitution The institution
 */
function stateSetInstitution(anInstitutionID, anInstitution) {
    state.institutions[anInstitutionID] = anInstitution
}

/**
 * Deletes the state. FIXME
 *
 * @param {Number} anInstitutionID The ID of the institution
 */
function stateDeleteInstitution(anInstitutionID) {
    delete state.jobs[anInstitutionID]
    delete state.institutions[anInstitutionID]
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

provide("stateSetInstitution", stateSetInstitution)
provide("stateDeleteInstitution", stateDeleteInstitution)
</script>

<template>
    <header>
        <h1>PRL Harvester Admin</h1>
    </header>

    <main>
        <HarvesterAdmin v-bind="state" />
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
