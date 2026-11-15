package app.adapter.out.jdbc;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Native image only: Spring Data JDBC creates {@link ClientRow} and calls its {@code withId} wither
 * reflectively there (on the JVM it generates bytecode instead, so the tracing agent never sees
 * it). Spring AOT writes these hints into the native-image configuration; the JVM ignores them.
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(JdbcNativeHints.Registrar.class)
class JdbcNativeHints {

  static final class Registrar implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      hints
          .reflection()
          .registerType(
              ClientRow.class,
              MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
              MemberCategory.INVOKE_DECLARED_METHODS,
              MemberCategory.DECLARED_FIELDS);
    }
  }
}
