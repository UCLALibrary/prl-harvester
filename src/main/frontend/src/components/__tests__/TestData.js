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

const testInstitution = {
    id: 1,
    name: "Test Institution",
    description: "A description of the institution.",
    location: "The location of the institution.",
    email: "test@example.edu",
    phone: "+1 800 200 0000",
    webContact: "http://example.edu/contact",
    website: "http://example.edu",
}

export { testJob, testJobSelectiveHarvest, testInstitution }
