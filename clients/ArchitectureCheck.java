///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//JAVAC_OPTIONS -parameters
//DEPS org.springframework.boot:spring-boot-dependencies:3.5.16@pom
//DEPS org.springframework.boot:spring-boot-starter-web
//DEPS org.springframework.boot:spring-boot-starter-validation
//DEPS org.springframework.boot:spring-boot-starter-data-jdbc
//DEPS org.flowable:flowable-spring-boot-starter-cmmn:7.2.0
//DEPS com.tngtech.archunit:archunit:1.5.1
//SOURCES app/**/*.java

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;

/**
 * Hexagonal-architecture guardrail for the clients app. Run with {@code jbang ArchitectureCheck.java};
 * exits 1 when any rule is violated.
 */
void main() {
  JavaClasses app = new ClassFileImporter().importPackages("app");

  List<ArchRule> rules =
      List.of(
          classes()
              .that().resideInAPackage("app.domain..")
              .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "app.domain..")
              .because("the domain is plain Java"),
          classes()
              .that().resideInAPackage("app.application..")
              .should().onlyDependOnClassesThat()
              .resideInAnyPackage(
                  "java..",
                  "app.domain..",
                  "app.application..",
                  "org.springframework.stereotype..",
                  "org.springframework.transaction.annotation..")
              .because("the application core may only use @Service and @Transactional from Spring"),
          slices().matching("app.adapter.(*)..").should().notDependOnEachOther()
              .because("driving and driven adapters only meet through the application core"),
          slices().matching("app.adapter.in.(*)..").should().notDependOnEachOther()
              .because("driving adapters are independent of each other"),
          slices().matching("app.adapter.out.(*)..").should().notDependOnEachOther()
              .because("driven adapters are independent of each other"),
          noClasses()
              .that().resideOutsideOfPackages("app.adapter.out.flowable..", "app.adapter.in.cmmn..")
              .should().dependOnClassesThat().resideInAPackage("org.flowable..")
              .because("Flowable is confined to its two adapters"),
          layeredArchitecture()
              .consideringOnlyDependenciesInLayers()
              .withOptionalLayers(true)
              .layer("Domain").definedBy("app.domain..")
              .layer("Application").definedBy("app.application..")
              .layer("Adapters").definedBy("app.adapter..")
              .layer("Config").definedBy("app.config..")
              .whereLayer("Adapters").mayNotBeAccessedByAnyLayer()
              .whereLayer("Config").mayNotBeAccessedByAnyLayer()
              .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapters", "Config")
              .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapters", "Config"));

  var violations = 0;
  for (var rule : rules) {
    var result = rule.evaluate(app);
    if (result.hasViolation()) {
      violations++;
      System.out.println("FAIL " + result.getFailureReport());
    } else {
      System.out.println("OK   " + rule.getDescription());
    }
  }
  System.out.printf("%d classes checked, %d of %d rules violated%n", app.size(), violations, rules.size());
  if (violations > 0) {
    System.exit(1);
  }
}
