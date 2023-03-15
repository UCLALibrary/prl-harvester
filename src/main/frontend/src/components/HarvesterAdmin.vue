<script setup>
import { reactive, ref, computed, provide } from "vue"
import InstitutionItem from "./InstitutionItem.vue"

const state = reactive({ institutions: {}, jobs: {} })
const institutionToRemove = ref()
const actionResultAlert = ref()
const sortedInstitutions = computed(() => Object.values(state.institutions).sort((a, b) => (a.name < b.name ? -1 : 1)))
const hasInstitutions = computed(() => Object.keys(state.institutions).length > 0)

fetch("/institutions")
    .then((response) => response.json())
    .then((institutions) => {
        institutions.forEach((institution) => {
            state.institutions[institution.id] = institution
        })
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

/**
 * Sets the component state that renders a dialog for the user to confirm institution removal.
 *
 * @param {Number} anInstitutionID The ID of the institution to remove
 */
function setInstitutionToRemove(anInstitutionID) {
    institutionToRemove.value = anInstitutionID
}

/**
 * Removes an institution.
 *
 * @param {Number} anInstitutionID The ID of the institution to remove
 */
async function removeInstitution(anInstitutionID) {
    const response = await fetch(`/institutions/${anInstitutionID}`, { method: "DELETE" })

    if (response.status === 204) {
        delete state.jobs[anInstitutionID]
        delete state.institutions[anInstitutionID]

        actionResultAlert.value = {
            color: "success",
            message: "Institution removed successfully",
        }
    } else {
        actionResultAlert.value = {
            color: "error",
            message: `Institution remove failed: HTTP ${response.status} (${response.statusText})`,
        }
    }

    setInstitutionToRemove(undefined)
}

provide("setInstitutionToRemove", setInstitutionToRemove)
</script>

<template>
    <ol v-if="hasInstitutions">
        <li v-for="institution in sortedInstitutions" :key="institution.id">
            <InstitutionItem v-bind="institution" :jobs="state.jobs[institution.id] || []" />
        </li>
    </ol>
    <p v-else>No institutions yet!</p>

    <v-dialog v-if="institutionToRemove" v-model="institutionToRemove" width="auto">
        <v-card>
            <v-card-text>
                <p>
                    This action will remove <strong>{{ state.institutions[institutionToRemove].name }}</strong
                    >.
                </p>
                <p v-if="state.jobs[institutionToRemove]">
                    This will also remove its {{ state.jobs[institutionToRemove].length }} jobs and all harvested items.
                </p>
            </v-card-text>
            <v-card-actions class="d-flex justify-center align-baseline">
                <v-btn color="red" variant="outlined" @click="removeInstitution(institutionToRemove)" width="auto"
                    >Remove</v-btn
                >
                <v-btn variant="outlined" width="auto" @click="setInstitutionToRemove(undefined)">Cancel</v-btn>
            </v-card-actions>
        </v-card>
    </v-dialog>

    <v-snackbar v-if="actionResultAlert" v-model="actionResultAlert" :color="actionResultAlert.color">
        {{ actionResultAlert.message }}
    </v-snackbar>
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
