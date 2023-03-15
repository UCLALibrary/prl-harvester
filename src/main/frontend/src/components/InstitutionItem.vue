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
    <a class="anchor" aria-hidden="true" :href="`#${headingIdentifier}`">&sect;</a>
    <section :id="`${headingIdentifier}`">
        <h2>{{ name }}</h2>
        <table class="institution-metadata">
            <tr>
                <th>Description</th>
                <td>
                    <blockquote>{{ description }}</blockquote>
                </td>
            </tr>
            <tr>
                <th>Location</th>
                <td>{{ location }}</td>
            </tr>
            <tr>
                <th>Email</th>
                <td>
                    <a v-if="email" :href="`mailto:${email}`">{{ email }}</a>
                    <span v-else class="optional-field-placeholder">(not provided)</span>
                </td>
            </tr>
            <tr>
                <th>Phone</th>
                <td>
                    <a v-if="phone" :href="`tel:${phone}`">{{ phone }}</a>
                    <span v-else class="optional-field-placeholder">(not provided)</span>
                </td>
            </tr>
            <tr>
                <th>Web Contact</th>
                <td>
                    <a v-if="webContact" :href="webContact">{{ webContact }}</a>
                    <span v-else class="optional-field-placeholder">(not provided)</span>
                </td>
            </tr>
            <tr>
                <th>Website</th>
                <td>
                    <a :href="website">{{ website }}</a>
                </td>
            </tr>
        </table>

        <h3>Jobs</h3>
        <table v-if="jobs.length > 0">
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
        </table>
        <p v-else>No jobs yet!</p>

        <v-btn color="red" variant="outlined" @click="setInstitutionToRemove(id)">Remove {{ `"${name}"` }}</v-btn>
    </section>
</template>

<style scoped>
h2 {
    padding-bottom: 1rem;
}

h3 {
    padding: 1rem 0;
}

thead th {
    padding: 0.5rem;
}

tbody tr:nth-child(odd) {
    background-color: #8bb8e8;
}

tbody tr:nth-child(even) {
    background-color: #ffd100;
}

.anchor {
    float: left;
    margin-left: -2rem;
    line-height: 1;
    padding: 0.5rem;
}

.institution-metadata {
    border-collapse: collapse;
}

.institution-metadata th {
    padding: 0.5rem;
}

.institution-metadata tr {
    border-bottom: thin solid;
}
</style>
