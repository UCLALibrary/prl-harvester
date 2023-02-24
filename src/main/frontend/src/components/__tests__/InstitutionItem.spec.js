import { describe, it, expect } from "vitest"

import { mount } from "@vue/test-utils"
import InstitutionItem from "../InstitutionItem.vue"
import { testJob, testJobSelectiveHarvest } from "./JobItem.spec.js"

const testInstitutionNoJobs = {
    id: 1,
    name: "Test Institution",
    description: "A description of the institution.",
    location: "The institution's location.",
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
    it("renders properly without jobs", () => {
        const wrapper = mount(InstitutionItem, { props: { ...testInstitutionNoJobs } })

        expect(wrapper.text()).toContain("Test Institution").toContain("No jobs yet!")
    })
    it("renders properly with jobs", () => {
        const wrapper = mount(InstitutionItem, { props: { ...testInstitution } })

        expect(wrapper.text())
            .toContain("Test Institution")
            .toContain(testJob.repositoryBaseURL)
            .toContain(testJobSelectiveHarvest.repositoryBaseURL)
    })
})
