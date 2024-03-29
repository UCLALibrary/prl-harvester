<script setup>
import { computed } from "vue"
import JobItem from "./JobItem.vue"

const props = defineProps({
    id: { type: Number, required: true },
    name: { type: String, required: true },
    description: { type: String, required: true },
    location: { type: String, required: true },
    email: { type: String },
    phone: { type: String },
    webContact: { type: String },
    website: { type: String, required: true },
    jobs: { type: Object, required: true },
    selectInstitutionToUpdate: { type: Function },
    selectInstitutionToRemove: { type: Function },
    toggleDisplayJobForm: { type: Function },
    selectJobToUpdate: { type: Function },
    selectJobToRemove: { type: Function },
})
const sortedJobs = computed(() => {
    return Object.values(props.jobs).sort((a, b) => {
        const hostnameA = new URL(a.repositoryBaseURL).hostname
        const hostnameB = new URL(b.repositoryBaseURL).hostname

        if (hostnameA < hostnameB) {
            return -1
        } else if (hostnameA > hostnameB) {
            return 1
        } else {
            const sortedSetsA = a.sets.slice().sort()
            const sortedSetsB = b.sets.slice().sort()

            for (let i = 0; ; i++) {
                if (i === sortedSetsA.length) {
                    return -1
                } else if (i === sortedSetsB.length) {
                    return 1
                } else {
                    if (sortedSetsA[i] < sortedSetsB[i]) {
                        return -1
                    } else if (sortedSetsA[i] > sortedSetsB[i]) {
                        return 1
                    }
                }
            }
        }
    })
})
const headingIdentifier = computed(() => props.name.toLowerCase().replaceAll(" ", "-"))
</script>

<template>
    <v-card :id="`${headingIdentifier}`" variant="outlined">
        <!-- First, a rendering of the institution metadata -->
        <v-card-title class="text-h">{{ name }}</v-card-title>
        <v-row no-gutters>
            <v-col cols="4">
                <v-list>
                    <v-list-item density="compact" prepend-icon="mdi-map-marker">
                        <v-list-item-subtitle>{{ location }}</v-list-item-subtitle>
                    </v-list-item>
                    <v-list-item density="compact" prepend-icon="mdi-web">
                        <v-list-item-subtitle>
                            <a :href="website">{{ website }}</a>
                        </v-list-item-subtitle>
                    </v-list-item>
                    <v-list-item density="compact" prepend-icon="mdi-email">
                        <v-list-item-subtitle>
                            <a v-if="email" :href="`mailto:${email}`">{{ email }}</a>
                            <span v-else class="optional-field-placeholder">(not provided)</span>
                        </v-list-item-subtitle>
                    </v-list-item>
                    <v-list-item density="compact" prepend-icon="mdi-phone">
                        <v-list-item-subtitle>
                            <a v-if="phone" :href="`tel:${phone}`">{{ phone }}</a>
                            <span v-else class="optional-field-placeholder">(not provided)</span>
                        </v-list-item-subtitle>
                    </v-list-item>
                    <v-list-item density="compact" prepend-icon="mdi-link">
                        <v-list-item-subtitle>
                            <a v-if="webContact" :href="webContact">{{ webContact }}</a>
                            <span v-else class="optional-field-placeholder">(not provided)</span>
                        </v-list-item-subtitle>
                    </v-list-item>
                </v-list>
            </v-col>
            <v-col>
                <v-sheet class="ma-2 pa-2">
                    <blockquote>{{ description }}</blockquote>
                </v-sheet>
            </v-col>
        </v-row>
        <v-divider class="border-opacity-25"></v-divider>

        <!-- Next, a rendering of the associated jobs (if any) -->
        <v-card-subtitle class="ma-2 pa-2 text-subtitle-1">Jobs</v-card-subtitle>
        <v-card-actions>
            <v-btn color="primary" variant="outlined" @click="toggleDisplayJobForm(id)" class="propose-add-job ma-2">
                Add Job
            </v-btn>
        </v-card-actions>
        <v-card-text>
            <v-container v-if="sortedJobs.length > 0" class="harvest-jobs">
                <v-row justify="left">
                    <v-col v-for="job in sortedJobs" :key="job.id" cols="auto">
                        <JobItem
                            v-bind="job"
                            :selectJobToUpdate="selectJobToUpdate"
                            :selectJobToRemove="selectJobToRemove" />
                    </v-col>
                </v-row>
            </v-container>
            <p v-else>No jobs yet!</p>
        </v-card-text>
        <v-card-actions class="ma-2 pa-2">
            <v-btn
                color="primary"
                variant="outlined"
                @click="selectInstitutionToUpdate(id)"
                class="propose-edit-institution">
                Edit
            </v-btn>
            <v-btn
                color="red"
                variant="outlined"
                @click="selectInstitutionToRemove(id)"
                class="propose-remove-institution">
                Remove {{ `"${name}"` }}
            </v-btn>
        </v-card-actions>
    </v-card>
</template>

<style scoped></style>
