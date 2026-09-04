package com.foreignerwarsaw.health;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foreignerwarsaw.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Phase 11 brief §88/§91 - the sitemap is public (no auth required, matching robots.txt/search
 * crawlers), well-formed, and includes only the homepage/procedures index/public procedure pages -
 * never an admin, assessment, case, or recommendation URL (brief §91's explicit exclusion list).
 */
class SitemapControllerTest extends AbstractIntegrationTest {

  @Test
  void sitemapIsPubliclyReachableAndListsOnlyPublicPages() throws Exception {
    mockMvc
        .perform(get("/sitemap.xml"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/xml"))
        .andExpect(content().string(containsString("<urlset")))
        .andExpect(content().string(containsString("/procedures</loc>")))
        .andExpect(content().string(containsString("/privacy</loc>")))
        .andExpect(content().string(containsString("/terms</loc>")))
        .andExpect(content().string(containsString("/cookies</loc>")))
        .andExpect(content().string(containsString("/disclaimer</loc>")))
        .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/admin"))))
        .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/assessment"))))
        .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/cases"))))
        .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/recommendations"))));
  }
}
