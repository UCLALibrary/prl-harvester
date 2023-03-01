<script setup>
import { reactive, computed } from "vue"
import InstitutionItem from "./InstitutionItem.vue"

const state = reactive({ institutions: [], jobs: {} })
const sortedInstitutions = computed(() => state.institutions.slice().sort((a, b) => (a.name < b.name ? -1 : 1)))
const hasInstitutions = computed(() => state.institutions.length > 0)

fetch("/institutions")
    .then((response) => response.json())
    .then((institutions) => {
        state.institutions = institutions
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
    <ol v-if="hasInstitutions">
        <li v-for="institution in sortedInstitutions" :key="institution.id">
            <InstitutionItem v-bind="institution" :jobs="state.jobs[institution.id] || []" />
        </li>
    </ol>
    <p v-else>No institutions yet!</p>
</template>

<style scoped>
ol {
    display: grid;
    gap: 2rem;
}

li {
    list-style-type: none;
}
</style>
