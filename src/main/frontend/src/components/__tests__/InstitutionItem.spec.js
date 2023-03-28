import { describe, it, expect, beforeEach, afterEach } from "vitest"

import { createVuetify } from "vuetify"
import * as components from "vuetify/components"
import * as directives from "vuetify/directives"
import { mount } from "@vue/test-utils"

import InstitutionItem from "../InstitutionItem.vue"
import JobItem from "../JobItem.vue"
import { testJob, testJobSelectiveHarvest, testInstitution } from "./TestData.js"

describe("InstitutionItem", () => {
    const vuetify = createVuetify({ components, directives })

    /**
     * Checks that the institution metadata is rendered as expected.
     *
     * @param {VueWrapper} wrapper The result of mounting a {@link InstitutionItem}
     * @param {object} institution The data that should be rendered
     */
    function checkInstitution(wrapper, institution) {
        Object.entries(institution)
            .filter(([key, value]) => key !== "id" && value !== null)
            .forEach((keyValuePair) => {
                expect(wrapper.text()).toContain(keyValuePair.at(1))
            })
    }

    /**
     * Checks whether the jobs metadata is rendered or not.
     *
     * @param {VueWrapper} wrapper The result of mounting a {@link InstitutionItem}
     * @param {object[]} jobs The data that should be rendered
     */
    function checkJobs(wrapper, jobs) {
        const jobsList = wrapper.find(".harvest-jobs")

        if (jobs.length > 0) {
            expect(jobsList.exists()).toBeTruthy()
            expect(jobsList.findAllComponents(JobItem).length).toStrictEqual(jobs.length)
        } else {
            expect(jobsList.exists()).toBeFalsy()
            expect(wrapper.text()).toContain("No jobs yet!")
        }
    }

    /**
     * Checks that the HTML button elements are rendered as expected.
     *
     * @param {VueWrapper} wrapper The result of mounting a {@link InstitutionItem}
     * @param {object[]} jobs The data that should be rendered
     */
    function checkButtons(wrapper, jobs) {
        expect(wrapper.findAll("button").length).toStrictEqual(3 + 2 * jobs.length)

        expect(wrapper.find(".propose-add-job").exists()).toBeTruthy()
        expect(wrapper.find(".propose-edit-institution").exists()).toBeTruthy()
        expect(wrapper.find(".propose-remove-institution").exists()).toBeTruthy()

        for (const selector of [".propose-edit-job", ".propose-remove-job"]) {
            expect(wrapper.findAll(selector).length).toStrictEqual(jobs.length)
        }
    }

    for (const testInfo of [
        {
            name: "without jobs",
            institution: testInstitution,
            jobs: [],
        },
        {
            name: "with jobs",
            institution: testInstitution,
            jobs: [testJob, testJobSelectiveHarvest],
        },
    ]) {
        describe(testInfo.name, () => {
            const props = { ...testInfo.institution, jobs: testInfo.jobs }

            let wrapper

            beforeEach(() => {
                wrapper = mount(InstitutionItem, { props, global: { plugins: [vuetify] } })
            })

            it("renders properly", () => {
                checkInstitution(wrapper, testInfo.institution)
                checkJobs(wrapper, testInfo.jobs)
                checkButtons(wrapper, testInfo.jobs)
            })

            afterEach(() => {
                wrapper.unmount()
            })
        })
    }
})
