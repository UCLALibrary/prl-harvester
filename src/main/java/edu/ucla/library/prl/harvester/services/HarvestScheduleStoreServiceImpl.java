
package edu.ucla.library.prl.harvester.services;

import java.util.List;

import edu.ucla.library.prl.harvester.Institution;
import edu.ucla.library.prl.harvester.Job;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * The interface of the event bus service that stores the information needed to schedule harvest jobs.
 */
@SuppressWarnings("PMD.UnusedFormalParameter") //temp until constructor defined
public class HarvestScheduleStoreServiceImpl implements HarvestScheduleStoreService {

    HarvestScheduleStoreServiceImpl(final Vertx aVertx, final JsonObject aConfig) {
        // TODO code constructor
    }

    @Override
    public Future<Institution> getInstitution(final int anInstitutionId) {
        // TODO implement method
        return Future.succeededFuture(null);
    }

    @Override
    public Future<List<Institution>> listInstitutions() {
        // TODO implement method
        return Future.succeededFuture(null);
    }

    @Override
    public Future<Integer> addInstitution(final Institution anInstitution) {
        // TODO implement method
        return Future.succeededFuture(null);
    }

    @Override
    public Future<Void> updateInstitution(final int anInstitutionId, final Institution anInstitution) {
        // TODO implement method
        return Future.succeededFuture(null);
    }

    @Override
    public Future<Void> removeInstitution(final int anInstitutionId) {
        // TODO implement method
        return Future.succeededFuture(null);
    }

    @Override
    public Future<Job> getJob(final int aJobId) {
        // TODO implement method
        return Future.succeededFuture(null);
    }

    @Override
    public Future<List<Job>> listJobs() {
        // TODO implement method
        return Future.succeededFuture(null);
    }

    @Override
    public Future<Integer> addJob(final Job aJob) {
        // TODO implement method
        return Future.succeededFuture(null);
    }

    @Override
    public Future<Void> updateJob(final int aJobId, final Job aJob) {
        // TODO implement method
        return Future.succeededFuture(null);
    }

    @Override
    public Future<Void> removeJob(final int aJobId) {
        // TODO implement method
        return Future.succeededFuture(null);
    }

    @Override
    public Future<Void> close() {
        // TODO implement method
        return Future.succeededFuture(null);
    }
}
