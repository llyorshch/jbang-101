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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

/** Client onboarding/offboarding sample: Spring Boot + Flowable CMMN, run with JBang only. */
@SpringBootApplication(scanBasePackages = "app")
@EnableJdbcRepositories(basePackages = "app")
public class ClientsApp {

  public static void main(String[] args) {
    SpringApplication.run(ClientsApp.class, args);
  }
}
