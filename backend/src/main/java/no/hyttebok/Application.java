package no.hyttebok;

import io.micronaut.runtime.Micronaut;

public class Application {

    // Eager singletons: the security filter fetches Google's OpenID metadata with a blocking call when it's built,
    // which fails if the first request builds it on the Netty event loop. At startup it runs on the main thread.
    public static void main(String[] args) {
        Micronaut.build(args)
            .mainClass(Application.class)
            .eagerInitSingletons(true)
            .start();
    }
}
