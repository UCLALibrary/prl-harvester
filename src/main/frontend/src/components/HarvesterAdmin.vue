<script setup>
import { ref, computed } from "vue"
import { StatusCodes } from "http-status-codes"
import InstitutionItem from "./InstitutionItem.vue"

const props = defineProps({
    institutions: { type: Object, required: true },
    jobs: { type: Object, required: true },
    sendAddInstitutionRequest: { type: Function },
    sendUpdateInstitutionRequest: { type: Function },
    sendRemoveInstitutionRequest: { type: Function },
})
const hasInstitutions = computed(() => Object.keys(props.institutions).length > 0)
const sortedInstitutions = computed(() => Object.values(props.institutions).sort((a, b) => (a.name < b.name ? -1 : 1)))

const displayInstitutionForm = ref(false)
const institutionToAddOrUpdate = ref({})
const institutionToRemove = ref()
const actionResultAlert = ref()

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
    const response = await props.sendAddInstitutionRequest(anInstitution)

    if (response.status === StatusCodes.CREATED) {
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
function selectInstitutionToUpdate(anInstitutionID) {
    // Since the form is bound to `institutionToAddOrUpdate`, and we don't want to modify `state`: copy the object
    const copyOfInstitution = Object.assign({}, props.institutions[anInstitutionID])

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
    const response = await props.sendUpdateInstitutionRequest(anInstitution)

    if (response.status === StatusCodes.OK) {
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
function selectInstitutionToRemove(anInstitutionID) {
    institutionToRemove.value = anInstitutionID
}

/**
 * Removes an institution.
 *
 * @param {Number} anInstitutionID The ID of the institution to remove
 */
async function removeInstitution(anInstitutionID) {
    const response = await props.sendRemoveInstitutionRequest(anInstitutionID)

    if (response.status === StatusCodes.NO_CONTENT) {
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

    selectInstitutionToRemove(undefined)
}
</script>

<template>
    <v-btn
        color="primary"
        variant="outlined"
        @click="toggleDisplayInstitutionForm"
        class="propose-add-institution ma-4"
    >
        Add Institution
    </v-btn>

    <v-list v-if="hasInstitutions" lines="one">
        <v-list-item v-for="institution in sortedInstitutions" :key="institution.id">
            <InstitutionItem
                v-bind="institution"
                :jobs="props.jobs[institution.id] || []"
                :selectInstitutionToUpdate="selectInstitutionToUpdate"
                :selectInstitutionToRemove="selectInstitutionToRemove"
            />
        </v-list-item>
    </v-list>
    <v-card v-else variant="plain" width="auto">
        <v-card-text max-width="auto">No institutions yet!</v-card-text>
    </v-card>

    <!-- A conditionally-rendered form for adding or updating an institution -->
    <v-dialog v-model="displayInstitutionForm" width="768">
        <v-card>
            <v-form>
                <v-text-field
                    v-if="institutionToAddOrUpdate.id"
                    label="ID"
                    v-model="institutionToAddOrUpdate.id"
                    disabled
                ></v-text-field>
                <v-text-field label="Name" v-model="institutionToAddOrUpdate.name" required></v-text-field>
                <v-textarea label="Description" v-model="institutionToAddOrUpdate.description" required></v-textarea>
                <v-text-field label="Location" v-model="institutionToAddOrUpdate.location" required></v-text-field>
                <v-text-field
                    label="Website"
                    type="url"
                    v-model="institutionToAddOrUpdate.website"
                    required
                ></v-text-field>
                <v-text-field label="Email" type="email" v-model="institutionToAddOrUpdate.email"></v-text-field>
                <v-text-field label="Phone" type="tel" v-model="institutionToAddOrUpdate.phone"></v-text-field>
                <v-text-field
                    label="Web Contact"
                    type="url"
                    v-model="institutionToAddOrUpdate.webContact"
                ></v-text-field>
            </v-form>
            <v-card-actions class="d-flex justify-center align-baseline">
                <v-btn
                    v-if="institutionToAddOrUpdate.id === undefined"
                    color="primary"
                    variant="outlined"
                    width="auto"
                    class="confirm-add-institution"
                    @click="addInstitution(institutionToAddOrUpdate)"
                >
                    Save
                </v-btn>
                <v-btn
                    v-else
                    color="primary"
                    variant="outlined"
                    width="auto"
                    class="confirm-update-institution"
                    @click="updateInstitution(institutionToAddOrUpdate)"
                >
                    Save
                </v-btn>
                <v-btn
                    variant="outlined"
                    width="auto"
                    @click="toggleDisplayInstitutionForm"
                    class="cancel-add-or-update-institution"
                >
                    Cancel
                </v-btn>
            </v-card-actions>
        </v-card>
    </v-dialog>

    <!-- A conditionally-rendered confirmation prompt for removing an institution -->
    <v-dialog v-if="institutionToRemove" v-model="institutionToRemove" width="auto">
        <v-card>
            <v-card-text>
                <p>
                    This action will remove
                    <strong>
                        {{ props.institutions[institutionToRemove] && props.institutions[institutionToRemove].name }}
                    </strong>
                    .
                </p>
                <p v-if="props.jobs[institutionToRemove]">
                    This will also remove its
                    {{ props.jobs[institutionToRemove] && props.jobs[institutionToRemove].length }}
                    jobs and all harvested items.
                </p>
            </v-card-text>
            <v-card-actions class="d-flex justify-center align-baseline">
                <v-btn
                    color="red"
                    variant="outlined"
                    @click="removeInstitution(institutionToRemove)"
                    width="auto"
                    class="confirm-remove-institution"
                >
                    Remove
                </v-btn>
                <v-btn
                    variant="outlined"
                    width="auto"
                    @click="selectInstitutionToRemove(undefined)"
                    class="cancel-remove-institution"
                >
                    Cancel
                </v-btn>
            </v-card-actions>
        </v-card>
    </v-dialog>

    <!-- Information on the success or failure of the most recent HTTP request to the back-end -->
    <v-snackbar v-if="actionResultAlert" v-model="actionResultAlert" :color="actionResultAlert.color">
        {{ actionResultAlert.message }}
    </v-snackbar>
</template>

<style scoped></style>
