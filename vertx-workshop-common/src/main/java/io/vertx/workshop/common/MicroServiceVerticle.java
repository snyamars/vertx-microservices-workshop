package io.vertx.workshop.common;

import io.vertx.core.*;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.discovery.DiscoveryOptions;
import io.vertx.ext.discovery.DiscoveryService;
import io.vertx.ext.discovery.Record;
import io.vertx.ext.discovery.types.EventBusService;
import io.vertx.ext.discovery.types.MessageSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * An implementation of {@link Verticle} taking care of the discovery and publication of services.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class MicroServiceVerticle extends AbstractVerticle {

  protected DiscoveryService discovery;
  protected Set<Record> registeredRecords = new ConcurrentHashSet<>();

  @Override
  public void start() {
    discovery = DiscoveryService.create(vertx, new DiscoveryOptions().setBackendConfiguration(config()));
    discovery.registerDiscoveryBridge(new DockerEnvironmentBridge(), new JsonObject());
  }

  public void publishMessageSource(String name, String address, Class contentClass, Handler<AsyncResult<Void>>
      completionHandler) {
    Record record = MessageSource.createRecord(name, address, contentClass);
    publish(completionHandler, record);
  }

  public void publishMessageSource(String name, String address, Handler<AsyncResult<Void>>
      completionHandler) {
    Record record = MessageSource.createRecord(name, address);
    publish(completionHandler, record);
  }

  public void publishEventBusService(String name, String address, Class serviceClass, Handler<AsyncResult<Void>>
      completionHandler) {
    Record record = EventBusService.createRecord(name, address, serviceClass);
    publish(completionHandler, record);
  }

  private void publish(Handler<AsyncResult<Void>> completionHandler, Record record) {
    if (discovery == null) {
      try {
        start();
      } catch (Exception e) {
        throw new RuntimeException("Cannot create discovery service");
      }
    }

    discovery.publish(record, ar -> {
      if (ar.succeeded()) {
        registeredRecords.add(record);
        completionHandler.handle(Future.succeededFuture());
      } else {
        completionHandler.handle(Future.failedFuture(ar.cause()));
      }
    });
  }

  @Override
  public void stop(Future<Void> future) throws Exception {
    List<Future> futures = new ArrayList<>();
    for (Record record : registeredRecords) {
      Future<Void> unregistrationFuture = Future.future();
      futures.add(unregistrationFuture);
      discovery.unpublish(record.getRegistration(), unregistrationFuture.completer());
    }

    if (futures.isEmpty()) {
      discovery.close();
      future.complete();
    } else {
      CompositeFuture composite = CompositeFuture.all(futures);
      composite.setHandler(ar -> {
        discovery.close();
        if (ar.failed()) {
          future.fail(ar.cause());
        } else {
          future.complete();
        }
      });
    }
  }
}
