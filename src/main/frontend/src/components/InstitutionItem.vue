<script setup>
import { computed, inject } from "vue"
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
    jobs: { type: Array, required: true },
})
const setInstitutionToUpdate = inject("setInstitutionToUpdate")
const setInstitutionToRemove = inject("setInstitutionToRemove")
const sortedJobs = computed(() => {
    return props.jobs.slice().sort((a, b) => {
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
        <v-card-subtitle class="ma-2 pa-2 text-subtitle-1">Jobs</v-card-subtitle>
        <v-card-text>
            <v-table v-if="jobs.length > 0" class="harvest-jobs">
                <thead>
                    <tr>
                        <th>Repository Base URL</th>
                        <th>Sets</th>
                        <th>Metadata Format</th>
                        <th>Schedule</th>
                        <th>Last Successful Run</th>
                    </tr>
                </thead>
                <tbody>
                    <JobItem v-for="job in sortedJobs" v-bind="job" :key="job.id" />
                </tbody>
            </v-table>
            <p v-else>No jobs yet!</p>
        </v-card-text>
        <v-card-actions class="ma-2 pa-2">
            <v-btn color="primary" variant="outlined" @click="setInstitutionToUpdate(id)">Edit</v-btn>
            <v-btn color="red" variant="outlined" @click="setInstitutionToRemove(id)">Remove {{ `"${name}"` }}</v-btn>
        </v-card-actions>
    </v-card>
</template>

<style scoped></style>
