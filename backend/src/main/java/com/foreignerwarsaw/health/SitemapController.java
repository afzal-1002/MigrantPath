package com.foreignerwarsaw.health;

import com.foreignerwarsaw.procedure.core.Procedure;
import com.foreignerwarsaw.procedure.core.ProcedureQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public sitemap (Phase 11 brief §88/§91) - the homepage, the procedures index, and every currently
 * PUBLISHED procedure's public detail page, generated from the same {@link
 * ProcedureQueryService#listPublished()} the public procedures API itself uses, so it can never
 * drift out of sync with what's actually live (never a hand-maintained or build-time-frozen list).
 * Deliberately excludes every personalized/private route (admin, assessments, cases,
 * recommendations, auth) - those are additionally disallowed in {@code robots.txt}
 * (frontend/public/robots.txt), but robots.txt is only advisory; simply never listing them here is
 * the stronger guarantee. {@code APP_PUBLIC_URL} (brief §121, same variable the email links already
 * use - see {@code AuthProperties.frontendBaseUrl}) supplies the scheme+host every URL is built
 * against - never hard-coded to localhost.
 */
@RestController
public class SitemapController {

  private final ProcedureQueryService procedureQueryService;
  private final String publicUrl;

  public SitemapController(
      ProcedureQueryService procedureQueryService,
      @Value("${app.auth.frontend-base-url}") String publicUrl) {
    this.procedureQueryService = procedureQueryService;
    // Trim a trailing slash so every concatenation below produces exactly one "/".
    this.publicUrl =
        publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
  }

  @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
  public String sitemap() {
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
    url(xml, publicUrl + "/");
    url(xml, publicUrl + "/procedures");
    // Phase 11 brief §192/§91 - the four draft legal/policy pages are public and
    // deliberately indexable, unlike every route robots.txt disallows.
    url(xml, publicUrl + "/privacy");
    url(xml, publicUrl + "/terms");
    url(xml, publicUrl + "/cookies");
    url(xml, publicUrl + "/disclaimer");
    for (Procedure procedure : procedureQueryService.listPublished()) {
      url(xml, publicUrl + "/procedures/" + procedure.getCode());
    }
    xml.append("</urlset>\n");
    return xml.toString();
  }

  private void url(StringBuilder xml, String loc) {
    xml.append("  <url><loc>").append(loc.replace("&", "&amp;")).append("</loc></url>\n");
  }
}
