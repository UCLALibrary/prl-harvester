import { describe, it, expect, beforeEach, afterEach } from "vitest"

import { createVuetify } from "vuetify"
import * as components from "vuetify/components"
import * as directives from "vuetify/directives"
import { mount } from "@vue/test-utils"

import HarvesterAdmin from "../HarvesterAdmin.vue"
import InstitutionItem from "../InstitutionItem.vue"
import { testInstitution } from "./TestData.js"

describe("HarvesterAdmin", () => {
    const vuetify = createVuetify({ components, directives })

    const addInstitutionFormFields = ["Name", "Description", "Location", "Website", "Email", "Phone", "Web Contact"]
    const updateInstitutionFormFields = ["ID"].concat(addInstitutionFormFields)

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
        const nInstitutionButtons = 2 * institutions.length

        expect(buttons.length).toStrictEqual(1 + nInstitutionButtons)
        expect(buttons.at(0).text()).toStrictEqual("Add Institution")

        if (institutions.length > 0) {
            // Each institution should have an Edit and Remove button
            for (let i = 1; i < nInstitutionButtons; i++) {
                if (i % 2 === 1) {
                    expect(buttons.at(i).text()).toStrictEqual("Edit")
                } else {
                    expect(buttons.at(i).text()).toContain("Remove")
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
            const addInstitutionButton = wrapper.findAll("button").at(0)

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
            const updateInstitutionButton = wrapper.findAll("button").at(1) // FIXME: use id of the element to find

            updateInstitutionFormFields.forEach((value) => {
                expect(document.body.innerHTML).not.toContain(value)
            })

            await updateInstitutionButton.trigger("click")

            updateInstitutionFormFields.forEach((value) => {
                expect(document.body.innerHTML).toContain(value)
            })
        })

        it("displays a dialog to confirm removing an institution when the appropriate button is clicked", async () => {
            const removeInstitutionButton = wrapper.findAll("button").at(2) // FIXME: use id of the element to find
            const removeInstitutionDialogText = "This action will remove"

            expect(document.body.innerHTML).not.toContain(removeInstitutionDialogText)

            await removeInstitutionButton.trigger("click")

            expect(document.body.innerHTML).toContain(removeInstitutionDialogText)
        })

        afterEach(() => {
            wrapper.unmount()
        })
    })
})
