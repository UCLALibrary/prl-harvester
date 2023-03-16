import { describe, it, expect } from "vitest"

import { createVuetify } from "vuetify"
import * as components from "vuetify/components"
import * as directives from "vuetify/directives"
import { mount } from "@vue/test-utils"

import InstitutionItem from "../InstitutionItem.vue"
import { testJob, testJobSelectiveHarvest } from "./JobItem.spec.js"

const testInstitutionNoJobs = {
    id: 1,
    name: "Test Institution",
    description: "A description of the institution.",
    location: "The location of the institution.",
    email: "test@example.edu",
    phone: "+1 800 200 0000",
    webContact: "http://example.edu/contact",
    website: "http://example.edu",
    jobs: [],
}
const testInstitution = {
    ...testInstitutionNoJobs,
    jobs: [testJob, testJobSelectiveHarvest],
}

describe("InstitutionItem", () => {
    const vuetify = createVuetify({ components, directives })

    it("renders properly without jobs", () => {
        const wrapper = mount(InstitutionItem, {
            props: { ...testInstitutionNoJobs },
            global: { plugins: [vuetify] },
        })

        expect(wrapper.text()).toContain("Test Institution").toContain("No jobs yet!")
    })
    it("renders properly with jobs", () => {
        const wrapper = mount(InstitutionItem, {
            props: { ...testInstitution },
            global: { plugins: [vuetify] },
        })

        expect(wrapper.text())
            .toContain("Test Institution")
            .toContain(testJob.repositoryBaseURL)
            .toContain(testJobSelectiveHarvest.repositoryBaseURL)
    })
})
