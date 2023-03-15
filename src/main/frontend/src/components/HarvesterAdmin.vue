<script setup>
import { reactive, ref, computed, provide } from "vue"
import InstitutionItem from "./InstitutionItem.vue"

// State that must be kept in sync with the back-end
const state = reactive({ institutions: {}, jobs: {} })
const hasInstitutions = computed(() => Object.keys(state.institutions).length > 0)
const sortedInstitutions = computed(() => Object.values(state.institutions).sort((a, b) => (a.name < b.name ? -1 : 1)))

// State exclusive to the front-end
const displayInstitutionForm = ref(false)
const institutionToAddOrUpdate = ref({})
const institutionToRemove = ref()
const actionResultAlert = ref()

// Initialize application state

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

provide("setInstitutionToUpdate", setInstitutionToUpdate)
provide("setInstitutionToRemove", setInstitutionToRemove)

/**
 * Sets the component state that shows or hides the dialog with a form for the user to add or update an institution.
 */
function toggleDisplayInstitutionForm() {
    // If the form was rendered in update mode and is being hidden, clear it out
    if (displayInstitutionForm.value && institutionToAddOrUpdate.value.id !== undefined) {
        setInstitutionToAddOrUpdate({})
    }

    displayInstitutionForm.value = !displayInstitutionForm.value
}

/**
 * Sets the component state that is used to populate the institution form.
 *
 * @param {Object} anInstitution The institution to add
 */
function setInstitutionToAddOrUpdate(anInstitution) {
    institutionToAddOrUpdate.value = anInstitution
}

/**
 * Adds an institution.
 *
 * @param {Object} anInstitution The institution to add
 */
async function addInstitution(anInstitution) {
    const response = await fetch("/institutions", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(anInstitution),
    })

    if (response.status === 201) {
        const responseBody = await response.json()

        state.institutions[responseBody.id] = responseBody

        actionResultAlert.value = {
            color: "success",
            message: "Institution added successfully",
        }
    } else {
        actionResultAlert.value = {
            color: "error",
            message: `Institution add failed: HTTP ${response.status} (${response.statusText})`,
        }
    }

    // Clear the form and hide it
    setInstitutionToAddOrUpdate({})
    toggleDisplayInstitutionForm()
}

/**
 * Shows or hides the dialog with a form for the user to update the institution.
 *
 * @param {Number} anInstitutionID The ID of the institution to update
 */
function setInstitutionToUpdate(anInstitutionID) {
    // Since the form is bound to `institutionToAddOrUpdate`, and we don't want to modify `state`: copy the object
    const copyOfInstitution = Object.assign({}, state.institutions[anInstitutionID])

    setInstitutionToAddOrUpdate(copyOfInstitution)

    // Since the institution form is being reused for add and update, need to toggle its display manually
    toggleDisplayInstitutionForm()
}

/**
 * Updates an institution.
 *
 * @param {Object} anInstitution The institution to update
 */
async function updateInstitution(anInstitution) {
    const response = await fetch(`/institutions/${anInstitution.id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(anInstitution),
    })

    if (response.status === 200) {
        const responseBody = await response.json()

        state.institutions[responseBody.id] = responseBody

        actionResultAlert.value = {
            color: "success",
            message: "Institution updated successfully",
        }
    } else {
        actionResultAlert.value = {
            color: "error",
            message: `Institution update failed: HTTP ${response.status} (${response.statusText})`,
        }
    }

    // Clear the form and hide it
    setInstitutionToAddOrUpdate({})
    toggleDisplayInstitutionForm()
}

/**
 * Sets the component state that shows or hides the dialog for the user to confirm institution removal.
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
</script>

<template>
    <v-btn color="primary" variant="outlined" @click="toggleDisplayInstitutionForm">Add Institution</v-btn>

    <ol v-if="hasInstitutions">
        <li v-for="institution in sortedInstitutions" :key="institution.id">
            <InstitutionItem v-bind="institution" :jobs="state.jobs[institution.id] || []" />
        </li>
    </ol>
    <p v-else>No institutions yet!</p>

    <v-dialog v-model="displayInstitutionForm" width="768">
        <v-card>
            <v-form>
                <v-text-field v-if="institutionToAddOrUpdate.id" label="ID" v-model="institutionToAddOrUpdate.id" disabled></v-text-field>
                <v-text-field label="Name" v-model="institutionToAddOrUpdate.name" required></v-text-field>
                <v-textarea label="Description" v-model="institutionToAddOrUpdate.description" required></v-textarea>
                <v-text-field label="Location" v-model="institutionToAddOrUpdate.location" required></v-text-field>
                <v-text-field label="Website" type="url" v-model="institutionToAddOrUpdate.website" required></v-text-field>
                <v-text-field label="Email" type="email" v-model="institutionToAddOrUpdate.email"></v-text-field>
                <v-text-field label="Phone" type="tel" v-model="institutionToAddOrUpdate.phone"></v-text-field>
                <v-text-field label="Web Contact" type="url" v-model="institutionToAddOrUpdate.webContact"></v-text-field>
            </v-form>
            <v-card-actions class="d-flex justify-center align-baseline">
                <v-btn v-if="institutionToAddOrUpdate.id === undefined" color="primary" variant="outlined" width="auto" @click="addInstitution(institutionToAddOrUpdate)">Add</v-btn>
                <v-btn v-else color="primary" variant="outlined" width="auto" @click="updateInstitution(institutionToAddOrUpdate)">Update</v-btn>
                <v-btn variant="outlined" width="auto" @click="toggleDisplayInstitutionForm">Cancel</v-btn>
            </v-card-actions>
        </v-card>
    </v-dialog>

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
