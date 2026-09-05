/**
 * Shared kernel: cross-cutting concerns every application module may depend on.
 *
 * <p>Declared as an OPEN module so its sub-packages stay accessible without a named interface.
 * Nothing with school domain meaning belongs here — if it models the business, it belongs in a
 * feature module.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        displayName = "Platform")
package in.chalkbase.platform;
