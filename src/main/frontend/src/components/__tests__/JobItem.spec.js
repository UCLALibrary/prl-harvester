import { describe, it, expect } from "vitest"

import { createVuetify } from "vuetify"
import * as components from "vuetify/components"
import * as directives from "vuetify/directives"
import { mount } from "@vue/test-utils"

import JobItem from "../JobItem.vue"
import { testJob, testJobSelectiveHarvest } from "./TestData.js"

describe("JobItem", () => {
    const vuetify = createVuetify({ components, directives })

    /**
     * Checks that the job metadata is rendered as expected.
     *
     * @param {VueWrapper} wrapper The result of mounting a {@link JobItem}
     * @param {object} job The data that should be rendered
     */
    function checkJob(wrapper, job) {
        Object.entries(job)
            .filter(([key]) => !["id", "institutionID"].includes(key))
            .forEach(([key, value]) => {
                if (value !== null) {
                    if (value instanceof Array) {
                        value.forEach((element) => {
                            expect(wrapper.text()).toContain(element)
                        })
                    } else {
                        expect(wrapper.text()).toContain(value)
                    }
                } else {
                    if (key === "sets") {
                        expect(wrapper.text()).toContain("(entire repository)")
                    }
                }
            })
    }

    it("renders properly without sets specified", () => {
        const wrapper = mount(JobItem, {
            props: testJob,
            global: { plugins: [vuetify] },
        })

        checkJob(wrapper, testJob)
    })

    it("renders properly with sets specified", () => {
        const wrapper = mount(JobItem, {
            props: testJobSelectiveHarvest,
            global: { plugins: [vuetify] },
        })

        checkJob(wrapper, testJobSelectiveHarvest)
    })
})

export { testJob, testJobSelectiveHarvest }
