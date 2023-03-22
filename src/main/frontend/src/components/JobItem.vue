<script setup>
import { computed } from "vue"

const props = defineProps({
    id: { type: Number, required: true },
    institutionID: { type: Number, required: true },
    repositoryBaseURL: { type: String, required: true },
    sets: { type: Array, required: true },
    metadataPrefix: { type: String, required: true },
    scheduleCronExpression: { type: String, required: true },
    lastSuccessfulRun: { type: Date },
})
const sortedSets = computed(() => props.sets.slice().sort())
const isSelectiveHarvest = computed(() => props.sets.length > 0)
</script>

<template>
    <tr>
        <td>
            <a :href="`${repositoryBaseURL}?verb=Identify`">{{ repositoryBaseURL }}</a>
        </td>
        <td>
            <v-list v-if="isSelectiveHarvest">
                <v-list-item v-for="set in sortedSets" :key="set" density="compact">
                    <a :href="`${repositoryBaseURL}?verb=ListRecords&set=${set}&metadataPrefix=${metadataPrefix}`">
                        {{ set }}
                    </a>
                </v-list-item>
            </v-list>
            <span v-else class="optional-field-placeholder">(entire repository)</span>
        </td>
        <td>{{ metadataPrefix }}</td>
        <td>{{ scheduleCronExpression }}</td>
        <td>
            <span v-if="lastSuccessfulRun">{{ lastSuccessfulRun }}</span>
            <span v-else class="optional-field-placeholder">(no successful runs yet)</span>
        </td>
    </tr>
</template>

<style scoped></style>
