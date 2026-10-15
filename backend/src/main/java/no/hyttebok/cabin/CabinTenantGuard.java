package no.hyttebok.cabin;

import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.annotation.TenantId;
import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.event.EntityEventListener;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;

/**
 * Cabin data is always saved under the cabin of the request, whatever cabinId the body says. Micronaut Data
 * already limits updates and deletes to rows of the current cabin; this stops a row from being moved (or created)
 * into another cabin by sending a different cabinId.
 */
@Singleton
public class CabinTenantGuard implements EntityEventListener<Object> {

    @Override
    public boolean supports(RuntimePersistentEntity<Object> entity, Class<? extends Annotation> eventType) {
        return tenantProperty(entity) != null;
    }

    @Override
    public boolean prePersist(EntityEventContext<Object> context) {
        return pin(context);
    }

    @Override
    public boolean preUpdate(EntityEventContext<Object> context) {
        return pin(context);
    }

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE; // after Micronaut's own tenant listener, so this has the last word
    }

    @SuppressWarnings("unchecked")
    private static boolean pin(EntityEventContext<Object> context) {
        var property = (BeanProperty<Object, Object>) tenantProperty(context.getPersistentEntity());
        context.setProperty(property, CabinContext.require());
        return true;
    }

    private static BeanProperty<Object, ?> tenantProperty(RuntimePersistentEntity<Object> entity) {
        return entity.getPersistentProperties().stream()
            .filter(p -> p.getAnnotationMetadata().hasAnnotation(TenantId.class))
            .map(p -> p.getProperty())
            .findFirst().orElse(null);
    }
}
