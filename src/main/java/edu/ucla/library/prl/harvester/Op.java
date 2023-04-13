
package edu.ucla.library.prl.harvester;

/**
 * OpenAPI operation IDs.
 */
public enum Op {

    /**
     * A constant for the operation that sends a GET request to the root path.
     */
    getRoot,

    /**
     * A constant for the operation that retrieves the admin interface.
     */
    getAdmin,

    /**
     * A constant for the "get status" operation.
     */
    getStatus,

    /**
     * Institution operations.
     */
    addInstitutions, getInstitution, listInstitutions, removeInstitution, updateInstitution,

    /**
     * Job operations.
     */
    addJobs, getJob, listJobs, removeJob, updateJob
}
