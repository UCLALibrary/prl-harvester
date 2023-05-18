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
    selectJobToUpdate: { type: Function },
    selectJobToRemove: { type: Function },
})
const isSelectiveHarvest = computed(() => props.sets.length > 0)
const sortedSets = computed(() => props.sets.slice().sort())
</script>

<template>
    <v-card variant="outlined" height="auto" width="auto">
        <v-card-text>
            <v-list>
                <v-list-item density="compact" prepend-icon="mdi-town-hall">
                    <v-list-item-subtitle>
                        <a :href="`${repositoryBaseURL}?verb=Identify`">{{ repositoryBaseURL }}</a>
                    </v-list-item-subtitle>
                </v-list-item>
                <v-list-item density="compact" prepend-icon="mdi-archive">
                    <v-list v-if="isSelectiveHarvest">
                        <span v-for="set in sortedSets" :key="set" density="compact">
                            <a
                                :href="`${repositoryBaseURL}?verb=ListRecords&set=${set}&metadataPrefix=${metadataPrefix}`">
                                {{ set }} </a
                            >,
                        </span>
                    </v-list>
                    <span v-else class="optional-field-placeholder">(entire repository)</span>
                </v-list-item>
                <v-list-item density="compact" prepend-icon="mdi-file-code">{{ metadataPrefix }}</v-list-item>
                <v-list-item density="compact" prepend-icon="mdi-calendar-clock">
                    <v-list-item-subtitle>{{ scheduleCronExpression }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item density="compact" prepend-icon="mdi-timeline-check">
                    <v-list-item-subtitle>
                        <span v-if="lastSuccessfulRun">{{ lastSuccessfulRun }}</span>
                        <span v-else class="optional-field-placeholder">(no successful runs yet)</span>
                    </v-list-item-subtitle>
                </v-list-item>
            </v-list>
        </v-card-text>
        <v-card-actions class="ma-2 pa-2">
            <v-btn
                color="primary"
                variant="outlined"
                @click="selectJobToUpdate([id, institutionID])"
                class="propose-edit-job">
                Edit
            </v-btn>
            <v-btn
                color="red"
                variant="outlined"
                @click="selectJobToRemove([id, institutionID])"
                class="propose-remove-job">
                Remove
            </v-btn>
        </v-card-actions>
    </v-card>
</template>

<style scoped></style>
