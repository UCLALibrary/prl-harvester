import { describe, it, expect } from "vitest"

import { createVuetify } from "vuetify"
import * as components from "vuetify/components"
import * as directives from "vuetify/directives"
import { mount } from "@vue/test-utils"

import InstitutionItem from "../InstitutionItem.vue"
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
        const jobsTable = wrapper.find(".harvest-jobs")

        if (jobs.length > 0) {
            expect(jobsTable.exists()).toBeTruthy()
            expect(jobsTable.findAll("tbody").at(0).findAll("tr").length).toStrictEqual(jobs.length)
        } else {
            expect(jobsTable.exists()).toBeFalsy()
            expect(wrapper.text()).toContain("No jobs yet!")
        }
    }

    /**
     * Checks that the HTML button elements are rendered as expected.
     *
     * @param {VueWrapper} wrapper The result of mounting a {@link InstitutionItem}
     */
    function checkButtons(wrapper) {
        const buttons = wrapper.findAll("button")
        expect(buttons.length).toStrictEqual(2)
        expect(buttons.at(0).text()).toContain("Edit")
        expect(buttons.at(1).text()).toContain("Remove")
    }

    it("renders properly without jobs", () => {
        const jobs = []
        const wrapper = mount(InstitutionItem, {
            props: { ...testInstitution, jobs },
            global: { plugins: [vuetify] },
        })

        checkInstitution(wrapper, testInstitution)
        checkJobs(wrapper, jobs)
        checkButtons(wrapper)
    })

    it("renders properly with jobs", () => {
        const jobs = [testJob, testJobSelectiveHarvest]
        const wrapper = mount(InstitutionItem, {
            props: { ...testInstitution, jobs },
            global: { plugins: [vuetify] },
        })

        checkInstitution(wrapper, testInstitution)
        checkJobs(wrapper, jobs)
        checkButtons(wrapper)
    })
})
