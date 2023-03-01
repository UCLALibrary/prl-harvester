import { describe, it, expect } from "vitest"

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
    it("renders properly without sets specified", () => {
        const wrapper = mount(JobItem, { props: { ...testJob } })

        expect(wrapper.text()).toContain("(entire repository)")
    })
    it("renders properly with sets specified", () => {
        const wrapper = mount(JobItem, { props: { ...testJobSelectiveHarvest } })

        testJobSelectiveHarvest.sets.forEach((set) => {
            expect(wrapper.text()).toContain(set)
        })
    })
})

export { testJob, testJobSelectiveHarvest }
