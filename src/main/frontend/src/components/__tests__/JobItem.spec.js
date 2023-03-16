import { describe, it, expect } from "vitest"

import { createVuetify } from "vuetify"
import * as components from "vuetify/components"
import * as directives from "vuetify/directives"
import { mount } from "@vue/test-utils"

import JobItem from "../JobItem.vue"

const testJob = {
    id: 1,
    institutionID: 1,
    repositoryBaseURL: "http://example.edu/provider",
    sets: [],
    metadataPrefix: "oai_dc",
    scheduleCronExpression: "0 0 0 * * ?",
    lastSuccessfulRun: null,
}
const testJobSelectiveHarvest = {
    ...testJob,
    sets: ["set1", "set2"],
}

describe("JobItem", () => {
    const vuetify = createVuetify({ components, directives })

    it("renders properly without sets specified", () => {
        const wrapper = mount(JobItem, {
            props: testJob,
            global: { plugins: [vuetify] },
        })

        expect(wrapper.text()).toContain("(entire repository)")
    })
    it("renders properly with sets specified", () => {
        const wrapper = mount(JobItem, {
            props: testJobSelectiveHarvest,
            global: { plugins: [vuetify] },
        })

        testJobSelectiveHarvest.sets.forEach((set) => {
            expect(wrapper.text()).toContain(set)
        })
    })
})

export { testJob, testJobSelectiveHarvest }
