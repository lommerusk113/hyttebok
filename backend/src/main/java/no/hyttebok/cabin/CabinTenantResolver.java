package no.hyttebok.cabin;

import io.micronaut.multitenancy.exceptions.TenantNotFoundException;
import io.micronaut.multitenancy.tenantresolver.TenantResolver;
import jakarta.inject.Singleton;

import java.io.Serializable;

/** Tells Micronaut Data which cabin's rows a query may see and new rows belong to. */
@Singleton
public class CabinTenantResolver implements TenantResolver {

    @Override
    public Serializable resolveTenantIdentifier() throws TenantNotFoundException {
        return CabinContext.current().orElseThrow(() -> new TenantNotFoundException("No cabin selected"));
    }
}
