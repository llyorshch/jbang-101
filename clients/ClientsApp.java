///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//JAVAC_OPTIONS -parameters
//RUNTIME_OPTIONS -Djava.awt.headless=true
//DEPS org.springframework.boot:spring-boot-dependencies:3.5.16@pom
//DEPS org.springframework.boot:spring-boot-starter-web
//DEPS org.springframework.boot:spring-boot-starter-validation
//DEPS org.springframework.boot:spring-boot-starter-data-jdbc
//DEPS com.h2database:h2
//DEPS org.flowable:flowable-spring-boot-starter-cmmn:7.2.0
//SOURCES app/**/*.java
//FILES application.properties=resources/application.properties
//FILES schema.sql=resources/schema.sql
//FILES cases/onboarding.cmmn.xml=resources/cases/onboarding.cmmn.xml
//FILES cases/offboarding.cmmn.xml=resources/cases/offboarding.cmmn.xml
//FILES static/index.html=ui/index.html
//FILES static/styles.css=ui/styles.css
//FILES static/app.js=ui/app.js
//FILES static/api.js=ui/api.js
//FILES static/components/ClientList.js=ui/components/ClientList.js
//FILES static/components/ClientDetail.js=ui/components/ClientDetail.js
//FILES static/components/CasePanel.js=ui/components/CasePanel.js
//FILES static/vendor/react.js=ui/vendor/react.js
//FILES static/vendor/react-dom-client.js=ui/vendor/react-dom-client.js
//FILES static/vendor/htm.js=ui/vendor/htm.js

package app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Client onboarding/offboarding sample: Spring Boot + Flowable CMMN, run with JBang only. */
@SpringBootApplication
public class ClientsApp {

  public static void main(String[] args) {
    if ("runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
      useExecutableDirAsJavaHome();
      useJniFontLayoutOnLinux();
    }
    SpringApplication.run(ClientsApp.class, args);
  }

  /**
   * A native image has no {@code java.home}, but Java2D (case diagrams) needs it to find
   * {@code lib/fontconfig.bfc} (or, on Linux, an existing {@code lib/} before it asks the system
   * fontconfig). Native.java puts {@code lib/} next to the executable.
   */
  private static void useExecutableDirAsJavaHome() {
    if (System.getProperty("java.home") != null) {
      return;
    }
    ProcessHandle.current()
        .info()
        .command()
        .map(command -> java.nio.file.Path.of(command).toAbsolutePath().getParent())
        .ifPresent(dir -> System.setProperty("java.home", dir.toString()));
  }

  /**
   * Java2D shapes text through HarfBuzz with FFM upcalls by default; creating those upcall stubs
   * crashes a Linux native image (seen on linux/arm64, GraalVM 25.0.2). The JDK's JNI layout path
   * is equivalent; Native.java --collect --linux records its metadata with the same setting.
   */
  private static void useJniFontLayoutOnLinux() {
    if (System.getProperty("os.name", "").startsWith("Linux")
        && System.getProperty("sun.font.layout.ffm") == null) {
      System.setProperty("sun.font.layout.ffm", "false");
    }
  }
}
