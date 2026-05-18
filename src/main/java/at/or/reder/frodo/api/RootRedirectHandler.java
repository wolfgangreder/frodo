/*
 * Copyright 2026 Wolfgang Reder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package at.or.reder.frodo.api;

import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Redirects bare entry-point paths to the application root so browsers land
 * on the SPA without a 404.
 *
 * <p>{@code @Observes Router} installs into Quarkus's <em>inner</em> (sub-)router
 * where the root-path prefix is already stripped for route matching.
 * {@code ctx.request().path()} always returns the <em>raw</em>, unstripped URI,
 * so we use that for exact comparisons instead of relying on Vert.x path patterns.</p>
 *
 * <ul>
 *   <li>{@code /}       → {@code /frodo/} (301) — caught if a request somehow
 *       reaches the inner router with raw path "/"</li>
 *   <li>{@code /frodo}  → {@code /frodo/} (301) — missing trailing slash</li>
 * </ul>
 */
@ApplicationScoped
public class RootRedirectHandler {

  @ConfigProperty(name = "quarkus.http.root-path", defaultValue = "/")
  String rootPath;

  public void addRoutes(@Observes Router router) {
    String target = rootPath.endsWith("/") ? rootPath : rootPath + "/";
    // rootNoSlash = "/frodo"  (empty when root-path is "/")
    String rootNoSlash = target.length() > 1 ? target.substring(0, target.length() - 1) : "";

    // Catch-all with highest priority; use raw request path for exact matching.
    // Calling ctx.next() for everything else keeps all other routes untouched.
    router.route().order(Integer.MIN_VALUE).handler(ctx -> {
      String rawPath = ctx.request().path();
      if (rawPath.equals("/") || (!rootNoSlash.isEmpty() && rawPath.equals(rootNoSlash))) {
        ctx.response()
          .setStatusCode(301)
          .putHeader("Location", target)
          .end();
      } else {
        ctx.next();
      }
    });
  }

}
