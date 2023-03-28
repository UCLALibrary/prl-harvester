import { describe, it, expect, beforeEach, afterEach } from "vitest"

import { createVuetify } from "vuetify"
import * as components from "vuetify/components"
import * as directives from "vuetify/directives"
import { mount } from "@vue/test-utils"

import HarvesterAdmin from "../HarvesterAdmin.vue"
import InstitutionItem from "../InstitutionItem.vue"
import { testJob, testJobSelectiveHarvest, testInstitution } from "./TestData.js"

describe("HarvesterAdmin", () => {
    const vuetify = createVuetify({ components, directives })

    const addInstitutionFormFields = ["Name", "Description", "Location", "Website", "Email", "Phone", "Web Contact"]
    const updateInstitutionFormFields = ["ID"].concat(addInstitutionFormFields)

    const addJobFormFields = ["Repository Base URL", "Sets", "Metadata Format", "Schedule Cron Expression"]
    const updateJobFormFields = ["ID"].concat(addJobFormFields)

    /**
     * Checks that the expected number of institutions are rendered.
     *
     * @param {VueWrapper} wrapper The result of mounting a {@link HarvesterAdmin}
     * @param {object} institutionsMap A map from institution ID to institution (matching the part of the state that stores institutions)
     */
    function checkInstitutionsCount(wrapper, institutionsMap) {
        // The spec for InstitutionItem does a more thorough check of the rendering
        expect(wrapper.findAllComponents(InstitutionItem).length).toStrictEqual(Object.keys(institutionsMap).length)
    }

    /**
     * Checks that the HTML button elements are rendered as expected.
     *
     * @param {VueWrapper} wrapper The result of mounting a {@link HarvesterAdmin}
     * @param {Object} institutionsMap A map from institution ID to institution
     */
    function checkButtons(wrapper, institutionsMap) {
        const institutions = Object.values(institutionsMap)
        const buttons = wrapper.findAll("button")
        const nInstitutionButtons = 3 * institutions.length

        expect(buttons.length).toStrictEqual(1 + nInstitutionButtons)

        let button = buttons.at(0)

        expect(button.text()).toStrictEqual("Add Institution")
        expect(button.classes()).toContain("propose-add-institution")

        if (institutions.length > 0) {
            // Each institution should have an Edit and Remove button
            for (let i = 1; i < nInstitutionButtons; i++) {
                button = buttons.at(i)

                if (i % 3 === 1) {
                    expect(button.text()).toStrictEqual("Add Job")
                    expect(button.classes()).toContain("propose-add-job")
                } else if (i % 3 === 2) {
                    expect(button.text()).toStrictEqual("Edit")
                    expect(button.classes()).toContain("propose-edit-institution")
                } else {
                    expect(button.text()).toContain("Remove")
                    expect(button.classes()).toContain("propose-remove-institution")
                }
            }
        }
    }

    describe("without institutions", () => {
        const institutions = {}
        const jobs = {}
        const data = { institutions, jobs }

        let wrapper

        beforeEach(() => {
            wrapper = mount(HarvesterAdmin, {
                props: data,
                global: { plugins: [vuetify] },
            })
        })

        it("renders properly", () => {
            checkInstitutionsCount(wrapper, institutions)
            checkButtons(wrapper, institutions)
        })

        it("displays a form for adding an institution when the appropriate button is clicked", async () => {
            const addInstitutionButton = wrapper.find(".propose-add-institution")

            addInstitutionFormFields.forEach((value) => {
                expect(document.body.innerHTML).not.toContain(value)
            })

            await addInstitutionButton.trigger("click")

            addInstitutionFormFields.forEach((value) => {
                expect(document.body.innerHTML).toContain(value)
            })
        })

        afterEach(() => {
            wrapper.unmount()
        })
    })

    describe("with institutions", () => {
        const institutions = { [testInstitution.id]: testInstitution }
        const jobs = {}
        const data = { institutions, jobs }

        let wrapper

        beforeEach(() => {
            wrapper = mount(HarvesterAdmin, {
                props: data,
                global: { plugins: [vuetify] },
            })
        })

        it("renders properly", () => {
            checkInstitutionsCount(wrapper, institutions)
            checkButtons(wrapper, institutions)
        })

        it("displays a form for updating an institution when the appropriate button is clicked", async () => {
            const updateInstitutionButton = wrapper.find(".propose-edit-institution")

            updateInstitutionFormFields.forEach((value) => {
                expect(document.body.innerHTML).not.toContain(value)
            })

            await updateInstitutionButton.trigger("click")

            updateInstitutionFormFields.forEach((value) => {
                expect(document.body.innerHTML).toContain(value)
            })
        })

        it("displays a dialog to confirm removing an institution when the appropriate button is clicked", async () => {
            const removeInstitutionButton = wrapper.find(".propose-remove-institution")
            const removeInstitutionDialogText = "This action will remove"

            expect(document.body.innerHTML).not.toContain(removeInstitutionDialogText)

            await removeInstitutionButton.trigger("click")

            expect(document.body.innerHTML).toContain(removeInstitutionDialogText)
        })

        describe("without jobs", () => {
            it("displays a form for adding a job when the appropriate button is clicked", async () => {
                const addJobButton = wrapper.find(".propose-add-job")

                addJobFormFields.forEach((value) => {
                    expect(document.body.innerHTML).not.toContain(value)
                })

                await addJobButton.trigger("click")

                addJobFormFields.forEach((value) => {
                    expect(document.body.innerHTML).toContain(value)
                })
            })
        })

        describe("with jobs", () => {
            const jobs = {
                [testInstitution.id]: { [testJob.id]: testJob, [testJobSelectiveHarvest.id]: testJobSelectiveHarvest },
            }
            const data = { institutions, jobs }

            beforeEach(() => {
                wrapper = mount(HarvesterAdmin, {
                    props: data,
                    global: { plugins: [vuetify] },
                })
            })

            it("displays a form for updating a job when the appropriate button is clicked", async () => {
                const updateJobButton = wrapper.find(".propose-edit-job")

                updateJobFormFields.forEach((value) => {
                    expect(document.body.innerHTML).not.toContain(value)
                })

                await updateJobButton.trigger("click")

                updateJobFormFields.forEach((value) => {
                    expect(document.body.innerHTML).toContain(value)
                })
            })

            it("displays a dialog to confirm removing a job when the appropriate button is clicked", async () => {
                const removeJobButton = wrapper.find(".propose-remove-job")
                const removeJobDialogText = "This action will remove"

                expect(document.body.innerHTML).not.toContain(removeJobDialogText)

                await removeJobButton.trigger("click")

                expect(document.body.innerHTML).toContain(removeJobDialogText)
            })
        })

        afterEach(() => {
            wrapper.unmount()
        })
    })
})
