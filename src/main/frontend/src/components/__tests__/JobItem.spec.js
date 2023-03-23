import { describe, it, expect, beforeEach, afterEach } from "vitest"

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

    for (const testInfo of [
        {
            name: "without sets",
            job: testJob,
        },
        {
            name: "with sets",
            job: testJobSelectiveHarvest,
        },
    ]) {
        describe(testInfo.name, () => {
            const props = testInfo.job

            let wrapper

            beforeEach(() => {
                wrapper = mount(JobItem, { props, global: { plugins: [vuetify] } })
            })

            it("renders properly", () => {
                checkJob(wrapper, testInfo.job)
            })

            afterEach(() => {
                wrapper.unmount()
            })
        })
    }
})
